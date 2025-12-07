package org.example1.rCONPro;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 远程控制端 - 独立应用模式
 */
public class RemoteConsoleClient {
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private ExecutorService executor;
    private boolean running = false;
    private Scanner scanner;
    
    /**
     * 启动远程控制端
     */
    public void start() {
        System.out.println("=== RCONPro 远程控制端 ===");
        
        // 加载配置
        ConfigManager.ClientConfig config = ConfigManager.loadClientConfig();
        System.out.println("配置已加载: " + config.pluginHost + ":" + config.pluginPort);
        
        running = true;
        
        // 连接插件（带重试）
        if (!connectWithRetry(config)) {
            System.err.println("无法建立连接，程序退出");
            return;
        }
        executor = Executors.newCachedThreadPool();
        scanner = new Scanner(System.in);
        
        // 启动消息接收线程
        executor.submit(this::receiveMessages);
        
        // 启动心跳线程
        executor.submit(this::sendHeartbeat);
        
        // 启动命令输入线程
        System.out.println("已连接！输入命令发送到服务器（输入 'quit' 退出）:");
        System.out.println("----------------------------------------");
        handleCommandInput();
    }
    
    /**
     * 连接插件（带重试）
     */
    private boolean connectWithRetry(ConfigManager.ClientConfig config) {
        while (running) {
            try {
                System.out.println("正在连接到插件 " + config.pluginHost + ":" + config.pluginPort + "...");
                Socket newSocket = new Socket(config.pluginHost, config.pluginPort);
                // 设置Socket选项以保持连接
                newSocket.setKeepAlive(true);
                newSocket.setTcpNoDelay(true);
                newSocket.setSoTimeout(0); // 无读取超时，由心跳保持连接
                
                synchronized (this) {
                    socket = newSocket;
                    input = new DataInputStream(socket.getInputStream());
                    output = new DataOutputStream(socket.getOutputStream());
                }
                
                System.out.println("已连接到插件: " + config.pluginHost + ":" + config.pluginPort);
                
                // 立即发送RCON密码
                if (config.rconPassword != null && !config.rconPassword.isEmpty()) {
                    try {
                        NetworkProtocol.sendMessage(output, NetworkProtocol.MSG_RCON_CONFIG, config.rconPassword);
                        System.out.println("已发送RCON密码");
                    } catch (IOException e) {
                        System.err.println("发送RCON密码失败: " + e.getMessage());
                        return false;
                    }
                } else {
                    System.err.println("警告: RCON密码未配置，插件将无法连接RCON服务器");
                }
                
                return true;
            } catch (IOException e) {
                System.err.println("无法连接到插件: " + e.getMessage());
                System.err.println("将在 5 秒后重试...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
    
    /**
     * 重新连接
     */
    private void reconnect(ConfigManager.ClientConfig config) {
        synchronized (this) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                // 忽略
            }
            socket = null;
            input = null;
            output = null;
        }
        
        System.out.println("\n连接已断开，正在重连...");
        if (connectWithRetry(config)) {
            // 重连成功后，重新发送RCON密码
            synchronized (this) {
                if (output != null && config.rconPassword != null && !config.rconPassword.isEmpty()) {
                    try {
                        NetworkProtocol.sendMessage(output, NetworkProtocol.MSG_RCON_CONFIG, config.rconPassword);
                        System.out.println("已重新发送RCON密码");
                    } catch (IOException e) {
                        System.err.println("重新发送RCON密码失败: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * 检查连接状态
     */
    private boolean isConnected() {
        synchronized (this) {
            return socket != null && !socket.isClosed() && socket.isConnected() && input != null && output != null;
        }
    }
    
    /**
     * 接收来自插件的消息
     */
    private void receiveMessages() {
        ConfigManager.ClientConfig config = ConfigManager.loadClientConfig();
        
        while (running) {
            try {
                if (!isConnected()) {
                    Thread.sleep(1000);
                    continue;
                }
                
                DataInputStream currentInput;
                synchronized (this) {
                    currentInput = input;
                }
                
                if (currentInput == null) {
                    Thread.sleep(1000);
                    continue;
                }
                
                NetworkProtocol.Message msg = NetworkProtocol.receiveMessage(currentInput);
                
                if (msg.type == NetworkProtocol.MSG_LOG) {
                    // 打印日志
                    System.out.println(msg.content);
                } else if (msg.type == NetworkProtocol.MSG_PONG) {
                    // 心跳响应，不打印
                }
            } catch (IOException e) {
                if (running) {
                    // 尝试重连
                    reconnect(config);
                } else {
                    break;
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    /**
     * 发送心跳
     */
    private void sendHeartbeat() {
        while (running) {
            try {
                Thread.sleep(10000); // 10秒发送一次心跳（更频繁以保持连接）
                synchronized (this) {
                    if (running && output != null && socket != null && !socket.isClosed()) {
                        NetworkProtocol.sendMessage(output, NetworkProtocol.MSG_PING, "");
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (IOException e) {
                // 连接可能已断开，receiveMessages会处理重连
            }
        }
    }
    
    /**
     * 处理命令输入
     */
    private void handleCommandInput() {
        while (running) {
            try {
                if (scanner.hasNextLine()) {
                    String command = scanner.nextLine().trim();
                    
                    if (command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("exit")) {
                        stop();
                        break;
                    }
                    
                    if (!command.isEmpty()) {
                        synchronized (this) {
                            if (output != null && socket != null && !socket.isClosed()) {
                                try {
                                    NetworkProtocol.sendMessage(output, NetworkProtocol.MSG_COMMAND, command);
                                } catch (IOException e) {
                                    System.err.println("发送命令失败: " + e.getMessage());
                                }
                            } else {
                                System.err.println("未连接到插件，无法发送命令");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("发送命令失败: " + e.getMessage());
                }
                break;
            }
        }
    }
    
    /**
     * 停止远程控制端
     */
    private void stop() {
        running = false;
        
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            // 忽略
        }
        
        if (scanner != null) {
            scanner.close();
        }
        
        if (executor != null) {
            executor.shutdown();
        }
        
        System.out.println("远程控制端已关闭");
    }
}

