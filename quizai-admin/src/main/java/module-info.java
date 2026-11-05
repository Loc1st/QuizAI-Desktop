module com.quizai.admin {
    requires javafx.controls;
    requires javafx.fxml;

    exports com.quizai.admin;
    opens com.quizai.admin.ui.components to javafx.fxml;
    opens com.quizai.admin.ui.controllers to javafx.fxml;
}
