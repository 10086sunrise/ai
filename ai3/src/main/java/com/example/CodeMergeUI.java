package com.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CodeMergeUI {

    private Stage stage;
    private TextArea sourceCodeArea;
    private TextArea generatedCodeArea;
    private TextField sourceFileField;
    private TextField generatedFileField;
    private TextField targetFileField;
    private ComboBox<String> mergeStrategyCombo;
    private Label statusLabel;
    private CodeMerger codeMerger;

    public void show() {
        stage = new Stage();
        stage.setTitle("代码合并工具");

        codeMerger = new CodeMerger();

        // 创建主布局
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(15));

        // 顶部：文件选择和策略选择
        mainLayout.setTop(createTopPanel());

        // 中心：代码对比区域
        mainLayout.setCenter(createCenterPanel());

        // 底部：状态和控制按钮
        mainLayout.setBottom(createBottomPanel());

        Scene scene = new Scene(mainLayout, 1200, 800);
        stage.setScene(scene);
        stage.show();
    }

    private VBox createTopPanel() {
        VBox topPanel = new VBox(15);
        topPanel.setPadding(new Insets(10));
        topPanel.setStyle("-fx-background-color: #34495e;");

        Label titleLabel = new Label("🔗 JavaFX代码合并工具");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        // 源文件选择
        HBox sourceFileBox = createFileSelector("源文件（现有项目文件）:", sourceFileField = new TextField());

        // 生成的代码文件选择
        HBox generatedFileBox = createFileSelector("生成的代码文件:", generatedFileField = new TextField());

        // 目标文件选择（默认与源文件相同，可修改）
        HBox targetFileBox = createFileSelector("目标文件（合并到）:", targetFileField = new TextField());
        targetFileField.setPromptText("默认为源文件，可指定其他文件");

        // 合并策略选择
        HBox strategyBox = new HBox(10);
        strategyBox.setAlignment(Pos.CENTER_LEFT);

        Label strategyLabel = new Label("合并策略:");
        strategyLabel.setStyle("-fx-text-fill: white;");

        mergeStrategyCombo = new ComboBox<>();
        mergeStrategyCombo.getItems().addAll(
                "智能合并（AI辅助）",
                "智能合并（传统）",
                "替换整个类",
                "插入方法",
                "添加UI组件"
        );
        mergeStrategyCombo.setValue("智能合并（自动选择最佳方式）");
        mergeStrategyCombo.setPrefWidth(250);

        strategyBox.getChildren().addAll(strategyLabel, mergeStrategyCombo);

        topPanel.getChildren().addAll(
                titleLabel,
                sourceFileBox,
                generatedFileBox,
                targetFileBox,
                strategyBox
        );

        return topPanel;
    }

    private HBox createFileSelector(String labelText, TextField textField) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText);
        label.setPrefWidth(180);
        label.setStyle("-fx-text-fill: white;");

        textField.setPrefWidth(400);
        textField.setEditable(true);

        Button browseButton = new Button("浏览...");
        browseButton.setOnAction(e -> browseFile(textField, "选择Java文件", "*.java"));

        Button analyzeButton = new Button("分析");
        analyzeButton.setOnAction(e -> {
            if (!textField.getText().isEmpty()) {
                analyzeFile(textField.getText());
            }
        });

        box.getChildren().addAll(label, textField, browseButton, analyzeButton);
        return box;
    }

    private SplitPane createCenterPanel() {
        SplitPane centerPane = new SplitPane();
        centerPane.setDividerPositions(0.5);

        // 左侧：现有项目代码
        VBox sourcePanel = new VBox(10);
        sourcePanel.setPadding(new Insets(10));

        Label sourceLabel = new Label("📁 现有项目代码");
        sourceLabel.setStyle("-fx-font-weight: bold;");

        sourceCodeArea = new TextArea();
        sourceCodeArea.setEditable(false);
        sourceCodeArea.setWrapText(true);
        sourceCodeArea.setPrefHeight(500);
        sourceCodeArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        Button loadSourceButton = new Button("加载文件");
        loadSourceButton.setOnAction(e -> loadFile(sourceFileField, sourceCodeArea));

        sourcePanel.getChildren().addAll(sourceLabel, sourceCodeArea, loadSourceButton);

        // 右侧：AI生成的代码
        VBox generatedPanel = new VBox(10);
        generatedPanel.setPadding(new Insets(10));

        Label generatedLabel = new Label("🤖 AI生成的UI代码");
        generatedLabel.setStyle("-fx-font-weight: bold;");

        generatedCodeArea = new TextArea();
        generatedCodeArea.setEditable(true);
        generatedCodeArea.setWrapText(true);
        generatedCodeArea.setPrefHeight(500);
        generatedCodeArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        Button loadGeneratedButton = new Button("加载文件");
        loadGeneratedButton.setOnAction(e -> loadFile(generatedFileField, generatedCodeArea));

        generatedPanel.getChildren().addAll(generatedLabel, generatedCodeArea, loadGeneratedButton);

        centerPane.getItems().addAll(sourcePanel, generatedPanel);
        return centerPane;
    }

    private HBox createBottomPanel() {
        HBox bottomPanel = new HBox(15);
        bottomPanel.setPadding(new Insets(15));
        bottomPanel.setAlignment(Pos.CENTER);

        // 分析按钮
        Button analyzeButton = new Button("🔍 分析代码结构");
        analyzeButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        analyzeButton.setOnAction(e -> analyzeBothFiles());

        // 预览按钮
        Button previewButton = new Button("👁️ 预览合并");
        previewButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        previewButton.setOnAction(e -> previewMerge());

        // 执行合并按钮
        Button mergeButton = new Button("🔗 执行合并");
        mergeButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        mergeButton.setOnAction(e -> executeMerge());

        // 项目模式按钮
        Button projectModeButton = new Button("📂 项目模式");
        projectModeButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white;");
        projectModeButton.setOnAction(e -> openProjectMode());

        // 帮助按钮
        Button helpButton = new Button("❓ 帮助");
        helpButton.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white;");
        helpButton.setOnAction(e -> showHelp());

        // 状态标签
        statusLabel = new Label("就绪 - 选择要合并的文件");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");

        bottomPanel.getChildren().addAll(
                analyzeButton, previewButton, mergeButton,
                projectModeButton, helpButton,
                new Separator(), statusLabel
        );

        return bottomPanel;
    }

    private void browseFile(TextField textField, String title, String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);

        if (extension != null) {
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Java Files", extension)
            );
        }

        // 如果当前文本框有路径，设为初始目录
        String currentPath = textField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                fileChooser.setInitialDirectory(currentFile.getParentFile());
            }
        }

        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            textField.setText(selectedFile.getAbsolutePath());
            loadFile(textField, textField == sourceFileField ? sourceCodeArea : generatedCodeArea);
        }
    }

    private void loadFile(TextField fileField, TextArea codeArea) {
        String filePath = fileField.getText();
        if (filePath == null || filePath.trim().isEmpty()) {
            showAlert("错误", "请先选择文件");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            showAlert("错误", "文件不存在: " + filePath);
            return;
        }

        try {
            String content = Files.readString(file.toPath());
            codeArea.setText(content);
            statusLabel.setText("已加载: " + file.getName());

            // 如果是源文件，自动设置目标文件
            if (fileField == sourceFileField && targetFileField.getText().isEmpty()) {
                targetFileField.setText(filePath);
            }

        } catch (IOException e) {
            showAlert("错误", "读取文件失败: " + e.getMessage());
            statusLabel.setText("读取文件失败");
        }
    }

    private void analyzeFile(String filePath) {
        try {
            String content = Files.readString(Paths.get(filePath));
            CodeMerger.CodeAnalysis analysis = codeMerger.analyzeCode(content);

            String analysisResult = String.format(
                    "文件分析结果:\n" +
                            "• 文件名: %s\n" +
                            "• 包名: %s\n" +
                            "• 类名: %s\n" +
                            "• Import数量: %d\n" +
                            "• 方法数量: %d\n" +
                            "• 是否是JavaFX类: %s",
                    new File(filePath).getName(),
                    analysis.packageName != null ? analysis.packageName : "无",
                    analysis.className != null ? analysis.className : "无",
                    analysis.imports.size(),
                    analysis.methods.size(),
                    isJavaFXClass(content) ? "是" : "否"
            );

            showAlert("文件分析", analysisResult);

        } catch (IOException e) {
            showAlert("分析错误", "无法分析文件: " + e.getMessage());
        }
    }

    private void analyzeBothFiles() {
        if (sourceFileField.getText().isEmpty() || generatedFileField.getText().isEmpty()) {
            showAlert("错误", "请先选择源文件和生成的代码文件");
            return;
        }

        try {
            String sourceContent = Files.readString(Paths.get(sourceFileField.getText()));
            String generatedContent = Files.readString(Paths.get(generatedFileField.getText()));

            CodeMerger.CodeAnalysis sourceAnalysis = codeMerger.analyzeCode(sourceContent);
            CodeMerger.CodeAnalysis generatedAnalysis = codeMerger.analyzeCode(generatedContent);

            StringBuilder result = new StringBuilder();
            result.append("📊 代码对比分析\n\n");

            result.append("📁 源文件:\n");
            result.append("  类名: ").append(sourceAnalysis.className != null ? sourceAnalysis.className : "无").append("\n");
            result.append("  方法数: ").append(sourceAnalysis.methods.size()).append("\n");
            result.append("  是否是JavaFX类: ").append(isJavaFXClass(sourceContent) ? "是" : "否").append("\n\n");

            result.append("🤖 生成的代码:\n");
            result.append("  类名: ").append(generatedAnalysis.className != null ? generatedAnalysis.className : "无").append("\n");
            result.append("  方法数: ").append(generatedAnalysis.methods.size()).append("\n");

            // 推荐合并策略
            result.append("\n💡 推荐合并策略:\n");
            if (!isJavaFXClass(sourceContent)) {
                result.append("  推荐: 替换整个类（源文件不是JavaFX类）\n");
            } else if (sourceAnalysis.methods.size() > 0 && generatedAnalysis.methods.size() > 0) {
                result.append("  推荐: 插入方法或智能合并\n");
            } else {
                result.append("  推荐: 添加UI组件\n");
            }

            showAlert("代码对比分析", result.toString());

        } catch (IOException e) {
            showAlert("分析错误", "分析文件失败: " + e.getMessage());
        }
    }

    private boolean isJavaFXClass(String content) {
        return content.contains("extends Application") ||
                content.contains("javafx.application.Application") ||
                (content.contains("Application") && content.contains("javafx"));
    }

    private void previewMerge() {
        if (sourceFileField.getText().isEmpty() || generatedFileField.getText().isEmpty()) {
            showAlert("错误", "请先选择源文件和生成的代码文件");
            return;
        }

        String targetFile = targetFileField.getText();
        if (targetFile.isEmpty()) {
            targetFile = sourceFileField.getText();
        }

        statusLabel.setText("正在预览合并结果...");

        try {
            // 读取文件内容
            String sourceContent = Files.readString(Paths.get(sourceFileField.getText()));
            String generatedContent = Files.readString(Paths.get(generatedFileField.getText()));

            // 获取合并策略
            CodeMerger.MergeStrategy strategy = parseMergeStrategy(
                    mergeStrategyCombo.getValue()
            );

            // 执行合并（在内存中）
            String mergedContent = mergeInMemory(sourceContent, generatedContent, strategy);

            // 显示预览
            showPreviewWindow(mergedContent);

            statusLabel.setText("预览完成");

        } catch (IOException e) {
            showAlert("预览错误", "预览过程出错: " + e.getMessage());
            statusLabel.setText("预览出错");
        } catch (Exception e) {
            showAlert("预览错误", "合并过程出错: " + e.getMessage());
            statusLabel.setText("合并出错");
        }
    }

    private String mergeInMemory(String sourceContent, String generatedContent,
                                 CodeMerger.MergeStrategy strategy) throws Exception {
        CodeMerger.CodeAnalysis generatedAnalysis = codeMerger.analyzeCode(generatedContent);
        CodeMerger.CodeAnalysis sourceAnalysis = codeMerger.analyzeCode(sourceContent);

        switch (strategy) {
            case REPLACE_CLASS:
                return codeMerger.replaceClass(sourceContent, generatedContent, generatedAnalysis, sourceAnalysis);
            case INSERT_METHOD:
                return codeMerger.insertMethods(sourceContent, generatedAnalysis, sourceAnalysis);
            case ADD_COMPONENT:
                return codeMerger.addComponents(sourceContent, generatedAnalysis, sourceAnalysis);
            case SMART_MERGE:
            default:
                return codeMerger.smartMerge(sourceContent, generatedAnalysis, sourceAnalysis);
        }
    }

    private void executeMerge() {
        if (sourceFileField.getText().isEmpty() || generatedFileField.getText().isEmpty()) {
            showAlert("错误", "请先选择源文件和生成的代码文件");
            return;
        }

        String targetFile = targetFileField.getText();
        if (targetFile.isEmpty()) {
            targetFile = sourceFileField.getText();
        }

        statusLabel.setText("正在执行合并...");

        try {
            // 读取生成的代码（优先使用文本区域的内容，因为它可能被编辑过）
            String generatedContent = generatedCodeArea.getText();
            if (generatedContent.isEmpty()) {
                generatedContent = Files.readString(Paths.get(generatedFileField.getText()));
            }

            // 获取合并策略
            CodeMerger.MergeStrategy strategy = parseMergeStrategy(
                    mergeStrategyCombo.getValue()
            );

            // 执行合并
            CodeMerger.MergeResult result = codeMerger.mergeToFile(
                    targetFile,
                    generatedContent,
                    strategy
            );

            if (result.success) {
                showAlert("合并成功", result.message);
                statusLabel.setText("✅ " + result.message);

                // 刷新源文件显示
                loadFile(sourceFileField, sourceCodeArea);
            } else {
                showAlert("合并失败", result.message);
                statusLabel.setText("❌ 合并失败");
            }

        } catch (IOException e) {
            showAlert("合并错误", "文件操作失败: " + e.getMessage());
            statusLabel.setText("合并出错");
        } catch (Exception e) {
            showAlert("合并错误", "合并过程出错: " + e.getMessage());
            statusLabel.setText("合并出错");
        }
    }

    private void openProjectMode() {
        // 打开项目模式对话框
        ProjectMergeUI projectUI = new ProjectMergeUI();
        projectUI.show();
    }

    private CodeMerger.MergeStrategy parseMergeStrategy(String strategyText) {
        if (strategyText.contains("智能合并（AI辅助）")) {
            return CodeMerger.MergeStrategy.AI_ASSISTED;
        } else if (strategyText.contains("智能合并（传统）")) {
            return CodeMerger.MergeStrategy.SMART_MERGE;
        } else if (strategyText.contains("替换整个类")) {
            return CodeMerger.MergeStrategy.REPLACE_CLASS;
        } else if (strategyText.contains("插入方法")) {
            return CodeMerger.MergeStrategy.INSERT_METHOD;
        } else if (strategyText.contains("添加UI组件")) {
            return CodeMerger.MergeStrategy.ADD_COMPONENT;
        }
        return CodeMerger.MergeStrategy.SMART_MERGE;
    }

    private void showPreviewWindow(String mergedContent) {
        Stage previewStage = new Stage();
        previewStage.setTitle("合并预览");

        VBox previewLayout = new VBox(10);
        previewLayout.setPadding(new Insets(15));

        Label previewLabel = new Label("📄 合并结果预览");
        previewLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TextArea previewArea = new TextArea(mergedContent);
        previewArea.setEditable(false);
        previewArea.setWrapText(false);
        previewArea.setPrefHeight(600);
        previewArea.setPrefWidth(800);
        previewArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        Button saveAsButton = new Button("另存为...");
        saveAsButton.setOnAction(e -> savePreviewAs(mergedContent, previewStage));

        Button closeButton = new Button("关闭");
        closeButton.setOnAction(e -> previewStage.close());

        HBox buttonBox = new HBox(10, saveAsButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        previewLayout.getChildren().addAll(previewLabel, previewArea, buttonBox);

        Scene previewScene = new Scene(previewLayout, 850, 700);
        previewStage.setScene(previewScene);
        previewStage.show();
    }

    private void savePreviewAs(String content, Stage parentStage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存合并结果");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Java Files", "*.java")
        );

        File file = fileChooser.showSaveDialog(parentStage);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), content);
                showAlert("保存成功", "文件已保存到: " + file.getAbsolutePath());
                parentStage.close();
            } catch (IOException e) {
                showAlert("保存失败", "保存文件失败: " + e.getMessage());
            }
        }
    }

    private void showHelp() {
        String helpText = """
            =========== 代码合并工具使用帮助 ===========
            
            🎯 功能概述：
            将AI生成的JavaFX UI代码合并到现有Java文件中
            
            📝 使用步骤：
            1. 选择源文件（现有项目文件）
            2. 选择生成的代码文件（AI生成的UI代码）
            3. 选择目标文件（默认为源文件）
            4. 选择合并策略
            5. 点击"预览合并"查看效果
            6. 点击"执行合并"应用更改
            
            🔧 合并策略说明：
            • 智能合并：自动分析代码，选择最佳合并方式
            • 替换整个类：用生成的类完全替换现有类
            • 插入方法：将生成的方法插入到现有类中
            • 添加UI组件：只添加UI组件到现有方法中
            
            📂 项目模式：
            • 点击"项目模式"按钮可以切换到项目目录合并
            • 自动查找项目中的JavaFX文件
            
            ⚠️ 注意事项：
            1. 合并前会自动备份原文件（保存在backups目录）
            2. 预览功能不会修改实际文件
            3. 复杂的合并可能需要手动调整
            4. 确保代码语法正确
            
            💡 建议：
            • 先使用预览功能查看合并结果
            • 简单的UI组件推荐使用"添加UI组件"
            • 完整的UI界面推荐使用"智能合并"
            • 可以使用"分析代码"功能了解代码结构
            
            =========================================
            """;

        showAlert("使用帮助", helpText);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

/**
 * 项目模式合并界面
 */
class ProjectMergeUI {

    private Stage stage;
    private TextField projectPathField;
    private TextArea generatedCodeArea;
    private ComboBox<String> mergeStrategyCombo;
    private Label statusLabel;
    private CodeMerger codeMerger;

    public void show() {
        stage = new Stage();
        stage.setTitle("项目模式 - 代码合并");

        codeMerger = new CodeMerger();

        VBox mainLayout = new VBox(15);
        mainLayout.setPadding(new Insets(20));
        mainLayout.setStyle("-fx-background-color: #f5f7fa;");

        Label titleLabel = new Label("📂 项目模式合并");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // 项目路径选择
        HBox projectBox = new HBox(10);
        projectBox.setAlignment(Pos.CENTER_LEFT);

        Label projectLabel = new Label("项目目录:");
        projectPathField = new TextField();
        projectPathField.setPrefWidth(400);
        projectPathField.setPromptText("选择JavaFX项目目录");

        Button browseButton = new Button("浏览...");
        browseButton.setOnAction(e -> browseProjectPath());

        projectBox.getChildren().addAll(projectLabel, projectPathField, browseButton);

        // 生成的代码区域
        Label generatedLabel = new Label("生成的代码:");
        generatedCodeArea = new TextArea();
        generatedCodeArea.setPrefRowCount(15);
        generatedCodeArea.setWrapText(true);
        generatedCodeArea.setPromptText("在此粘贴或输入AI生成的JavaFX代码");

        // 合并策略
        HBox strategyBox = new HBox(10);
        strategyBox.setAlignment(Pos.CENTER_LEFT);

        Label strategyLabel = new Label("合并策略:");
        mergeStrategyCombo = new ComboBox<>();
        mergeStrategyCombo.getItems().addAll(
                "智能合并（自动查找合适文件）",
                "替换整个类",
                "插入方法",
                "添加UI组件"
        );
        mergeStrategyCombo.setValue("智能合并（自动查找合适文件）");

        strategyBox.getChildren().addAll(strategyLabel, mergeStrategyCombo);

        // 按钮
        Button findFilesButton = new Button("查找JavaFX文件");
        findFilesButton.setOnAction(e -> findJavaFXFiles());

        Button mergeButton = new Button("执行项目合并");
        mergeButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white;");
        mergeButton.setOnAction(e -> executeProjectMerge());

        Button closeButton = new Button("关闭");
        closeButton.setOnAction(e -> stage.close());

        HBox buttonBox = new HBox(10, findFilesButton, mergeButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER);

        // 状态标签
        statusLabel = new Label("请选择项目目录并输入生成的代码");

        mainLayout.getChildren().addAll(
                titleLabel, projectBox, generatedLabel,
                generatedCodeArea, strategyBox, buttonBox, statusLabel
        );

        Scene scene = new Scene(mainLayout, 700, 600);
        stage.setScene(scene);
        stage.show();
    }

    private void browseProjectPath() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择JavaFX项目目录");

        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            projectPathField.setText(selectedDir.getAbsolutePath());
            statusLabel.setText("已选择项目: " + selectedDir.getName());
        }
    }

    private void findJavaFXFiles() {
        String projectPath = projectPathField.getText();
        if (projectPath.isEmpty()) {
            showAlert("错误", "请先选择项目目录");
            return;
        }

        try {
            // 查找JavaFX文件
            List<Path> javaFiles = Files.walk(Paths.get(projectPath))
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        return filename.endsWith(".java") &&
                                Files.isRegularFile(path);
                    })
                    .toList();

            if (javaFiles.isEmpty()) {
                showAlert("结果", "在项目目录中未找到Java文件");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append("找到 ").append(javaFiles.size()).append(" 个Java文件:\n\n");

            for (Path file : javaFiles) {
                try {
                    String content = Files.readString(file);
                    boolean isJavaFX = content.contains("Application") &&
                            content.contains("extends");

                    result.append(isJavaFX ? "✅ " : "   ");
                    result.append(file.getFileName()).append("\n");
                    if (isJavaFX) {
                        result.append("     路径: ").append(file.toAbsolutePath()).append("\n");
                    }

                } catch (IOException e) {
                    result.append("❌ ").append(file.getFileName()).append(" (读取失败)\n");
                }
            }

            showAlert("Java文件查找结果", result.toString());

        } catch (IOException e) {
            showAlert("错误", "查找文件失败: " + e.getMessage());
        }
    }

    private void executeProjectMerge() {
        String projectPath = projectPathField.getText();
        String generatedCode = generatedCodeArea.getText();

        if (projectPath.isEmpty()) {
            showAlert("错误", "请选择项目目录");
            return;
        }

        if (generatedCode.isEmpty()) {
            showAlert("错误", "请输入生成的代码");
            return;
        }

        statusLabel.setText("正在执行项目合并...");

        try {
            CodeMerger.MergeStrategy strategy = parseMergeStrategy(
                    mergeStrategyCombo.getValue()
            );

            CodeMerger.MergeResult result = codeMerger.mergeToProject(
                    projectPath,
                    generatedCode,
                    strategy
            );

            if (result.success) {
                showAlert("合并成功", result.message);
                statusLabel.setText("✅ " + result.message);
            } else {
                showAlert("合并失败", result.message);
                statusLabel.setText("❌ " + result.message);
            }

        } catch (Exception e) {
            showAlert("合并错误", "合并过程出错: " + e.getMessage());
            statusLabel.setText("合并出错");
        }
    }

    private CodeMerger.MergeStrategy parseMergeStrategy(String strategyText) {
        if (strategyText.contains("智能合并")) {
            return CodeMerger.MergeStrategy.SMART_MERGE;
        } else if (strategyText.contains("替换整个类")) {
            return CodeMerger.MergeStrategy.REPLACE_CLASS;
        } else if (strategyText.contains("插入方法")) {
            return CodeMerger.MergeStrategy.INSERT_METHOD;
        } else if (strategyText.contains("添加UI组件")) {
            return CodeMerger.MergeStrategy.ADD_COMPONENT;
        }
        return CodeMerger.MergeStrategy.SMART_MERGE;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}