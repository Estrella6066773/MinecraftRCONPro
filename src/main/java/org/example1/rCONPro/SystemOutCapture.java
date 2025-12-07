package org.example1.rCONPro;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * System.out/System.err 捕获器 - 简洁实现
 */
public class SystemOutCapture {
    private PrintStream originalOut;
    private PrintStream originalErr;
    private TeeOutputStream teeOut;
    private TeeOutputStream teeErr;
    private final PluginMode pluginMode;
    private final ThreadLocal<Boolean> isForwarding = ThreadLocal.withInitial(() -> false);
    
    public SystemOutCapture(PluginMode pluginMode) {
        this.pluginMode = pluginMode;
    }
    
    /**
     * 启动捕获
     */
    public void start() {
        originalOut = System.out;
        originalErr = System.err;
        
        teeOut = new TeeOutputStream(originalOut);
        teeErr = new TeeOutputStream(originalErr);
        
        System.setOut(new PrintStream(teeOut, true));
        System.setErr(new PrintStream(teeErr, true));
    }
    
    /**
     * 停止捕获并恢复
     */
    public void stop() {
        if (originalOut != null) {
            System.setOut(originalOut);
        }
        if (originalErr != null) {
            System.setErr(originalErr);
        }
    }
    
    /**
     * Tee输出流 - 同时写入原始流和转发
     */
    private class TeeOutputStream extends OutputStream {
        private final OutputStream original;
        private final StringBuilder buffer = new StringBuilder();
        private final Object lock = new Object();
        
        public TeeOutputStream(OutputStream original) {
            this.original = original;
        }
        
        @Override
        public void write(int b) throws IOException {
            synchronized (lock) {
                // 先写入原始流
                original.write(b);
                
                // 避免循环输出（如果正在转发，只写入原始流，不收集）
                if (isForwarding.get()) {
                    return;
                }
                
                // 收集到缓冲区
                if (b == '\n') {
                    String line = buffer.toString();
                    buffer.setLength(0);
                    
                    // 移除末尾的空白字符，但保留原始内容
                    String trimmedLine = line.trim();
                    if (!trimmedLine.isEmpty()) {
                        // 转发到远程控制端（转发原始行，包含所有内容）
                        isForwarding.set(true);
                        try {
                            // 直接转发，不做任何过滤
                            pluginMode.sendLog(trimmedLine);
                        } catch (Exception e) {
                            // 忽略转发错误，不影响原始输出
                            // 注意：这里不能使用System.out/System.err，会导致循环
                        } finally {
                            isForwarding.set(false);
                        }
                    }
                } else if (b != '\r') {
                    // 收集所有非回车符的字符
                    buffer.append((char) b);
                }
            }
        }
        
        @Override
        public void flush() throws IOException {
            original.flush();
        }
    }
}

