package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class App extends Application {

    // UI组件
    private TextArea outputArea;
    private TextArea promptArea;
    private TextArea previewArea;
    private ComboBox<String> uiTypeComboBox;
    private TextField classNameField;
    private CheckBox includeCommentsCheckBox;
    private CheckBox includeMainMethodCheckBox;
    private Button generateButton;
    private Button runButton;
    private ProgressIndicator progressIndicator;
    private ProgressIndicator runProgressIndicator;
    private Label statusLabel;

    // 预览相关组件
    private StackPane previewContentPane;
    private javafx.stage.Stage embeddedStage;
    private javafx.scene.Scene embeddedScene;
    private BorderPane previewContainer; // 存储预览容器

    // 客户端和运行器
    private AliyunAIClient aiClient;
    private CodeRunner codeRunner;

    // JavaFX配置
    private static String javafxHome = null;

    static {
        // 启动时检测JavaFX
        detectJavaFX();
    }

    @Override
    public void start(Stage primaryStage) {
        // 初始化客户端和运行器
        initializeAIClient();
        initializeCodeRunner();

        primaryStage.setTitle("JavaFX AI代码生成器");

        // 创建主布局
        BorderPane mainLayout = createMainLayout();

        // 创建场景
        Scene scene = new Scene(mainLayout, 1400, 800);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * 检测JavaFX环境
     */
    private static void detectJavaFX() {
        System.out.println("=== JavaFX环境检测 ===");

        // 1. 检查系统属性
        javafxHome = System.getProperty("javafx.home");
        System.out.println("1. 系统属性 javafx.home: " + javafxHome);

        // 2. 检查环境变量
        if (javafxHome == null || javafxHome.isEmpty()) {
            javafxHome = System.getenv("JAVAFX_HOME");
            System.out.println("2. 环境变量 JAVAFX_HOME: " + javafxHome);
        }

        // 3. 检查常见的JavaFX安装路径
        if (javafxHome == null || javafxHome.isEmpty()) {
            String[] commonPaths = {
                    System.getProperty("user.home") + "/.m2/repository/org/openjfx",
                    System.getProperty("user.home") + "/.m2/repository/org/openjfx/javafx-sdk",
                    "C:/Java/javafx-sdk-21.0.1",
                    "C:/Program Files/Java/javafx-sdk-21.0.1",
                    "C:/javafx-sdk-21.0.1",
                    "/usr/lib/jvm/javafx-sdk-21.0.1",
                    "/usr/local/javafx-sdk-21.0.1"
            };

            for (String path : commonPaths) {
                Path p = Paths.get(path);
                if (Files.exists(p)) {
                    // 检查是否是有效的JavaFX SDK
                    if (isValidJavaFXSDK(p)) {
                        javafxHome = path;
                        System.out.println("3. 找到JavaFX SDK: " + path);
                        break;
                    }
                }
            }
        }

        // 4. 检查类路径中是否有JavaFX
        try {
            Class.forName("javafx.application.Application");
            System.out.println("4. JavaFX在类路径中检测到");
            if (javafxHome == null) {
                javafxHome = "classpath";
            }
        } catch (ClassNotFoundException e) {
            System.out.println("4. JavaFX未在类路径中找到");
        }

        System.out.println("最终JavaFX路径: " + (javafxHome != null ? javafxHome : "未找到"));
        System.out.println("========================\n");
    }

    /**
     * 检查是否是有效的JavaFX SDK目录
     */
    private static boolean isValidJavaFXSDK(Path path) {
        // 检查目录结构
        Path libPath = path.resolve("lib");
        if (Files.exists(libPath) && Files.isDirectory(libPath)) {
            // 检查是否有JavaFX jar文件
            try {
                return Files.list(libPath)
                        .filter(p -> p.toString().endsWith(".jar") && p.toString().contains("javafx"))
                        .count() > 0;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * 初始化AI客户端
     */
    private void initializeAIClient() {
        try {
            aiClient = new AliyunAIClient();
            System.out.println("AI客户端初始化完成");

        } catch (Exception e) {
            showAlert("初始化错误", "初始化AI客户端失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 初始化代码运行器
     */
    private void initializeCodeRunner() {
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");
        System.setProperty("javafx.verbose", "false");

        // 如果检测到JavaFX路径，设置到系统属性中
        if (javafxHome != null && !javafxHome.equals("classpath")) {
            System.setProperty("javafx.home", javafxHome);
        }

        codeRunner = new CodeRunner();
    }

    /**
     * 创建主布局
     */
    private BorderPane createMainLayout() {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #f5f7fa;");

        // 顶部 - 标题
        mainLayout.setTop(createHeader());

        // 中心 - 三列布局
        mainLayout.setCenter(createThreeColumnLayout());

        // 底部 - 状态栏
        mainLayout.setBottom(createFooter());

        return mainLayout;
    }

    /**
     * 创建顶部区域
     */
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: #3498db;");

        Label titleLabel = new Label("🔮 JavaFX AI代码生成器");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: white;");

        // 显示JavaFX状态
        String javafxStatus = getJavaFXStatus();
        Label subtitleLabel = new Label("JavaFX状态: " + javafxStatus);
        subtitleLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 14px;");

        header.getChildren().addAll(titleLabel, subtitleLabel);
        return header;
    }

    /**
     * 获取JavaFX状态文本
     */
    private String getJavaFXStatus() {
        if (javafxHome == null) {
            return "❌ 未检测到 - 请在设置中配置JavaFX路径";
        } else if (javafxHome.equals("classpath")) {
            return "✅ 已集成 (类路径)";
        } else {
            return "✅ 已配置: " + new File(javafxHome).getName();
        }
    }

    /**
     * 创建三列布局
     */
    private SplitPane createThreeColumnLayout() {
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setDividerPositions(0.33, 0.66); // 三列平均分配

        // 第一列：输入描述区域
        VBox inputColumn = createInputColumn();

        // 第二列：代码编辑区域
        VBox codeColumn = createCodeColumn();

        // 第三列：运行效果区域
        VBox previewColumn = createPreviewColumn();

        mainSplitPane.getItems().addAll(inputColumn, codeColumn, previewColumn);
        return mainSplitPane;
    }

    /**
     * 创建输入列（第一列）
     */
    private VBox createInputColumn() {
        VBox inputColumn = new VBox(15);
        inputColumn.setPadding(new Insets(20));
        inputColumn.setStyle("-fx-background-color: white; -fx-border-color: #ecf0f1; -fx-border-width: 1;");

        // 面板标题
        Label inputTitle = new Label("🎨 AI界面描述");
        inputTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // UI类型选择
        HBox typeBox = new HBox(10);
        typeBox.setAlignment(Pos.CENTER_LEFT);

        Label typeLabel = new Label("界面类型:");
        typeLabel.setStyle("-fx-font-weight: bold;");

        uiTypeComboBox = new ComboBox<>();
        uiTypeComboBox.getItems().addAll(
                "📱 登录界面",
                "📝 注册表单",
                "📊 数据表格",
                "🖥️ 仪表板",
                "⚙️ 设置面板",
                "📁 文件管理器",
                "🎯 自定义界面"
        );
        uiTypeComboBox.setValue("📱 登录界面");
        uiTypeComboBox.setPrefWidth(200);

        typeBox.getChildren().addAll(typeLabel, uiTypeComboBox);

        // 类名输入
        HBox classNameBox = new HBox(10);
        classNameBox.setAlignment(Pos.CENTER_LEFT);

        Label classNameLabel = new Label("类名:");
        classNameLabel.setStyle("-fx-font-weight: bold;");

        classNameField = new TextField();
        classNameField.setText("GeneratedUI");
        classNameField.setPrefWidth(200);

        classNameBox.getChildren().addAll(classNameLabel, classNameField);

        // 选项复选框
        HBox optionsBox = new HBox(20);
        optionsBox.setAlignment(Pos.CENTER_LEFT);

        includeCommentsCheckBox = new CheckBox("包含注释");
        includeCommentsCheckBox.setSelected(true);

        includeMainMethodCheckBox = new CheckBox("包含main方法");
        includeMainMethodCheckBox.setSelected(true);

        CheckBox includeCssCheckBox = new CheckBox("包含CSS样式");
        includeCssCheckBox.setSelected(true);

        optionsBox.getChildren().addAll(includeCommentsCheckBox, includeMainMethodCheckBox, includeCssCheckBox);

        // 描述输入区域
        Label promptLabel = new Label("✨ 详细描述你的UI需求:");
        promptLabel.setStyle("-fx-font-weight: bold;");

        promptArea = new TextArea();
        promptArea.setPromptText("在这里详细描述你想要的JavaFX界面...\n\n示例：\n创建一个现代化的登录界面，包含：\n• 左侧显示Logo和欢迎语\n• 右侧是登录表单\n• 用户名和密码输入框\n• 记住密码选项\n• 登录和注册按钮\n• 使用蓝色渐变背景");
        promptArea.setWrapText(true);
        promptArea.setPrefHeight(250);
        promptArea.setStyle("-fx-font-size: 14px;");

        // 示例按钮
        HBox exampleBox = new HBox(10);

        Button exampleLoginButton = new Button("登录界面示例");
        exampleLoginButton.setOnAction(e -> loadExample("login"));

        Button exampleTableButton = new Button("表格示例");
        exampleTableButton.setOnAction(e -> loadExample("table"));

        Button exampleDashboardButton = new Button("仪表板示例");
        exampleDashboardButton.setOnAction(e -> loadExample("dashboard"));

        exampleBox.getChildren().addAll(exampleLoginButton, exampleTableButton, exampleDashboardButton);

        // 生成按钮
        generateButton = new Button("🚀 生成JavaFX代码");
        generateButton.setStyle("-fx-background-color: linear-gradient(to right, #e74c3c, #c0392b); " +
                "-fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 10 30;");
        generateButton.setOnAction(e -> generateCode());

        // 进度指示器
        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(20, 20);

        HBox generateBox = new HBox(10, generateButton, progressIndicator);
        generateBox.setAlignment(Pos.CENTER);

        // 提示文本
        Label tipLabel = new Label("💡 提示：描述越详细，生成的代码越准确");
        tipLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        inputColumn.getChildren().addAll(
                inputTitle,
                typeBox,
                classNameBox,
                optionsBox,
                new Separator(),
                promptLabel,
                promptArea,
                exampleBox,
                generateBox,
                tipLabel
        );

        return inputColumn;
    }

    /**
     * 创建代码列（第二列）
     */
    private VBox createCodeColumn() {
        VBox codeColumn = new VBox(15);
        codeColumn.setPadding(new Insets(20));
        codeColumn.setStyle("-fx-background-color: #2c3e50;");

        // 面板标题
        HBox codeHeader = new HBox(10);
        codeHeader.setAlignment(Pos.CENTER_LEFT);

        Label codeTitle = new Label("💻 代码编辑区域");
        codeTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        // 状态标签
        Label codeStatusLabel = new Label("可粘贴AI生成的代码或自己编写的代码");
        codeStatusLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 12px;");

        codeHeader.getChildren().addAll(codeTitle, codeStatusLabel);

        // 代码编辑区域
        outputArea = new TextArea();
        outputArea.setPromptText("在此粘贴或编辑JavaFX代码...\n\n提示：\n• 可以直接粘贴AI生成的代码\n• 也可以自己编写JavaFX代码\n• 代码必须包含Application类和start方法\n• 支持编辑后运行预览");
        outputArea.setWrapText(false);
        outputArea.setPrefHeight(500);
        outputArea.setStyle("-fx-font-family: 'Consolas', 'Monospaced'; -fx-font-size: 12px; " +
                "-fx-background-color: #ffffff; -fx-text-fill: #000000; " +
                "-fx-border-color: #7f8c8d;");

        // 代码操作工具栏
        HBox codeToolbar = new HBox(10);
        codeToolbar.setAlignment(Pos.CENTER_LEFT);

        Button copyButton = new Button("📋 复制");
        copyButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        copyButton.setOnAction(e -> copyCodeToClipboard());

        Button pasteButton = new Button("📄 粘贴");
        pasteButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        pasteButton.setOnAction(e -> pasteCodeFromClipboard());

        Button formatButton = new Button("✨ 格式化");
        formatButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        formatButton.setOnAction(e -> formatCode());

        Button saveButton = new Button("💾 保存");
        saveButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white;");
        saveButton.setOnAction(e -> saveCodeToFile());

        Button clearButton = new Button("🗑️ 清空");
        clearButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        clearButton.setOnAction(e -> outputArea.clear());

        Button loadButton = new Button("📂 加载");
        loadButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");
        loadButton.setOnAction(e -> loadCodeFromFile());

        codeToolbar.getChildren().addAll(copyButton, pasteButton, formatButton, saveButton, clearButton, loadButton);

        // 代码信息显示
        HBox codeInfoBox = new HBox(10);
        codeInfoBox.setAlignment(Pos.CENTER_LEFT);

        Label lineCountLabel = new Label("行数: 0");
        lineCountLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12px;");

        Label charCountLabel = new Label("字符: 0");
        charCountLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12px;");

        // 监听代码变化更新计数
        outputArea.textProperty().addListener((observable, oldValue, newValue) -> {
            int lines = newValue.split("\n").length;
            int chars = newValue.length();
            lineCountLabel.setText("行数: " + lines);
            charCountLabel.setText("字符: " + chars);

            // 自动启用/禁用运行按钮
            if (!newValue.trim().isEmpty()) {
                runButton.setDisable(false);
            } else {
                runButton.setDisable(true);
            }
        });

        codeInfoBox.getChildren().addAll(lineCountLabel, charCountLabel);

        codeColumn.getChildren().addAll(
                codeHeader,
                outputArea,
                codeToolbar,
                codeInfoBox
        );

        return codeColumn;
    }

    /**
     * 创建预览列（第三列）
     */
    private VBox createPreviewColumn() {
        VBox previewColumn = new VBox(15);
        previewColumn.setPadding(new Insets(20));
        previewColumn.setStyle("-fx-background-color: #34495e;");

        // 面板标题
        HBox previewHeader = new HBox(10);
        previewHeader.setAlignment(Pos.CENTER_LEFT);

        Label previewTitle = new Label("🎮 运行效果预览");
        previewTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        previewHeader.getChildren().addAll(previewTitle);

        // 预览效果显示区域
        VBox previewDisplayBox = new VBox(10);
        previewDisplayBox.setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 5; -fx-padding: 10;");

        Label previewDisplayLabel = new Label("运行效果图:");
        previewDisplayLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        // 创建预览内容面板
        previewContentPane = new StackPane();
        previewContentPane.setStyle("-fx-background-color: white; -fx-border-color: #000000; -fx-border-width: 2;");
        previewContentPane.setPrefSize(400, 300);

        // 初始显示提示
        Label initialLabel = new Label("运行代码后显示效果");
        initialLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px;");
        initialLabel.setAlignment(Pos.CENTER);
        previewContentPane.getChildren().add(initialLabel);

        // 控制按钮
        HBox controlBox = new HBox(10);
        Button clearPreviewButton = new Button("清空预览");
        clearPreviewButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 12px;");
        clearPreviewButton.setOnAction(e -> clearPreview());

        Button zoomButton = new Button("🔍 放大");
        zoomButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 12px;");
        zoomButton.setOnAction(e -> openPreviewWindow());

        controlBox.getChildren().addAll(previewDisplayLabel, clearPreviewButton, zoomButton);
        controlBox.setAlignment(Pos.CENTER_LEFT);

        previewDisplayBox.getChildren().addAll(controlBox, previewContentPane);

        // 运行控制按钮
        HBox runControls = new HBox(15);
        runControls.setAlignment(Pos.CENTER);
        runControls.setStyle("-fx-padding: 10 0;");

        // 运行按钮
        runButton = new Button("▶ 运行代码");
        runButton.setStyle("-fx-background-color: linear-gradient(to right, #2ecc71, #27ae60); " +
                "-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; " +
                "-fx-padding: 10 25;");
        runButton.setOnAction(e -> runCode());
        runButton.setDisable(true); // 初始禁用

        // 验证按钮
        Button validateButton = new Button("🔍 验证");
        validateButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");
        validateButton.setOnAction(e -> validateCode());

        // 配置按钮
        Button configButton = new Button("⚙ 配置");
        configButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        configButton.setOnAction(e -> openSettings());

        runControls.getChildren().addAll(runButton, validateButton, configButton);

        // 运行日志区域
        VBox logBox = new VBox(10);
        logBox.setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 5; -fx-padding: 10;");

        Label logTitle = new Label("📝 运行日志");
        logTitle.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        previewArea.setPrefHeight(120);
        previewArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px; " +
                "-fx-background-color: #ffffff; -fx-text-fill: #000000; " +
                "-fx-border-color: #000000;");
        previewArea.setPromptText("运行日志将在此显示...");

        Button clearLogButton = new Button("清空日志");
        clearLogButton.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-font-size: 11px;");
        clearLogButton.setOnAction(e -> previewArea.clear());

        logBox.getChildren().addAll(logTitle, previewArea, clearLogButton);

        // 监听代码变化，启用/禁用运行按钮
        outputArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.trim().isEmpty()) {
                runButton.setDisable(false);
            } else {
                runButton.setDisable(true);
            }
        });

        // 添加所有组件到预览列
        previewColumn.getChildren().addAll(
                previewHeader,
                previewDisplayBox,
                runControls,
                logBox
        );

        return previewColumn;
    }

    /**
     * 清空预览区域
     */
    private void clearPreview() {
        previewContentPane.getChildren().clear();
        Label label = new Label("预览已清空");
        label.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px;");
        label.setAlignment(Pos.CENTER);
        previewContentPane.getChildren().add(label);

        if (embeddedStage != null) {
            embeddedStage.close();
            embeddedStage = null;
        }
        if (embeddedScene != null) {
            embeddedScene = null;
        }
        previewContainer = null;
        addLog("预览已清空");
    }

    /**
     * 打开预览窗口（放大功能）- 改进版：创建场景副本
     */
    private void openPreviewWindow() {
        if (previewContentPane.getChildren().isEmpty() ||
                previewContentPane.getChildren().size() == 1 &&
                        previewContentPane.getChildren().get(0) instanceof Label) {
            showAlert("提示", "没有预览内容可放大");
            return;
        }

        String code = outputArea.getText().trim();
        if (code.isEmpty()) {
            showAlert("错误", "没有代码可运行");
            return;
        }

        Stage previewStage = new Stage();
        previewStage.setTitle("预览效果图 - 放大模式");

        // 创建放大预览的容器
        StackPane zoomContentPane = new StackPane();
        zoomContentPane.setStyle("-fx-background-color: white; -fx-border-color: #000000; -fx-border-width: 2;");
        zoomContentPane.setPrefSize(800, 600);

        // 显示加载中提示
        Label loadingLabel = new Label("正在加载放大预览...");
        loadingLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px;");
        loadingLabel.setAlignment(Pos.CENTER);
        zoomContentPane.getChildren().add(loadingLabel);

        BorderPane previewLayout = new BorderPane();
        previewLayout.setStyle("-fx-background-color: #ffffff;");
        previewLayout.setCenter(zoomContentPane);

        // 添加控制按钮
        HBox controls = new HBox(10);
        controls.setPadding(new Insets(5));
        controls.setStyle("-fx-background-color: #f0f0f0;");
        controls.setAlignment(Pos.CENTER);

        Button closeButton = new Button("关闭");
        closeButton.setOnAction(e -> previewStage.close());

        Button refreshButton = new Button("刷新");
        refreshButton.setOnAction(event -> {
            // 重新运行代码更新预览
            runCodeForZoom(previewStage, zoomContentPane);
        });

        controls.getChildren().addAll(closeButton, refreshButton);
        previewLayout.setBottom(controls);

        Scene previewScene = new Scene(previewLayout, 800, 600);
        previewStage.setScene(previewScene);

        // 监听窗口关闭事件
        previewStage.setOnCloseRequest(event -> {
            addLog("放大预览窗口已关闭");
        });

        previewStage.show();
        addLog("打开放大预览窗口");

        // 重新运行代码来创建放大预览
        runCodeForZoom(previewStage, zoomContentPane);
    }
    /**
     * 为放大窗口运行代码
     */
    private void runCodeForZoom(Stage zoomStage, StackPane zoomContentPane) {
        String code = outputArea.getText().trim();

        if (code.isEmpty()) {
            return;
        }

        // 清空之前的预览
        zoomContentPane.getChildren().clear();

        // 显示加载中
        Label loadingLabel = new Label("正在加载预览...");
        loadingLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px;");
        loadingLabel.setAlignment(Pos.CENTER);
        zoomContentPane.getChildren().add(loadingLabel);

        // 创建一个新的CodeRunner实例用于放大预览
        CodeRunner zoomRunner = new CodeRunner();

        // 设置舞台回调
        zoomRunner.setStageCallback(stage -> {
            Platform.runLater(() -> {
                try {
                    // 获取舞台的场景
                    javafx.scene.Scene scene = stage.getScene();
                    if (scene != null) {
                        // 清空预览区域
                        zoomContentPane.getChildren().clear();

                        // 创建新的根节点
                        BorderPane container = new BorderPane();
                        container.setStyle("-fx-background-color: white;");

                        // 添加场景的根节点到容器
                        Node rootNode = scene.getRoot();
                        container.setCenter(rootNode);

                        // 添加到预览区域
                        zoomContentPane.getChildren().add(container);

                        // 隐藏原始舞台
                        stage.hide();

                        addLog("✅ 放大预览加载成功");
                    }
                } catch (Exception e) {
                    addLog("❌ 放大预览加载失败: " + e.getMessage());

                    // 显示错误信息
                    zoomContentPane.getChildren().clear();
                    Label errorLabel = new Label("放大预览加载失败: " + e.getMessage());
                    errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12px; -fx-wrap-text: true;");
                    errorLabel.setAlignment(Pos.CENTER);
                    zoomContentPane.getChildren().add(errorLabel);
                }
            });
        });

        // 在新的线程中运行代码
        new Thread(() -> {
            try {
                // 运行代码
                zoomRunner.runJavaFXInCurrentVM(code, error -> {
                    Platform.runLater(() -> {
                        addLog("❌ 放大预览运行失败: " + error);

                        // 显示错误信息
                        zoomContentPane.getChildren().clear();
                        Label errorLabel = new Label("运行失败: " + error);
                        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12px; -fx-wrap-text: true;");
                        errorLabel.setAlignment(Pos.CENTER);
                        zoomContentPane.getChildren().add(errorLabel);
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    addLog("❌ 放大预览异常: " + e.getMessage());

                    // 显示错误信息
                    zoomContentPane.getChildren().clear();
                    Label errorLabel = new Label("运行异常: " + e.getMessage());
                    errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12px; -fx-wrap-text: true;");
                    errorLabel.setAlignment(Pos.CENTER);
                    zoomContentPane.getChildren().add(errorLabel);
                });
            }
        }).start();
    }
    /**
     * 创建节点的深度副本（简化版）
     */
    private Region createDeepCopy(Node original) {
        try {
            // 这是一个简化的深度复制方法
            // 对于复杂的场景，可能需要更复杂的复制逻辑
            if (original instanceof Region) {
                Region region = (Region) original;

                // 创建一个相同类型的新Region（简化处理）
                Region copy = new Pane();
                copy.setStyle(region.getStyle());
                copy.setPrefSize(region.getPrefWidth(), region.getPrefHeight());
                copy.setMinSize(region.getMinWidth(), region.getMinHeight());
                copy.setMaxSize(region.getMaxWidth(), region.getMaxHeight());

                // 复制布局约束
                copy.setPadding(region.getPadding());

                // 复制子节点（递归）
                if (region instanceof Pane) {
                    Pane pane = (Pane) region;
                    for (Node child : pane.getChildren()) {
                        Node childCopy = createDeepCopy(child);
                        if (childCopy != null) {
                            ((Pane) copy).getChildren().add(childCopy);
                        }
                    }
                }

                return copy;
            }
        } catch (Exception e) {
            addLog("创建节点副本失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 运行代码 - 在当前JVM中运行并显示预览
     */
    private void runCode() {
        String code = outputArea.getText().trim();

        if (code.isEmpty()) {
            showAlert("错误", "没有代码可运行");
            return;
        }

        // 更新运行状态
        runButton.setDisable(true);
        runProgressIndicator = new ProgressIndicator();
        runProgressIndicator.setVisible(true);
        addLog("开始预览代码...");

        // 清空之前的预览
        clearPreview();

        // 设置舞台回调
        codeRunner.setStageCallback(stage -> {
            Platform.runLater(() -> {
                try {
                    // 获取舞台的场景
                    javafx.scene.Scene scene = stage.getScene();
                    if (scene != null) {
                        // 清空预览区域
                        previewContentPane.getChildren().clear();

                        // 创建新的根节点
                        BorderPane container = new BorderPane();
                        container.setStyle("-fx-background-color: white;");

                        // 添加场景的根节点到容器
                        Node rootNode = scene.getRoot();
                        container.setCenter(rootNode);

                        // 添加控制按钮
                        HBox controls = new HBox(10);
                        controls.setPadding(new Insets(5));
                        controls.setStyle("-fx-background-color: #f0f0f0;");

                        Button closeButton = new Button("关闭");
                        closeButton.setOnAction(e -> clearPreview());

                        Button refreshButton = new Button("刷新");
                        refreshButton.setOnAction(event -> runCode());

                        controls.getChildren().addAll(closeButton, refreshButton);
                        container.setBottom(controls);

                        // 添加到预览区域
                        previewContentPane.getChildren().add(container);

                        // 保存容器引用
                        previewContainer = container;

                        // 保存引用
                        embeddedStage = stage;
                        embeddedScene = scene;

                        // 隐藏原始舞台
                        stage.hide();

                        addLog("✅ 代码预览加载成功");
                        runProgressIndicator.setVisible(false);
                        runButton.setDisable(false);
                    }
                } catch (Exception e) {
                    addLog("❌ 预览加载失败: " + e.getMessage());
                    runProgressIndicator.setVisible(false);
                    runButton.setDisable(false);

                    // 显示错误信息
                    previewContentPane.getChildren().clear();
                    Label errorLabel = new Label("预览加载失败: " + e.getMessage());
                    errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12px; -fx-wrap-text: true;");
                    errorLabel.setAlignment(Pos.CENTER);
                    previewContentPane.getChildren().add(errorLabel);
                }
            });
        });

        // 使用新的预览方法
        codeRunner.runJavaFXInCurrentVM(code, error -> {
            Platform.runLater(() -> {
                addLog("❌ " + error);
                runProgressIndicator.setVisible(false);
                runButton.setDisable(false);

                // 显示错误信息
                previewContentPane.getChildren().clear();
                Label errorLabel = new Label("运行失败: " + error);
                errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12px; -fx-wrap-text: true;");
                errorLabel.setAlignment(Pos.CENTER);
                previewContentPane.getChildren().add(errorLabel);
            });
        });
    }

    /**
     * 验证代码
     */
    private void validateCode() {
        String code = outputArea.getText().trim();

        if (code.isEmpty()) {
            showAlert("错误", "没有代码可验证");
            return;
        }

        addLog("验证代码...");

        String validationError = codeRunner.validateCode(code);
        if (validationError == null) {
            addLog("✅ 代码验证通过");
            showAlert("验证成功", "✅ 代码验证通过，可以运行！");
        } else {
            addLog("❌ 代码验证失败: " + validationError);
            showAlert("验证失败", "❌ " + validationError);
        }
    }

    /**
     * 创建底部区域
     */
    private HBox createFooter() {
        HBox footer = new HBox(15);
        footer.setPadding(new Insets(15, 20, 15, 20));
        footer.setStyle("-fx-background-color: #2c3e50;");
        footer.setAlignment(Pos.CENTER);

        // 状态标签
        statusLabel = new Label(getStatusText());
        statusLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 12px;");

        // 辅助按钮
        Button mergeButton = new Button("🔗 合并代码");
        mergeButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        mergeButton.setOnAction(e -> openCodeMerger());

        Button helpButton = new Button("❓ 帮助");
        helpButton.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white;");
        helpButton.setOnAction(e -> showHelp());

        footer.getChildren().addAll(
                statusLabel,
                new Separator(),
                mergeButton,
                helpButton
        );

        return footer;
    }

    /**
     * 获取状态文本
     */
    private String getStatusText() {
        if (javafxHome == null) {
            return "⚠ JavaFX未检测到 - 请在设置中配置JavaFX SDK路径";
        } else if (javafxHome.equals("classpath")) {
            return "📊 就绪 - JavaFX已集成，可直接生成和运行代码";
        } else {
            return "📊 就绪 - JavaFX已配置，可生成和运行代码";
        }
    }

    /**
     * 生成代码
     */
    private void generateCode() {
        String prompt = promptArea.getText().trim();
        String uiType = uiTypeComboBox.getValue().replaceAll("[^\\p{ASCII}]", "").trim();
        String className = classNameField.getText().trim();

        // 验证输入
        if (prompt.isEmpty()) {
            showAlert("输入错误", "请描述你想要的UI界面");
            return;
        }

        if (className.isEmpty()) {
            classNameField.setText("GeneratedUI");
            className = "GeneratedUI";
        }

        // 更新UI状态
        statusLabel.setText("🤖 AI正在生成代码...");
        generateButton.setDisable(true);
        progressIndicator.setVisible(true);
        addLog("开始生成代码...");

        // 异步生成代码
        String finalClassName = className;
        CompletableFuture.runAsync(() -> {
            try {
                // 构建完整的prompt
                String fullPrompt = buildFullPrompt(prompt, uiType, finalClassName);
                addLog("构建提示完成，长度: " + fullPrompt.length());

                // 生成代码
                String generatedCode = aiClient.generateCode(fullPrompt);
                addLog("AI响应接收完成，代码长度: " + generatedCode.length());

                // 清理代码
                String cleanCode = cleanGeneratedCode(generatedCode);
                addLog("代码清理完成，行数: " + cleanCode.split("\n").length);

                // 更新UI
                Platform.runLater(() -> {
                    outputArea.setText(cleanCode);
                    statusLabel.setText("✅ AI代码生成完成！");
                    generateButton.setDisable(false);
                    progressIndicator.setVisible(false);

                    // 启用运行按钮
                    runButton.setDisable(false);

                    addLog("代码生成成功，已显示在编辑区域");

                    // 显示成功消息
                    showAlert("生成成功",
                            "✨ JavaFX代码生成完成！\n\n" +
                                    "• 生成的类: " + finalClassName + "\n" +
                                    "• 代码行数: " + cleanCode.lines().count() + "\n" +
                                    "• 已复制到代码编辑区域，可编辑后运行");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    String errorMessage = "❌ 生成代码时出错:\n" + e.getMessage();
                    outputArea.setText(errorMessage);
                    statusLabel.setText("❌ AI生成失败");
                    generateButton.setDisable(false);
                    progressIndicator.setVisible(false);

                    addLog("代码生成失败: " + e.getMessage());
                    e.printStackTrace();

                    showAlert("生成失败",
                            "生成JavaFX代码失败: " + e.getMessage() +
                                    "\n\n可能的原因：\n" +
                                    "1. API密钥无效\n" +
                                    "2. 网络连接问题\n" +
                                    "3. 输入描述过于模糊\n" +
                                    "4. API服务暂时不可用");
                });
            }
        });
    }

    /**
     * 粘贴代码从剪贴板
     */
    private void pasteCodeFromClipboard() {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            String code = clipboard.getString();
            outputArea.setText(code);
            statusLabel.setText("📋 已从剪贴板粘贴代码");
            addLog("已从剪贴板粘贴代码");
        } else {
            showAlert("剪贴板为空", "剪贴板中没有文本内容");
        }
    }

    /**
     * 从文件加载代码
     */
    private void loadCodeFromFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("加载Java文件");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Java Files", "*.java")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                String code = Files.readString(file.toPath());
                outputArea.setText(code);
                statusLabel.setText("📂 已加载文件: " + file.getName());
                addLog("已从文件加载代码: " + file.getName());
                showAlert("加载成功", "代码已从文件加载: " + file.getName());
            } catch (IOException e) {
                showAlert("加载失败", "读取文件失败: " + e.getMessage());
                addLog("❌ 加载文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 添加日志
     */
    private void addLog(String message) {
        String timestamp = java.time.LocalTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")
        );
        Platform.runLater(() -> {
            previewArea.appendText("[" + timestamp + "] " + message + "\n");
            // 自动滚动到底部
            previewArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    /**
     * 构建完整的prompt
     */
    private String buildFullPrompt(String description, String uiType, String className) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专业的JavaFX UI代码生成专家。请生成一个完整的JavaFX UI类。\n\n");
        prompt.append("## 需求规格\n");
        prompt.append("1. UI类型: ").append(uiType).append("\n");
        prompt.append("2. 类名: ").append(className).append("\n");
        prompt.append("3. 使用Java 17和JavaFX 21\n\n");

        prompt.append("## 详细需求描述\n");
        prompt.append(description).append("\n\n");

        prompt.append("## 技术要求\n");
        prompt.append("- 使用现代JavaFX布局（VBox, HBox, GridPane, BorderPane等）\n");
        prompt.append("- 使用FXML或纯Java代码（推荐纯Java）\n");
        prompt.append("- 包含必要的事件处理逻辑\n");
        prompt.append("- 代码结构清晰，有良好的缩进\n");
        prompt.append("- 遵循Java命名规范\n");

        if (includeCommentsCheckBox.isSelected()) {
            prompt.append("- 添加必要的注释说明重要部分\n");
        }

        if (includeMainMethodCheckBox.isSelected()) {
            prompt.append("- 包含main方法，使程序可以独立运行\n");
            prompt.append("- main方法中应启动JavaFX应用程序\n");
        }

        prompt.append("\n## 输出要求\n");
        prompt.append("1. 只返回Java代码，不要任何解释\n");
        prompt.append("2. 不要使用markdown代码块标记（不要```java或```）\n");
        prompt.append("3. 代码必须是完整、可编译、可运行的\n");
        prompt.append("4. 包含所有必要的import语句\n");
        prompt.append("5. 如果涉及样式，可以内联CSS或使用外部样式表\n");

        prompt.append("\n## 示例参考\n");
        prompt.append("类的基本结构应类似：\n");
        prompt.append("package com.example.ui;\n");
        prompt.append("import javafx.application.Application;\n");
        prompt.append("import javafx.scene.Scene;\n");
        prompt.append("// ... 其他import\n");
        prompt.append("public class ").append(className).append(" extends Application {\n");
        prompt.append("    // 类实现\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * 清理生成的代码
     */
    private String cleanGeneratedCode(String code) {
        // 移除markdown代码块标记
        code = code.replaceAll("(?i)```java\\s*", "")
                .replaceAll("(?i)```\\s*", "")
                .replaceAll("(?i)```[a-z]*", "")
                .trim();

        // 如果代码不以package或import开头，添加默认导入
        if (!code.startsWith("package") && !code.startsWith("import")) {
            code = "import javafx.application.Application;\n" +
                    "import javafx.scene.Scene;\n" +
                    "import javafx.scene.layout.*;\n" +
                    "import javafx.scene.control.*;\n" +
                    "import javafx.stage.Stage;\n\n" + code;
        }

        return code;
    }

    /**
     * 加载示例描述
     */
    private void loadExample(String exampleType) {
        String exampleText = "";
        String className = "";

        switch (exampleType) {
            case "login":
                exampleText = "创建一个现代化的登录界面，包含以下功能：\n" +
                        "1. 左侧区域：显示应用Logo和欢迎语，使用渐变色背景\n" +
                        "2. 右侧登录表单：\n" +
                        "   - 用户名输入框（带用户图标）\n" +
                        "   - 密码输入框（带锁图标和显示/隐藏切换）\n" +
                        "   - 记住密码复选框\n" +
                        "   - 忘记密码链接\n" +
                        "   - 登录按钮（使用渐变色，有悬停效果）\n" +
                        "   - 第三方登录按钮（Google、GitHub）\n" +
                        "3. 底部：显示注册链接和版权信息\n" +
                        "4. 使用CSS实现：\n" +
                        "   - 整体白色和蓝色主题\n" +
                        "   - 输入框圆角和阴影\n" +
                        "   - 按钮渐变和动画效果\n" +
                        "   - 响应式布局\n\n" +
                        "要求代码完整，可以直接运行";
                className = "LoginUI";
                break;

            case "table":
                exampleText = "创建一个学生信息管理系统界面，包含：\n" +
                        "1. 顶部菜单栏：文件、编辑、查看、帮助菜单\n" +
                        "2. 工具栏：添加、编辑、删除、刷新、导出按钮（带图标）\n" +
                        "3. 左侧树形导航：学生管理、课程管理、成绩管理、统计报表\n" +
                        "4. 中央区域：\n" +
                        "   - 搜索框（可实时筛选）\n" +
                        "   - 学生信息表格（学号、姓名、性别、年龄、专业、成绩）\n" +
                        "   - 支持列排序和筛选\n" +
                        "   - 分页控件\n" +
                        "5. 右侧详细信息面板：显示选中学生的详细信息\n" +
                        "6. 底部状态栏：显示记录数和操作状态\n" +
                        "7. 使用TableView和ObservableList管理数据\n" +
                        "8. 添加数据验证和错误处理";
                className = "StudentManagementUI";
                break;

            case "dashboard":
                exampleText = "创建一个数据可视化仪表板，包含：\n" +
                        "1. 顶部导航栏：应用名称、用户头像、通知图标、搜索框、设置按钮\n" +
                        "2. 侧边栏导航菜单：仪表板、用户管理、数据分析、报表、设置（带图标）\n" +
                        "3. 主内容区分4个统计卡片：\n" +
                        "   - 卡片1：今日活跃用户（数字+折线图）\n" +
                        "   - 卡片2：收入统计（饼图）\n" +
                        "   - 卡片3：任务完成进度（进度条）\n" +
                        "   - 卡片4：系统状态监控（CPU、内存使用率）\n" +
                        "4. 底部：最近活动列表和系统消息\n" +
                        "5. 使用JavaFX图表库（LineChart, PieChart, BarChart）\n" +
                        "6. 使用CSS实现：\n" +
                        "   - 卡片阴影和悬停效果\n" +
                        "   - 暗色主题\n" +
                        "   - 响应式网格布局";
                className = "DashboardUI";
                break;
        }

        promptArea.setText(exampleText);
        classNameField.setText(className);

        statusLabel.setText("📚 已加载" + exampleType + "示例");
        addLog("已加载示例: " + exampleType);
    }

    /**
     * 复制代码到剪贴板
     */
    private void copyCodeToClipboard() {
        String code = outputArea.getText();
        if (!code.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(code);
            clipboard.setContent(content);

            statusLabel.setText("📋 代码已复制到剪贴板");
            addLog("代码已复制到剪贴板");
        }
    }

    /**
     * 格式化代码
     */
    private void formatCode() {
        String code = outputArea.getText();
        if (code.isEmpty()) {
            return;
        }

        // 简单的代码格式化
        String formatted = code.replaceAll("\\s+\\n", "\n")  // 移除行尾空白
                .replaceAll("\\n{3,}", "\n\n") // 限制空行数量
                .replaceAll("(?m)^\\s+$", "")  // 移除纯空白行
                .trim();

        outputArea.setText(formatted);
        statusLabel.setText("✨ 代码已格式化");
        addLog("代码已格式化");
    }

    /**
     * 保存代码到文件
     */
    private void saveCodeToFile() {
        String code = outputArea.getText();
        if (code.isEmpty()) {
            showAlert("保存错误", "没有代码可保存");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存JavaFX代码");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Java Files", "*.java")
        );

        String className = classNameField.getText().trim();
        if (!className.isEmpty()) {
            fileChooser.setInitialFileName(className + ".java");
        } else {
            fileChooser.setInitialFileName("GeneratedUI.java");
        }

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), code);
                showAlert("保存成功", "✅ JavaFX代码已保存到:\n" + file.getAbsolutePath());
                statusLabel.setText("💾 已保存: " + file.getName());
                addLog("代码已保存到: " + file.getAbsolutePath());
            } catch (IOException e) {
                showAlert("保存失败", "保存文件失败: " + e.getMessage());
                addLog("❌ 保存文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 打开代码合并器
     */
    private void openCodeMerger() {
        try {
            CodeMergeUI mergeUI = new CodeMergeUI();
            mergeUI.show();
            addLog("打开代码合并工具");
        } catch (Exception e) {
            showAlert("错误", "无法打开代码合并器: " + e.getMessage());
            addLog("❌ 打开代码合并器失败: " + e.getMessage());
        }
    }

    /**
     * 打开设置
     */
    private void openSettings() {
        Stage settingsStage = new Stage();
        settingsStage.setTitle("JavaFX配置");

        VBox settingsLayout = new VBox(15);
        settingsLayout.setPadding(new Insets(20));
        settingsLayout.setStyle("-fx-background-color: #ecf0f1;");

        Label titleLabel = new Label("🔧 JavaFX运行环境配置");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // JavaFX路径设置
        Label javafxLabel = new Label("JavaFX SDK路径:");
        javafxLabel.setStyle("-fx-font-weight: bold;");

        TextField javafxPathField = new TextField(javafxHome != null && !javafxHome.equals("classpath") ? javafxHome : "");
        javafxPathField.setPromptText("例如: C:/javafx-sdk-21.0.1 或 " + System.getProperty("user.home") + "/.m2/repository/org/openjfx");
        javafxPathField.setPrefWidth(400);

        Button browseJavafxButton = new Button("浏览...");
        browseJavafxButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        browseJavafxButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择JavaFX SDK目录");
            chooser.setInitialDirectory(new File(System.getProperty("user.home")));
            File dir = chooser.showDialog(settingsStage);
            if (dir != null) {
                javafxPathField.setText(dir.getAbsolutePath());
            }
        });

        HBox javafxBox = new HBox(10, javafxPathField, browseJavafxButton);
        javafxBox.setAlignment(Pos.CENTER_LEFT);

        // 检测按钮
        Button detectButton = new Button("🔍 自动检测JavaFX");
        detectButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        detectButton.setOnAction(e -> {
            String detectedPath = autoDetectJavaFX();
            if (detectedPath != null) {
                javafxPathField.setText(detectedPath);
                showAlert("检测成功", "自动检测到JavaFX: " + detectedPath);
            } else {
                showAlert("检测失败", "未自动检测到JavaFX SDK，请手动选择");
            }
        });

        // 环境信息
        Label envLabel = new Label("当前环境信息:");
        envLabel.setStyle("-fx-font-weight: bold;");

        TextArea envInfo = new TextArea();
        envInfo.setEditable(false);
        envInfo.setWrapText(true);
        envInfo.setPrefHeight(100);
        envInfo.setText(getEnvironmentInfo());

        // 说明文本
        Label explanationLabel = new Label("💡 说明：\n" +
                "• 如果JavaFX已在类路径中（如使用Maven依赖），可以留空\n" +
                "• 如果单独下载了JavaFX SDK，请选择SDK目录\n" +
                "• 建议路径：C:/javafx-sdk-21.0.1 或 " + System.getProperty("user.home") + "/.m2/repository/org/openjfx");
        explanationLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px; -fx-wrap-text: true;");

        // 保存按钮
        Button saveButton = new Button("💾 保存配置");
        saveButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        saveButton.setOnAction(e -> {
            String newPath = javafxPathField.getText().trim();

            if (newPath.isEmpty()) {
                // 检查类路径中是否有JavaFX
                try {
                    Class.forName("javafx.application.Application");
                    javafxHome = "classpath";
                    showAlert("保存成功", "✅ 已设置为使用类路径中的JavaFX");
                } catch (ClassNotFoundException ex) {
                    showAlert("配置错误", "请指定JavaFX SDK路径或确保JavaFX在类路径中");
                    return;
                }
            } else {
                File dir = new File(newPath);
                if (!dir.exists() || !isValidJavaFXSDK(dir.toPath())) {
                    showAlert("路径无效", "指定的路径不是有效的JavaFX SDK目录\n请选择包含lib目录和jar文件的JavaFX SDK");
                    return;
                }
                javafxHome = newPath;
                showAlert("保存成功", "✅ JavaFX SDK路径已设置: " + newPath);
            }

            // 更新状态
            updateJavaFXStatus();
            settingsStage.close();
            addLog("JavaFX配置已更新: " + (javafxHome.equals("classpath") ? "类路径" : javafxHome));
        });

        Button cancelButton = new Button("取消");
        cancelButton.setOnAction(e -> settingsStage.close());

        HBox buttonBox = new HBox(15, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        settingsLayout.getChildren().addAll(
                titleLabel,
                javafxLabel,
                javafxBox,
                detectButton,
                new Separator(),
                envLabel,
                envInfo,
                explanationLabel,
                new Separator(),
                buttonBox
        );

        Scene settingsScene = new Scene(settingsLayout, 550, 450);
        settingsStage.setScene(settingsScene);
        settingsStage.show();

        addLog("打开JavaFX配置");
    }

    /**
     * 自动检测JavaFX
     */
    private String autoDetectJavaFX() {
        // 重新检测
        detectJavaFX();
        if (javafxHome != null && !javafxHome.equals("classpath")) {
            return javafxHome;
        }
        return null;
    }

    /**
     * 获取环境信息
     */
    private String getEnvironmentInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Java版本: ").append(System.getProperty("java.version")).append("\n");
        info.append("Java Home: ").append(System.getProperty("java.home")).append("\n");
        info.append("当前用户: ").append(System.getProperty("user.name")).append("\n");
        info.append("操作系统: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
        info.append("类路径: ").append(System.getProperty("java.class.path").length() > 100 ?
                System.getProperty("java.class.path").substring(0, 100) + "..." :
                System.getProperty("java.class.path"));
        return info.toString();
    }

    /**
     * 更新JavaFX状态
     */
    private void updateJavaFXStatus() {
        // 更新标题
        ((Label) ((VBox) ((BorderPane) statusLabel.getScene().getRoot()).getTop()).getChildren().get(1))
                .setText("JavaFX状态: " + getJavaFXStatus());

        // 更新状态标签
        statusLabel.setText(getStatusText());
    }

    /**
     * 显示帮助信息
     */
    private void showHelp() {
        String helpText = """
            =========== JavaFX AI代码生成器 - 使用帮助 ===========
            
            🎯 三列布局说明：
            第一列（左侧）：AI描述输入
                • 输入界面需求描述
                • 选择界面类型和选项
                • 点击生成按钮获取AI代码
            
            第二列（中间）：代码编辑区域
                • 显示和编辑AI生成的代码
                • 支持粘贴自己的JavaFX代码
                • 提供复制、保存、格式化等功能
                • 实时显示代码行数和字符数
            
            第三列（右侧）：运行效果预览
                • 编译和运行代码
                • 运行结果会直接嵌入到此区域
                • 显示运行状态和日志
                • 提供运行代码功能，在当前JVM中运行
                • 显示运行环境信息
            
            🎮 主要功能：
            1. AI生成：输入描述，AI自动生成JavaFX代码
            2. 代码编辑：支持编辑AI生成的或自己编写的代码
            3. 实时预览：运行代码查看UI效果（直接嵌入到右侧）
            4. 代码验证：检查代码是否可以运行
            5. 文件操作：加载、保存Java文件
            6. 代码合并：将生成的代码合并到现有项目
            
            🔧 JavaFX配置：
            • 如果JavaFX已在类路径中（如Maven依赖），程序会自动检测
            • 如果单独安装了JavaFX SDK，请在设置中配置路径
            • 建议下载地址：https://gluonhq.com/products/javafx/
            
            ⚡ 运行模式：
            • 运行代码（当前JVM）：在当前JVM中运行JavaFX应用
            • 优点：启动速度快，代码执行效率高
            • 注意：如果代码有问题可能会影响主程序
            
            🔍 放大预览功能改进：
            • 放大预览现在创建场景副本，不会影响原始预览
            • 关闭放大窗口后，主预览区域保持不变
            • 支持刷新功能，可以重新运行代码
            
            💡 使用技巧：
            • 可以直接粘贴已有的JavaFX代码运行
            • 代码编辑后会自动启用运行按钮
            • 运行日志帮助调试问题
            • 可以配置JavaFX SDK路径
            • 点击"清空预览"按钮可以清空预览区域
            • 点击"放大"按钮可以全屏查看预览效果（不会影响原始预览）
            
            ===============================================
            """;

        showAlert("使用帮助", helpText);
        addLog("查看帮助信息");
    }

    /**
     * 显示提示对话框
     */
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            // 设置对话框大小
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.getDialogPane().setMinWidth(400);

            alert.showAndWait();
        });
    }

    /**
     * 主方法
     */
    public static void main(String[] args) {
        // 设置JavaFX相关属性
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");

        // 打印启动信息
        System.out.println("=================================");
        System.out.println("JavaFX AI代码生成器 - 三列布局版");
        System.out.println("启动中...");
        System.out.println("Java版本: " + System.getProperty("java.version"));
        System.out.println("Java Home: " + System.getProperty("java.home"));
        System.out.println("用户Home: " + System.getProperty("user.home"));
        System.out.println("=================================\n");

        // 启动应用程序
        launch(args);
    }
}