package com.quizai.client.ui.components;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** One consistent statistic card, reused across the overview. Values are demo data. */
public final class MetricCard extends VBox {
    private final Label title = new Label();
    private final Label value = new Label();
    private final Label note = new Label();
    private final LineIcon icon = new LineIcon();

    public MetricCard() {
        getStyleClass().add("metric-card");
        title.getStyleClass().add("muted");
        value.getStyleClass().add("metric-value");
        note.getStyleClass().add("caption");
        title.setWrapText(true);
        note.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        StackIcon badge = new StackIcon(icon);
        HBox heading = new HBox(8, title, spacer, badge);
        heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        getChildren().addAll(heading, value, note);
        setSpacing(8);
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
    }

    public String getTitle() { return title.getText(); }
    public void setTitle(String text) { title.setText(text); }
    public String getValue() { return value.getText(); }
    public void setValue(String text) { value.setText(text); }
    public String getNote() { return note.getText(); }
    public void setNote(String text) { note.setText(text); }
    public String getIcon() { return icon.getKind(); }
    public void setIcon(String kind) { icon.setKind(kind); }

    private static final class StackIcon extends javafx.scene.layout.StackPane {
        StackIcon(LineIcon icon) {
            getStyleClass().add("metric-icon");
            getChildren().add(icon);
        }
    }
}
