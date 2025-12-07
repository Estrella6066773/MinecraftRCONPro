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
        config.rconHost = props.getProperty("rcon.host", "localhost");
        config.rconPort = Integer.parseInt(props.getProperty("rcon.port", "25575"));
        config.listenPort = Integer.parseInt(props.getProperty("listen.port", "25577"));
        
        return config;
    }
    
    /**
     * 保存插件配置
     */
    public static void savePluginConfig(PluginConfig config) {
        Properties props = new Properties();
        props.setProperty("rcon.host", config.rconHost);
        props.setProperty("rcon.port", String.valueOf(config.rconPort));
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
        config.rconPassword = props.getProperty("rcon.password", "");
        
        return config;
    }
    
    /**
     * 保存远程控制端配置
     */
    public static void saveClientConfig(ClientConfig config) {
        Properties props = new Properties();
        props.setProperty("plugin.host", config.pluginHost);
        props.setProperty("plugin.port", String.valueOf(config.pluginPort));
        props.setProperty("rcon.password", config.rconPassword);
        
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
        public String rconHost = "localhost";       // MC服务器RCON地址（等待控制端提供密码后连接）
        public int rconPort = 25575;               // MC服务器RCON端口
        public int listenPort = 25577;             // 插件监听端口（用于接收控制端连接）
    }
    
    /**
     * 远程控制端配置类
     */
    public static class ClientConfig {
        public String pluginHost = "localhost";    // 插件服务器地址
        public int pluginPort = 25577;             // 插件监听端口
        public String rconPassword = "";           // RCON密码（连接时发送给插件）
    }
}

