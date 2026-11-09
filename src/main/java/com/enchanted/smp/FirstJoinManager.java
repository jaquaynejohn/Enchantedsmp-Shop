
package com.enchanted.smp;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class FirstJoinManager implements Listener {

    private final EnchantedSMP plugin;
    private final File file;
    private final YamlConfiguration data;

    private static final double STARTING_MONEY = 300000;
    private static final double STARTING_SHARDS = 0;

    public FirstJoinManager(EnchantedSMP plugin) {
        this.plugin = plugin;

        file = new File(plugin.getDataFolder(), "first-join.yml");

        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe(
                        "Could not create first-join.yml!"
                );
            }
        }

        data = YamlConfiguration.loadConfiguration(file);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        UUID uuid = player.getUniqueId();
        String path = "players." + uuid;

        if (data.getBoolean(path + ".received", false)) {
            return;
        }

        if (plugin.getEconomy() == null) {
            plugin.getLogger().warning(
                    "Could not give starting money to "
                            + player.getName()
                            + ": Vault economy unavailable."
            );
            return;
        }

        plugin.getEconomy().setBalance(player, STARTING_MONEY);

        plugin.getShardManager().setShards(
                player,
                STARTING_SHARDS
        );

        data.set(path + ".received", true);
        save();

        player.sendMessage(
                plugin.getServer().getConsoleSender().getName().isEmpty()
                        ? "Welcome to EnchantedSMP!"
                        : "§5§lENCHANTEDSMP §8» §aWelcome! §fYou received §6$300,000 §aand §d0 Shards§a!"
        );
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe(
                    "Could not save first-join data!"
            );
        }
    }
}
