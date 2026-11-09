
package com.enchanted.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopManager implements Listener {

    private final EnchantedSMP plugin;
    private final Map<Integer, String> categories = new LinkedHashMap<>();

    public ShopManager(EnchantedSMP plugin) {
        this.plugin = plugin;

        categories.put(10, "building");
        categories.put(12, "redstone");
        categories.put(14, "traps");
        categories.put(16, "end");
        categories.put(19, "nether");
        categories.put(21, "cpvp");
        categories.put(23, "mob_drops");
        categories.put(25, "farming");
        categories.put(31, "shard_shop");

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMainMenu(Player player) {
        MenuHolder holder = new MenuHolder(false);

        Inventory inventory = Bukkit.createInventory(
                holder, 54, color("&5&lENCHANTEDSMP SHOP")
        );

        holder.setInventory(inventory);

        addCategory(inventory, 10, Material.BRICKS,
                "&6&lBUILDING", "&7Building supplies");

        addCategory(inventory, 12, Material.REDSTONE,
                "&c&lREDSTONE", "&7Redstone components");

        addCategory(inventory, 14, Material.TRIPWIRE_HOOK,
                "&4&lTRAPS", "&7Trap supplies");

        addCategory(inventory, 16, Material.ENDER_PEARL,
                "&5&lEND", "&7End supplies");

        addCategory(inventory, 19, Material.BLAZE_ROD,
                "&6&lNETHER", "&7Nether supplies");

        addCategory(inventory, 21, Material.END_CRYSTAL,
                "&d&lCRYSTAL PVP", "&7CPvP equipment and loadouts");

        addCategory(inventory, 23, Material.GUNPOWDER,
                "&8&lMOB DROPS", "&7Useful mob drops");

        addCategory(inventory, 25, Material.SUGAR_CANE,
                "&a&lFARMING", "&7Farming supplies");

        addCategory(inventory, 31, Material.AMETHYST_SHARD,
                "&d&lSHARD SHOP", "&7Buy mob spawners with shards");

        if (player.isOp()) {
            addCategory(inventory, 8, Material.ENCHANTED_BOOK,
                    "&d&lSHOP EDITOR", "&7Click to edit the shop");
        }

        player.openInventory(inventory);
    }

    public void openEditorMenu(Player player) {
        if (!player.isOp()) {
            player.sendMessage(color("&cYou cannot access the shop editor."));
            return;
        }

        MenuHolder holder = new MenuHolder(true);

        Inventory inventory = Bukkit.createInventory(
                holder, 54, color("&5&lSHOP EDITOR")
        );

        holder.setInventory(inventory);

        addCategory(inventory, 20, Material.CHEST,
                "&a&lEDIT CATEGORIES", "&7Manage shop categories");

        addCategory(inventory, 22, Material.DIAMOND_SWORD,
                "&b&lEDIT ITEMS", "&7Manage shop items");

        addCategory(inventory, 24, Material.GOLD_INGOT,
                "&6&lEDIT PRICES", "&7Change item prices");

        addCategory(inventory, 49, Material.ARROW,
                "&c&lBACK", "&7Return to the shop");

        player.openInventory(inventory);
    }

    private void addCategory(
            Inventory inventory,
            int slot,
            Material material,
            String name,
            String description
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(color(name));

        meta.setLore(List.of(
                color(description),
                color("&7Click to open!")
        ));

        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
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

        if (holder.isEditor()) {
            if (!player.isOp()) {
                player.closeInventory();
                return;
            }

            if (slot == 49) {
                openMainMenu(player);
            } else {
                player.sendMessage(color(
                        "&dShop editor tools will be added next!"
                ));
            }

            return;
        }

        if (slot == 8) {
            if (player.isOp()) {
                openEditorMenu(player);
            }
            return;
        }

        String category = categories.get(slot);

        if (category == null) {
            return;
        }

        if (category.equals("shard_shop")) {
            plugin.getShardShopManager().openShop(player);
            return;
        }

        player.sendMessage(color(
                "&5&lEnchantedSMP &8» &d"
                        + category.replace("_", " ")
                        + " shop is coming next!"
        ));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static class MenuHolder implements InventoryHolder {

        private final boolean editor;
        private Inventory inventory;

        MenuHolder(boolean editor) {
            this.editor = editor;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        boolean isEditor() {
            return editor;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
