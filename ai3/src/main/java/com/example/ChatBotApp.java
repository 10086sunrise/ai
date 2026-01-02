package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatBotApp extends Application {

    private final AliyunAIClient aiClient = new AliyunAIClient();
    private final WeatherService weatherService = new WeatherService();
    private final NewsService newsService = new NewsService();

    private final VBox chatBox = new VBox(12);
    private final ScrollPane scrollPane = new ScrollPane();
    private final TextArea inputArea = new TextArea();
    private final Button sendButton = new Button("发送");
    private final Label statusLabel = new Label("就绪");

    public ChatBotApp() throws IOException {
    }

    @Override
    public void start(Stage primaryStage) {
        String fontFamily = "Segoe UI, Microsoft YaHei, sans-serif";

        Scene scene = new Scene(new BorderPane(), 900, 650);
        scene.getStylesheets().add("data:text/css," + String.join("",
                "* { -fx-font-family: \"" + fontFamily + "\"; }",
                ".root { -fx-background-color: #1e1e1e; }"
        ));

        BorderPane root = (BorderPane) scene.getRoot();

        // 聊天区域
        chatBox.setFillWidth(true);
        chatBox.setPadding(new Insets(15));
        chatBox.setStyle("-fx-background-color: #1e1e1e;");

        scrollPane.setContent(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #1e1e1e; -fx-border-color: transparent;");

        // 输入区域
        inputArea.setPrefRowCount(2);
        inputArea.setWrapText(true);
        inputArea.setStyle(
                "-fx-background-color: #f8f8f8;" +
                        "-fx-text-fill: black;" +
                        "-fx-font-size: 14px;" +
                        "-fx-padding: 10;" +
                        "-fx-border-radius: 16;" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-color: #ddd;"
        );

        sendButton.setStyle(
                "-fx-background-color: #0078d4;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-padding: 8 20;" +
                        "-fx-background-radius: 16;"
        );
        sendButton.setOnMouseEntered(e -> sendButton.setStyle(
                "-fx-background-color: #106ebe;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-padding: 8 20;" +
                        "-fx-background-radius: 16;"
        ));
        sendButton.setOnMouseExited(e -> sendButton.setStyle(
                "-fx-background-color: #0078d4;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 14px;" +
                        "-fx-padding: 8 20;" +
                        "-fx-background-radius: 16;"
        ));

        HBox inputBox = new HBox(12, inputArea, sendButton);
        inputBox.setAlignment(Pos.CENTER_RIGHT);
        inputBox.setPadding(new Insets(12));
        HBox.setHgrow(inputArea, Priority.ALWAYS);

        // 状态栏
        statusLabel.setTextFill(Color.LIGHTGRAY);
        statusLabel.setStyle("-fx-font-size: 12px; -fx-padding: 0 10;");
        HBox statusBox = new HBox(statusLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setStyle("-fx-background-color: #252526; -fx-padding: 6 0;");

        root.setCenter(scrollPane);
        root.setBottom(new VBox(inputBox, statusBox));

        // 事件绑定
        sendButton.setOnAction(e -> sendMessage());
        inputArea.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode().toString().equals("ENTER")) {
                sendMessage();
            }
        });

        addMessage("🤖", "你好！我是你的智能助手～", false);

        primaryStage.setTitle("💬 智能对话机器人 · 基于通义千问");
        primaryStage.setScene(scene);
        primaryStage.show();

        // 测试连接
        new Thread(() -> {
            boolean connected = aiClient.testConnection();
            Platform.runLater(() -> {
                statusLabel.setText(connected ? "✅ 已连接 DashScope API" : "❌ API 连接失败");
                statusLabel.setTextFill(connected ? Color.LIGHTGREEN : Color.ORANGERED);
            });
        }).start();
    }

    private void sendMessage() {
        String userText = inputArea.getText().trim();
        if (userText.isEmpty()) return;

        addMessage("👤", userText, true);
        inputArea.clear();

        // 检查是否包含关键词 "javafx ai"（不区分大小写）
        String lowerText = userText.toLowerCase();
        if (lowerText.contains("javafx ai") || lowerText.contains("javafx-ai") ||
                lowerText.contains("javafx_ai") ||
                lowerText.contains("javafx ai代码生成器") ||
                lowerText.contains("javaFX AI代码生成器") ||
                lowerText.contains("javaFX AI") ||
                lowerText.contains("javafx代码生成器")) {
            handleJavaFXAI();
            return;
        }

        Label thinking = new Label("🤖 正在思考...");
        thinking.setTextFill(Color.LIGHTGRAY);
        thinking.setStyle("-fx-font-style: italic; -fx-font-size: 13px; -fx-padding: 8;");
        chatBox.getChildren().add(thinking);
        scrollPane.setVvalue(1.0);

        new Thread(() -> {
            try {
                String response = aiClient.chat(userText);
                Platform.runLater(() -> {
                    chatBox.getChildren().remove(thinking);
                    if (isToolCall(response)) {
                        handleToolCall(response);
                    } else {
                        addMessage("🤖", response, false);
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    chatBox.getChildren().remove(thinking);
                    addMessage("⚠️", "出错了：" + e.getMessage(), false);
                });
            }
        }).start();
    }

    /**
     * 处理 JavaFX AI 请求 - 启动代码生成器
     */
    /**
     * 处理 JavaFX AI 请求 - 启动代码生成器
     */
    /**
     * 处理 JavaFX AI 请求 - 启动代码生成器
     */
    /**
     * 处理 JavaFX AI 请求 - 启动代码生成器
     */
    private void handleJavaFXAI() {
        addMessage("🤖", "🚀 正在启动 JavaFX AI 代码生成器...", false);

        // 在新线程中启动JavaFX AI代码生成器
        new Thread(() -> {
            try {
                // 设置JavaFX相关属性
                System.setProperty("prism.lcdtext", "false");
                System.setProperty("prism.text", "t2k");

                // 使用Application.launch()在新线程中启动
                new Thread(() -> {
                    // 创建新的Stage实例
                    Platform.runLater(() -> {
                        try {
                            // 创建App实例
                            App app = new App();
                            Stage newStage = new Stage();
                            app.start(newStage);

                            Platform.runLater(() -> {
                                addMessage("✅", "🎉 JavaFX AI 代码生成器已成功启动！", false);
                                addMessage("🤖", "💡 提示：JavaFX AI 代码生成器已在新窗口中打开。\n" +
                                        "• 您可以在左侧输入界面描述\n" +
                                        "• 在中间编辑生成的代码\n" +
                                        "• 在右侧预览运行效果\n" +
                                        "• 支持实时预览和放大功能", false);
                            });
                        } catch (Exception e) {
                            Platform.runLater(() -> {
                                addMessage("⚠️", "启动失败：" + e.getMessage(), false);
                                e.printStackTrace();
                            });
                        }
                    });
                }).start();

            } catch (Exception e) {
                Platform.runLater(() -> {
                    addMessage("⚠️", "启动时发生错误：" + e.getMessage(), false);
                });
            }
        }).start();
    }

    private boolean isToolCall(String text) {
        return text != null && text.trim().startsWith("{") && text.contains("\"tool\"");
    }

    private void handleToolCall(String jsonStr) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(jsonStr);
            String tool = node.get("tool").asText();

            switch (tool) {
                case "weather":
                    String city = node.get("city").asText();
                    String weatherResult = weatherService.getWeather(city);
                    Platform.runLater(() -> addMessage("🤖", weatherResult, false));
                    break;
                case "news":
                    String cat = node.has("category") ? node.get("category").asText() : "general";
                    String newsResult = newsService.getNews(cat);
                    Platform.runLater(() -> addMessage("🤖", newsResult, false));
                    break;
                case "time":
                    String timeResult = "⏰ 当前时间：" + java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")
                    );
                    Platform.runLater(() -> addMessage("🤖", timeResult, false));
                    break;
                case "open_app":
                    String app = node.get("app").asText();
                    if ("netease_music".equalsIgnoreCase(app)) {
                        openNeteaseMusicFromDesktop();
                    }
                    break;
                default:
                    Platform.runLater(() -> addMessage("⚠️", "未知工具: " + tool, false));
            }

        } catch (Exception e) {
            Platform.runLater(() -> addMessage("⚠️", "工具执行出错：" + e.getMessage(), false));
        }
    }

    /**
     * 专门从【桌面】打开"网易云音乐"
     */
    private void openNeteaseMusicFromDesktop() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            Platform.runLater(() -> addMessage("⚠️", "仅支持 Windows 系统。", false));
            return;
        }

        new Thread(() -> {
            try {
                String desktopPath = System.getProperty("user.home") + "\\Desktop";
                java.io.File desktop = new java.io.File(desktopPath);

                if (!desktop.exists() || !desktop.isDirectory()) {
                    throw new IOException("桌面目录不存在");
                }

                // 1. 优先找 .lnk 快捷方式（最常见）
                java.io.File lnkFile = new java.io.File(desktop, "网易云音乐.lnk");
                if (lnkFile.exists()) {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "\"\"", "\"" + lnkFile.getAbsolutePath() + "\""});
                    Platform.runLater(() -> addMessage("🤖", "✅ 正在通过桌面快捷方式启动网易云音乐...", false));
                    return;
                }

                // 2. 再找 cloudmusic.exe（较少见，但支持）
                java.io.File exeFile = new java.io.File(desktop, "cloudmusic.exe");
                if (exeFile.exists()) {
                    Runtime.getRuntime().exec("\"" + exeFile.getAbsolutePath() + "\"");
                    Platform.runLater(() -> addMessage("🤖", "✅ 正在启动桌面版网易云音乐程序...", false));
                    return;
                }

                // 3. 尝试模糊匹配（如"网易云.lnk"、"NeteaseMusic.lnk"等）
                java.io.File[] allFiles = desktop.listFiles();
                if (allFiles != null) {
                    for (java.io.File file : allFiles) {
                        String name = file.getName().toLowerCase();
                        if (name.endsWith(".lnk") &&
                                (name.contains("网易云") || name.contains("netease") || name.contains("cloudmusic"))) {
                            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "\"\"", "\"" + file.getAbsolutePath() + "\""});
                            Platform.runLater(() -> addMessage("🤖", "✅ 已通过桌面找到并启动网易云音乐（文件: " + file.getName() + "）", false));
                            return;
                        }
                    }
                }

                // 4. 全部未找到
                throw new IOException("桌面未找到网易云音乐快捷方式或程序");

            } catch (Exception e) {
                Platform.runLater(() -> {
                    String msg = "❌ 无法从桌面打开网易云音乐：" + e.getMessage();
                    addMessage("⚠️", msg, false);
                });
            }
        }).start();
    }

    private void addMessage(String sender, String text, boolean isUser) {
        HBox messageBox = new HBox(12);
        messageBox.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messageBox.setPadding(new Insets(4, 0, 4, 0));

        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(650);

        if (isUser) {
            label.setStyle(
                    "-fx-background-color: #cce5ff;" +
                            "-fx-text-fill: black;" +
                            "-fx-font-size: 14px;" +
                            "-fx-background-radius: 14;" +
                            "-fx-padding: 12 16;"
            );
        } else {
            label.setStyle(
                    "-fx-background-color: #333333;" +
                            "-fx-text-fill: white;" +
                            "-fx-font-size: 14px;" +
                            "-fx-background-radius: 14;" +
                            "-fx-padding: 12 16;"
            );
        }

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.2));
        shadow.setRadius(4);
        shadow.setOffsetY(2);
        label.setEffect(shadow);

        Label timeLabel = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        timeLabel.setTextFill(Color.GRAY);
        timeLabel.setStyle("-fx-font-size: 11px;");

        if (isUser) {
            VBox bubble = new VBox(4, label, timeLabel);
            bubble.setAlignment(Pos.BOTTOM_RIGHT);
            messageBox.getChildren().add(bubble);
        } else {
            Label senderLabel = new Label(sender);
            senderLabel.setTextFill(Color.LIGHTBLUE);
            senderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

            VBox bubble = new VBox(4, senderLabel, label, timeLabel);
            bubble.setAlignment(Pos.BOTTOM_LEFT);
            messageBox.getChildren().add(bubble);
        }

        chatBox.getChildren().add(messageBox);
        scrollPane.setVvalue(1.0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}