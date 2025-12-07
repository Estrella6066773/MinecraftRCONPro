package org.example1.rCONPro;

import org.bukkit.plugin.java.JavaPlugin;

public final class RCONPro extends JavaPlugin {
    
    private PluginMode pluginMode;

    @Override
    public void onEnable() {
        // 加载配置
        ConfigManager.PluginConfig config = ConfigManager.loadPluginConfig();
        getLogger().info("配置已加载");
        
        // 启动插件模式
        pluginMode = new PluginMode(this, config);
        pluginMode.start();
    }

    @Override
    public void onDisable() {
        // 停止插件模式
        if (pluginMode != null) {
            pluginMode.stop();
        }
    }
}
