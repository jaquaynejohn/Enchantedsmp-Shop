
package com.enchanted.smp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class EnchantedSMP extends JavaPlugin {

    private Economy economy;
    private ShopManager shopManager;
    private ShardManager shardManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        shardManager = new ShardManager(this);
        shopManager = new ShopManager(this);

        getCommand("shop").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            shopManager.openMainMenu(player);
            return true;
        });

        getLogger().info("EnchantedSMP enabled successfully!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> provider =
                getServer().getServicesManager()
                        .getRegistration(Economy.class);

        if (provider == null) {
            return false;
        }

        economy = provider.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    @Override
    public void onDisable() {
        if (shardManager != null) {
            shardManager.save();
        }
    }
}
