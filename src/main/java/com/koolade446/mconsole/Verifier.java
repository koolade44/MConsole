package com.koolade446.mconsole;

import javafx.application.Platform;
import org.apache.commons.codec.binary.Hex;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Verifier {
    private static ExecutorService executorService = Executors.newFixedThreadPool(1);
    private static Boolean run;
    private static MainController mainController;
    private static String worldPath;
    public static Map<File, Long> failedChecksums = new HashMap<>();
    public static Map<File, String> checksums = new HashMap<>();
    private static File checksumFile;
    private static File failedChecksumsFile;

    public static void init(MainController controller) {
        mainController = controller;
        worldPath = mainController.directoryPath.toString() + File.separator + "world";
        checksumFile = new File(mainController.directoryPath + File.separator + "mconsole/checksums.dat");
        failedChecksumsFile = new File(mainController.directoryPath + File.separator + "mconsole/failed-checksums.dat");

        updateMapsFromFiles();
    }

    public static void updateSums() {
        Runnable runnable = () -> {
            try {
                Files.walkFileTree(Path.of(worldPath), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            File f = file.toFile();
                            FileInputStream fis = new FileInputStream(f);
                            MessageDigest md = MessageDigest.getInstance("MD5");
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                md.update(buffer, 0, bytesRead);
                            }
                            byte[] digest = md.digest();
                            fis.close();
                            String checksum = Hex.encodeHexString(digest);

                            checksums.put(f, checksum);

                            return FileVisitResult.CONTINUE;
                        } catch (NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> mainController.printToConsole("ERROR", "Unable to check one or more files! If you experience any issues please run the \"verify\" command or the troubleshooter"));
                System.out.println(e.getMessage());
            }
        };
        executorService.execute(runnable);
    }

    public static void runChecks() {
        Runnable runnable = () -> {
            try {
                Files.walkFileTree(Path.of(worldPath), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            File f = file.toFile();
                            FileInputStream fis = new FileInputStream(f);
                            MessageDigest md = MessageDigest.getInstance("MD5");
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                md.update(buffer, 0, bytesRead);
                            }
                            byte[] digest = md.digest();
                            fis.close();
                            String checksum = Hex.encodeHexString(digest);
                            if (checksums.get(f) != null) {
                                if (!checksum.equals(checksums.get(f))) {
                                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss MM/dd/yyyy");
                                    Platform.runLater(() -> mainController.printToConsole("Alert", String.format(
                                            "One or more of the files on your server has failed verification! This can be due to the file being altered in a normal way but it can also be due to a corruption.\n" +
                                                    "No action will be taken at this time, however if the file fails to verify again it will be replaced from your most recent backup. If you are experiencing issues with the file,\n" +
                                                    "you can run the \"verify\" command or use the troubleshooter to restore any corrupted files.\n" +
                                                    "File: %s\nTime: %s",
                                            f.getName(),
                                            formatter.format(LocalDateTime.now())
                                    )));
                                    failedChecksums.put(f, System.currentTimeMillis());
                                }
                            }


                            return FileVisitResult.CONTINUE;
                        } catch (NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> mainController.printToConsole("ERROR", "Unable to verify one or more files! If you experience any issues please run the \"verify\" command or the troubleshooter"));
                System.out.println(e.getMessage());
            }
        };
        executorService.execute(runnable);
    }

    public static void updateMapsFromFiles() {
        if (checksums.isEmpty() || failedChecksums.isEmpty()) {
            try {
                if (checksumFile.exists()) {
                    FileInputStream fis = new FileInputStream(checksumFile);
                    ObjectInputStream ois = new ObjectInputStream(fis);
                    checksums = (Map<File, String>) ois.readObject();
                    ois.close();
                }

                if (failedChecksumsFile.exists()) {
                    FileInputStream fis = new FileInputStream(failedChecksumsFile);
                    ObjectInputStream ois = new ObjectInputStream(fis);
                    failedChecksums = (Map<File, Long>) ois.readObject();
                    ois.close();
                }
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void deInit() {
        executorService.shutdown();
        try {
            if (!checksumFile.exists()) checksumFile.createNewFile();
            if (!failedChecksumsFile.exists()) failedChecksumsFile.createNewFile();

            FileOutputStream fos = new FileOutputStream(checksumFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(checksums);
            fos.close();
            oos.close();

            fos = new FileOutputStream(failedChecksumsFile);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(failedChecksums);
            fos.close();
            oos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
