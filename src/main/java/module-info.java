module com.koolade446.mconsole {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.json;
    requires org.jetbrains.annotations;
    requires org.apache.commons.codec;
    requires org.apache.commons.io;
    requires javafx.web;
    requires java.desktop;

    opens com.koolade446.mconsole to javafx.fxml;
    exports com.koolade446.mconsole;
}