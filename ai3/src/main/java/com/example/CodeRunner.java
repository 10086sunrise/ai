package com.example;

import javafx.application.Platform;

import javax.tools.*;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeRunner {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/javafx_code_runner/";

    // 固定的JavaFX路径 - 指向本地SDK的lib目录
    private String javafxModulePath = "C:\\javafx-sdk-21.0.3\\lib";
    private String additionalModules = "javafx.controls,javafx.fxml,javafx.graphics,javafx.base,javafx.media,javafx.swing,javafx.web";

    // 用于界面回调的函数式接口
    @FunctionalInterface
    public interface StageCallback {
        void onStageCreated(javafx.stage.Stage stage);
    }

    private StageCallback stageCallback;

    // 编译结果封装类
    private static class CompilationResult {
        final boolean success;
        final String output;
        final String errorOutput;

        CompilationResult(boolean success, String output, String errorOutput) {
            this.success = success;
            this.output = output;
            this.errorOutput = errorOutput;
        }
    }

    // 运行结果封装类
    private static class ExecutionResult {
        final boolean success;
        final String output;
        final int exitCode;
        final Throwable exception;

        ExecutionResult(boolean success, String output, int exitCode, Throwable exception) {
            this.success = success;
            this.output = output;
            this.exitCode = exitCode;
            this.exception = exception;
        }
    }

    /**
     * 设置舞台回调
     */
    public void setStageCallback(StageCallback callback) {
        this.stageCallback = callback;
    }

    /**
     * 设置JavaFX配置 - 强制使用本地路径
     */
    public void setJavaFXConfig(String config) {
        if (config == null || config.isEmpty()) {
            return;
        }

        String[] pairs = config.split(";");
        for (String pair : pairs) {
            if (pair.contains("=")) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();

                    if ("MODULE_PATH".equals(key)) {
                        javafxModulePath = value;
                        System.out.println("JavaFX模块路径已强制更新为: " + javafxModulePath);
                    }
                }
            }
        }
    }

    /**
     * 运行JavaFX代码 - 返回运行是否成功
     */
    public void runJavaFXCode(String code, Runnable onSuccess, Consumer<String> onError) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.submit(() -> {
            try {
                // 先检查并设置正确的JavaFX路径
                ensureJavaFXPath();
                runInIsolatedProcess(code, onSuccess, onError);
            } catch (Exception e) {
                Platform.runLater(() -> onError.accept("运行失败: " + e.getMessage()));
                e.printStackTrace();
            }
        });

        executor.shutdown();
    }

    /**
     * 确保使用正确的JavaFX路径
     */
    private void ensureJavaFXPath() {
        System.out.println("检查JavaFX路径配置...");
        System.out.println("配置的JavaFX路径: " + javafxModulePath);

        // 检查配置的路径是否存在
        File configuredPath = new File(javafxModulePath);
        if (configuredPath.exists()) {
            System.out.println("✓ 配置的路径存在: " + javafxModulePath);
            return;
        }

        // 如果配置的路径不存在，尝试其他可能的路径
        System.out.println("⚠ 配置的路径不存在，尝试其他路径...");

        // 1. 检查是否有Maven路径
        String mavenPath = System.getProperty("user.home") + "\\.m2\\repository\\org\\openjfx";
        File mavenDir = new File(mavenPath);
        if (mavenDir.exists()) {
            System.out.println("发现Maven仓库路径: " + mavenPath);
            // 找到最新版本的javafx-controls
            findLatestJavaFXInMaven(mavenDir);
        }

        // 2. 检查其他可能的位置
        String[] possiblePaths = {
                "C:\\Program Files\\Java\\javafx-sdk-21.0.3\\lib",
                "C:\\javafx-sdk-21.0.3\\lib",
                "D:\\javafx-sdk-21.0.3\\lib",
                System.getProperty("user.home") + "\\Downloads\\javafx-sdk-21.0.3\\lib"
        };

        for (String path : possiblePaths) {
            File testPath = new File(path);
            if (testPath.exists()) {
                javafxModulePath = path;
                System.out.println("✓ 找到JavaFX路径: " + javafxModulePath);
                return;
            }
        }

        System.out.println("✗ 未找到有效的JavaFX路径，请确保已安装JavaFX SDK");
    }

    /**
     * 在Maven仓库中查找最新的JavaFX
     */
    private void findLatestJavaFXInMaven(File mavenDir) {
        try {
            // 查找javafx-controls模块
            File[] modules = mavenDir.listFiles();
            if (modules != null) {
                for (File module : modules) {
                    if (module.isDirectory() && module.getName().startsWith("javafx-")) {
                        File[] versions = module.listFiles();
                        if (versions != null && versions.length > 0) {
                            // 按版本号排序，取最新的
                            Arrays.sort(versions, (a, b) -> b.getName().compareTo(a.getName()));
                            File latestVersion = versions[0];

                            // 构建lib路径
                            File libDir = new File(latestVersion, "lib");
                            if (libDir.exists()) {
                                javafxModulePath = libDir.getAbsolutePath();
                                System.out.println("使用Maven中的JavaFX: " + javafxModulePath);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("查找Maven JavaFX时出错: " + e.getMessage());
        }
    }

    /**
     * 在独立的进程中运行代码
     */
    private void runInIsolatedProcess(String code, Runnable onSuccess, Consumer<String> onError) {
        Path tempDir = null;
        try {
            tempDir = createTempDirectory();
            System.out.println("临时目录: " + tempDir);
            System.out.println("最终使用的JavaFX路径: " + javafxModulePath);

            String className = extractClassName(code);
            if (className == null) {
                Platform.runLater(() -> onError.accept("无法从代码中提取类名"));
                return;
            }
            System.out.println("提取的类名: " + className);

            Path javaFile = saveJavaFile(tempDir, className, code);
            System.out.println("保存Java文件: " + javaFile);

            CompilationResult compilationResult = compileJavaFile(javaFile, className, tempDir);
            if (!compilationResult.success) {
                Platform.runLater(() -> onError.accept("编译失败:\n" + compilationResult.errorOutput));
                return;
            }
            System.out.println("编译成功");

            ExecutionResult executionResult = executeJavaClass(tempDir, className);

            if (executionResult.success) {
                Platform.runLater(onSuccess);
            } else {
                Platform.runLater(() -> {
                    String errorMsg = "运行失败 (退出码: " + executionResult.exitCode + ")";
                    if (executionResult.output != null && !executionResult.output.isEmpty()) {
                        errorMsg += ":\n" + executionResult.output;
                    }
                    if (executionResult.exception != null) {
                        errorMsg += "\n异常: " + executionResult.exception.getMessage();
                    }
                    onError.accept(errorMsg);
                });
            }

        } catch (Exception e) {
            Platform.runLater(() -> onError.accept("执行过程出错: " + e.getMessage()));
            e.printStackTrace();
        } finally {
            if (tempDir != null) {
                cleanupTempDirectory(tempDir);
            }
        }
    }

    private Path createTempDirectory() throws IOException {
        Path tempDir = Paths.get(TEMP_DIR + System.currentTimeMillis());
        Files.createDirectories(tempDir);
        return tempDir;
    }

    private String extractClassName(String code) {
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(code);

        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = Pattern.compile("class\\s+(\\w+)");
        matcher = pattern.matcher(code);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private Path saveJavaFile(Path tempDir, String className, String code) throws IOException {
        Path javaFile = tempDir.resolve(className + ".java");
        Files.writeString(javaFile, code);
        return javaFile;
    }

    /**
     * 编译Java文件 - 强制使用我们配置的路径
     */
    private CompilationResult compileJavaFile(Path javaFile, String className, Path outputDir) {
        StringWriter outputWriter = new StringWriter();
        StringWriter errorWriter = new StringWriter();

        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return new CompilationResult(false, "", "找不到Java编译器，请确保使用JDK而不是JRE");
            }

            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromFiles(Arrays.asList(javaFile.toFile()));

            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(outputDir.toString());

            String javaVersion = System.getProperty("java.version");
            int targetVersion = getJavaMajorVersion(javaVersion);
            targetVersion = Math.min(targetVersion, 21);
            options.add("-source");
            options.add(String.valueOf(targetVersion));
            options.add("-target");
            options.add(String.valueOf(targetVersion));

            // 构建类路径 - 只使用我们配置的路径
            StringBuilder classpath = new StringBuilder();
            classpath.append(outputDir.toString());
            classpath.append(File.pathSeparator);
            classpath.append(System.getProperty("java.class.path"));

            // 添加我们配置的JavaFX路径
            File javafxDir = new File(javafxModulePath);
            if (javafxDir.exists()) {
                if (javafxDir.isDirectory()) {
                    // 如果是目录，添加所有jar文件
                    classpath.append(File.pathSeparator);
                    classpath.append(javafxModulePath).append(File.separator).append("*");
                } else if (javafxDir.getName().endsWith(".jar")) {
                    // 如果是单个jar文件
                    classpath.append(File.pathSeparator);
                    classpath.append(javafxModulePath);
                }
            } else {
                return new CompilationResult(false, "",
                        "JavaFX路径不存在: " + javafxModulePath +
                                "\n请检查路径或重新设置JavaFX配置");
            }

            options.add("-cp");
            options.add(classpath.toString());

            // 对于Java 9+，添加模块选项
            if (targetVersion >= 9) {
                // 构建模块路径
                String modulePath = javafxModulePath;
                if (new File(javafxModulePath).isDirectory()) {
                    // 如果是目录，直接使用
                    modulePath = javafxModulePath;
                } else if (javafxModulePath.endsWith(".jar")) {
                    // 如果是jar文件，使用其父目录
                    modulePath = new File(javafxModulePath).getParent();
                }

                options.add("--module-path");
                options.add(modulePath);
                options.add("--add-modules");
                options.add(additionalModules);

                System.out.println("编译时模块路径: " + modulePath);
            }

            options.add("-Xlint:unchecked");
            options.add("-parameters");
            options.add("-encoding");
            options.add("UTF-8");

            System.out.println("编译选项: " + options);
            System.out.println("最终类路径: " + classpath.toString());

            JavaCompiler.CompilationTask task = compiler.getTask(
                    errorWriter, fileManager, null, options, null, compilationUnits
            );

            boolean success = task.call();
            fileManager.close();

            String output = outputWriter.toString();
            String errorOutput = errorWriter.toString();

            if (!success) {
                System.err.println("编译错误: " + errorOutput);
            }

            return new CompilationResult(success, output, errorOutput);

        } catch (Exception e) {
            return new CompilationResult(false, "", "编译过程出错: " + e.getMessage() + "\n" + errorWriter.toString());
        }
    }

    private int getJavaMajorVersion(String version) {
        if (version == null) return 8;

        if (version.startsWith("1.")) {
            try {
                return Integer.parseInt(version.substring(2, version.indexOf('.', 2)));
            } catch (Exception e) {
                return 8;
            }
        } else {
            try {
                String[] parts = version.split("\\.");
                return Integer.parseInt(parts[0]);
            } catch (Exception e) {
                return 9;
            }
        }
    }

    /**
     * 执行Java类 - 强制使用我们配置的路径
     */
    private ExecutionResult executeJavaClass(Path classDir, String className) {
        Process process = null;
        try {
            List<String> command = new ArrayList<>();
            command.add("java");

            String javaVersion = System.getProperty("java.version");
            int javaMajorVersion = getJavaMajorVersion(javaVersion);

            System.out.println("Java版本: " + javaVersion);
            System.out.println("Java主版本: " + javaMajorVersion);

            // 构建类路径 - 只使用我们配置的路径
            StringBuilder classpath = new StringBuilder();
            classpath.append(classDir.toString());
            classpath.append(File.pathSeparator);
            classpath.append(System.getProperty("java.class.path"));

            // 添加我们配置的JavaFX路径
            File javafxDir = new File(javafxModulePath);
            if (javafxDir.exists()) {
                if (javafxDir.isDirectory()) {
                    classpath.append(File.pathSeparator);
                    classpath.append(javafxModulePath).append(File.separator).append("*");
                } else if (javafxDir.getName().endsWith(".jar")) {
                    classpath.append(File.pathSeparator);
                    classpath.append(javafxModulePath);
                }
            } else {
                return new ExecutionResult(false,
                        "错误: JavaFX路径不存在: " + javafxModulePath, -1, null);
            }

            command.add("-cp");
            command.add(classpath.toString());

            // 对于Java 9+，添加模块选项
            if (javaMajorVersion >= 9) {
                // 构建模块路径
                String modulePath = javafxModulePath;
                if (new File(javafxModulePath).isDirectory()) {
                    modulePath = javafxModulePath;
                } else if (javafxModulePath.endsWith(".jar")) {
                    modulePath = new File(javafxModulePath).getParent();
                }

                command.add("--module-path");
                command.add(modulePath);
                command.add("--add-modules");
                command.add(additionalModules);

                System.out.println("执行时模块路径: " + modulePath);
            }

            // 添加其他VM参数
            command.add("-Dprism.lcdtext=false");
            command.add("-Dprism.text=t2k");
            command.add("-Djavafx.verbose=false");
            command.add("-Dfile.encoding=UTF-8");

            // 添加必要的opens选项
            if (javaMajorVersion >= 9) {
                command.add("--add-opens");
                command.add("java.base/java.lang=ALL-UNNAMED");
                command.add("--add-opens");
                command.add("java.base/java.io=ALL-UNNAMED");
                command.add("--add-opens");
                command.add("java.base/java.util=ALL-UNNAMED");
                command.add("--add-opens");
                command.add("java.base/java.lang.reflect=ALL-UNNAMED");
                command.add("--add-opens");
                command.add("javafx.graphics/com.sun.javafx.application=ALL-UNNAMED");
                command.add("--add-opens");
                command.add("javafx.graphics/com.sun.glass.ui=ALL-UNNAMED");
            }

            // 添加类名
            command.add(className);

            System.out.println("执行命令: " + String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            processBuilder.directory(classDir.toFile());

            // 设置环境变量 - 清除可能的环境变量影响
            Map<String, String> env = processBuilder.environment();
            env.put("PATH", System.getenv("PATH"));
            env.put("JAVA_HOME", System.getProperty("java.home"));

            // 移除可能影响的环境变量
            env.remove("JAVAFX_MODULE_PATH");
            env.remove("JAVAFX_HOME");

            process = processBuilder.start();

            // 读取输出
            StringBuilder outputBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line).append("\n");
                    System.out.println("[Process Output] " + line);
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                if (process.waitFor(5, TimeUnit.SECONDS)) {
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                }
                return new ExecutionResult(false, "进程执行超时", -1, null);
            }

            int exitCode = process.exitValue();
            String output = outputBuilder.toString();

            return new ExecutionResult(exitCode == 0, output, exitCode, null);

        } catch (Exception e) {
            System.err.println("执行过程出错: " + e.getMessage());
            e.printStackTrace();
            return new ExecutionResult(false, "执行异常: " + e.getMessage(), -1, e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 直接运行JavaFX代码在当前JVM中 - 用于预览功能
     * 这个方法会尝试在当前JVM中加载并运行代码
     */
    public void runJavaFXInCurrentVM(String code, Consumer<String> onError) {
        try {
            // 提取类名
            String className = extractClassName(code);
            if (className == null) {
                Platform.runLater(() -> onError.accept("无法从代码中提取类名"));
                return;
            }

            // 创建临时目录
            Path tempDir = createTempDirectory();
            System.out.println("预览临时目录: " + tempDir);

            // 保存Java文件
            Path javaFile = saveJavaFile(tempDir, className, code);

            // 编译Java文件
            CompilationResult compilationResult = compileJavaFile(javaFile, className, tempDir);
            if (!compilationResult.success) {
                Platform.runLater(() -> onError.accept("编译失败:\n" + compilationResult.errorOutput));
                cleanupTempDirectory(tempDir);
                return;
            }

            // 创建自定义类加载器
            URLClassLoader classLoader = new URLClassLoader(
                    new java.net.URL[]{tempDir.toUri().toURL()},
                    Thread.currentThread().getContextClassLoader()
            );

            // 加载并运行类
            Platform.runLater(() -> {
                try {
                    Class<?> clazz = classLoader.loadClass(className);

                    // 检查是否是Application的子类
                    if (javafx.application.Application.class.isAssignableFrom(clazz)) {
                        // 创建新线程启动JavaFX应用
                        new Thread(() -> {
                            try {
                                // 在JavaFX应用线程中运行
                                Platform.runLater(() -> {
                                    try {
                                        // 创建实例并启动
                                        javafx.application.Application app =
                                                (javafx.application.Application) clazz.newInstance();

                                        // 创建自定义Stage来嵌入到我们的界面中
                                        javafx.stage.Stage embeddedStage = new javafx.stage.Stage();

                                        // 如果有回调，调用回调
                                        if (stageCallback != null) {
                                            stageCallback.onStageCreated(embeddedStage);
                                        }

                                        // 启动应用（但隐藏主Stage）
                                        app.start(embeddedStage);

                                    } catch (Exception e) {
                                        onError.accept("启动应用失败: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                });
                            } catch (Exception e) {
                                Platform.runLater(() -> onError.accept("运行失败: " + e.getMessage()));
                            }
                        }).start();
                    } else {
                        onError.accept("代码不是有效的JavaFX Application类");
                    }
                } catch (Exception e) {
                    onError.accept("加载类失败: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    // 清理临时目录
                    cleanupTempDirectory(tempDir);
                }
            });

        } catch (Exception e) {
            Platform.runLater(() -> onError.accept("预览失败: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void cleanupTempDirectory(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                System.out.println("清理临时目录: " + tempDir);
            }
        } catch (IOException e) {
            System.err.println("清理临时目录失败: " + e.getMessage());
        }
    }

    public String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "代码不能为空";
        }

        if (!code.contains("Application")) {
            return "代码必须包含JavaFX Application类";
        }

        if (!code.contains("start(") && !code.contains("public void start")) {
            return "代码必须包含start方法";
        }

        if (!code.contains("import javafx")) {
            return "警告: 代码可能需要导入JavaFX相关包";
        }

        String className = extractClassName(code);
        if (className == null) {
            return "无法提取有效的类名";
        }

        return null;
    }

    /**
     * 强制设置JavaFX路径
     */
    public void forceSetJavaFXPath(String path) {
        this.javafxModulePath = path;
        System.out.println("强制设置JavaFX路径为: " + path);
    }

    /**
     * 检查当前使用的JavaFX路径
     */
    public String getCurrentJavaFXPath() {
        return this.javafxModulePath;
    }

    /**
     * 检查JavaFX SDK路径是否有效
     */
    public boolean checkJavaFXPath() {
        File javafxDir = new File(javafxModulePath);

        System.out.println("检查JavaFX路径: " + javafxModulePath);

        if (!javafxDir.exists()) {
            System.out.println("✗ 路径不存在");
            return false;
        }

        if (javafxDir.isDirectory()) {
            System.out.println("✓ 路径是目录");
            File[] jarFiles = javafxDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles != null && jarFiles.length > 0) {
                System.out.println("✓ 找到 " + jarFiles.length + " 个jar文件");
                for (File jar : jarFiles) {
                    System.out.println("  - " + jar.getName());
                }
                return true;
            } else {
                System.out.println("✗ 目录中没有找到jar文件");
                return false;
            }
        } else if (javafxDir.getName().endsWith(".jar")) {
            System.out.println("✓ 路径是jar文件: " + javafxDir.getName());
            return true;
        } else {
            System.out.println("✗ 路径既不是目录也不是jar文件");
            return false;
        }
    }

    // 函数式接口定义
    @FunctionalInterface
    public interface Consumer<T> {
        void accept(T t);
    }
}