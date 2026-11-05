package com.quizai.client;

import com.quizai.client.ui.navigation.ViewLoader;
import javafx.application.Application;
import javafx.stage.Stage;

public class QuizAIClientApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("QuizAI Desktop - Student");
        stage.setScene(ViewLoader.loadScene("/fxml/shell.fxml"));
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
