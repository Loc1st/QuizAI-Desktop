package com.quizai.client.ui.controllers;

import java.io.IOException;
import com.quizai.client.ui.navigation.Screen;
import com.quizai.client.ui.navigation.ViewLoader;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public final class ShellController {
    @FXML private SidebarController sidebarController;
    @FXML private Label pageTitle;
    @FXML private StackPane contentHost;

    @FXML
    private void initialize() {
        sidebarController.setOnNavigate(this::navigate);
        navigate(Screen.OVERVIEW);
    }

    public void navigate(Screen screen) {
        try {
            ViewLoader.LoadedView view;
            if (screen == Screen.OVERVIEW) {
                view = ViewLoader.load("/fxml/overview.fxml");
                ((OverviewController) view.controller()).setOnNavigate(this::navigate);
            } else {
                view = ViewLoader.load("/fxml/placeholder.fxml");
                ((PlaceholderController) view.controller()).configure(screen, () -> navigate(Screen.OVERVIEW));
            }
            contentHost.getChildren().setAll(view.root());
            sidebarController.select(screen);
            pageTitle.setText(screen.title());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load UI destination: " + screen, exception);
        }
    }
}
