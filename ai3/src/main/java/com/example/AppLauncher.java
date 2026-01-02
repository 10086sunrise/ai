package com.example;

import java.io.IOException;

public class AppLauncher {

    /**
     * 在 Windows 系统中尝试打开指定的应用程序
     *
     * @param appName 应用名称（如 "网易云音乐"、"微信"、"notepad"、"chrome"）
     * @return 执行结果消息
     */
    public static String openApplication(String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return "❌ 应用名称不能为空";
        }

        // 确保是 Windows 系统
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            return "⚠️ 当前仅支持 Windows 系统";
        }

        try {
            appName = appName.trim();

            // 特殊处理：常见应用映射（可选，增强兼容性）
            String finalAppName = mapAppName(appName);

            // 使用 cmd /c start 命令启动应用
            // 注意：start 后的第一个引号是“窗口标题”，留空即可；第二个是程序名
            Process process = Runtime.getRuntime().exec(
                    new String[]{"cmd", "/c", "start", "\"\"", finalAppName}
            );

            // 可选：等待一小段时间看是否启动成功（非必须）
            try {
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return "✅ 正在为您打开「" + appName + "」...";
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 即使 waitFor 返回非 0，也可能已启动（start 是异步的）
            return "✅ 已发送打开「" + appName + "」的指令";

        } catch (IOException e) {
            return "❌ 无法打开「" + appName + "」，请确认是否已安装。\n错误: " + e.getMessage();
        }
    }

    /**
     * （可选）将用户输入映射为更准确的应用标识
     * 例如：“网易云” → “网易云音乐”
     */
    private static String mapAppName(String input) {
        switch (input.toLowerCase()) {
            case "网易云":
            case "网易云音乐":
            case "netease music":
                return "网易云音乐";
            case "微信":
            case "wechat":
                return "WeChat";
            case "qq":
                return "QQ";
            case "浏览器":
            case "chrome":
                return "chrome";
            case "edge":
                return "msedge";
            case "记事本":
                return "notepad";
            case "计算器":
                return "calc";
            default:
                return input; // 直接使用原名称
        }
    }

    // ===== 测试用例 =====
    public static void main(String[] args) {
        // 测试打开不同应用
        System.out.println(openApplication("网易云音乐"));
        // System.out.println(openApplication("微信"));
        // System.out.println(openApplication("notepad"));
        // System.out.println(openApplication("chrome"));
    }
}