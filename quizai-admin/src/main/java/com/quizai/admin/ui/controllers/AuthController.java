package com.quizai.admin.ui.controllers;

import com.quizai.common.api.ApiException;
import com.quizai.common.api.QuizApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.concurrent.CompletableFuture;

/** Owns only the teacher authentication form; network completion returns to the FX thread. */
public final class AuthController {
    @FXML private Label roleLabel;
    @FXML private Label message;
    @FXML private TextField email;
    @FXML private PasswordField password;
    @FXML private PasswordField confirmation;
    @FXML private CheckBox registerMode;
    @FXML private Button submit;
    @FXML private ProgressIndicator progress;
    @FXML private VBox fields;
    private QuizApiClient api;
    private Runnable onAuthenticated;
    private boolean active = true;
    private boolean busy;

    @FXML private void initialize() {
        registerMode.selectedProperty().addListener((value, oldValue, selected) -> updateMode());
        updateMode();
    }

    public void configure(QuizApiClient api, Runnable onAuthenticated) {
        this.api = api;
        this.onAuthenticated = onAuthenticated;
        roleLabel.setText("Vai trò: Giảng viên (TEACHER), có quyền ngay sau đăng ký. Danh tính chưa được xác minh.");
    }

    private void updateMode() {
        confirmation.setVisible(registerMode.isSelected());
        confirmation.setManaged(registerMode.isSelected());
        submit.setText(registerMode.isSelected() ? "Đăng ký giảng viên" : "Đăng nhập");
        message.setText("");
    }

    @FXML private void submit() {
        if (busy || api == null) return;
        String address = email.getText().trim();
        String secret = password.getText();
        boolean registering = registerMode.isSelected();
        if (address.isBlank() || secret.isBlank()) {
            message.setText("Vui lòng nhập email và mật khẩu.");
            return;
        }
        if (registering && !secret.equals(confirmation.getText())) {
            message.setText("Mật khẩu xác nhận chưa khớp.");
            return;
        }
        setBusy(true);
        message.setText(registering ? "Đang đăng ký…" : "Đang đăng nhập…");
        CompletableFuture<?> operation = registering ? api.register(address, secret) : api.login(address, secret);
        password.clear();
        confirmation.clear();
        operation.whenComplete((result, failure) -> Platform.runLater(() -> {
            if (!active) {
                if (!registering && failure == null) api.logout();
                return;
            }
            setBusy(false);
            if (failure != null) {
                message.setText(friendly(failure));
            } else if (registering) {
                registerMode.setSelected(false);
                message.setText("Đăng ký thành công. Nhập mật khẩu để đăng nhập.");
                password.requestFocus();
            } else {
                onAuthenticated.run();
            }
        }));
    }

    private void setBusy(boolean value) {
        busy = value;
        fields.setDisable(value);
        submit.setDisable(value);
        registerMode.setDisable(value);
        progress.setVisible(value);
        progress.setManaged(value);
    }

    public void deactivate() { active = false; password.clear(); confirmation.clear(); }

    static String friendly(Throwable failure) {
        while (failure.getCause() != null && !(failure instanceof ApiException)) failure = failure.getCause();
        return failure instanceof ApiException ? failure.getMessage() : "Thao tác chưa hoàn tất. Vui lòng thử lại.";
    }
}
