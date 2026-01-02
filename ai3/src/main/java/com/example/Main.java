package com.example;

import javafx.application.Application;

public class Main {
    public static void main(String[] args) {
        // 设置JavaFX相关属性
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");
        System.setProperty("javafx.verbose", "true");

        // 启动应用程序
        App.main(args);
    }

    /**
     * 静态方法用于在其他地方启动App
     */
    public static void launchApp() {
        new Thread(() -> {
            // 设置JavaFX相关属性
            System.setProperty("prism.lcdtext", "false");
            System.setProperty("prism.text", "t2k");

            // 在新的线程中启动JavaFX应用
            App.main(new String[]{});
        }).start();
    }
}