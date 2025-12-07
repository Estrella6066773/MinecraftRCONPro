# RCONPro

一个 Minecraft 服务器远程控制工具，支持作为 Paper 插件或独立应用运行。

## 功能特性

- 🔌 **双模式运行**：可作为 Paper 插件或独立应用启动
- 📡 **实时日志转发**：自动监听并转发服务器控制台所有日志
- ⌨️ **远程命令执行**：通过 RCON 协议执行服务器命令
- 🔐 **安全设计**：RCON密码由控制端管理，插件不存储密码
- ⚙️ **自动配置**：首次运行自动生成配置文件
- 🔄 **自动重连**：连接断开时自动重连，无需手动干预
- 💪 **稳定连接**：KeepAlive和心跳机制确保连接稳定

## 快速开始

### 作为插件使用

1. 将 `rconpro-1.0-beta.jar` 放入服务器的 `plugins` 目录
2. 启动服务器，插件会自动生成配置文件
3. 编辑 `plugins/RCONPro/config.properties` 配置 RCON 地址和端口（**不需要密码**）
4. 重启服务器，插件会监听端口等待控制端连接

### 作为远程控制端使用

1. 使用 `java -jar rconpro-1.0-beta.jar` 启动
2. 首次运行会自动生成 `rconpro-client.properties`
3. 编辑配置文件：
   - 设置插件服务器地址和端口
   - **配置RCON密码**（连接时发送给插件）
4. 重新运行程序，连接成功后会自动发送RCON密码并建立连接

## 配置说明

### 插件配置 (`plugins/RCONPro/config.properties`)

```properties
rcon.host=localhost         # MC服务器RCON地址（等待控制端提供密码后连接）
rcon.port=25575            # MC服务器RCON端口
listen.port=25577          # 插件监听端口（用于接收远程控制端连接）
```

**重要说明**：
- 插件配置中**不包含RCON密码**，密码由远程控制端提供
- 插件启动时**不会立即连接RCON**，而是等待控制端连接
- 当控制端连接并提供RCON密码后，插件才会连接RCON服务器
- `listen.port` 是插件监听端口，控制端通过此端口连接插件

### 远程控制端配置 (`rconpro-client.properties`)

```properties
plugin.host=localhost       # 插件服务器地址
plugin.port=25577          # 插件监听端口（必须与插件配置中的listen.port一致）
rcon.password=your_password # RCON密码（连接时发送给插件）
```

**重要说明**：
- `rcon.password` 是必需的，连接成功后会立即发送给插件
- 如果连接断开后重连，会自动重新发送RCON密码

## 使用示例

### 启动流程

**插件端日志**：
```
[INFO] [RCONPro] 监听端口: 25577，等待远程控制端连接...
[INFO] [RCONPro] RCONPro 插件模式已启动，等待远程控制端连接并提供RCON密码
[INFO] [RCONPro] 远程控制端已连接: /127.0.0.1:51913
[INFO] [RCONPro] 收到RCON密码，准备连接RCON服务器 localhost:25575
[INFO] [RCONPro] 正在连接RCON服务器 localhost:25575...
[INFO] [RCONPro] 已连接到RCON服务器
```

**控制端日志**：
```
=== RCONPro 远程控制端 ===
配置已加载: localhost:25577
正在连接到插件 localhost:25577...
已连接到插件: localhost:25577
已发送RCON密码
已连接！输入命令发送到服务器（输入 'quit' 退出）:
----------------------------------------
```

### 使用示例

```
[INFO] Server started
[INFO] Loading plugins...
[INFO] Estrella6066 joined the game
/list
[INFO] There are 1 of a max of 20 players online
say Hello from remote console!
[INFO] [Server] Hello from remote console!
quit
```

### 自动重连

如果连接断开，控制端会自动重连：

```
连接已断开，正在重连...
正在连接到插件 localhost:25577...
已连接到插件: localhost:25577
已重新发送RCON密码
```

## 系统要求

- Java 21 或更高版本
- Paper 1.21+ 服务器
- 已启用 RCON 的 Minecraft 服务器

## 工作原理

```
┌─────────────────┐         ┌──────────────┐         ┌─────────────┐
│  远程控制端      │◄───────►│  插件模式    │◄───────►│ MC服务器    │
│ (独立应用)      │  TCP    │ (Paper插件)  │  RCON   │ (RCON)      │
└─────────────────┘ 25577  └──────────────┘  25575  └─────────────┘
      │                              │                        │
      │                              │                        │
   输入命令                      转发日志                  执行命令
   接收日志                      接收命令                  输出日志
   发送密码                      连接RCON
```

**工作流程**：
1. 插件启动，监听端口等待控制端连接
2. 控制端启动，连接到插件
3. 控制端发送RCON密码给插件
4. 插件使用配置的host:port和收到的密码连接RCON
5. 建立双向通信：日志转发 + 命令执行

## 重要特性

- ✅ **自动重连**：连接断开时自动重连，无需手动干预
- ✅ **连接恢复**：网络中断后自动恢复，重连后自动重新发送RCON密码
- ✅ **稳定连接**：KeepAlive和10秒心跳机制确保连接稳定
- ✅ **完整日志**：转发所有服务器日志，包括插件日志、玩家消息等
- ✅ **安全设计**：RCON密码由控制端管理，插件不存储密码

## 注意事项

- 确保服务器已启用 RCON 功能（在 `server.properties` 中配置）
- 防火墙需要开放插件监听端口（默认 25577）
- 远程控制端和插件服务器需要在同一网络或可互相访问
- RCON密码存储在控制端配置文件中，请妥善保管
- 如果RCON连接失败，插件会在后台持续重试（每5秒一次）
- 如果控制端连接失败，会自动重试（每5秒一次）
- 连接断开时会自动重连，重连后会自动重新发送RCON密码

