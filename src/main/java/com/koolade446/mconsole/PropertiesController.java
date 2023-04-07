package com.koolade446.mconsole;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PropertiesController {

    @FXML
    Pane windowPane;
    @FXML
    CheckBox allowFlight;
    @FXML
    CheckBox allowNether;
    @FXML
    CheckBox enableCommandBlocks;
    @FXML
    CheckBox enforceWhitelist;
    @FXML
    CheckBox forceGamemode;
    @FXML
    CheckBox hardcore;
    @FXML
    CheckBox onlineMode;
    @FXML
    CheckBox pvpEnabled;
    @FXML
    CheckBox requireRP;
    @FXML
    CheckBox whiteList;
    @FXML
    ComboBox<String> difficulty;
    @FXML
    ComboBox<String> gamemode;
    @FXML
    TextField motd;
    @FXML
    TextField resourcePack;
    @FXML
    TextField serverIP;
    @FXML
    TextField serverPort;
    @FXML
    TextField simulationDistance;
    @FXML
    TextField spawnProtection;
    @FXML
    TextField maxPlayers;

    MainController controller = ApplicationMain.controller;
    ExecutorService executor;
    File propertiesFile;
    Map<String, Control> controlDict = new HashMap<>();
    String[] difficultyOptions = new String[]{
            "easy",
            "normal",
            "hard"
    };
    String[] gamemodeOptions = new String[]{
            "survival",
            "creative",
            "spectator"
    };

    public void initialize() {

        controlDict.put("allow-flight", allowFlight);
        controlDict.put("allow-nether", allowNether);
        controlDict.put("difficulty", difficulty);
        controlDict.put("enable-command-blocks", enableCommandBlocks);
        controlDict.put("enforce-whitelist", enforceWhitelist);
        controlDict.put("force-gamemode", forceGamemode);
        controlDict.put("gamemode", gamemode);
        controlDict.put("hardcore", hardcore);
        controlDict.put("motd", motd);
        controlDict.put("online-mode", onlineMode);
        controlDict.put("pvp", pvpEnabled);
        controlDict.put("require-resource-pack", requireRP);
        controlDict.put("resource-pack", resourcePack);
        controlDict.put("server-ip", serverIP);
        controlDict.put("server-port", serverPort);
        controlDict.put("simulation-distance", simulationDistance);
        controlDict.put("spawn-protection", spawnProtection);
        controlDict.put("white-list", whiteList);
        controlDict.put("max-players", maxPlayers);

        gamemode.getItems().addAll(gamemodeOptions);
        difficulty.getItems().addAll(difficultyOptions);

        executor = Executors.newFixedThreadPool(1);
        propertiesFile = new File(controller.directoryPath + File.separator + "server.properties");
        Runnable task = ()-> {
            if (!propertiesFile.exists()) {
                try {
                    propertiesFile.createNewFile();
                    InputStream is = this.getClass().getClassLoader().getResourceAsStream("default-server.properties");
                    byte[] fileBytes = new byte[is.available()];
                    is.read(fileBytes);
                    is.close();

                    FileOutputStream fos = new FileOutputStream(propertiesFile);
                    fos.write(fileBytes, 0, fileBytes.length);
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(propertiesFile)));
                List<String> lines = br.lines().toList();
                br.close();
                for (String line : lines) {
                    String[] keyValPair = line.split("=");
                    if (controlDict.get(keyValPair[0]) != null && keyValPair.length > 1) {
                        Control control = controlDict.get(keyValPair[0]);
                        if (control instanceof CheckBox cb) {
                            cb.setSelected(Boolean.parseBoolean(keyValPair[1]));
                        }
                        else if (control instanceof ComboBox<?>) {
                            ComboBox<String> cb = (ComboBox<String>) control;
                            cb.setValue(keyValPair[1]);
                        }
                        else if (control instanceof TextField tf) {
                            tf.setText(keyValPair[1]);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        executor.execute(task);
        executor.shutdown();
    }

    public void updateProperties(ActionEvent actionEvent) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(propertiesFile)));
            List<String> lines = br.lines().toList();
            br.close();

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("E, MMM d, y @ HH:mm:ss");

            if (!propertiesFile.exists()) propertiesFile.createNewFile();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    "#Updated by MConsole %s\n",
                    dateFormatter.format(LocalDateTime.now())
            ));
            for (String line : lines) {
                String[] splitLine = line.split("=");
                if (splitLine.length > 1) {
                    String value = splitLine[1];
                    if (controlDict.get(splitLine[0]) != null) {
                        Control control = controlDict.get(splitLine[0]);
                        if (control instanceof CheckBox cb) {
                            value = cb.isSelected() ? "true" : "false";
                        } else if (control instanceof ComboBox<?>) {
                            ComboBox<String> cb = (ComboBox<String>) control;
                            value = cb.getValue();
                        } else if (control instanceof TextField tf) {
                            value = tf.getText();
                        }
                    }
                    line = splitLine[0] + "=" + value;
                }
                sb.append(line + "\n");
            }
            sb.deleteCharAt(sb.lastIndexOf("\n"));

            byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);

            FileOutputStream fos = new FileOutputStream(propertiesFile);
            fos.write(content, 0, content.length);
            fos.flush();
            fos.close();

            Alert alert = new Alert(Alert.AlertType.INFORMATION,"Properties updated", ButtonType.OK);
            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(getClass().getResource("main.css").toExternalForm());
            dialogPane.getStyleClass().add("main");
            alert.show();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
