package org.example1.rCONPro;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    
    public RCONClient(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
    }
    
    /**
     * 连接到RCON服务器
     */
    public boolean connect() {
        try {
            socket = new Socket(host, port);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            
            // 发送认证
            return authenticate();
        } catch (IOException e) {
            System.err.println("连接RCON服务器失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 认证
     */
    private boolean authenticate() {
        try {
            sendPacket(3, password); // 3 = SERVERDATA_AUTH
            RCONPacket response = receivePacket();
            return response != null && response.requestId != -1;
        } catch (IOException e) {
            System.err.println("RCON认证失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 发送命令
     */
    public String sendCommand(String command) {
        try {
            sendPacket(2, command); // 2 = SERVERDATA_EXECCOMMAND
            RCONPacket response = receivePacket();
            if (response != null) {
                return response.body;
            }
            return "";
        } catch (IOException e) {
            System.err.println("发送RCON命令失败: " + e.getMessage());
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
        int length = Integer.reverseBytes(input.readInt());
        int requestId = Integer.reverseBytes(input.readInt());
        int type = Integer.reverseBytes(input.readInt());
        
        byte[] bodyBytes = new byte[length - 4 - 4 - 2];
        input.readFully(bodyBytes);
        input.readByte(); // null terminator
        input.readByte(); // null terminator
        
        String body = new String(bodyBytes, "UTF-8");
        return new RCONPacket(requestId, type, body);
    }
    
    /**
     * 关闭连接
     */
    public void disconnect() {
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
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

