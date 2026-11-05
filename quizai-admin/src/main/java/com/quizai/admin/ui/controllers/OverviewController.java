package com.quizai.admin.ui.controllers;

import java.util.function.Consumer;
import com.quizai.admin.ui.components.StatusBadge;
import com.quizai.admin.ui.demo.DemoData;
import com.quizai.admin.ui.navigation.Screen;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;

public final class OverviewController {
    @FXML private AreaChart<String, Number> activityChart;
    @FXML private TableView<DemoData.ClassSummary> overviewTable;
    @FXML private TableColumn<DemoData.ClassSummary, String> nameColumn;
    @FXML private TableColumn<DemoData.ClassSummary, String> subjectColumn;
    @FXML private TableColumn<DemoData.ClassSummary, String> studentsColumn;
    @FXML private TableColumn<DemoData.ClassSummary, String> progressColumn;
    @FXML private TableColumn<DemoData.ClassSummary, String> statusColumn;
    private Consumer<Screen> onNavigate = screen -> { };

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        subjectColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().subject()));
        studentsColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().students()));
        progressColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().progress()));
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
        overviewTable.setItems(FXCollections.observableArrayList(DemoData.classes()));
        overviewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        overviewTable.setPlaceholder(new Label("Chưa có dữ liệu minh họa"));

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        String[] days = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        var values = DemoData.weeklySubmissions();
        for (int i = 0; i < days.length; i++) {
            series.getData().add(new XYChart.Data<>(days[i], values.get(i)));
        }
        activityChart.getData().add(series);
    }

    public void setOnNavigate(Consumer<Screen> handler) { onNavigate = handler; }

    @FXML private void openClasses() { onNavigate.accept(Screen.CLASSES); }
    @FXML private void openAssignments() { onNavigate.accept(Screen.ASSIGN); }
    @FXML private void openReview() { onNavigate.accept(Screen.REVIEW); }
    @FXML private void openLate() { onNavigate.accept(Screen.LATE); }
}
