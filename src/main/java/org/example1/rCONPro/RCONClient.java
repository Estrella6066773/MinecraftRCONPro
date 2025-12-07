package org.example1.rCONPro;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

/**
 * RCON客户端 - 用于连接MC服务器的RCON
 */
public class RCONClient {
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private String host;
    private int port;
    private String password;
    private int requestId = 0;
    private Logger logger;
    private boolean connected = false;
    
    public RCONClient(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.logger = null;
    }
    
    public RCONClient(String host, int port, String password, Logger logger) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.logger = logger;
    }
    
    /**
     * 连接到RCON服务器（带重试）
     */
    public boolean connectWithRetry(long retryIntervalMs) {
        while (true) {
            if (connect()) {
                return true;
            }
            if (logger != null) {
                logger.warning("RCON连接失败，将在 " + (retryIntervalMs / 1000) + " 秒后重试...");
            } else {
                System.err.println("RCON连接失败，将在 " + (retryIntervalMs / 1000) + " 秒后重试...");
            }
            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
    
    /**
     * 连接到RCON服务器
     */
    public boolean connect() {
        disconnect(); // 先断开旧连接
        
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(10000); // 10秒超时
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            
            // 发送认证
            boolean authSuccess = authenticate();
            connected = authSuccess;
            return authSuccess;
        } catch (IOException e) {
            String errorMsg = "连接RCON服务器失败: " + e.getMessage();
            if (logger != null) {
                logger.warning(errorMsg);
            } else {
                System.err.println(errorMsg);
            }
            disconnect();
            return false;
        }
    }
    
    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed() && socket.isConnected();
    }
    
    /**
     * 认证
     */
    private boolean authenticate() {
        try {
            sendPacket(3, password); // 3 = SERVERDATA_AUTH
            RCONPacket response = receivePacket();
            boolean success = response != null && response.requestId != -1;
            if (!success && logger != null) {
                logger.warning("RCON认证失败: 密码可能不正确");
            }
            return success;
        } catch (IOException e) {
            String errorMsg = "RCON认证失败: " + e.getMessage();
            if (logger != null) {
                logger.warning(errorMsg);
            } else {
                System.err.println(errorMsg);
            }
            return false;
        }
    }
    
    /**
     * 发送命令（带自动重连）
     */
    public String sendCommand(String command) {
        if (!isConnected()) {
            if (logger != null) {
                logger.warning("RCON连接已断开，尝试重连...");
            }
            connectWithRetry(5000); // 5秒重试间隔
        }
        
        try {
            sendPacket(2, command); // 2 = SERVERDATA_EXECCOMMAND
            RCONPacket response = receivePacket();
            if (response != null) {
                return response.body;
            }
            return "";
        } catch (IOException e) {
            connected = false;
            String errorMsg = "发送RCON命令失败: " + e.getMessage();
            if (logger != null) {
                logger.warning(errorMsg);
            } else {
                System.err.println(errorMsg);
            }
            return "";
        }
    }
    
    /**
     * 发送数据包
     */
    private void sendPacket(int type, String body) throws IOException {
        byte[] bodyBytes = body.getBytes("UTF-8");
        int packetLength = 4 + 4 + bodyBytes.length + 2; // requestId + type + body + null terminator
        
        ByteBuffer buffer = ByteBuffer.allocate(4 + packetLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(packetLength);
        buffer.putInt(++requestId);
        buffer.putInt(type);
        buffer.put(bodyBytes);
        buffer.put((byte) 0); // null terminator
        buffer.put((byte) 0); // null terminator
        
        output.write(buffer.array());
        output.flush();
    }
    
    /**
     * 接收数据包
     */
    private RCONPacket receivePacket() throws IOException {
        try {
            int length = Integer.reverseBytes(input.readInt());
            int requestId = Integer.reverseBytes(input.readInt());
            int type = Integer.reverseBytes(input.readInt());
            
            byte[] bodyBytes = new byte[length - 4 - 4 - 2];
            input.readFully(bodyBytes);
            input.readByte(); // null terminator
            input.readByte(); // null terminator
            
            String body = new String(bodyBytes, "UTF-8");
            return new RCONPacket(requestId, type, body);
        } catch (EOFException e) {
            connected = false;
            throw new IOException("连接已断开", e);
        }
    }
    
    /**
     * 关闭连接
     */
    public void disconnect() {
        connected = false;
        try {
            if (input != null) {
                input.close();
                input = null;
            }
            if (output != null) {
                output.close();
                output = null;
            }
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            // 忽略关闭错误
        }
    }
    
    /**
     * RCON数据包
     */
    private static class RCONPacket {
        int requestId;
        @SuppressWarnings("unused")
        int type;
        String body;
        
        RCONPacket(int requestId, int type, String body) {
            this.requestId = requestId;
            this.type = type;
            this.body = body;
        }
    }
}

