
package com.enchanted.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class ShardManager {

    private final EnchantedSMP plugin;
    private final File file;
    private final YamlConfiguration data;

    public ShardManager(EnchantedSMP plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "shards.yml");

        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create shards.yml!");
            }
        }

        data = YamlConfiguration.loadConfiguration(file);
        startRewards();
    }

    public double getBalance(OfflinePlayer player) {
        return data.getDouble("players." + player.getUniqueId(), 0);
    }

    public void giveShards(OfflinePlayer player, double amount) {
        if (amount <= 0) return;

        UUID uuid = player.getUniqueId();
        String path = "players." + uuid;

        data.set(path, getBalance(player) + amount);
        save();
    }

    public boolean takeShards(OfflinePlayer player, double amount) {
        if (amount <= 0 || getBalance(player) < amount) {
            return false;
        }

        data.set(
                "players." + player.getUniqueId(),
                getBalance(player) - amount
        );

        save();
        return true;
    }

    public void setShards(OfflinePlayer player, double amount) {
        if (amount < 0) return;

        data.set("players." + player.getUniqueId(), amount);
        save();
    }

    private void startRewards() {
        if (!plugin.getConfig().getBoolean("shards.enabled", true)) return;

        long seconds = Math.max(1,
                plugin.getConfig().getLong("shards.reward-interval-seconds", 60));

        long amount = plugin.getConfig().getLong(
                "shards.reward-amount", 200);

        boolean notify = plugin.getConfig().getBoolean(
                "shards.notify-player", true);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                giveShards(player, amount);

                if (notify) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes(
                            '&',
                            "&5&lENCHANTEDSMP &8» &d+" + amount
                                    + " Shards! &7(Playtime reward)"
                    ));
                }
            }
        }, seconds * 20L, seconds * 20L);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save shard balances!");
        }
    }
}
