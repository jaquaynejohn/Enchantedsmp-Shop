
package com.enchanted.smp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class EnchantedSMP extends JavaPlugin {

    private Economy economy;

    private ShopManager shopManager;
    private ShardManager shardManager;
    private ShardShopManager shardShopManager;
    private AuctionHouseManager auctionHouseManager;
    private OrderManager orderManager;
    private SpawnerManager spawnerManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found! Economy features may not work.");
        }

        shardManager = new ShardManager(this);
        shardShopManager = new ShardShopManager(this);
        spawnerManager = new SpawnerManager(this);
        shopManager = new ShopManager(this);
        auctionHouseManager = new AuctionHouseManager(this);
        orderManager = new OrderManager(this);

        getLogger().info("EnchantedSMP has been enabled!");
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
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

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        String name = command.getName().toLowerCase();

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        switch (name) {

            case "shop":
                shopManager.openShop(player);
                return true;

            case "shard":
                handleShard(player, args);
                return true;

            case "ah":
                handleAuctionHouse(player, args);
                return true;

            case "order":
                handleOrder(player, args);
                return true;

            default:
                return false;
        }
    }

    private void handleShard(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(color(
                    "&5&lENCHANTEDSMP &8» &dYour balance: &f"
                            + shardManager.getBalance(player) + " Shards"
            ));
            return;
        }

        if (!player.hasPermission("enchantedsmp.shard.admin")) {
            player.sendMessage(color("&cYou cannot use admin shard commands."));
            return;
        }

        if (args.length != 3) {
            player.sendMessage(color(
                    "&cUsage: /shard <give|take|set> <player> <amount>"
            ));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        double amount;

        try {
            amount = Double.parseDouble(args[2]);

            if (!Double.isFinite(amount) || amount <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            player.sendMessage(color("&cEnter a valid positive amount."));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "give":
                shardManager.giveShards(target, amount);
                break;

            case "take":
                if (!shardManager.takeShards(target, amount)) {
                    player.sendMessage(color("&cThe player does not have enough shards."));
                    return;
                }
                break;

            case "set":
                shardManager.setShards(target, amount);
                break;

            default:
                player.sendMessage(color(
                        "&cUsage: /shard <give|take|set> <player> <amount>"
                ));
                return;
        }

        player.sendMessage(color(
                "&aUpdated &f" + target.getName()
                        + "&a's shard balance."
        ));
    }

    private void handleAuctionHouse(Player player, String[] args) {
        if (args.length == 0) {
            auctionHouseManager.openAuctionHouse(player);
            return;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("collect")) {
            auctionHouseManager.collectExpired(player);
            return;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            if (economy == null) {
                player.sendMessage(color("&cThe money system is unavailable."));
                return;
            }

            double price;

            try {
                price = Double.parseDouble(args[1]);

                if (!Double.isFinite(price) || price <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                player.sendMessage(color("&cEnter a valid price."));
                return;
            }

            auctionHouseManager.listItem(player, price);
            return;
        }

        player.sendMessage(color("&cAuction House commands:"));
        player.sendMessage(color("&7/ah"));
        player.sendMessage(color("&7/ah sell <price>"));
        player.sendMessage(color("&7/ah collect"));
    }

    private void handleOrder(Player player, String[] args) {
        if (args.length == 0) {
            orderManager.openOrders(player);
            return;
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("create")) {
            if (economy == null) {
                player.sendMessage(color("&cThe money system is unavailable."));
                return;
            }

            player.sendMessage(color(
                    "&cUsage: /order create <item> <amount> <reward>"
            ));
            return;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
            if (economy == null) {
                player.sendMessage(color("&cThe money system is unavailable."));
                return;
            }

            Material material = Material.matchMaterial(args[1]);

            if (material == null || material.isAir()) {
                player.sendMessage(color("&cInvalid item."));
                return;
            }

            int amount;
            double reward;

            try {
                amount = Integer.parseInt(args[2]);
                reward = Double.parseDouble(args[3]);

                if (amount <= 0 || amount > 2304
                        || !Double.isFinite(reward) || reward <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                player.sendMessage(color(
                        "&cUsage: /order create <item> <amount> <reward>"
                ));
                return;
            }

            orderManager.createOrder(player, material, amount, reward);
            return;
        }

        player.sendMessage(color(
                "&cUsage: /order or /order create <item> <amount> <reward>"
        ));
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

    public ShardShopManager getShardShopManager() {
        return shardShopManager;
    }

    public AuctionHouseManager getAuctionHouseManager() {
        return auctionHouseManager;
    }

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    @Override
    public void onDisable() {
        if (shardManager != null) shardManager.save();
        if (auctionHouseManager != null) auctionHouseManager.save();
        if (orderManager != null) orderManager.save();
        if (spawnerManager != null) spawnerManager.saveSpawners();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
