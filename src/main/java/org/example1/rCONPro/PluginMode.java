package org.example1.rCONPro;

import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private SystemOutCapture systemOutCapture;
    private boolean running = false;
    
    public PluginMode(Plugin plugin, ConfigManager.PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    /**
     * 启动插件模式
     */
    public void start() {
        executor = Executors.newCachedThreadPool();
        running = true;
        
        // 启动网络服务器
        try {
            serverSocket = new ServerSocket(config.listenPort);
            plugin.getLogger().info("监听端口: " + config.listenPort + "，等待远程控制端连接...");
        } catch (IOException e) {
            plugin.getLogger().severe("无法启动网络服务器: " + e.getMessage());
            return;
        }
        
        // 启动System.out捕获
        systemOutCapture = new SystemOutCapture(this);
        systemOutCapture.start();
        
        // 接受客户端连接
        executor.submit(this::acceptConnections);
        
        // 启动命令接收线程
        executor.submit(this::receiveCommands);
        
        plugin.getLogger().info("RCONPro 插件模式已启动，等待远程控制端连接并提供RCON密码");
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
                    // 设置Socket选项以保持连接
                    socket.setKeepAlive(true);
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(0); // 无读取超时
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
                
                if (msg.type == NetworkProtocol.MSG_RCON_CONFIG) {
                    // 收到RCON配置，解析并连接
                    handleRCONConfig(msg.content);
                } else if (msg.type == NetworkProtocol.MSG_COMMAND) {
                    // 通过RCON发送命令
                    if (rconClient != null && rconClient.isConnected()) {
                        String response = rconClient.sendCommand(msg.content);
                        plugin.getLogger().info("执行命令: " + msg.content);
                        
                        // 将RCON响应转发到远程控制端
                        if (response != null && !response.trim().isEmpty()) {
                            sendLog("[RCON Response] " + response);
                        }
                    } else {
                        plugin.getLogger().warning("RCON未连接，无法执行命令: " + msg.content);
                        // 发送错误消息到控制端
                        sendLog("[ERROR] RCON未连接，无法执行命令: " + msg.content);
                    }
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
                        plugin.getLogger().warning("客户端连接断开: " + e.getMessage());
                        try {
                            if (!clientSocket.isClosed()) {
                                clientSocket.close();
                            }
                        } catch (IOException ex) {
                            // 忽略
                        }
                        clientSocket = null;
                        clientInput = null;
                        clientOutput = null;
                    }
                }
                // 等待新连接，不退出循环
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    /**
     * 处理RCON配置
     * 控制端发送的是RCON密码，插件使用配置中的host和port
     */
    private void handleRCONConfig(String rconPassword) {
        if (rconPassword == null || rconPassword.isEmpty()) {
            plugin.getLogger().warning("收到空的RCON密码");
            return;
        }
        
        plugin.getLogger().info("收到RCON密码，准备连接RCON服务器 " + config.rconHost + ":" + config.rconPort);
        
        // 如果已有RCON连接，先断开
        if (rconClient != null) {
            rconClient.disconnect();
        }
        
        // 在后台线程中连接RCON（带重试）
        final String finalHost = config.rconHost;
        final int finalPort = config.rconPort;
        final String finalPassword = rconPassword;
        
        executor.submit(() -> {
            rconClient = new RCONClient(finalHost, finalPort, finalPassword, plugin.getLogger());
            plugin.getLogger().info("正在连接RCON服务器 " + finalHost + ":" + finalPort + "...");
            rconClient.connectWithRetry(5000); // 5秒重试间隔
            plugin.getLogger().info("已连接到RCON服务器");
        });
    }
    
    /**
     * 发送日志到远程控制端
     */
    void sendLog(String logMessage) {
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
     * 停止插件模式
     */
    public void stop() {
        running = false;
        
        // 停止System.out捕获并恢复
        if (systemOutCapture != null) {
            systemOutCapture.stop();
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

