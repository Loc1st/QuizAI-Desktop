package com.quizai.client.ui.controllers;

import com.quizai.common.api.ApiException;
import com.quizai.common.api.QuizApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

abstract class AsyncController implements AutoCloseable {
    @FXML protected VBox workspace;
    @FXML protected Label feedback;
    @FXML protected ProgressIndicator progress;
    protected QuizApiClient api;
    protected Runnable onExpired;
    protected boolean active = true;
    private boolean busy;

    protected void configureApi(QuizApiClient api, Runnable onExpired) { this.api=api; this.onExpired=onExpired; }
    protected <T> void run(Supplier<CompletableFuture<T>> action, Consumer<T> success) {
        run(action,success,error->{});
    }
    protected <T> void run(Supplier<CompletableFuture<T>> action, Consumer<T> success, Consumer<Throwable> failed) {
        if (!active || busy) return;
        busy=true; workspace.setDisable(true); progress.setVisible(true); progress.setManaged(true); feedback.setText("Đang xử lý…");
        CompletableFuture<T> operation;
        try { operation=action.get(); } catch (RuntimeException error) { operation=CompletableFuture.failedFuture(error); }
        operation.whenComplete((value,error) -> Platform.runLater(() -> {
            if (!active) return;
            busy=false; workspace.setDisable(false); progress.setVisible(false); progress.setManaged(false);
            if (error==null) { feedback.setText(""); success.accept(value); }
            else {
                Throwable cause=error; while(cause.getCause()!=null && !(cause instanceof ApiException)) cause=cause.getCause();
                if(cause instanceof ApiException failure && failure.status()==401) onExpired.run();
                else {
                    failed.accept(cause);
                    feedback.setText(AuthController.friendly(cause));
                    if(workspace.getChildren().isEmpty())workspace.getChildren().add(button("Thử tải lại",()->run(action,success,failed)));
                }
            }
        }));
    }
    protected static Label label(String text, String style) { Label value=new Label(text); value.setWrapText(true); value.getStyleClass().add(style); return value; }
    protected static Button button(String text, Runnable action) { Button value=new Button(text); value.setOnAction(e -> action.run()); return value; }
    protected static VBox card(Node... nodes) { VBox value=new VBox(12,nodes); value.getStyleClass().add("card"); return value; }
    protected static TextField field(String prompt) { TextField value=new TextField(); value.setPromptText(prompt); return value; }
    protected static HBox row(Node... nodes) { HBox value=new HBox(12,nodes); value.setAlignment(javafx.geometry.Pos.CENTER_LEFT); return value; }
    @Override public void close() { active=false; }
}
