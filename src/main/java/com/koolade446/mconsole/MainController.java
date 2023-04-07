package com.koolade446.mconsole;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MainController {

    //FXML GUI variables
    @FXML
    public ComboBox<String> typeBox;
    @FXML
    public ComboBox<String> versionBox;
    @FXML
    public TextArea console;
    @FXML
    public Button startStopButton;
    @FXML
    public Button killButton;
    @FXML
    Button changeVersionButton;
    @FXML
    Label pathContainer;
    @FXML
    TextField commandBox;
    @FXML
    Button ePropButton;
    @FXML
    Pane mainPane;
    @FXML
    TextField ramAmount;
    @FXML
    ComboBox<String> ramTypeBox;

    //private variables
    private final Map<String, List<String>> typeMap;
    public final Map<String, String> localConfig;
    private final File dataFile;
    private final String[] endpoints;
    private final Map<String, String> staticVars = new HashMap<>();
    private final ExecutorService executorService;
    private File serverJar;

    //Public variables
    public final Map<String, String> config;
    public Path directoryPath;
    public Process process;
    public File localDataFile;
    public PrintWriter outputStreamWriter;


    //Initializes our variables
    public MainController() {
        try {
            typeMap = new HashMap<>();
            executorService = Executors.newFixedThreadPool(1);
            directoryPath = Path.of(new File(".").getCanonicalPath());
            serverJar = new File(directoryPath + "/server.jar");
            outputStreamWriter = null;
            config = new HashMap<>();
            localConfig = new HashMap<>();
            dataFile = new File("./MConsole-Data/data.dat");
            endpoints = new String[]{
                    "servers/spigot",
                    "servers/paper",
                    "vanilla/vanilla",
                    "vanilla/snapshot",
                    "modded/forge",
                    "modded/fabric"
            };

            staticVars.put("current", new File(".").getCanonicalPath());
            staticVars.put("home", new File(System.getProperty("user.home")).getCanonicalPath());
            staticVars.put("minecraft", new File(System.getenv("APPDATA") + "/.minecraft").getCanonicalPath());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //Initialize our GUI
    public void initialize() {
        startStopButton.setStyle("-fx-background-image: url('power.png')");
        killButton.setStyle("-fx-background-image: url('kill.png')");
        pathContainer.setText(directoryPath.toString());

        readOrCreateConfig();

        directoryPath = Path.of(config.get("path"));
        pathContainer.setText(directoryPath.toString());
        serverJar = new File(directoryPath + "/server.jar");
        localDataFile = new File(directoryPath + File.separator + "mconsole/mconsole.dat");

        readOrCreateLocalConfig();

        try {
            for (String endpoint : endpoints) {
                List<String> l = new ArrayList<>();
                URL url = new URL("https://serverjars.com/api/fetchAll/" + endpoint);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.connect();

                JSONObject responseJson = new JSONObject(new BufferedReader(new InputStreamReader(con.getInputStream())).lines().collect(Collectors.joining()));

                con.disconnect();

                for (Object obj : responseJson.getJSONArray("response")) {
                    JSONObject jsonObject = (JSONObject) obj;
                    l.add(jsonObject.getString("version"));
                }
                typeMap.put(endpoint.split("/")[1].replace("/", ""), l);
            }
            typeBox.getItems().addAll(typeMap.keySet());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        ramTypeBox.getItems().addAll("G", "M");
        ramTypeBox.setValue(localConfig.get("ram-type"));
        ramAmount.setText(localConfig.get("ram-amount"));

        Verifier.init(this);
        if (!config.get("eula-agreement").equalsIgnoreCase("true")) showEula();
    }

    //Called when the "Edit Properties" button is pressed
    public void editProperties(ActionEvent actionEvent) {
        try {
            Parent root;
            root = FXMLLoader.load(getClass().getResource("properties-window.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Edit Server Properties");
            stage.getIcons().add(new Image(ApplicationMain.class.getClassLoader().getResourceAsStream("app-icon.jpg")));
            Scene scene = new Scene(root, 604, 326);
            stage.setScene(scene);
            stage.show();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //Called when the "Update" button is pressed after a new type and version is selected
    public void updateVersion(ActionEvent actionEvent) {
        printToConsole("INFO", "Attempting to update to " + typeBox.getValue() + " " + versionBox.getValue());

        Runnable runnable = () -> {
            try {
                URL url = new URL("https://serverjars.com/api/fetchJar/" + endpoints[getClosestIndex(endpoints, typeBox.getValue())] + "/" + versionBox.getValue());
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                Platform.runLater(()->printToConsole("INFO", "Connecting to servers..."));
                con.connect();

                Platform.runLater(()->printToConsole("INFO", "Downloading new jar file..."));
                InputStream is = con.getInputStream();

                Platform.runLater(()->printToConsole("INFO", "Writing new jar file..."));
                byte[] jarFileData = is.readAllBytes();

                //Because forge just HAS TO BE DIFFERENT (ಠ_ಠ)
                if (typeBox.getValue().equals("forge")) {
                    FileOutputStream fos = new FileOutputStream(directoryPath + File.separator + "installer.jar");
                    fos.write(jarFileData, 0, jarFileData.length);

                    Platform.runLater(()->printToConsole("INFO", "Server is forge, running installer..."));

                    ProcessBuilder pb = new ProcessBuilder("java", "-jar", "installer.jar", "--installServer");
                    pb.directory(directoryPath.toFile());
                    Process nestedProcess = pb.start();
                    BufferedReader br = new BufferedReader(new InputStreamReader(nestedProcess.getInputStream()));
                    String line;
                    while (nestedProcess.isAlive()) {
                        if ((line = br.readLine()) != null) {
                            System.out.println(line);
                        }
                    }

                    localConfig.put("is-forge", "true");

                    FileInputStream fis = new FileInputStream(directoryPath + File.separator + "run.bat");
                    BufferedReader fileReader = new BufferedReader(new InputStreamReader(fis));
                    StringBuilder sb = new StringBuilder();
                    while ((line = fileReader.readLine()) != null) {
                        if (line.contains("java")) {
                            line = line.replace("%*", "nogui %*");
                        }
                        if (line.contains("pause")) line = line.replace("pause", "");
                        sb.append(line + "\n");
                    }

                    FileOutputStream fileWriter = new FileOutputStream(directoryPath + File.separator + "run.bat");
                    fileWriter.write(sb.toString().getBytes(StandardCharsets.UTF_8), 0, sb.toString().getBytes().length);

                    Platform.runLater(()->printToConsole("INFO", "Successfully installed forge server!"));
                    br.close();
                    fos.close();
                    fis.close();
                    fileReader.close();
                }

                //Hey forge, notice how fabric made a mod loader that works JUST LIKE VANILLA ¯\_ಠ_ಠ_/¯
                else {
                    FileOutputStream fos = new FileOutputStream(serverJar);
                    fos.write(jarFileData, 0, jarFileData.length);
                    fos.close();
                    localConfig.put("is-forge", "false");
                }

                is.close();
                con.disconnect();

                Platform.runLater(()->printToConsole("INFO", "Version successfully updated!"));
            }
            catch (Exception e) {
                Platform.runLater(()->printToConsole("ERROR", "Error when updating version. Please make sure you select a type and version"));
                e.printStackTrace();
            }
        };
        executorService.execute(runnable);
    }

    //Called when the "Change" button is pressed to change the running directory
    //"Never change we love you the way you are" - My dad
    public void changeFolder(MouseEvent event) throws IOException {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select minecraft server folder");
        File file = directoryChooser.showDialog(mainPane.getScene().getWindow());

        if (file != null) {
            directoryPath = Path.of(file.getCanonicalPath());
            pathContainer.setText(directoryPath.toString());
            serverJar = new File(directoryPath + "/server.jar");
            localDataFile = new File(directoryPath + File.separator + "mconsole/mconsole.dat");

            readOrCreateLocalConfig();

            if (config.get("save-path").equals("true")) {
                config.put("path", directoryPath.toString());
            }
        }
    }

    //Called when the Start/Stop button is pressed
    public void togglePowerState(ActionEvent actionEvent) {
        if (process == null || !process.isAlive()) {

            //Verify all the files to make sure no corruptions happened while the server was offline
            Verifier.runChecks();

            console.clear();

            //Auto agree to the eula
            try {
                File eula = new File(directoryPath + "/eula.txt");
                if (!eula.exists()) eula.createNewFile();
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(eula)));
                List<String> strs = br.lines().toList();

                if (!strs.contains("eula=true")) {
                    String agree = "eula=true";
                    FileOutputStream fos = new FileOutputStream(eula);
                    fos.write(agree.getBytes(StandardCharsets.UTF_8), 0, agree.getBytes(StandardCharsets.UTF_8).length);
                }

            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }

            Runnable runnable = ()-> {
                try {
                    //Create process builder and set directory
                    String allocatedRam = "-Xmx" + ramAmount.getText() + ramTypeBox.getValue();
                    System.out.println(allocatedRam);

                    //Because Forge just HAS to be different, we have to rewrite an entire file rather than just changing one command line argument
                    //THANKS FORGE (ノಠ益ಠ)ノ彡┻━┻
                    if (localConfig.get("is-forge").equals("true")) {
                        Platform.runLater(()-> printToConsole("INFO", "Building forge run scripts"));
                        FileInputStream fis = new FileInputStream(directoryPath + File.separator + "user_jvm_args.txt");
                        BufferedReader fileReader = new BufferedReader(new InputStreamReader(fis));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = fileReader.readLine()) != null) {
                            if (line.contains("-Xmx")) {
                                line = allocatedRam;
                            }
                            sb.append(line);
                        }
                        FileOutputStream fileWriter = new FileOutputStream(directoryPath + File.separator + "user_jvm_args.txt");
                        fileWriter.write(sb.toString().getBytes(StandardCharsets.UTF_8), 0, sb.toString().getBytes().length);

                        fis.close();
                        fileReader.close();
                    }

                    Platform.runLater(()->printToConsole("INFO", "Server is starting"));
                    ProcessBuilder pb = localConfig.get("is-forge").equals("true") ? new ProcessBuilder("cmd", "/c", "run.bat") : new ProcessBuilder("java", "-jar", allocatedRam, "-Xms1G", "server.jar", "nogui");
                    pb.directory(new File(directoryPath.toString()));

                    //Start the server
                    process = pb.start();

                    //Set up readers and writers for reading from and printing to the server binary
                    BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    setOutputStreamWriter(new PrintWriter(process.getOutputStream()));

                    //Start printing output to the console
                    String line;
                    while (process.isAlive()) {
                        if ((line = br.readLine()) != null) {
                            String finalLine = line;
                            Platform.runLater(() -> printToConsole("MINECRAFT", finalLine));
                        }
                    }
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };

            //Run everything in a new thread to make sure the GUI remains responsive and operable
            executorService.execute(runnable);
        }
        else if (process.isAlive()) {
            outputStreamWriter.println("stop");
            outputStreamWriter.flush();
        }
    }

    //For the last time, killing the server isn't a crime, it's not a living thing
    public void killServer(ActionEvent actionEvent) {
        if (process.isAlive()) {
            //Not that kind of alive (－‸ლ)
            process.destroyForcibly();
            printToConsole("WARN", "Server was forcibly terminated.");
        }
    }

    public void sendCommand(ActionEvent event) {
        outputStreamWriter.println(commandBox.getText());
        outputStreamWriter.flush();
        commandBox.setText("");
    }

    public void onKeyPress(KeyEvent keyEvent) {
        if (keyEvent.getCode().equals(KeyCode.ENTER)) {
            outputStreamWriter.println(commandBox.getText());
            outputStreamWriter.flush();
            commandBox.setText("");
        }
    }



    //Utility methods, they kinda ugly so the get to get hidden down here

    //This is such a bad way of doing this lmao, but I can't be bothered to think of a better way so here you go
    private int getClosestIndex(String[] array, String option) {
        int index = -1;
        for (String str : array) {
            index++;
            if (str.contains(option)) return index;
        }
        return 0;
    }
    public void printToConsole(String sender, String message) {
        //Kinda a dirty way to do it but this is how we detect when all the files are released from the server and can now be read and their checksums calculated
        //You know this would be a lot easier if minecraft didn't lock the files for the entire time the server is running.
        //It would also help Minecraft not be so much of a ram hog
        if (message.toLowerCase().contains("all dimensions are saved")) {
            Verifier.updateSums();
        }

        //Forward the message to the console in the format "[SENDER] message content"
        console.appendText(String.format(
                "[%s] %s\n",
                sender.toUpperCase(),
                message
        ));
    }

    //Updates the options for versions based on the selected type
    public void changeVersionOptions() {
        versionBox.getItems().clear();
        //This version box isn't worth anything, it has no value... I'll show myself out
        versionBox.setValue(null);
        versionBox.getItems().addAll(typeMap.get(typeBox.getValue()));
    }

    //These two methods are just here for thread safety purposes
    public void setOutputStreamWriter(PrintWriter outputStreamWriter) {
        this.outputStreamWriter = outputStreamWriter;
    }
    public PrintWriter getOutputStreamWriter() {
        return outputStreamWriter;
    }

    private void showEula() {
        try {
            Parent root;
            root = FXMLLoader.load(getClass().getResource("eula.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Edit Server Properties");
            stage.getIcons().add(new Image(ApplicationMain.class.getClassLoader().getResourceAsStream("app-icon.jpg")));
            Scene scene = new Scene(root, 584, 549);
            stage.setScene(scene);

            stage.setAlwaysOnTop(true);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setResizable(false);
            stage.setOnCloseRequest(Event::consume);
            mainPane.setDisable(true);
            stage.setOnHidden(event -> mainPane.setDisable(false));

            stage.show();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //This code is ugly, so I moved it to a utility method, so I can hide it ;)
    public void readOrCreateConfig() {
        try {
            if (!dataFile.exists()) {
                try {
                    dataFile.getParentFile().mkdirs();
                    dataFile.createNewFile();
                    BufferedInputStream bis = new BufferedInputStream(getClass().getClassLoader().getResourceAsStream("default-data.dat"));
                    byte[] fileBytes = new byte[bis.available()];

                    bis.read(fileBytes);
                    OutputStream os = new FileOutputStream(dataFile);
                    os.write(fileBytes, 0, fileBytes.length);
                    bis.close();
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(dataFile)));
            String line;
            while ((line = br.readLine()) != null) {
                String[] splitLine = line.split("=", 0);
                if (splitLine.length > 1) {
                    if (splitLine[1].startsWith("$")) {
                        String key = splitLine[1].replace("$", "");
                        if (staticVars.keySet().contains(key)) {
                            config.put(splitLine[0], staticVars.get(key));
                        } else {
                            printToConsole("ERROR", "No such variable as $" + key + ". Please check the config file. Falling back to default");
                            config.put(splitLine[0], staticVars.get("current"));
                        }
                    } else {
                        config.put(splitLine[0], splitLine[1]);
                    }
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void readOrCreateLocalConfig() {
        try {
            if (!localDataFile.exists()) {
                try {
                    localDataFile.getParentFile().mkdirs();
                    localDataFile.createNewFile();
                    BufferedInputStream bis = new BufferedInputStream(getClass().getClassLoader().getResourceAsStream("default-local-data.dat"));
                    byte[] fileBytes = new byte[bis.available()];

                    bis.read(fileBytes);
                    OutputStream os = new FileOutputStream(localDataFile);
                    os.write(fileBytes, 0, fileBytes.length);
                    bis.close();
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(localDataFile)));
            String line;
            while ((line = br.readLine()) != null) {
                String[] splitLine = line.split("=", 0);
                if (splitLine.length > 1) {
                    localConfig.put(splitLine[0], splitLine[1]);
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void saveConfigs() {
        StringBuilder sb = new StringBuilder();
        for (String string : config.keySet()) {
            sb.append(String.format(
                    "%s=%s\n",
                    string,
                    config.get(string)
            ));
        }

        try {
            OutputStream os = new FileOutputStream(dataFile);
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8), 0 , sb.toString().getBytes(StandardCharsets.UTF_8).length);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!localDataFile.exists()) {
            try {
                localDataFile.getParentFile().mkdirs();
                localDataFile.createNewFile();
                BufferedInputStream bis = new BufferedInputStream(getClass().getClassLoader().getResourceAsStream("default-local-data.dat"));
                byte[] fileBytes = new byte[bis.available()];

                bis.read(fileBytes);
                OutputStream os = new FileOutputStream(localDataFile);
                os.write(fileBytes, 0, fileBytes.length);
                bis.close();
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        sb = new StringBuilder();
        //I had to add this line for a reason, if you're reading this you know who you are ¬_¬
        sb.append("# DO NOT MODIFY THESE VALUES IF YOU DO NOT KNOW WHAT YOU ARE DOING\n");
        for (String string : localConfig.keySet()) {
            sb.append(String.format(
                    "%s=%s\n",
                    string,
                    localConfig.get(string)
            ));
        }

        try {
            OutputStream os = new FileOutputStream(localDataFile);
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8), 0 , sb.toString().getBytes(StandardCharsets.UTF_8).length);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //Make sure everything shuts down nice and cleanly leaving no threads running (IM LOOKING AT YOU MINECRAFT)
    public void runExitTasks() {
        //Initiate shutdown of the minecraft server using the "stop" command to cleanly save worlds and data
        if (getOutputStreamWriter() != null) {
            getOutputStreamWriter().println("stop");
            getOutputStreamWriter().flush();
            getOutputStreamWriter().close();
        }

        //Make sure Minecraft doesn't choose violence and hog up a thread for eternity (... or until you restart your pc)
        ExecutorService executor = Executors.newFixedThreadPool(1);

        executor.execute(() -> {
            try {
                //Save shutdown values
                localConfig.put("ram-amount", ramAmount.getText());
                localConfig.put("ram-type", ramTypeBox.getValue());

                executorService.shutdown();
                saveConfigs();
                if (process != null) {
                    if (process.isAlive()) {
                        //Sometimes minecraft can hang especially if the reason the user is closing is because of a "soft crash"
                        //While the ideal solution would be to use the "kill" button some users may not do that so just in case
                        //we need to add a time-out that will kill the minecraft thread after 60 seconds if it does not close nicely to prevent thread locking
                        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) process.destroyForcibly();
                    }
                }
                Verifier.deInit();
                Platform.runLater(Platform::exit);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        executor.shutdown();
    }
}