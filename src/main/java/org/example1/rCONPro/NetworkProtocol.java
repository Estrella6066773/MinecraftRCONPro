package org.example1.rCONPro;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 网络通信协议 - 插件和控制端之间的通信
 */
public class NetworkProtocol {
    
    // 消息类型
    public static final int MSG_LOG = 1;        // 日志消息
    public static final int MSG_COMMAND = 2;    // 命令消息
    public static final int MSG_PING = 3;       // 心跳
    public static final int MSG_PONG = 4;       // 心跳响应
    
    /**
     * 发送消息
     */
    public static void sendMessage(DataOutputStream out, int type, String message) throws IOException {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        out.writeInt(type);
        out.writeInt(messageBytes.length);
        out.write(messageBytes);
        out.flush();
    }
    
    /**
     * 接收消息
     */
    public static Message receiveMessage(DataInputStream in) throws IOException {
        int type = in.readInt();
        int length = in.readInt();
        byte[] messageBytes = new byte[length];
        in.readFully(messageBytes);
        String message = new String(messageBytes, StandardCharsets.UTF_8);
        return new Message(type, message);
    }
    
    /**
     * 消息类
     */
    public static class Message {
        public final int type;
        public final String content;
        
        public Message(int type, String content) {
            this.type = type;
            this.content = content;
        }
    }
}

