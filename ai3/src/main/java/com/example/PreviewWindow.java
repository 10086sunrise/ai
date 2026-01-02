package com.example;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class PreviewWindow {

    private Stage stage;
    private TextArea codeArea;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private Button runButton;
    private Button stopButton;
    private CodeRunner codeRunner;

    public void show(String code) {
        stage = new Stage();
        stage.setTitle("JavaFX代码运行效果预览");

        codeRunner = new CodeRunner();

        // 创建主布局
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        // 顶部：标题和控制
        mainLayout.setTop(createTopPanel());

        // 中心：代码预览
        mainLayout.setCenter(createCenterPanel(code));

        // 底部：运行状态和控制
        mainLayout.setBottom(createBottomPanel());

        Scene scene = new Scene(mainLayout, 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    private VBox createTopPanel() {
        VBox topPanel = new VBox(10);
        topPanel.setPadding(new Insets(10));
        topPanel.setStyle("-fx-background-color: #34495e;");

        Label titleLabel = new Label("🎮 JavaFX代码运行效果预览");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label infoLabel = new Label("查看生成的JavaFX代码运行效果");
        infoLabel.setStyle("-fx-text-fill: #bdc3c7;");

        topPanel.getChildren().addAll(titleLabel, infoLabel);
        return topPanel;
    }

    private VBox createCenterPanel(String code) {
        VBox centerPanel = new VBox(10);
        centerPanel.setPadding(new Insets(10));

        Label codeLabel = new Label("📄 代码预览:");
        codeLabel.setStyle("-fx-font-weight: bold;");

        codeArea = new TextArea(code);
        codeArea.setEditable(true);
        codeArea.setWrapText(false);
        codeArea.setPrefHeight(300);
        codeArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        centerPanel.getChildren().addAll(codeLabel, codeArea);
        return centerPanel;
    }

    private VBox createBottomPanel() {
        VBox bottomPanel = new VBox(15);
        bottomPanel.setPadding(new Insets(10));

        // 运行状态
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(20, 20);

        statusLabel = new Label("点击运行按钮开始执行代码");
        statusLabel.setStyle("-fx-font-size: 14px;");

        statusBox.getChildren().addAll(progressIndicator, statusLabel);

        // 控制按钮
        HBox controlBox = new HBox(15);
        controlBox.setAlignment(Pos.CENTER);

        runButton = new Button("▶ 运行代码");
        runButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        runButton.setOnAction(e -> runCode());

        stopButton = new Button("⏹ 停止运行");
        stopButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        stopButton.setDisable(true);
        stopButton.setOnAction(e -> stopRunning());

        Button refreshButton = new Button("🔄 刷新代码");
        refreshButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        refreshButton.setOnAction(e -> refreshCode());

        Button closeButton = new Button("❌ 关闭");
        closeButton.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white;");
        closeButton.setOnAction(e -> stage.close());

        controlBox.getChildren().addAll(runButton, stopButton, refreshButton, closeButton);

        // 环境信息
        Label envLabel = new Label("运行环境: Java " + System.getProperty("java.version"));
        envLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12px;");

        bottomPanel.getChildren().addAll(statusBox, controlBox, envLabel);
        return bottomPanel;
    }

    private void runCode() {
        String code = codeArea.getText().trim();

        if (code.isEmpty()) {
            showAlert("错误", "没有代码可运行");
            return;
        }

        // 验证代码
        String validationError = codeRunner.validateCode(code);
        if (validationError != null) {
            statusLabel.setText("❌ " + validationError);
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        // 更新UI状态
        runButton.setDisable(true);
        stopButton.setDisable(false);
        progressIndicator.setVisible(true);
        statusLabel.setText("🚀 正在编译运行代码...");
        statusLabel.setStyle("-fx-text-fill: #3498db;");

        // 异步运行代码
        new Thread(() -> {
            codeRunner.runJavaFXCode(code,
                    () -> Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        statusLabel.setText("✅ 代码运行成功！");
                        statusLabel.setStyle("-fx-text-fill: #2ecc71;");
                        runButton.setDisable(false);
                        stopButton.setDisable(true);
                    }),
                    error -> Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        statusLabel.setText("❌ " + error);
                        statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                        runButton.setDisable(false);
                        stopButton.setDisable(true);
                    })
            );
        }).start();
    }

    private void stopRunning() {
        // 这里可以实现停止运行逻辑
        statusLabel.setText("⏹ 运行已停止");
        statusLabel.setStyle("-fx-text-fill: #f39c12;");
        runButton.setDisable(false);
        stopButton.setDisable(true);
        progressIndicator.setVisible(false);
    }

    private void refreshCode() {
        // 刷新代码显示（可以重新生成或重置）
        statusLabel.setText("代码已刷新");
        statusLabel.setStyle("-fx-text-fill: #3498db;");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}