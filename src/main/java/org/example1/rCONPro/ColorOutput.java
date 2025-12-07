package org.example1.rCONPro;

/**
 * 控制台颜色输出工具
 */
public class ColorOutput {
    // ANSI颜色代码
    private static final String RESET = "\u001B[0m";
    private static final String INFO_COLOR = "\u001B[32m";      // 墨绿色
    private static final String WARN_COLOR = "\u001B[33m";     // 暗黄色
    private static final String ERROR_COLOR = "\u001B[31m";    // 暗红色
    private static final String BRIGHT_YELLOW = "\u001B[93m";  // 亮黄色
    
    private static boolean ansiEnabled = false;
    
    /**
     * 启用ANSI颜色支持
     */
    public static void enableAnsi() {
        if (ansiEnabled) {
            return;
        }
        
        // Java 10+ 在Windows 10+上支持ANSI
        // 对于旧版本Windows，可以尝试使用JNI或jansi库
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            try {
                // 尝试通过反射启用ANSI（如果可用）
                // 注意：这需要Java 10+和Windows 10+
                // 如果失败，颜色代码可能不会显示，但不影响功能
            } catch (Exception e) {
                // 忽略错误，继续尝试
            }
        }
        
        ansiEnabled = true;
    }
    
    /**
     * 格式化日志行，添加颜色
     */
    public static String formatLogLine(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        
        String result = line;
        
        // 检查是否包含特殊文本（玩家加入/离开游戏）
        if (line.contains("joined the game") || line.contains("left the game")) {
            String pattern = line.contains("joined the game") ? "joined the game" : "left the game";
            int patternIndex = line.indexOf(pattern);
            
            if (patternIndex > 0) {
                // 找到 ": " 的位置（日志消息开始）
                int colonIndex = line.lastIndexOf(": ", patternIndex);
                if (colonIndex > 0) {
                    // 提取 ": " 之后到 pattern 之前的内容
                    String beforePattern = line.substring(colonIndex + 2, patternIndex).trim();
                    
                    // 玩家名字通常是最后一个单词（在特殊文本之前）
                    // 例如："Estrella6066 " 或 "Estrella6066"
                    String[] parts = beforePattern.split("\\s+");
                    if (parts.length > 0) {
                        String playerName = parts[parts.length - 1];
                        
                        // 构建替换字符串：玩家名 + 空格 + 特殊文本
                        String toReplace = playerName + " " + pattern;
                        
                        // 检查是否包含这个完整模式
                        if (line.contains(toReplace)) {
                            // 给玩家名字和特殊文本都加上亮黄色
                            result = result.replace(toReplace, 
                                BRIGHT_YELLOW + playerName + RESET + " " + BRIGHT_YELLOW + pattern + RESET);
                        } else {
                            // 如果没有空格，可能是紧挨着的
                            toReplace = playerName + pattern;
                            if (line.contains(toReplace)) {
                                result = result.replace(toReplace, 
                                    BRIGHT_YELLOW + playerName + RESET + BRIGHT_YELLOW + pattern + RESET);
                            }
                        }
                    }
                }
            }
        }
        
        // 只给日志级别单词添加颜色，而不是整行
        if (line.contains("/INFO]")) {
            // 只替换 "INFO" 单词
            result = result.replace("/INFO]", "/" + INFO_COLOR + "INFO" + RESET + "]");
        } else if (line.contains("/WARN]")) {
            // 只替换 "WARN" 单词
            result = result.replace("/WARN]", "/" + WARN_COLOR + "WARN" + RESET + "]");
        } else if (line.contains("/ERROR]")) {
            // 只替换 "ERROR" 单词
            result = result.replace("/ERROR]", "/" + ERROR_COLOR + "ERROR" + RESET + "]");
        }
        
        return result;
    }
}

