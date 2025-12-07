# RCONPro

Minecraft 服务器远程控制工具，支持作为 Paper 插件或独立应用运行。

## 主要功能

- 📡 **实时日志转发**：监控服务器日志文件，实时转发所有日志
- ⌨️ **远程命令执行**：通过 RCON 协议执行服务器命令
- 🔄 **自动重连**：连接断开时自动重连
- 🔐 **安全设计**：RCON密码由控制端管理，插件不存储密码

## 使用方法

### 插件端（服务器）

1. 将 `rconpro-1.0-beta.jar` 放入 `plugins` 目录
2. 启动服务器，编辑 `plugins/RCONPro/config.properties`：
   ```properties
   rcon.host=localhost      # MC服务器RCON地址
   rcon.port=25575          # MC服务器RCON端口
   listen.port=25577        # 插件监听端口
   ```
3. 重启服务器，插件会监听端口等待控制端连接

### 控制端（远程）

1. 运行 `java -jar rconpro-1.0-beta.jar`
2. 编辑生成的 `rconpro-client.properties`：
   ```properties
   plugin.host=localhost       # 插件服务器地址
   plugin.port=25577          # 插件监听端口
   rcon.password=your_password # RCON密码
   ```
3. 重新运行程序，连接成功后输入命令（输入 `quit` 退出）

## 系统要求

- Java 21+
- Paper 1.21+
- 已启用 RCON 的 Minecraft 服务器

## 注意事项

- 确保服务器已启用 RCON（在 `server.properties` 中配置）
- 防火墙需要开放插件监听端口（默认 25577）
- RCON密码存储在控制端配置文件中，请妥善保管

