package com.example;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.protocol.Protocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class AliyunAIClient {
    private static final Logger logger = LoggerFactory.getLogger(AliyunAIClient.class);

    // ====================== 阿里云API密钥配置 ======================
    private static final String ALIYUN_API_KEY = "sk-f4c7898cf95a41658c778e491eb3e097";
    // ============================================================

    private static final String MODEL_NAME = "qwen-plus";

    private final ObjectMapper objectMapper;
    private final Generation generation;

    public AliyunAIClient() throws IOException {
        this.objectMapper = new ObjectMapper();
        validateApiKey();

        logger.info("阿里云API密钥已配置，使用模型: {}", MODEL_NAME);
        System.out.println("✅ 阿里云AI客户端初始化完成");
        System.out.println("   API密钥长度：" + ALIYUN_API_KEY.length());
        System.out.println("   使用模型：" + MODEL_NAME);

        this.generation = new Generation(Protocol.HTTP.getValue(), "https://dashscope.aliyuncs.com/api/v1");
    }

    public AliyunAIClient(String apiKey, String model) {
        this.objectMapper = new ObjectMapper();
        String finalApiKey = (apiKey != null && !apiKey.trim().isEmpty()) ? apiKey : ALIYUN_API_KEY;
        String finalModel = (model != null && !model.trim().isEmpty()) ? model : MODEL_NAME;

        if (finalApiKey.equals("YOUR_ALIYUN_API_KEY_HERE")) {
            throw new IllegalArgumentException("API密钥未配置！");
        }

        this.generation = new Generation(Protocol.HTTP.getValue(), "https://dashscope.aliyuncs.com/api/v1");
    }

    // 💡【关键】增强版 System Prompt —— 强制工具调用返回 JSON
    private static final String TOOL_CALL_SYSTEM_PROMPT =
            "你是一个智能助手，具备调用外部工具的能力。请严格遵守以下规则：\n" +
                    "1. 当用户请求查询【天气】、【新闻】、【当前时间】或【打开应用】时，你必须调用对应工具。\n" +
                    "2. 调用工具时，请**仅返回一个纯 JSON 对象**，不要任何解释、问候、markdown 或其他文字。\n" +
                    "3. JSON 必须包含字段 \"tool\"，其值为以下之一：\"weather\"、\"news\"、\"time\"、\"open_app\"。\n" +
                    "4. 根据 tool 类型，补充必要参数：\n" +
                    "   - weather: 必须包含 \"city\"（如 \"北京\"）\n" +
                    "   - news: 可选 \"category\"（如 \"科技\"、\"体育\"，默认 \"general\"）\n" +
                    "   - open_app: 必须包含 \"app\"，值为 \"netease_music\"、\"browser\" 或 \"notepad\"\n" +
                    "5. 如果用户问题不涉及上述功能，请像普通助手一样直接回答。\n" +
                    "6. **绝对不要说“我无法获取实时信息”、“我的知识截止于...”等话术！**\n" +
                    "7. **输出必须是合法 JSON，且仅包含 JSON，前后不能有任何字符。**\n" +
                    "\n" +
                    "示例：\n" +
                    "用户：北京今天天气如何？\n" +
                    "你：{\"tool\":\"weather\",\"city\":\"北京\"}\n" +
                    "\n" +
                    "用户：打开网易云音乐\n" +
                    "你：{\"tool\":\"open_app\",\"app\":\"netease_music\"}\n" +
                    "\n" +
                    "用户：讲个笑话\n" +
                    "你：为什么程序员分不清万圣节和圣诞节？因为 Oct 31 == Dec 25！";

    /**
     * 【改造】通用对话接口：支持工具调用（用于 ChatBotApp）
     */
    public String chat(String userMessage) throws IOException {
        logger.info("开始通用对话（含工具调用），输入: {}", userMessage);

        validateApiKey();

        try {
            Message systemMsg = Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(TOOL_CALL_SYSTEM_PROMPT)
                    .build();

            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(userMessage)
                    .build();

            GenerationParam param = GenerationParam.builder()
                    .apiKey(ALIYUN_API_KEY)
                    .model(MODEL_NAME)
                    .messages(Arrays.asList(systemMsg, userMsg))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .temperature(0.3F)  // 👈 降低随机性，提高 JSON 稳定性
                    .topP(0.85)
                    .maxTokens(512)
                    .incrementalOutput(false)
                    .build();

            GenerationResult result = generation.call(param);

            if (result == null || result.getOutput() == null ||
                    result.getOutput().getChoices() == null || result.getOutput().getChoices().isEmpty()) {
                throw new IOException("API 返回空响应");
            }

            String content = result.getOutput().getChoices().get(0).getMessage().getContent();
            if (content == null) content = "";

            // 🔍 调试日志（可选开启）
            logger.debug("AI 原始响应: [{}]", content);

            return content.trim();

        } catch (ApiException e) {
            logger.error("API 调用异常", e);
            throw new IOException("通义千问 API 错误: " + e.getMessage(), e);
        } catch (NoApiKeyException | InputRequiredException e) {
            logger.error("请求参数错误", e);
            throw new IOException("请求参数无效: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("通用对话发生未知错误", e);
            throw new IOException("对话失败: " + e.getMessage(), e);
        }
    }

    /**
     * 【保留】生成 JavaFX 代码（原有功能不变）
     */
    public String generateCode(String prompt) throws IOException {
        logger.info("开始生成JavaFX代码，提示长度: {}", prompt.length());

        validateApiKey();

        try {
            Message systemMsg = Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(getSystemPrompt())
                    .build();

            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(prompt)
                    .build();

            GenerationParam param = GenerationParam.builder()
                    .apiKey(ALIYUN_API_KEY)
                    .model(MODEL_NAME)
                    .messages(Arrays.asList(systemMsg, userMsg))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .temperature(0.7F)
                    .topP(0.8)
                    .maxTokens(4000)
                    .incrementalOutput(false)
                    .build();

            GenerationResult result = generation.call(param);
            String generatedCode = extractCodeFromResponse(result);

            logger.info("代码生成成功，长度: {} 字符", generatedCode.length());
            return generatedCode;

        } catch (ApiException e) {
            logger.error("API 调用异常", e);
            throw new IOException("API调用异常: " + e.getMessage(), e);
        } catch (NoApiKeyException e) {
            logger.error("API密钥异常", e);
            throw new IOException("API密钥无效或缺失: " + e.getMessage(), e);
        } catch (InputRequiredException e) {
            logger.error("输入参数异常", e);
            throw new IOException("输入参数异常: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("生成代码时发生未知错误", e);
            throw new IOException("生成代码失败: " + e.getMessage(), e);
        }
    }

    private String extractCodeFromResponse(GenerationResult result) throws IOException {
        if (result == null || result.getOutput() == null ||
                result.getOutput().getChoices() == null || result.getOutput().getChoices().isEmpty()) {
            throw new IOException("API返回的响应为空或不完整");
        }

        String content = result.getOutput().getChoices().get(0).getMessage().getContent();
        if (content == null || content.trim().isEmpty()) {
            throw new IOException("API返回的代码内容为空");
        }

        content = content.replaceAll("(?i)```[a-z]*\\s*", "").trim();

        if (!content.contains("class") && !content.contains("import")) {
            logger.warn("响应可能不是Java代码: {}", content.substring(0, Math.min(100, content.length())));
        }

        if (!content.startsWith("package") && !content.startsWith("import")) {
            content = "import javafx.application.Application;\n" +
                    "import javafx.scene.Scene;\n" +
                    "import javafx.scene.layout.*;\n" +
                    "import javafx.scene.control.*;\n" +
                    "import javafx.stage.Stage;\n\n" + content;
        }

        return content;
    }

    private String getSystemPrompt() {
        return "你是一个专业的JavaFX UI代码生成助手。\n" +
                "请遵循以下规则：\n" +
                "1. 只返回Java代码，不要任何解释\n" +
                "2. 不要使用markdown代码块标记（如```java或```）\n" +
                "3. 代码必须是完整、可编译、可运行的\n" +
                "4. 使用Java 17和JavaFX 21\n" +
                "5. 包含必要的import语句\n" +
                "6. 使用现代JavaFX布局（VBox, HBox, GridPane, BorderPane等）\n" +
                "7. 如果有样式，可以内联CSS或使用外部样式表\n" +
                "8. 如果可能，包含main方法使程序可独立运行\n" +
                "9. 代码格式要清晰，有良好的缩进\n" +
                "10. 遵循Java命名规范\n\n" +
                "重要：直接返回代码，不要其他任何内容！";
    }

    public boolean testConnection() {
        try {
            logger.info("测试阿里云DashScope API连接...");

            Message systemMsg = Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content("你是一个JavaFX代码生成助手。请只返回Java代码。")
                    .build();

            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content("生成一个简单的JavaFX HelloWorld程序，窗口标题为'测试窗口'，内容显示'Hello World!'")
                    .build();

            GenerationParam param = GenerationParam.builder()
                    .apiKey(ALIYUN_API_KEY)
                    .model(MODEL_NAME)
                    .messages(Arrays.asList(systemMsg, userMsg))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .temperature(0.7F)
                    .maxTokens(500)
                    .build();

            GenerationResult result = generation.call(param);

            boolean success = result != null &&
                    result.getOutput() != null &&
                    result.getOutput().getChoices() != null &&
                    !result.getOutput().getChoices().isEmpty() &&
                    result.getOutput().getChoices().get(0).getMessage().getContent() != null;

            if (success) {
                logger.info("✅ API连接测试成功");
                System.out.println("✅ 阿里云DashScope API连接测试成功！");
                return true;
            } else {
                logger.warn("API连接测试返回异常响应");
                System.out.println("⚠️ API连接测试返回异常响应");
                return false;
            }
        } catch (Exception e) {
            logger.error("API连接测试失败: {}", e.getMessage());
            System.err.println("❌ 阿里云DashScope API连接测试失败: " + e.getMessage());
            return false;
        }
    }

    public List<String> generateMultiple(String prompt, int count) throws IOException {
        List<String> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            try {
                String code = generateCode(prompt + "\n\n这是第 " + (i + 1) + " 个版本。");
                results.add(code);
                logger.info("生成第 {} 个版本，长度: {}", i + 1, code.length());
            } catch (IOException e) {
                logger.error("生成第 {} 个版本失败: {}", i + 1, e.getMessage());
                results.add("生成失败: " + e.getMessage());
            }
            if (i < count - 1) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return results;
    }

    public String getMaskedApiKey() {
        if (ALIYUN_API_KEY == null || ALIYUN_API_KEY.length() <= 10) {
            return ALIYUN_API_KEY;
        }
        return ALIYUN_API_KEY.substring(0, 6) + "..." + ALIYUN_API_KEY.substring(ALIYUN_API_KEY.length() - 4);
    }

    public List<String> getAvailableModels() {
        return Arrays.asList(
                "qwen-turbo",
                "qwen-plus",
                "qwen-max",
                "qwen-long",
                "qwen-max-1201",
                "qwen-plus-1201",
                "qwen-long-latest",
                "qwen3-32b"
        );
    }

    private void validateApiKey() throws IOException {
        if (ALIYUN_API_KEY.equals("YOUR_ALIYUN_API_KEY_HERE") || ALIYUN_API_KEY.trim().isEmpty()) {
            String msg = "阿里云API密钥未配置！请修改AliyunAIClient.java中的ALIYUN_API_KEY常量";
            logger.error(msg);
            throw new IOException(msg);
        }
    }
}