module com.quizai.client {
    requires com.quizai.common;
    requires javafx.controls;
    requires javafx.fxml;

    exports com.quizai.client;
    opens com.quizai.client.ui.components to javafx.fxml;
    opens com.quizai.client.ui.controllers to javafx.fxml;
}
