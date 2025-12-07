package org.example1.rCONPro;

/**
 * 主启动类 - 检测启动模式
 * 如果作为独立应用启动，则运行远程控制端
 */
public class Main {
    public static void main(String[] args) {
        // 检测是否在Bukkit/Paper环境中
        try {
            Class.forName("org.bukkit.plugin.java.JavaPlugin");
            // 如果找到了Bukkit类，说明是作为插件启动，不应该运行main方法
            System.out.println("检测到Bukkit环境，请将此JAR作为插件使用，而不是直接运行。");
            return;
        } catch (ClassNotFoundException e) {
            // 没有找到Bukkit类，说明是独立应用模式
            RemoteConsoleClient client = new RemoteConsoleClient();
            client.start();
        }
    }
}

