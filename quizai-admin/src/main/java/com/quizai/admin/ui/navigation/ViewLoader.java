package com.quizai.admin.ui.navigation;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/** Loads classpath resources; navigation replaces content, never the application's Scene. */
public final class ViewLoader {
    private ViewLoader() { }

    public record LoadedView(Parent root, Object controller) { }

    public static LoadedView load(String path) throws IOException {
        FXMLLoader loader = new FXMLLoader(resource(path));
        return new LoadedView(loader.load(), loader.getController());
    }

    public static Scene loadScene(String path) throws IOException {
        Scene scene = new Scene(load(path).root(), 1280, 800);
        scene.getStylesheets().add(resource("/css/theme.css").toExternalForm());
        return scene;
    }

    private static URL resource(String path) {
        return Objects.requireNonNull(ViewLoader.class.getResource(path), "Missing UI resource: " + path);
    }
}
