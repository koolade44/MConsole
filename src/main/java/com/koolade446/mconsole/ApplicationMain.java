package com.koolade446.mconsole;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;

public class ApplicationMain extends Application {

    public static MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ApplicationMain.class.getResource("console-window.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1030, 570);
        stage.setTitle("Server control panel");
        stage.getIcons().add(new Image(ApplicationMain.class.getClassLoader().getResourceAsStream("app-icon.jpg")));
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        controller = fxmlLoader.getController();

        stage.getScene().getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, this::onWindowClose);
    }

    public static void startApplication() {
        launch();
    }

    public void onWindowClose(WindowEvent event) {
        controller.runExitTasks();
        event.consume();
    }

}