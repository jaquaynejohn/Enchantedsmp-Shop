
package com.enchanted.smp;

import org.bukkit.plugin.java.JavaPlugin;

public class EnchantedSMP extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("EnchantedSMP has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("EnchantedSMP has been disabled!");
    }
}
