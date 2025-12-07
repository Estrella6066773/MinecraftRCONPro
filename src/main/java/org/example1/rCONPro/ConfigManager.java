package org.example1.rCONPro;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 配置文件管理器
 */
public class ConfigManager {
    
    // 插件模式配置文件
    public static final String PLUGIN_CONFIG_FILE = "plugins/RCONPro/config.properties";
    
    // 远程控制端配置文件
    public static final String CLIENT_CONFIG_FILE = "rconpro-client.properties";
    
    /**
     * 加载插件配置
     */
    public static PluginConfig loadPluginConfig() {
        Properties props = loadProperties(PLUGIN_CONFIG_FILE);
        if (props == null) {
            // 创建默认配置
            PluginConfig config = new PluginConfig();
            savePluginConfig(config);
            return config;
        }
        
        PluginConfig config = new PluginConfig();
        config.serverHost = props.getProperty("server.host", "localhost");
        config.serverPort = Integer.parseInt(props.getProperty("server.port", "25575"));
        config.serverPassword = props.getProperty("server.password", "changeme");
        config.remoteHost = props.getProperty("remote.host", "localhost");
        config.remotePort = Integer.parseInt(props.getProperty("remote.port", "25576"));
        config.listenPort = Integer.parseInt(props.getProperty("listen.port", "25577"));
        
        return config;
    }
    
    /**
     * 保存插件配置
     */
    public static void savePluginConfig(PluginConfig config) {
        Properties props = new Properties();
        props.setProperty("server.host", config.serverHost);
        props.setProperty("server.port", String.valueOf(config.serverPort));
        props.setProperty("server.password", config.serverPassword);
        props.setProperty("remote.host", config.remoteHost);
        props.setProperty("remote.port", String.valueOf(config.remotePort));
        props.setProperty("listen.port", String.valueOf(config.listenPort));
        
        saveProperties(PLUGIN_CONFIG_FILE, props);
    }
    
    /**
     * 加载远程控制端配置
     */
    public static ClientConfig loadClientConfig() {
        Properties props = loadProperties(CLIENT_CONFIG_FILE);
        if (props == null) {
            // 创建默认配置
            ClientConfig config = new ClientConfig();
            saveClientConfig(config);
            return config;
        }
        
        ClientConfig config = new ClientConfig();
        config.pluginHost = props.getProperty("plugin.host", "localhost");
        config.pluginPort = Integer.parseInt(props.getProperty("plugin.port", "25577"));
        
        return config;
    }
    
    /**
     * 保存远程控制端配置
     */
    public static void saveClientConfig(ClientConfig config) {
        Properties props = new Properties();
        props.setProperty("plugin.host", config.pluginHost);
        props.setProperty("plugin.port", String.valueOf(config.pluginPort));
        
        saveProperties(CLIENT_CONFIG_FILE, props);
    }
    
    private static Properties loadProperties(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return null;
        }
        
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(path)) {
            props.load(is);
            return props;
        } catch (IOException e) {
            System.err.println("加载配置文件失败: " + filePath);
            e.printStackTrace();
            return null;
        }
    }
    
    private static void saveProperties(String filePath, Properties props) {
        Path path = Paths.get(filePath);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream os = Files.newOutputStream(path)) {
                props.store(os, "RCONPro Configuration");
                System.out.println("配置文件已生成: " + path.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("保存配置文件失败: " + filePath);
            e.printStackTrace();
        }
    }
    
    /**
     * 插件配置类
     */
    public static class PluginConfig {
        public String serverHost = "localhost";     // MC服务器RCON地址
        public int serverPort = 25575;              // MC服务器RCON端口
        public String serverPassword = "changeme";  // MC服务器RCON密码
        public String remoteHost = "localhost";   // 远程控制端地址
        public int remotePort = 25576;              // 远程控制端端口（用于主动连接）
        public int listenPort = 25577;             // 插件监听端口（用于接收连接）
    }
    
    /**
     * 远程控制端配置类
     */
    public static class ClientConfig {
        public String pluginHost = "localhost";    // 插件地址
        public int pluginPort = 25577;             // 插件端口
    }
}

