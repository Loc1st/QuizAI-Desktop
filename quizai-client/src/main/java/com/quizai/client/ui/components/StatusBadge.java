package com.quizai.client.ui.components;

import javafx.scene.control.Label;

/** Status vocabulary is presentation only; this component never changes an attempt. */
public final class StatusBadge extends Label {
    private String tone = "neutral";

    public StatusBadge() {
        getStyleClass().addAll("status-badge", "neutral");
    }

    public StatusBadge(String text, String tone) {
        this();
        setText(text);
        setTone(tone);
    }

    public String getTone() { return tone; }

    public void setTone(String value) {
        getStyleClass().remove(tone);
        tone = switch (value) {
            case "purple", "green", "amber", "blue" -> value;
            default -> "neutral";
        };
        getStyleClass().add(tone);
    }
}
