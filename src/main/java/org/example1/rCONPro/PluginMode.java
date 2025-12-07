package org.example1.rCONPro;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * 插件模式 - 在MC服务器内运行
 */
public class PluginMode {
    private final Plugin plugin;
    private final ConfigManager.PluginConfig config;
    private RCONClient rconClient;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataInputStream clientInput;
    private DataOutputStream clientOutput;
    private ExecutorService executor;
    private LogHandler logHandler;
    private boolean running = false;
    
    public PluginMode(Plugin plugin, ConfigManager.PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    /**
     * 启动插件模式
     */
    public void start() {
        // 连接RCON
        rconClient = new RCONClient(config.serverHost, config.serverPort, config.serverPassword);
        if (!rconClient.connect()) {
            plugin.getLogger().severe("无法连接到RCON服务器！请检查配置。");
            return;
        }
        plugin.getLogger().info("已连接到RCON服务器");
        
        // 启动网络服务器
        try {
            serverSocket = new ServerSocket(config.listenPort);
            plugin.getLogger().info("监听端口: " + config.listenPort);
        } catch (IOException e) {
            plugin.getLogger().severe("无法启动网络服务器: " + e.getMessage());
            return;
        }
        
        // 启动日志监听
        logHandler = new LogHandler();
        Logger rootLogger = Bukkit.getLogger().getParent();
        rootLogger.addHandler(logHandler);
        
        executor = Executors.newCachedThreadPool();
        running = true;
        
        // 接受客户端连接
        executor.submit(this::acceptConnections);
        
        // 启动命令接收线程
        executor.submit(this::receiveCommands);
        
        plugin.getLogger().info("RCONPro 插件模式已启动");
    }
    
    /**
     * 接受客户端连接
     */
    private void acceptConnections() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                plugin.getLogger().info("远程控制端已连接: " + socket.getRemoteSocketAddress());
                
                synchronized (this) {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        socket.close();
                        continue;
                    }
                    clientSocket = socket;
                    clientInput = new DataInputStream(socket.getInputStream());
                    clientOutput = new DataOutputStream(socket.getOutputStream());
                }
            } catch (IOException e) {
                if (running) {
                    plugin.getLogger().warning("接受连接时出错: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 接收来自远程控制端的命令
     */
    private void receiveCommands() {
        while (running) {
            try {
                synchronized (this) {
                    if (clientSocket == null || clientSocket.isClosed() || clientInput == null) {
                        Thread.sleep(1000);
                        continue;
                    }
                }
                
                NetworkProtocol.Message msg = NetworkProtocol.receiveMessage(clientInput);
                
                if (msg.type == NetworkProtocol.MSG_COMMAND) {
                    // 通过RCON发送命令
                    rconClient.sendCommand(msg.content);
                    plugin.getLogger().info("执行命令: " + msg.content);
                } else if (msg.type == NetworkProtocol.MSG_PING) {
                    // 响应心跳
                    synchronized (this) {
                        if (clientOutput != null) {
                            NetworkProtocol.sendMessage(clientOutput, NetworkProtocol.MSG_PONG, "");
                        }
                    }
                }
            } catch (IOException e) {
                synchronized (this) {
                    if (clientSocket != null) {
                        plugin.getLogger().warning("客户端连接断开");
                        try {
                            clientSocket.close();
                        } catch (IOException ex) {
                            // 忽略
                        }
                        clientSocket = null;
                        clientInput = null;
                        clientOutput = null;
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    /**
     * 发送日志到远程控制端
     */
    private void sendLog(String logMessage) {
        synchronized (this) {
            if (clientOutput != null && clientSocket != null && !clientSocket.isClosed()) {
                try {
                    NetworkProtocol.sendMessage(clientOutput, NetworkProtocol.MSG_LOG, logMessage);
                } catch (IOException e) {
                    // 连接可能已断开
                }
            }
        }
    }
    
    /**
     * 日志处理器
     */
    private class LogHandler extends java.util.logging.Handler {
        @Override
        public void publish(LogRecord record) {
            if (running) {
                String message = "[" + record.getLevel() + "] " + record.getMessage();
                sendLog(message);
            }
        }
        
        @Override
        public void flush() {
        }
        
        @Override
        public void close() throws SecurityException {
        }
    }
    
    /**
     * 停止插件模式
     */
    public void stop() {
        running = false;
        
        if (logHandler != null) {
            Logger rootLogger = Bukkit.getLogger().getParent();
            rootLogger.removeHandler(logHandler);
        }
        
        if (rconClient != null) {
            rconClient.disconnect();
        }
        
        synchronized (this) {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                // 忽略
            }
            
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                // 忽略
            }
        }
        
        if (executor != null) {
            executor.shutdown();
        }
        
        plugin.getLogger().info("RCONPro 插件模式已停止");
    }
}

