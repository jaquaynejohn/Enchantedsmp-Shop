
package com.enchanted.smp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
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

    public void openShop(Player player) {
        openMainMenu(player);
    }

    public void openMainMenu(Player player) {
        MenuHolder holder = new MenuHolder("main");

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

        MenuHolder holder = new MenuHolder("editor");

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

    private void openCrystalShop(Player player) {
        MenuHolder holder = new MenuHolder("cpvp");

        Inventory inventory = Bukkit.createInventory(
                holder, 54, color("&5&lCRYSTAL PVP SHOP")
        );

        holder.setInventory(inventory);

        String base = "shop.categories.cpvp.loadout.";

        String[] armor = {
                "armor.helmet",
                "armor.chestplate",
                "armor.leggings",
                "armor.boots"
        };

        int[] armorSlots = {10, 12, 14, 16};

        for (int i = 0; i < armor.length; i++) {
            addConfiguredItem(
                    inventory, holder, armorSlots[i],
                    base + armor[i]
            );
        }

        String[] items = {
                "items.end_crystal",
                "items.obsidian",
                "items.totem",
                "items.ender_pearl",
                "items.golden_apple",
                "items.experience_bottle"
        };

        int[] itemSlots = {19, 21, 23, 25, 28, 30};

        for (int i = 0; i < items.length; i++) {
            addConfiguredItem(
                    inventory, holder, itemSlots[i],
                    base + items[i]
            );
        }

        // Extra CPvP weapons with editable config prices.
        addExtraItem(inventory, holder, 32,
                "items.netherite_sword",
                Material.NETHERITE_SWORD, 1, 10000);

        addExtraItem(inventory, holder, 34,
                "items.netherite_pickaxe",
                Material.NETHERITE_PICKAXE, 1, 8000);

        addCategory(inventory, 49, Material.EMERALD_BLOCK,
                "&a&lBUY FULL LOADOUT",
                "&7Purchase every CPvP item together");

        addCategory(inventory, 45, Material.ARROW,
                "&c&lBACK", "&7Return to the main shop");

        player.openInventory(inventory);
    }

    private void addConfiguredItem(
            Inventory inventory,
            MenuHolder holder,
            int slot,
            String path
    ) {
        if (!plugin.getConfig().contains(path + ".material")) {
            return;
        }

        Material material = Material.matchMaterial(
                plugin.getConfig().getString(path + ".material", "")
        );

        if (material == null || material.isAir()) {
            return;
        }

        int amount = plugin.getConfig().getInt(path + ".amount", 1);
        double price = plugin.getConfig().getDouble(path + ".price", 0);

        addShopItem(inventory, holder, slot,
                material, amount, price, path);
    }

    private void addExtraItem(
            Inventory inventory,
            MenuHolder holder,
            int slot,
            String configPath,
            Material defaultMaterial,
            int defaultAmount,
            double defaultPrice
    ) {
        String path = "shop.categories.cpvp.loadout." + configPath;

        Material material = Material.matchMaterial(
                plugin.getConfig().getString(
                        path + ".material", defaultMaterial.name()
                )
        );

        if (material == null || material.isAir()) {
            material = defaultMaterial;
        }

        int amount = plugin.getConfig().getInt(
                path + ".amount", defaultAmount
        );

        double price = plugin.getConfig().getDouble(
                path + ".price", defaultPrice
        );

        addShopItem(inventory, holder, slot,
                material, amount, price, path);
    }

    private void addShopItem(
            Inventory inventory,
            MenuHolder holder,
            int slot,
            Material material,
            int amount,
            double price,
            String path
    ) {
        if (amount <= 0 || amount > material.getMaxStackSize()
                || !Double.isFinite(price) || price <= 0) {
            return;
        }

        ItemStack item = createEnchantedItem(material, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setLore(List.of(
                color("&7Amount: &f" + amount),
                color("&7Price: &a$" + String.format("%.0f", price)),
                color("&eClick to purchase!")
        ));

        item.setItemMeta(meta);
        inventory.setItem(slot, item);

        holder.entries.put(slot,
                new ShopEntry(material, amount, price, path));
    }

    private ItemStack createEnchantedItem(Material material, int amount) {
        ItemStack item = new ItemStack(material, amount);

        if (material.name().contains("NETHERITE_HELMET")
                || material.name().contains("NETHERITE_CHESTPLATE")
                || material.name().contains("NETHERITE_LEGGINGS")
                || material.name().contains("NETHERITE_BOOTS")) {

            enchant(item, "protection", 4);
            enchant(item, "unbreaking", 3);
            enchant(item, "mending", 1);
        }

        if (material == Material.NETHERITE_SWORD) {
            enchant(item, "sharpness", 5);
            enchant(item, "unbreaking", 3);
            enchant(item, "fire_aspect", 2);
            enchant(item, "looting", 3);
            enchant(item, "mending", 1);
        }

        if (material == Material.NETHERITE_PICKAXE) {
            enchant(item, "efficiency", 5);
            enchant(item, "unbreaking", 3);
            enchant(item, "fortune", 3);
            enchant(item, "mending", 1);
        }

        return item;
    }

    private void enchant(ItemStack item, String name, int level) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(
                NamespacedKey.minecraft(name)
        );

        if (enchantment == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(enchantment, level, true);
        item.setItemMeta(meta);
    }

    private void purchase(Player player, ShopEntry entry) {
        if (plugin.getEconomy() == null) {
            player.sendMessage(color("&cMoney system unavailable!"));
            return;
        }

        ItemStack item = createEnchantedItem(
                entry.material, entry.amount
        );

        // Check inventory space before charging.
        Inventory temporary = Bukkit.createInventory(null, 36);
        temporary.setContents(player.getInventory().getStorageContents());

        if (!temporary.addItem(item.clone()).isEmpty()) {
            player.sendMessage(color("&cYour inventory is full!"));
            return;
        }

        if (plugin.getEconomy().getBalance(player) < entry.price) {
            player.sendMessage(color("&cYou do not have enough money!"));
            return;
        }

        var response = plugin.getEconomy().withdrawPlayer(
                player, entry.price
        );

        if (!response.transactionSuccess()) {
            player.sendMessage(color("&cPayment failed!"));
            return;
        }

        player.getInventory().addItem(item);

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aPurchased "
                        + entry.amount + "x "
                        + entry.material.name()
                        + " for $" + String.format("%.0f", entry.price)
        ));
    }

    private void purchaseFullLoadout(Player player, MenuHolder holder) {
        if (plugin.getEconomy() == null) {
            player.sendMessage(color("&cMoney system unavailable!"));
            return;
        }

        double total = 0;
        Inventory temporary = Bukkit.createInventory(null, 36);

        temporary.setContents(player.getInventory().getStorageContents());

        for (ShopEntry entry : holder.entries.values()) {
            total += entry.price;

            ItemStack item = createEnchantedItem(
                    entry.material, entry.amount
            );

            if (!temporary.addItem(item).isEmpty()) {
                player.sendMessage(color("&cYour inventory is too full!"));
                return;
            }
        }

        if (!Double.isFinite(total)) {
            player.sendMessage(color("&cInvalid loadout price."));
            return;
        }

        if (plugin.getEconomy().getBalance(player) < total) {
            player.sendMessage(color(
                    "&cYou need $" + String.format("%.0f", total)
                            + " to buy the full loadout!"
            ));
            return;
        }

        var response = plugin.getEconomy().withdrawPlayer(player, total);

        if (!response.transactionSuccess()) {
            player.sendMessage(color("&cPayment failed!"));
            return;
        }

        for (ShopEntry entry : holder.entries.values()) {
            player.getInventory().addItem(
                    createEnchantedItem(entry.material, entry.amount)
            );
        }

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aFull CPvP loadout purchased for $"
                        + String.format("%.0f", total) + "!"
        ));
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
        if (!(event.getInventory().getHolder()
                instanceof MenuHolder holder)) {
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

        if (holder.type.equals("editor")) {
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

        if (holder.type.equals("cpvp")) {
            if (slot == 45) {
                openMainMenu(player);
                return;
            }

            if (slot == 49) {
                purchaseFullLoadout(player, holder);
                return;
            }

            ShopEntry entry = holder.entries.get(slot);

            if (entry != null) {
                purchase(player, entry);
            }
            return;
        }

        if (slot == 8 && player.isOp()) {
            openEditorMenu(player);
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

        if (category.equals("cpvp")) {
            openCrystalShop(player);
            return;
        }

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &d"
                        + category.replace("_", " ")
                        + " shop will be added next!"
        ));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static class ShopEntry {
        private final Material material;
        private final int amount;
        private final double price;
        private final String path;

        ShopEntry(Material material, int amount, double price, String path) {
            this.material = material;
            this.amount = amount;
            this.price = price;
            this.path = path;
        }
    }

    private static class MenuHolder implements InventoryHolder {
        private final String type;
        private final Map<Integer, ShopEntry> entries = new LinkedHashMap<>();
        private Inventory inventory;

        MenuHolder(String type) {
            this.type = type;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
