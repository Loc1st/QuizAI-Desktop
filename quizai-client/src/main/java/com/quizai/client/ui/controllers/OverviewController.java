package com.quizai.client.ui.controllers;

import java.util.function.Consumer;
import com.quizai.client.ui.components.StatusBadge;
import com.quizai.client.ui.demo.DemoData;
import com.quizai.client.ui.navigation.Screen;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;

public final class OverviewController {
    @FXML private BarChart<String, Number> activityChart;
    @FXML private TableView<DemoData.Assignment> overviewTable;
    @FXML private TableColumn<DemoData.Assignment, String> nameColumn;
    @FXML private TableColumn<DemoData.Assignment, String> subjectColumn;
    @FXML private TableColumn<DemoData.Assignment, String> deadlineColumn;
    @FXML private TableColumn<DemoData.Assignment, String> statusColumn;
    private Consumer<Screen> onNavigate = screen -> { };

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().title()));
        subjectColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().subject()));
        deadlineColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().deadline()));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().status()));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(new StatusBadge(value, getTableRow().getItem().tone()));
                }
            }
        });
        overviewTable.setItems(FXCollections.observableArrayList(DemoData.assignments()));
        overviewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        overviewTable.setPlaceholder(new Label("Chưa có dữ liệu minh họa"));

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        String[] days = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        var values = DemoData.weeklyAnswers();
        for (int i = 0; i < days.length; i++) {
            series.getData().add(new XYChart.Data<>(days[i], values.get(i)));
        }
        activityChart.getData().add(series);
    }

    public void setOnNavigate(Consumer<Screen> handler) { onNavigate = handler; }

    @FXML private void openPractice() { onNavigate.accept(Screen.PRACTICE); }
    @FXML private void openExams() { onNavigate.accept(Screen.EXAMS); }
    @FXML private void openTutor() { onNavigate.accept(Screen.TUTOR); }
}
