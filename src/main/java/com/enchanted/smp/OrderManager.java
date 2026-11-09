
package com.enchanted.smp;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderManager implements Listener {

    private final EnchantedSMP plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey orderKey;

    public OrderManager(EnchantedSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.orderKey = new NamespacedKey(plugin, "player_order");

        plugin.getDataFolder().mkdirs();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openOrders(Player player) {
        OrderHolder holder = new OrderHolder();

        Inventory inventory = Bukkit.createInventory(
                holder, 54, color("&5&lPLAYER ORDERS")
        );

        holder.inventory = inventory;

        ConfigurationSection section =
                data.getConfigurationSection("orders");

        if (section != null) {
            int slot = 0;

            for (String id : section.getKeys(false)) {
                if (slot >= 45) break;

                String path = "orders." + id;

                Material material = Material.matchMaterial(
                        data.getString(path + ".material", "STONE")
                );

                if (material == null) continue;

                int amount = data.getInt(path + ".amount");
                double reward = data.getDouble(path + ".reward");

                ItemStack display = new ItemStack(material, Math.max(1, Math.min(64, amount)));
                ItemMeta meta = display.getItemMeta();

                meta.setDisplayName(color("&d&lITEM ORDER"));

                meta.setLore(List.of(
                        color("&7Item: &f" + material.name()),
                        color("&7Amount: &f" + amount),
                        color("&7Reward: &a$" + reward),
                        color("&7Ordered by: &f" +
                                data.getString(path + ".owner-name")),
                        color("&aClick to fulfil this order!")
                ));

                meta.getPersistentDataContainer().set(
                        orderKey,
                        PersistentDataType.STRING,
                        id
                );

                display.setItemMeta(meta);
                inventory.setItem(slot++, display);
            }
        }

        ItemStack info = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta infoMeta = info.getItemMeta();

        infoMeta.setDisplayName(color("&d&lCREATE AN ORDER"));

        infoMeta.setLore(List.of(
                color("&7Use the command:"),
                color("&f/order create <item> <amount> <reward>")
        ));

        info.setItemMeta(infoMeta);
        inventory.setItem(49, info);

        player.openInventory(inventory);
    }

    public boolean createOrder(
            Player player,
            Material material,
            int amount,
            double reward
    ) {
        if (material == Material.AIR || amount < 1 || amount > 2304
                || reward <= 0 || !Double.isFinite(reward)) {
            player.sendMessage(color("&cInvalid order details."));
            return false;
        }

        if (plugin.getEconomy().getBalance(player) < reward) {
            player.sendMessage(color("&cYou don't have enough money to fund this order."));
            return false;
        }

        EconomyResponse payment =
                plugin.getEconomy().withdrawPlayer(player, reward);

        if (!payment.transactionSuccess()) {
            player.sendMessage(color("&cCould not reserve your reward."));
            return false;
        }

        String id = UUID.randomUUID().toString();
        String path = "orders." + id;

        data.set(path + ".owner", player.getUniqueId().toString());
        data.set(path + ".owner-name", player.getName());
        data.set(path + ".material", material.name());
        data.set(path + ".amount", amount);
        data.set(path + ".reward", reward);

        try {
            save();
        } catch (RuntimeException e) {
            data.set(path, null);
            plugin.getEconomy().depositPlayer(player, reward);
            player.sendMessage(color("&cOrder creation failed. Your money was refunded."));
            return false;
        }

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aOrder created! Reward: &f$"
                        + reward
        ));

        return true;
    }

    @EventHandler
    public void onOrderClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof OrderHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.hasItemMeta()) return;

        String id = clicked.getItemMeta()
                .getPersistentDataContainer()
                .get(orderKey, PersistentDataType.STRING);

        if (id == null) return;

        String path = "orders." + id;

        if (!data.contains(path)) {
            player.sendMessage(color("&cThis order is no longer available."));
            openOrders(player);
            return;
        }

        String ownerId = data.getString(path + ".owner", "");

        if (player.getUniqueId().toString().equals(ownerId)) {
            player.sendMessage(color("&cYou cannot fulfil your own order."));
            return;
        }

        Material material = Material.matchMaterial(
                data.getString(path + ".material", "")
        );

        int amount = data.getInt(path + ".amount");
        double reward = data.getDouble(path + ".reward");

        if (material == null || material == Material.AIR
                || amount < 1 || amount > 2304
                || reward <= 0 || !Double.isFinite(reward)) {
            player.sendMessage(color("&cThis order is invalid."));
            return;
        }

        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] simulated = new ItemStack[contents.length];

        for (int i = 0; i < contents.length; i++) {
            simulated[i] = contents[i] == null ? null : contents[i].clone();
        }

        if (!removeItems(simulated, material, amount)) {
            player.sendMessage(color("&cYou don't have enough items."));
            return;
        }

        player.getInventory().setStorageContents(simulated);

        EconomyResponse payment =
                plugin.getEconomy().depositPlayer(player, reward);

        if (!payment.transactionSuccess()) {
            player.getInventory().setStorageContents(contents);
            player.sendMessage(color("&cPayment failed. Your items were returned."));
            return;
        }

        data.set(path, null);

        try {
            save();
        } catch (RuntimeException e) {
            data.set(path + ".material", material.name());
            data.set(path + ".amount", amount);
            data.set(path + ".reward", reward);
            plugin.getEconomy().withdrawPlayer(player, reward);
            player.getInventory().setStorageContents(contents);
            player.sendMessage(color("&cOrder completion failed. Please try again."));
            return;
        }

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aOrder fulfilled! You earned &f$"
                        + reward + "&a."
        ));

        openOrders(player);
    }

    private boolean removeItems(
            ItemStack[] contents,
            Material material,
            int amount
    ) {
        int found = 0;

        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                found += item.getAmount();
            }
        }

        if (found < amount) return false;

        int remaining = amount;

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];

            if (item == null || item.getType() != material) continue;

            int take = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - take);

            if (item.getAmount() <= 0) {
                contents[i] = null;
            }

            remaining -= take;
        }

        return true;
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save orders.yml!", e);
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static class OrderHolder implements InventoryHolder {

        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
