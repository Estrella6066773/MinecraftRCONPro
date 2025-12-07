package org.example1.rCONPro;

import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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
    private LogFileMonitor logFileMonitor;
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
        
        // 启动日志文件监控
        logFileMonitor = new LogFileMonitor();
        logFileMonitor.start();
        
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
                    
                    // 发送连接成功测试消息（使用原始流，避免被SystemOutCapture捕获）
                    try {
                        NetworkProtocol.sendMessage(clientOutput, NetworkProtocol.MSG_LOG, "=== 远程控制端已连接，开始接收日志 ===");
                    } catch (IOException e) {
                        plugin.getLogger().warning("发送连接消息失败: " + e.getMessage());
                    }
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
                            // 直接发送，不使用sendLog（避免被SystemOutCapture捕获）
                            synchronized (this) {
                                if (clientOutput != null && clientSocket != null && !clientSocket.isClosed()) {
                                    try {
                                        NetworkProtocol.sendMessage(clientOutput, NetworkProtocol.MSG_LOG, response);
                                    } catch (IOException e) {
                                        plugin.getLogger().warning("发送RCON响应失败: " + e.getMessage());
                                    }
                                }
                            }
                        } else {
                            // 即使响应为空，也发送一个空响应标记
                            synchronized (this) {
                                if (clientOutput != null && clientSocket != null && !clientSocket.isClosed()) {
                                    try {
                                        NetworkProtocol.sendMessage(clientOutput, NetworkProtocol.MSG_LOG, "[RCON Response] (empty)");
                                    } catch (IOException e) {
                                        // 忽略
                                    }
                                }
                            }
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
        if (logMessage == null || logMessage.trim().isEmpty()) {
            return;
        }
        
        synchronized (this) {
            // 检查客户端是否已连接
            if (clientOutput == null || clientSocket == null || clientSocket.isClosed()) {
                // 客户端未连接，不发送（这是正常的，因为SystemOutCapture在客户端连接前就启动了）
                return;
            }
            
            try {
                NetworkProtocol.sendMessage(clientOutput, NetworkProtocol.MSG_LOG, logMessage);
            } catch (IOException e) {
                // 连接可能已断开，清理连接状态
                plugin.getLogger().warning("发送日志失败: " + e.getMessage());
                try {
                    if (clientSocket != null && !clientSocket.isClosed()) {
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
    }
    
    /**
     * 日志文件监控器 - 监控logs/latest.log文件
     */
    private class LogFileMonitor {
        private Path logFile;
        private long lastPosition = 0;
        private WatchService watchService;
        private boolean monitoring = false;
        
        public LogFileMonitor() {
            // 获取服务器根目录下的logs/latest.log
            File serverDir = new File(".").getAbsoluteFile();
            logFile = Paths.get(serverDir.getParent(), "logs", "latest.log");
            
            // 如果找不到，尝试当前目录
            if (!Files.exists(logFile)) {
                logFile = Paths.get("logs", "latest.log");
            }
        }
        
        public void start() {
            if (!Files.exists(logFile)) {
                plugin.getLogger().warning("日志文件不存在: " + logFile + "，等待文件创建...");
            } else {
                plugin.getLogger().info("开始监控日志文件: " + logFile);
                // 初始化位置到文件末尾
                try {
                    lastPosition = Files.size(logFile);
                } catch (IOException e) {
                    plugin.getLogger().warning("无法获取日志文件大小: " + e.getMessage());
                }
            }
            
            monitoring = true;
            executor.submit(this::monitorLoop);
        }
        
        private void monitorLoop() {
            while (running && monitoring) {
                try {
                    if (Files.exists(logFile)) {
                        // 读取新增内容
                        readNewLines();
                    }
                    // 每100ms检查一次
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    plugin.getLogger().warning("监控日志文件时出错: " + e.getMessage());
                    try {
                        Thread.sleep(1000); // 出错后等待1秒再重试
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }
        
        private void readNewLines() {
            try {
                long currentSize = Files.size(logFile);
                
                if (currentSize < lastPosition) {
                    // 文件被截断（可能是日志轮转），从头开始
                    lastPosition = 0;
                }
                
                if (currentSize > lastPosition) {
                    // 有新内容，读取并转发
                    try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                        raf.seek(lastPosition);
                        
                        // 读取新增的字节
                        byte[] buffer = new byte[(int)(currentSize - lastPosition)];
                        int bytesRead = raf.read(buffer);
                        
                        if (bytesRead > 0) {
                            // 转换为UTF-8字符串
                            String newContent = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                            
                            // 按行分割并转发
                            String[] lines = newContent.split("\n", -1);
                            for (int i = 0; i < lines.length; i++) {
                                String line = lines[i];
                                // 如果不是最后一行，或者最后一行不为空，则转发
                                if (i < lines.length - 1 || !line.isEmpty()) {
                                    if (!line.trim().isEmpty()) {
                                        sendLog(line);
                                    }
                                }
                            }
                            
                            lastPosition = raf.getFilePointer();
                        }
                    }
                }
            } catch (IOException e) {
                // 文件可能正在被写入，忽略错误
            }
        }
        
        public void stop() {
            monitoring = false;
            if (watchService != null) {
                try {
                    watchService.close();
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
    }
    
    /**
     * 停止插件模式
     */
    public void stop() {
        running = false;
        
        // 停止日志文件监控
        if (logFileMonitor != null) {
            logFileMonitor.stop();
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

