
package com.enchanted.smp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        shardManager = new ShardManager(this);
        shardShopManager = new ShardShopManager(this);
        shopManager = new ShopManager(this);
        auctionHouseManager = new AuctionHouseManager(this);
        orderManager = new OrderManager(this);

        getCommand("shop").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            shopManager.openMainMenu(player);
            return true;
        });

        getCommand("shard").setExecutor((sender, command, label, args) -> {

            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can use this command.");
                    return true;
                }

                sender.sendMessage(color(
                        "&5&lENCHANTEDSMP &8» &dYour Shards: &f"
                                + shardManager.getBalance(player)
                ));
                return true;
            }

            if (!sender.hasPermission("enchantedsmp.shard.admin")) {
                sender.sendMessage(color("&cYou cannot manage shards."));
                return true;
            }

            if (args.length != 3) {
                sender.sendMessage(color(
                        "&cUsage: /shard <give|take|set> <player> <amount>"
                ));
                return true;
            }

            String action = args[0].toLowerCase();
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(color("&cPlayer not found."));
                return true;
            }

            double amount;

            try {
                amount = Double.parseDouble(args[2]);

                if (!Double.isFinite(amount) || amount <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(color("&cEnter a valid positive amount."));
                return true;
            }

            switch (action) {
                case "give":
                    shardManager.giveShards(target, amount);
                    break;

                case "take":
                    if (!shardManager.takeShards(target, amount)) {
                        sender.sendMessage(color("&cNot enough shards!"));
                        return true;
                    }
                    break;

                case "set":
                    shardManager.setShards(target, amount);
                    break;

                default:
                    sender.sendMessage(color(
                            "&cUsage: /shard <give|take|set> <player> <amount>"
                    ));
                    return true;
            }

            sender.sendMessage(color(
                    "&5&lENCHANTEDSMP &8» &dSuccessfully "
                            + action + " &f" + amount
                            + " &dshards for &f" + target.getName() + "&d."
            ));

            if (target.isOnline()) {
                target.getPlayer().sendMessage(color(
                        "&5&lENCHANTEDSMP &8» &dYour shard balance is now &f"
                                + shardManager.getBalance(target) + "&d."
                ));
            }

            return true;
        });

        getCommand("ah").setExecutor((sender, command, label, args) -> {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            if (args.length == 0) {
                auctionHouseManager.openAuctionHouse(player);
                return true;
            }

            if (args[0].equalsIgnoreCase("sell")) {
                if (args.length != 2) {
                    player.sendMessage(color("&cUsage: /ah sell <price>"));
                    return true;
                }

                double price;

                try {
                    price = Double.parseDouble(args[1]);

                    if (!Double.isFinite(price) || price <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(color("&cEnter a valid price."));
                    return true;
                }

                auctionHouseManager.listItem(player, price);
                return true;
            }

            player.sendMessage(color("&cUsage: /ah or /ah sell <price>"));
            return true;
        });

        getCommand("order").setExecutor((sender, command, label, args) -> {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            if (args.length == 0) {
                orderManager.openOrders(player);
                return true;
            }

            if (args[0].equalsIgnoreCase("create")) {

                if (args.length != 4) {
                    player.sendMessage(color(
                            "&cUsage: /order create <item> <amount> <reward>"
                    ));
                    return true;
                }

                Material material = Material.matchMaterial(args[1]);

                if (material == null || material == Material.AIR) {
                    player.sendMessage(color("&cInvalid item name."));
                    return true;
                }

                int amount;
                double reward;

                try {
                    amount = Integer.parseInt(args[2]);
                    reward = Double.parseDouble(args[3]);

                    if (amount < 1 || amount > 2304
                            || !Double.isFinite(reward) || reward <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(color("&cEnter a valid amount and reward."));
                    return true;
                }

                orderManager.createOrder(player, material, amount, reward);
                return true;
            }

            player.sendMessage(color(
                    "&cUsage: /order or /order create <item> <amount> <reward>"
            ));

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

        if (provider == null) return false;

        economy = provider.getProvider();
        return economy != null;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
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

    @Override
    public void onDisable() {
        if (shardManager != null) {
            shardManager.save();
        }

        if (auctionHouseManager != null) {
            auctionHouseManager.save();
        }

        if (orderManager != null) {
            orderManager.save();
        }
    }
}
