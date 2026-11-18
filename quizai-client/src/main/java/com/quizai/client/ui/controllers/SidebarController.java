package com.quizai.client.ui.controllers;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import com.quizai.client.ui.components.LineIcon;
import com.quizai.client.ui.navigation.Screen;
import javafx.fxml.FXML;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

public final class SidebarController {
    @FXML private VBox sidebarRoot;
    @FXML private VBox sidebarNote;
    @FXML private VBox navigation;
    private final ToggleGroup group = new ToggleGroup();
    private final Map<Screen, ToggleButton> buttons = new EnumMap<>(Screen.class);
    private Consumer<Screen> onNavigate = screen -> { };

    @FXML
    private void initialize() {
        for (Screen screen : Screen.values()) {
            ToggleButton button = new ToggleButton(screen.title());
            button.setId("nav-" + screen.name().toLowerCase(java.util.Locale.ROOT));
            button.setGraphic(icon(screen.icon()));
            button.setToggleGroup(group);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinHeight(44);
            button.setWrapText(true);
            button.getStyleClass().add("nav-item");
            button.setOnAction(event -> onNavigate.accept(screen));
            buttons.put(screen, button);
            navigation.getChildren().add(button);
        }
        sidebarRoot.heightProperty().addListener((property, oldHeight, newHeight) -> adaptToHeight(newHeight.doubleValue()));
        adaptToHeight(sidebarRoot.getHeight());
    }

    private void adaptToHeight(double height) {
        boolean compact = height < 740;
        sidebarRoot.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("compact"), compact);
        sidebarRoot.setSpacing(compact ? 14 : 22);
        navigation.setSpacing(compact ? 4 : 6);
        sidebarNote.setVisible(!compact);
        sidebarNote.setManaged(!compact);
        buttons.values().forEach(button -> button.setMinHeight(compact ? 40 : 44));
    }

    private LineIcon icon(String kind) {
        LineIcon icon = new LineIcon();
        icon.setKind(kind);
        return icon;
    }

    public void setOnNavigate(Consumer<Screen> handler) { onNavigate = handler; }
    public void select(Screen screen) { group.selectToggle(buttons.get(screen)); }
    public void setSignedIn(boolean value) { navigation.setDisable(!value); }
}
