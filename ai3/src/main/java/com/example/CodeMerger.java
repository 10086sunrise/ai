package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeMerger {

    private AliyunAIClient aiClient;

    public CodeMerger() {
        try {
            this.aiClient = new AliyunAIClient();
        } catch (Exception e) {
            System.err.println("AI客户端初始化失败: " + e.getMessage());
        }
    }

    /**
     * 代码合并方法：将生成的UI代码合并到指定的目标文件
     * @param targetFilePath 目标文件路径（要合并到的Java文件）
     * @param generatedCode AI生成的完整JavaFX代码
     * @param mergeStrategy 合并策略：REPLACE_CLASS, INSERT_METHOD, ADD_COMPONENT, SMART_MERGE, AI_ASSISTED
     * @return 合并结果信息
     */
    public MergeResult mergeToFile(String targetFilePath, String generatedCode, MergeStrategy mergeStrategy) {
        try {
            // 检查目标文件是否存在
            Path targetPath = Paths.get(targetFilePath);
            if (!Files.exists(targetPath)) {
                return new MergeResult(false, "目标文件不存在: " + targetFilePath);
            }

            // 解析生成的代码
            CodeAnalysis generatedAnalysis = analyzeCode(generatedCode);

            // 读取目标文件内容
            String targetContent = Files.readString(targetPath);
            CodeAnalysis targetAnalysis = analyzeCode(targetContent);

            // 根据策略执行合并
            String mergedContent;

            switch (mergeStrategy) {
                case REPLACE_CLASS:
                    mergedContent = replaceClass(targetContent, generatedCode, generatedAnalysis, targetAnalysis);
                    break;
                case INSERT_METHOD:
                    mergedContent = insertMethods(targetContent, generatedAnalysis, targetAnalysis);
                    break;
                case ADD_COMPONENT:
                    mergedContent = addComponents(targetContent, generatedAnalysis, targetAnalysis);
                    break;
                case AI_ASSISTED:
                    mergedContent = aiAssistedMerge(targetContent, generatedCode, generatedAnalysis, targetAnalysis);
                    break;
                case SMART_MERGE:
                default:
                    mergedContent = smartMerge(targetContent, generatedAnalysis, targetAnalysis);
                    break;
            }

            // 备份原文件
            backupOriginalFile(targetFilePath);

            // 写入合并后的内容
            Files.writeString(targetPath, mergedContent);

            return new MergeResult(true,
                    String.format("合并成功！\n文件：%s\n策略：%s",
                            targetPath.getFileName(),
                            mergeStrategy.toString()));

        } catch (IOException e) {
            return new MergeResult(false, "文件操作失败：" + e.getMessage());
        } catch (Exception e) {
            return new MergeResult(false, "合并过程出错：" + e.getMessage());
        }
    }

    /**
     * 合并到项目目录中的合适文件（自动查找）
     */
    public MergeResult mergeToProject(String projectPath, String generatedCode, MergeStrategy mergeStrategy) {
        try {
            // 查找项目中的JavaFX文件
            String targetFile = findJavaFXFileInProject(projectPath);
            if (targetFile == null) {
                return new MergeResult(false, "在项目目录中未找到合适的JavaFX文件");
            }

            return mergeToFile(targetFile, generatedCode, mergeStrategy);

        } catch (IOException e) {
            return new MergeResult(false, "查找项目文件失败：" + e.getMessage());
        }
    }

    /**
     * AI辅助智能合并
     */
    public String aiAssistedMerge(String targetContent, String generatedCode,
                                  CodeAnalysis generated, CodeAnalysis target) {
        try {
            if (aiClient == null) {
                throw new Exception("AI客户端未初始化");
            }

            // 构建AI提示词
            String prompt = buildAIMergePrompt(targetContent, generatedCode, target, generated);

            // 调用AI进行分析和合并
            String aiResponse = aiClient.generateCode(prompt);

            // 清理AI返回的代码
            return cleanAIGeneratedCode(aiResponse, targetContent, generatedCode);

        } catch (Exception e) {
            System.err.println("AI辅助合并失败，降级为智能合并: " + e.getMessage());
            // 如果AI合并失败，降级为智能合并
            return smartMerge(targetContent, generated, target);
        }
    }

    /**
     * 构建AI合并提示词
     */
    private String buildAIMergePrompt(String targetContent, String generatedCode,
                                      CodeAnalysis target, CodeAnalysis generated) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专业的Java代码合并专家。请帮我合并两个JavaFX代码文件。\n\n");

        prompt.append("## 任务要求\n");
        prompt.append("1. 将第二个代码文件（AI生成的UI代码）合并到第一个代码文件（现有项目代码）中\n");
        prompt.append("2. 保持现有代码的结构和逻辑完整\n");
        prompt.append("3. 智能识别重复的代码和方法\n");
        prompt.append("4. 确保合并后的代码可以编译和运行\n\n");

        prompt.append("## 代码分析\n");
        prompt.append("第一个文件（目标文件）：\n");
        prompt.append("- 包名: ").append(target.packageName != null ? target.packageName : "无").append("\n");
        prompt.append("- 类名: ").append(target.className != null ? target.className : "无").append("\n");
        prompt.append("- 方法数: ").append(target.methods.size()).append("\n");
        prompt.append("- 是否有main方法: ").append(hasMethod(target, "main") ? "是" : "否").append("\n");
        prompt.append("- 是否有start方法: ").append(hasMethod(target, "start") ? "是" : "否").append("\n\n");

        prompt.append("第二个文件（生成的UI代码）：\n");
        prompt.append("- 包名: ").append(generated.packageName != null ? generated.packageName : "无").append("\n");
        prompt.append("- 类名: ").append(generated.className != null ? generated.className : "无").append("\n");
        prompt.append("- 方法数: ").append(generated.methods.size()).append("\n");
        prompt.append("- 是否有main方法: ").append(hasMethod(generated, "main") ? "是" : "否").append("\n");
        prompt.append("- 是否有start方法: ").append(hasMethod(generated, "start") ? "是" : "否").append("\n\n");

        prompt.append("## 具体合并要求\n");
        prompt.append("1. 合并import语句，去除重复\n");
        prompt.append("2. 如果两个文件都有start方法，将生成的UI代码合并到现有的start方法中\n");
        prompt.append("3. 如果目标文件没有start方法但生成的代码有，添加start方法\n");
        prompt.append("4. 保留目标文件的其他方法（如initialize、event handlers等）\n");
        prompt.append("5. 如果两个文件都有main方法，保留目标文件的main方法\n");
        prompt.append("6. 保持代码格式规范和良好注释\n\n");

        prompt.append("## 第一个文件的完整代码\n```java\n").append(targetContent).append("\n```\n\n");
        prompt.append("## 第二个文件的完整代码\n```java\n").append(generatedCode).append("\n```\n\n");

        prompt.append("## 输出要求\n");
        prompt.append("1. 只返回合并后的Java代码，不要任何解释\n");
        prompt.append("2. 不要使用markdown代码块标记（不要```java或```）\n");
        prompt.append("3. 代码必须是完整、可编译、可运行的\n");
        prompt.append("4. 使用目标文件的包名和类名\n");

        return prompt.toString();
    }

    /**
     * 清理AI生成的代码
     */
    private String cleanAIGeneratedCode(String aiResponse, String targetContent, String generatedCode) {
        // 移除markdown代码块标记
        String code = aiResponse.replaceAll("(?i)```java\\s*", "")
                .replaceAll("(?i)```\\s*", "")
                .replaceAll("(?i)```[a-z]*", "")
                .trim();

        // 检查代码是否包含关键部分
        if (!isValidMergedCode(code, targetContent, generatedCode)) {
            // 如果AI生成的代码无效，降级为智能合并
            CodeAnalysis targetAnalysis = analyzeCode(targetContent);
            CodeAnalysis generatedAnalysis = analyzeCode(generatedCode);
            return smartMerge(targetContent, generatedAnalysis, targetAnalysis);
        }

        return code;
    }

    /**
     * 验证合并后的代码是否有效
     */
    private boolean isValidMergedCode(String mergedCode, String targetContent, String generatedCode) {
        // 检查基本有效性
        if (mergedCode == null || mergedCode.trim().isEmpty()) {
            return false;
        }

        // 应该包含目标文件的一些关键元素
        if (targetContent.contains("class ") && !mergedCode.contains("class ")) {
            return false;
        }

        // 应该包含生成的UI代码的一些关键元素
        if (generatedCode.contains("new ") && !mergedCode.contains("new ")) {
            // 但这不是绝对必要的，可能UI代码被整合了
        }

        return true;
    }

    /**
     * 智能合并（自动选择最佳策略）
     */
    public String smartMerge(String targetContent, CodeAnalysis generated, CodeAnalysis target) {
        // 如果AI客户端可用，优先使用AI辅助合并
        try {
            String generatedCode = generated.fullCode;
            if (aiClient != null && shouldUseAIForMerge(targetContent, generatedCode)) {
                return aiAssistedMerge(targetContent, generatedCode, generated, target);
            }
        } catch (Exception e) {
            System.err.println("AI合并失败，使用传统方法: " + e.getMessage());
        }

        // 传统智能合并逻辑
        // 如果目标文件没有UI类，直接替换
        if (!isJavaFXClass(target)) {
            return replaceClass(targetContent, generated.fullCode, generated, target);
        }

        // 如果目标文件已经有UI类，尝试插入方法
        if (canInsertMethods(target, generated)) {
            return insertMethods(targetContent, generated, target);
        }

        // 否则添加组件
        return addComponents(targetContent, generated, target);
    }

    /**
     * 判断是否应该使用AI进行合并
     */
    private boolean shouldUseAIForMerge(String targetContent, String generatedCode) {
        // 简单判断：如果代码较复杂或两个文件都有start方法，使用AI
        CodeAnalysis targetAnalysis = analyzeCode(targetContent);
        CodeAnalysis generatedAnalysis = analyzeCode(generatedCode);

        boolean bothHaveStartMethod = hasMethod(targetAnalysis, "start") && hasMethod(generatedAnalysis, "start");
        boolean bothHaveMainMethod = hasMethod(targetAnalysis, "main") && hasMethod(generatedAnalysis, "main");
        boolean isComplexCode = targetAnalysis.methods.size() > 3 || generatedAnalysis.methods.size() > 3;

        return bothHaveStartMethod || bothHaveMainMethod || isComplexCode;
    }

    /**
     * 替换整个类
     */
    public String replaceClass(String targetContent, String generatedCode,
                               CodeAnalysis generated, CodeAnalysis target) {
        // 如果目标文件有类名，用目标类名替换生成的类名
        if (target.className != null && !target.className.isEmpty() &&
                generated.className != null && !generated.className.isEmpty() &&
                !target.className.equals(generated.className)) {

            // 替换类名
            generatedCode = generatedCode.replaceAll(
                    "class\\s+" + Pattern.quote(generated.className),
                    "class " + target.className
            );

            // 替换构造函数名（如果有）
            generatedCode = generatedCode.replaceAll(
                    "public\\s+" + Pattern.quote(generated.className) + "\\s*\\(",
                    "public " + target.className + "("
            );
        }

        // 保留目标文件的package和import，结合生成的类内容
        StringBuilder result = new StringBuilder();

        // 添加包声明
        if (target.packageName != null && !target.packageName.isEmpty()) {
            result.append("package ").append(target.packageName).append(";\n\n");
        }

        // 合并import语句
        Set<String> allImports = new TreeSet<>();
        allImports.addAll(target.imports);
        allImports.addAll(generated.imports);

        for (String imp : allImports) {
            result.append("import ").append(imp).append(";\n");
        }

        if (!allImports.isEmpty()) {
            result.append("\n");
        }

        // 提取生成的类内容（去掉package和import部分）
        String generatedClassContent = extractClassContent(generatedCode);
        result.append(generatedClassContent);

        return result.toString();
    }

    /**
     * 插入方法到现有类
     */
    public String insertMethods(String targetContent, CodeAnalysis generated, CodeAnalysis target) {
        StringBuilder merged = new StringBuilder(targetContent);

        // 找到类定义结束位置
        int classEnd = findClassEnd(targetContent, target.className);
        if (classEnd == -1) {
            return targetContent; // 找不到类结束位置
        }

        // 插入生成的方法
        for (MethodInfo method : generated.methods) {
            if (!hasMethod(target, method.name)) {
                // 在类结束前插入方法
                String methodCode = "\n\n    " + method.content;
                merged.insert(classEnd, methodCode);

                // 更新类结束位置
                classEnd += methodCode.length();
            }
        }

        // 添加缺失的import语句
        String importsToAdd = findMissingImports(target, generated);
        if (!importsToAdd.isEmpty()) {
            int lastImportPos = findLastImportPosition(targetContent);
            if (lastImportPos > 0) {
                merged.insert(lastImportPos + 1, "\n" + importsToAdd);
            } else {
                // 如果没有import语句，在package后添加
                int packageEnd = targetContent.indexOf(";");
                if (packageEnd > 0) {
                    merged.insert(packageEnd + 1, "\n" + importsToAdd);
                }
            }
        }

        return merged.toString();
    }

    /**
     * 添加UI组件到现有类
     */
    public String addComponents(String targetContent, CodeAnalysis generated, CodeAnalysis target) {
        // 查找start方法或initialize方法
        int methodStart = findMethodStart(targetContent, "start");
        if (methodStart == -1) {
            methodStart = findMethodStart(targetContent, "initialize");
        }
        if (methodStart == -1) {
            return targetContent; // 找不到合适的方法
        }

        // 找到方法体开始位置
        int bodyStart = targetContent.indexOf('{', methodStart) + 1;
        int bodyEnd = findMatchingBrace(targetContent, methodStart);

        if (bodyEnd <= bodyStart) return targetContent;

        // 提取生成的UI创建代码
        String uiCreationCode = extractUICreationCode(generated);
        if (uiCreationCode.isEmpty()) return targetContent;

        // 插入UI创建代码
        StringBuilder merged = new StringBuilder(targetContent);
        String codeToInsert = "\n        // === 生成的UI组件 ===\n" +
                uiCreationCode + "\n        // ======================\n";
        merged.insert(bodyStart, codeToInsert);

        return merged.toString();
    }

    /**
     * 分析代码结构
     */
    public CodeAnalysis analyzeCode(String code) {
        CodeAnalysis analysis = new CodeAnalysis();
        analysis.fullCode = code;

        // 提取包名
        Pattern packagePattern = Pattern.compile("package\\s+([\\w.]+)\\s*;");
        Matcher packageMatcher = packagePattern.matcher(code);
        if (packageMatcher.find()) {
            analysis.packageName = packageMatcher.group(1).trim();
        }

        // 提取import语句
        Pattern importPattern = Pattern.compile("import\\s+([\\w.*]+)\\s*;");
        Matcher importMatcher = importPattern.matcher(code);
        while (importMatcher.find()) {
            analysis.imports.add(importMatcher.group(1).trim());
        }

        // 提取类名
        Pattern classPattern = Pattern.compile("class\\s+(\\w+)\\s+");
        Matcher classMatcher = classPattern.matcher(code);
        if (classMatcher.find()) {
            analysis.className = classMatcher.group(1);
        }

        // 提取方法
        extractMethods(code, analysis);

        return analysis;
    }

    /**
     * 查找项目中的JavaFX文件
     */
    String findJavaFXFileInProject(String projectPath) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Path projectDir = Paths.get(projectPath);

        // 检查目录是否存在
        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            throw new IOException("项目目录不存在或不是目录: " + projectPath);
        }

        // 收集所有Java文件
        Files.walk(projectDir)
                .filter(path -> {
                    String filename = path.getFileName().toString();
                    return filename.endsWith(".java") &&
                            !filename.contains("Test") &&  // 排除测试文件
                            !filename.contains("test") &&
                            Files.isRegularFile(path);
                })
                .forEach(javaFiles::add);

        if (javaFiles.isEmpty()) {
            return null;
        }

        // 优先选择有Application类的文件
        for (Path file : javaFiles) {
            try {
                String content = Files.readString(file);
                if (isJavaFXClass(content)) {
                    return file.toAbsolutePath().toString();
                }
            } catch (IOException e) {
                System.err.println("读取文件失败: " + file + " - " + e.getMessage());
                continue;
            }
        }

        // 如果没有找到Application类，返回第一个Java文件
        return javaFiles.get(0).toAbsolutePath().toString();
    }

    /**
     * 备份原文件
     */
    private void backupOriginalFile(String filePath) throws IOException {
        Path original = Paths.get(filePath);
        Path backupDir = original.getParent().resolve("backups");

        // 创建备份目录
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String backupName = original.getFileName() + ".backup_" + timestamp;
        Path backup = backupDir.resolve(backupName);

        Files.copy(original, backup);
        System.out.println("已创建备份: " + backup);
    }

    // ============== 辅助方法 ==============

    private void extractMethods(String code, CodeAnalysis analysis) {
        // 简化方法提取：查找public/protected/private方法
        Pattern methodPattern = Pattern.compile(
                "(public|private|protected|\\s)?\\s+" + // 访问修饰符
                        "(static\\s+)?" +                       // static
                        "(\\w+\\s+)?" +                         // 返回类型（可能有多部分，如List<String>）
                        "(\\w+)\\s*\\(" +                       // 方法名
                        "([^)]*)\\)\\s*\\{",                    // 参数
                Pattern.DOTALL
        );

        Matcher methodMatcher = methodPattern.matcher(code);
        while (methodMatcher.find()) {
            MethodInfo method = new MethodInfo();
            method.name = methodMatcher.group(4);
            method.parameters = methodMatcher.group(5);

            // 提取方法体
            int start = methodMatcher.end() - 1; // 从 { 开始
            int end = findMatchingBrace(code, start);

            if (end > start) {
                // 包含方法签名和整个方法体
                int methodStart = methodMatcher.start();
                method.content = code.substring(methodStart, end + 1);
                analysis.methods.add(method);
            }
        }
    }

    private int findMatchingBrace(String text, int start) {
        int count = 1;
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                count++;
            } else if (c == '}') {
                count--;
                if (count == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findClassEnd(String text, String className) {
        if (className == null || className.isEmpty()) {
            return -1;
        }

        Pattern classPattern = Pattern.compile("class\\s+" + Pattern.quote(className) + "\\s+.*\\{");
        Matcher matcher = classPattern.matcher(text);

        if (matcher.find()) {
            return findMatchingBrace(text, matcher.end() - 1);
        }

        return -1;
    }

    private int findMethodStart(String text, String methodName) {
        Pattern methodPattern = Pattern.compile(
                "\\b" + Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*\\{"
        );
        Matcher matcher = methodPattern.matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    private int findLastImportPosition(String text) {
        Pattern importPattern = Pattern.compile("import\\s+[\\w.*]+\\s*;");
        Matcher matcher = importPattern.matcher(text);
        int lastPos = -1;

        while (matcher.find()) {
            lastPos = matcher.end();
        }

        return lastPos;
    }

    private String extractClassContent(String code) {
        // 找到类定义开始
        Pattern classPattern = Pattern.compile("class\\s+\\w+");
        Matcher matcher = classPattern.matcher(code);

        if (matcher.find()) {
            return code.substring(matcher.start()).trim();
        }

        return code;
    }

    private String extractUICreationCode(CodeAnalysis generated) {
        // 查找start方法中的UI创建代码
        for (MethodInfo method : generated.methods) {
            if ("start".equals(method.name)) {
                // 提取方法体中的核心UI创建部分
                return extractUICreationFromMethod(method.content);
            }
        }
        return "";
    }

    private String extractUICreationFromMethod(String methodContent) {
        // 简单的提取：从第一个组件创建到设置场景前
        Pattern uiPattern = Pattern.compile(
                "(new\\s+(Label|Button|TextField|TextArea|VBox|HBox|GridPane|BorderPane|TableView))"
        );

        String[] lines = methodContent.split("\n");
        StringBuilder uiCode = new StringBuilder();
        boolean inUICreation = false;

        for (String line : lines) {
            if (uiPattern.matcher(line).find()) {
                inUICreation = true;
            }

            if (inUICreation) {
                if (line.contains("Scene") || line.contains("primaryStage.setScene")) {
                    break;
                }

                // 移除方法签名和开头的 {
                if (!line.contains("public void start") && !line.trim().equals("{")) {
                    uiCode.append("        ").append(line).append("\n");
                }
            }
        }

        return uiCode.toString();
    }

    private boolean isJavaFXClass(CodeAnalysis analysis) {
        return analysis.fullCode.contains("Application") &&
                analysis.fullCode.contains("extends") &&
                analysis.fullCode.contains("javafx");
    }

    private boolean isJavaFXClass(String code) {
        return code.contains("Application") &&
                code.contains("extends") &&
                (code.contains("javafx") || code.contains("JavaFX"));
    }

    private boolean canInsertMethods(CodeAnalysis target, CodeAnalysis generated) {
        return !generated.methods.isEmpty() &&
                target.className != null &&
                !target.className.isEmpty();
    }

    private boolean hasMethod(CodeAnalysis analysis, String methodName) {
        return analysis.methods.stream()
                .anyMatch(m -> methodName != null && methodName.equals(m.name));
    }

    private String findMissingImports(CodeAnalysis target, CodeAnalysis generated) {
        StringBuilder missing = new StringBuilder();

        for (String imp : generated.imports) {
            if (!target.imports.contains(imp) &&
                    !imp.startsWith("java.lang.")) {
                missing.append("import ").append(imp).append(";\n");
            }
        }

        return missing.toString().trim();
    }

    // ============== 内部类 ==============

    /**
     * 代码分析结果
     */
    public static class CodeAnalysis {
        public String fullCode;
        public String packageName;
        public String className;
        public Set<String> imports = new HashSet<>();
        public List<MethodInfo> methods = new ArrayList<>();

        @Override
        public String toString() {
            return String.format("类: %s, 包: %s, 方法数: %d, import数: %d",
                    className, packageName, methods.size(), imports.size());
        }
    }

    /**
     * 方法信息
     */
    public static class MethodInfo {
        public String name;
        public String parameters;
        public String content;

        @Override
        public String toString() {
            return String.format("方法: %s(%s)", name, parameters);
        }
    }

    /**
     * 合并结果
     */
    public static class MergeResult {
        public boolean success;
        public String message;

        public MergeResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /**
     * 合并策略枚举
     */
    public enum MergeStrategy {
        REPLACE_CLASS("替换整个类"),
        INSERT_METHOD("插入方法"),
        ADD_COMPONENT("添加组件"),
        SMART_MERGE("智能合并（传统）"),
        AI_ASSISTED("智能合并（AI辅助）");

        private final String description;

        MergeStrategy(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}