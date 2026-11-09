
package com.enchanted.smp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class FirstJoinManager implements Listener {

    private static final double STARTING_MONEY = 300000;
    private static final double STARTING_SHARDS = 0;

    private final EnchantedSMP plugin;
    private final File file;
    private final YamlConfiguration data;

    public FirstJoinManager(EnchantedSMP plugin) {
        this.plugin = plugin;

        this.file = new File(plugin.getDataFolder(), "first-join.yml");
        this.data = YamlConfiguration.loadConfiguration(file);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        String path = "players." + uuid + ".received";

        if (data.getBoolean(path, false)) {
            return;
        }

        if (plugin.getEconomy() == null) {
            plugin.getLogger().warning(
                "Vault economy is unavailable. Starting money was not given to "
                + player.getName()
            );
            return;
        }

        double currentBalance = plugin.getEconomy().getBalance(player);

        if (currentBalance > 0) {
            plugin.getEconomy().withdrawPlayer(player, currentBalance);
        }

        plugin.getEconomy().depositPlayer(player, STARTING_MONEY);

        plugin.getShardManager().setShards(player, STARTING_SHARDS);

        data.set(path, true);
        save();

        player.sendMessage(
            "§5§lENCHANTEDSMP §8» §aWelcome! §fYou received §6$300,000 §aand §d0 Shards§a!"
        );
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe(
                "Could not save first-join.yml!"
            );
            exception.printStackTrace();
        }
    }
}
