package com.enchanted.smp;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.text.DecimalFormat;

public class ShardPlaceholderExpansion extends PlaceholderExpansion {

    private final EnchantedSMP plugin;

    public ShardPlaceholderExpansion(EnchantedSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "enchantedsmp";
    }

    @Override
    public String getAuthor() {
        return "EnchantedSMP";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {

        if (player == null) {
            return "0";
        }

        if (params.equalsIgnoreCase("shards")) {
            double balance = plugin.getShardManager()
                    .getBalance(player);

            return new DecimalFormat("#,##0.##")
                    .format(balance);
        }

        return null;
    }
}
