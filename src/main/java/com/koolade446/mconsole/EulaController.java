package com.koolade446.mconsole;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebView;

import java.awt.*;

public class EulaController {
    @FXML
    Pane panel;
    @FXML
    CheckBox readUnderstoodCheck;
    @FXML
    Button disagreeButton;
    @FXML
    Button agreeButton;
    @FXML
    WebView webPanel;


    public void initialize() {
        webPanel.getEngine().load("https://www.minecraft.net/en-us/eula");
    }

    public void checkboxToggled(ActionEvent actionEvent) {
        if (readUnderstoodCheck.isSelected()) {
            agreeButton.setId("standard-button");
        }
        else {
            agreeButton.setId("locked-button");
        }
    }

    public void disagreePressed(ActionEvent actionEvent) {
        Platform.exit();
    }

    public void agreePressed(ActionEvent actionEvent) {
        if (readUnderstoodCheck.isSelected()) {
            ApplicationMain.controller.config.put("eula-agreement", "true");
            panel.getScene().getWindow().hide();
        }
        else {
            Toolkit.getDefaultToolkit().beep();
        }
    }
}
