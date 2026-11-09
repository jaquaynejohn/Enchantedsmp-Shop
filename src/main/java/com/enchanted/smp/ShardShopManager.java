
package com.enchanted.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ShardShopManager implements Listener {

    private final EnchantedSMP plugin;
    private final NamespacedKey spawnerKey;

    public ShardShopManager(EnchantedSMP plugin) {
        this.plugin = plugin;
        this.spawnerKey = new NamespacedKey(plugin, "shard_spawner");

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openShop(Player player) {
        String title = plugin.getConfig().getString(
                "shop.categories.shard_shop.title",
                "&d&lSPAWNER SHOP"
        );

        int size = plugin.getConfig().getInt(
                "shop.categories.shard_shop.size", 54
        );

        ShopHolder holder = new ShopHolder();

        Inventory inventory = Bukkit.createInventory(
                holder, size, color(title)
        );

        holder.inventory = inventory;

        var section = plugin.getConfig().getConfigurationSection(
                "shop.categories.shard_shop.spawners"
        );

        if (section == null) {
            player.sendMessage(color("&cSpawner shop is not configured!"));
            return;
        }

        int slot = 0;

        for (String id : section.getKeys(false)) {
            if (slot >= size) break;

            String path = "shop.categories.shard_shop.spawners." + id;

            try {
                EntityType mob = EntityType.valueOf(
                        plugin.getConfig().getString(path + ".mob", "")
                                .toUpperCase()
                );

                double price = plugin.getConfig().getDouble(
                        path + ".price", -1
                );

                if (price <= 0) continue;

                ItemStack item = createSpawner(mob);

                ItemMeta meta = item.getItemMeta();

                meta.setDisplayName(color(
                        "&d&l" + formatName(id)
                ));

                List<String> lore = new ArrayList<>();
                lore.add(color("&7Mob: &f" + formatName(mob.name())));
                lore.add(color("&7Price: &d" + price + " Shards"));
                lore.add(color("&aClick to purchase!"));

                meta.setLore(lore);

                meta.getPersistentDataContainer().set(
                        spawnerKey,
                        PersistentDataType.STRING,
                        id
                );

                item.setItemMeta(meta);
                inventory.setItem(slot++, item);

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(
                        "Invalid spawner mob configured: " + id
                );
            }
        }

        player.openInventory(inventory);
    }

    private ItemStack createSpawner(EntityType mob) {
        ItemStack item = new ItemStack(Material.SPAWNER);

        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();

        BlockState state = meta.getBlockState();

        if (!(state instanceof CreatureSpawner spawner)) {
            throw new IllegalStateException(
                    "Could not create spawner block state!"
            );
        }

        spawner.setSpawnedType(mob);
        meta.setBlockState(spawner);
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.hasItemMeta()) return;

        String id = clicked.getItemMeta()
                .getPersistentDataContainer()
                .get(spawnerKey, PersistentDataType.STRING);

        if (id == null) return;

        String path = "shop.categories.shard_shop.spawners." + id;

        double price = plugin.getConfig().getDouble(
                path + ".price", -1
        );

        String mobName = plugin.getConfig().getString(path + ".mob", "");

        if (price <= 0 || mobName == null || mobName.isBlank()) {
            player.sendMessage(color("&cThis spawner is unavailable."));
            return;
        }

        if (plugin.getShardManager().getBalance(player) < price) {
            player.sendMessage(color("&cYou don't have enough shards!"));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(color("&cYour inventory is full!"));
            return;
        }

        try {
            EntityType mob = EntityType.valueOf(mobName.toUpperCase());
            ItemStack spawnerItem = createSpawner(mob);

            if (!plugin.getShardManager().takeShards(player, price)) {
                player.sendMessage(color("&cPurchase failed!"));
                return;
            }

            player.getInventory().addItem(spawnerItem);

            player.sendMessage(color(
                    "&5&lENCHANTEDSMP &8» &aPurchased "
                            + formatName(id)
                            + " Spawner for &d"
                            + price + " Shards&a!"
            ));

        } catch (IllegalArgumentException e) {
            player.sendMessage(color("&cThat spawner type is not supported."));
        }
    }

    private String formatName(String name) {
        String[] words = name.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (result.length() > 0) result.append(" ");

            result.append(
                    Character.toUpperCase(word.charAt(0))
            ).append(word.substring(1));
        }

        return result.toString();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static class ShopHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
