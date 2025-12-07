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
        
        // 连接插件
        try {
            socket = new Socket(config.pluginHost, config.pluginPort);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            System.out.println("已连接到插件: " + config.pluginHost + ":" + config.pluginPort);
        } catch (IOException e) {
            System.err.println("无法连接到插件: " + e.getMessage());
            System.err.println("请确保插件已启动并监听端口 " + config.pluginPort);
            return;
        }
        
        running = true;
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
     * 接收来自插件的消息
     */
    private void receiveMessages() {
        while (running) {
            try {
                NetworkProtocol.Message msg = NetworkProtocol.receiveMessage(input);
                
                if (msg.type == NetworkProtocol.MSG_LOG) {
                    // 打印日志
                    System.out.println(msg.content);
                } else if (msg.type == NetworkProtocol.MSG_PONG) {
                    // 心跳响应，不打印
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("\n连接已断开: " + e.getMessage());
                    stop();
                }
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
                Thread.sleep(30000); // 30秒发送一次心跳
                if (running && output != null) {
                    NetworkProtocol.sendMessage(output, NetworkProtocol.MSG_PING, "");
                }
            } catch (InterruptedException e) {
                break;
            } catch (IOException e) {
                if (running) {
                    System.err.println("发送心跳失败: " + e.getMessage());
                }
                break;
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
                    
                    if (!command.isEmpty() && output != null) {
                        NetworkProtocol.sendMessage(output, NetworkProtocol.MSG_COMMAND, command);
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

