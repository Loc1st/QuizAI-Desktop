package com.quizai.admin;

import com.quizai.admin.ui.navigation.ViewLoader;
import javafx.application.Application;
import javafx.stage.Stage;

public class QuizAIAdminApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("QuizAI Desktop - Teacher Admin");
        stage.setScene(ViewLoader.loadScene("/fxml/shell.fxml"));
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
