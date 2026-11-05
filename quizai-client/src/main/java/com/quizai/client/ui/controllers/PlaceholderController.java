package com.quizai.client.ui.controllers;

import com.quizai.client.ui.navigation.Screen;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class PlaceholderController {
    @FXML private Label title;
    @FXML private Label description;
    @FXML private VBox examStates;
    private Runnable onBack = () -> { };

    public void configure(Screen screen, Runnable onBack) {
        title.setText(screen.title());
        description.setText(screen.description());
        this.onBack = onBack;
        boolean showStates = screen == Screen.EXAMS;
        examStates.setVisible(showStates);
        examStates.setManaged(showStates);
    }

    @FXML
    private void backToOverview() { onBack.run(); }
}
