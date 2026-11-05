package com.quizai.client.ui.components;

import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/** Small, locally drawn geometric icons. No font or external icon library required. */
public final class LineIcon extends StackPane {
    private String kind = "home";
    private final SVGPath path = new SVGPath();

    public LineIcon() {
        getStyleClass().add("line-icon");
        setMinSize(20, 20);
        setPrefSize(20, 20);
        setMaxSize(20, 20);
        path.getStyleClass().add("icon-stroke");
        path.getTransforms().add(new Scale(0.78, 0.78));
        getChildren().add(path);
        setKind(kind);
        setMouseTransparent(true);
    }

    public String getKind() { return kind; }

    public void setKind(String value) {
        kind = value;
        path.setContent(switch (value) {
            case "book" -> "M3 4 Q8 2 12 5 Q16 2 21 4 L21 20 Q16 18 12 21 Q8 18 3 20 Z M12 5 L12 21";
            case "layers" -> "M12 3 L22 8 L12 13 L2 8 Z M2 13 L12 18 L22 13 M2 18 L12 23 L22 18";
            case "file" -> "M5 2 L14 2 L20 8 L20 22 L5 22 Z M14 2 L14 8 L20 8 M9 13 L16 13 M9 17 L16 17";
            case "spark" -> "M12 2 L15 9 L22 12 L15 15 L12 22 L9 15 L2 12 L9 9 Z M21 2 L21 6 M19 4 L23 4";
            case "clipboard" -> "M8 4 L4 4 L4 22 L20 22 L20 4 L16 4 M8 2 L16 2 L16 6 L8 6 Z M8 11 L16 11 M8 15 L16 15 M8 19 L13 19";
            case "users" -> "M8 3 A4 4 0 1 1 7.99 3 M2 22 L2 18 Q2 13 8 13 Q14 13 14 18 L14 22 M17 4 Q23 4 22 9 Q22 12 18 12 M18 15 Q23 15 23 20 L23 22";
            case "chart" -> "M3 3 L3 21 L23 21 M7 16 L7 11 M12 16 L12 6 M17 16 L17 9 M22 16 L22 3";
            case "check" -> "M4 12 L9 17 L21 5 M21 13 L21 21 L3 21 L3 3 L15 3";
            case "clock" -> "M12 2 A10 10 0 1 1 11.99 2 M12 6 L12 12 L16 15";
            case "arrow" -> "M4 12 L21 12 M15 6 L21 12 L15 18";
            default -> "M3 10 L12 3 L21 10 L21 22 L15 22 L15 15 L9 15 L9 22 L3 22 Z";
        });
    }
}
