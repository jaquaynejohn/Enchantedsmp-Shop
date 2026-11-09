
package com.enchanted.smp;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopManager implements Listener {

    private final EnchantedSMP plugin;

    private final Map<Integer, String> categories = new LinkedHashMap<>();
    private final Map<Integer, String> editorCategories = new LinkedHashMap<>();
    private final Map<UUID, PendingEdit> pendingEdits = new ConcurrentHashMap<>();

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

        editorCategories.put(10, "building");
        editorCategories.put(12, "redstone");
        editorCategories.put(14, "traps");
        editorCategories.put(16, "end");
        editorCategories.put(19, "nether");
        editorCategories.put(21, "cpvp");
        editorCategories.put(23, "mob_drops");
        editorCategories.put(25, "farming");

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
                    "&d&lSHOP EDITOR", "&7Manage shop categories and items");
        }

        player.openInventory(inventory);
    }

    private void openCategory(Player player, String category) {
        String path = "shop.categories." + category + ".items";
        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection(path);

        if (section == null) {
            player.sendMessage(color("&cNo items are configured for this category."));
            return;
        }

        MenuHolder holder = new MenuHolder("category");
        holder.category = category;

        String title = plugin.getConfig().getString(
                "shop.categories." + category + ".title",
                "&5&l" + category.replace("_", " ").toUpperCase()
        );

        int size = validSize(plugin.getConfig().getInt(
                "shop.categories." + category + ".size", 54
        ));

        Inventory inventory = Bukkit.createInventory(holder, size, color(title));
        holder.setInventory(inventory);

        int slot = 0;

        for (String key : section.getKeys(false)) {
            if (slot >= size - 9) break;

            String itemPath = path + "." + key;
            Material material = getMaterial(itemPath + ".material");

            if (material == null) continue;

            int amount = plugin.getConfig().getInt(itemPath + ".amount", 1);
            double price = plugin.getConfig().getDouble(itemPath + ".price", 0);

            if (!validItem(material, amount, price)) continue;

            addShopItem(inventory, holder, slot,
                    material, amount, price, itemPath);
            slot++;
        }

        addCategory(inventory, size - 5, Material.ARROW,
                "&c&lBACK", "&7Return to the main shop");

        player.openInventory(inventory);
    }

    // ==========================================
    //                 SHOP EDITOR
    // ==========================================

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

        addCategory(inventory, 10, Material.BRICKS,
                "&6&lBUILDING", "&7Edit building shop");
        addCategory(inventory, 12, Material.REDSTONE,
                "&c&lREDSTONE", "&7Edit redstone shop");
        addCategory(inventory, 14, Material.TRIPWIRE_HOOK,
                "&4&lTRAPS", "&7Edit traps shop");
        addCategory(inventory, 16, Material.ENDER_PEARL,
                "&5&lEND", "&7Edit End shop");
        addCategory(inventory, 19, Material.BLAZE_ROD,
                "&6&lNETHER", "&7Edit Nether shop");
        addCategory(inventory, 21, Material.END_CRYSTAL,
                "&d&lCPVP", "&7Edit CPvP items");
        addCategory(inventory, 23, Material.GUNPOWDER,
                "&8&lMOB DROPS", "&7Edit mob drops shop");
        addCategory(inventory, 25, Material.SUGAR_CANE,
                "&a&lFARMING", "&7Edit farming shop");

        addCategory(inventory, 49, Material.ARROW,
                "&c&lBACK", "&7Return to the main shop");

        player.openInventory(inventory);
    }

    private String getEditorItemsPath(String category) {
        if (category.equals("cpvp")) {
            return "shop.categories.cpvp.loadout.items";
        }

        return "shop.categories." + category + ".items";
    }

    private void openEditorItems(Player player, String category) {
        if (!player.isOp()) return;

        String path = getEditorItemsPath(category);
        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection(path);

        MenuHolder holder = new MenuHolder("editor_items");
        holder.category = category;

        Inventory inventory = Bukkit.createInventory(
                holder, 54,
                color("&5&lEDIT " + category.replace("_", " ").toUpperCase())
        );

        holder.setInventory(inventory);

        if (section != null) {
            int slot = 0;

            for (String key : section.getKeys(false)) {
                if (slot >= 45) break;

                String itemPath = path + "." + key;
                Material material = getMaterial(itemPath + ".material");

                if (material == null) continue;

                int amount = plugin.getConfig().getInt(itemPath + ".amount", 1);
                double price = plugin.getConfig().getDouble(itemPath + ".price", 0);

                ItemStack display = new ItemStack(material);
                ItemMeta meta = display.getItemMeta();

                meta.setDisplayName(color("&e" + key));
                meta.setLore(List.of(
                        color("&7Material: &f" + material.name()),
                        color("&7Amount: &f" + amount),
                        color("&7Price: &a$" + price),
                        color("&eClick to edit this item")
                ));

                display.setItemMeta(meta);
                inventory.setItem(slot, display);

                holder.entries.put(slot,
                        new ShopEntry(material, amount, price, itemPath));

                slot++;
            }
        }

        addCategory(inventory, 45, Material.EMERALD,
                "&a&lADD ITEM", "&7Add a new shop item");

        addCategory(inventory, 49, Material.ARROW,
                "&c&lBACK", "&7Return to editor categories");

        player.openInventory(inventory);
    }

    private void openItemEditor(Player player, String category, ShopEntry entry) {
        if (!player.isOp()) return;

        MenuHolder holder = new MenuHolder("editor_item");
        holder.category = category;
        holder.selectedPath = entry.path;

        Inventory inventory = Bukkit.createInventory(
                holder, 54, color("&5&lEDIT SHOP ITEM")
        );

        holder.setInventory(inventory);

        ItemStack display = new ItemStack(entry.material);
        ItemMeta meta = display.getItemMeta();

        meta.setDisplayName(color("&eCURRENT ITEM"));
        meta.setLore(List.of(
                color("&7Material: &f" + entry.material.name()),
                color("&7Amount: &f" + entry.amount),
                color("&7Price: &a$" + entry.price)
        ));

        display.setItemMeta(meta);
        inventory.setItem(4, display);

        addCategory(inventory, 20, Material.ANVIL,
                "&b&lCHANGE MATERIAL", "&7Change the item type");

        addCategory(inventory, 22, Material.CHEST,
                "&e&lCHANGE AMOUNT", "&7Change how many are sold");

        addCategory(inventory, 24, Material.GOLD_INGOT,
                "&6&lCHANGE PRICE", "&7Change the purchase price");

        addCategory(inventory, 31, Material.BARRIER,
                "&c&lDELETE ITEM", "&7Permanently remove this item");

        addCategory(inventory, 49, Material.ARROW,
                "&c&lBACK", "&7Return to the item list");

        player.openInventory(inventory);
    }

    private void beginPrompt(
            Player player, String action, String category, String path
    ) {
        PendingEdit edit = new PendingEdit(action, category, path);
        pendingEdits.put(player.getUniqueId(), edit);

        player.closeInventory();

        switch (action) {
            case "add_material" ->
                    player.sendMessage(color("&aEnter the new item's material name. Example: DIAMOND_BLOCK"));
            case "edit_material" ->
                    player.sendMessage(color("&aEnter the new material name. Example: DIAMOND_BLOCK"));
            case "edit_amount" ->
                    player.sendMessage(color("&aEnter the new item amount."));
            case "edit_price" ->
                    player.sendMessage(color("&aEnter the new price."));
        }

        player.sendMessage(color("&7Type &ccancel &7to stop."));
    }

    @EventHandler
    public void onEditorChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        PendingEdit edit = pendingEdits.get(id);
        if (edit == null) return;

        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                pendingEdits.remove(id);
                return;
            }

            if (message.equalsIgnoreCase("cancel")) {
                pendingEdits.remove(id);
                player.sendMessage(color("&cShop edit cancelled."));
                openEditorItems(player, edit.category);
                return;
            }

            handleEditorInput(player, edit, message);
        });
    }

    private void handleEditorInput(
            Player player, PendingEdit edit, String message
    ) {
        if (!player.isOp()) {
            pendingEdits.remove(player.getUniqueId());
            return;
        }

        if (edit.action.equals("add_material")) {
            Material material = Material.matchMaterial(message);

            if (material == null || material.isAir()) {
                player.sendMessage(color("&cInvalid material. Try again or type cancel."));
                return;
            }

            edit.material = material;
            edit.action = "add_amount";
            player.sendMessage(color("&aEnter the amount to sell."));
            return;
        }

        if (edit.action.equals("add_amount")) {
            Integer amount = parseAmount(message, edit.material);

            if (amount == null) {
                player.sendMessage(color("&cInvalid amount. Enter a valid stack amount."));
                return;
            }

            edit.amount = amount;
            edit.action = "add_price";
            player.sendMessage(color("&aEnter the item's price."));
            return;
        }

        if (edit.action.equals("add_price")) {
            Double price = parsePrice(message);

            if (price == null) {
                player.sendMessage(color("&cInvalid price. Enter a number above 0."));
                return;
            }

            String key = edit.material.name().toLowerCase();
            String path = getEditorItemsPath(edit.category) + "." + key;

            if (plugin.getConfig().contains(path)) {
                player.sendMessage(color("&cThat item already exists in this category."));
                return;
            }

            plugin.getConfig().set(path + ".material", edit.material.name());
            plugin.getConfig().set(path + ".amount", edit.amount);
            plugin.getConfig().set(path + ".price", price);
            plugin.saveConfig();

            pendingEdits.remove(player.getUniqueId());

            player.sendMessage(color("&aItem added and saved to config.yml!"));
            openEditorItems(player, edit.category);
            return;
        }

        if (edit.action.equals("edit_material")) {
            Material material = Material.matchMaterial(message);

            if (material == null || material.isAir()) {
                player.sendMessage(color("&cInvalid material. Try again or type cancel."));
                return;
            }

            int currentAmount = plugin.getConfig().getInt(
                    edit.path + ".amount", 1
            );

            if (currentAmount > material.getMaxStackSize()) {
                player.sendMessage(color("&cThe current amount is too large for that material."));
                return;
            }

            plugin.getConfig().set(edit.path + ".material", material.name());
            saveEdit(player, edit);
            return;
        }

        if (edit.action.equals("edit_amount")) {
            Material material = getMaterial(edit.path + ".material");
            Integer amount = parseAmount(message, material);

            if (amount == null) {
                player.sendMessage(color("&cInvalid amount. Try again or type cancel."));
                return;
            }

            plugin.getConfig().set(edit.path + ".amount", amount);
            saveEdit(player, edit);
            return;
        }

        if (edit.action.equals("edit_price")) {
            Double price = parsePrice(message);

            if (price == null) {
                player.sendMessage(color("&cInvalid price. Try again or type cancel."));
                return;
            }

            plugin.getConfig().set(edit.path + ".price", price);
            saveEdit(player, edit);
        }
    }

    private void saveEdit(Player player, PendingEdit edit) {
        plugin.saveConfig();
        pendingEdits.remove(player.getUniqueId());

        player.sendMessage(color("&aShop item updated and saved!"));
        openEditorItems(player, edit.category);
    }

    private Integer parseAmount(String message, Material material) {
        if (material == null) return null;

        try {
            int amount = Integer.parseInt(message);

            if (amount < 1 || amount > material.getMaxStackSize()) {
                return null;
            }

            return amount;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double parsePrice(String message) {
        try {
            double price = Double.parseDouble(message);

            if (!Double.isFinite(price) || price <= 0) {
                return null;
            }

            return price;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void deleteItem(Player player, MenuHolder holder) {
        if (!player.isOp()) return;

        plugin.getConfig().set(holder.selectedPath, null);
        plugin.saveConfig();

        player.sendMessage(color("&aItem deleted and config.yml saved!"));
        openEditorItems(player, holder.category);
    }

    // ==========================================
    //                CPVP SHOP
    // ==========================================

    private void openCrystalShop(Player player) {
        MenuHolder holder = new MenuHolder("cpvp");

        Inventory inventory = Bukkit.createInventory(
                holder, 54, color("&5&lCRYSTAL PVP SHOP")
        );

        holder.setInventory(inventory);

        String base = "shop.categories.cpvp.loadout.";

        String[] armor = {
                "armor.helmet", "armor.chestplate",
                "armor.leggings", "armor.boots"
        };

        int[] armorSlots = {10, 12, 14, 16};

        for (int i = 0; i < armor.length; i++) {
            addConfiguredItem(inventory, holder,
                    armorSlots[i], base + armor[i]);
        }

        String[] items = {
                "items.end_crystal", "items.obsidian",
                "items.totem", "items.ender_pearl",
                "items.golden_apple", "items.experience_bottle"
        };

        int[] itemSlots = {19, 21, 23, 25, 28, 30};

        for (int i = 0; i < items.length; i++) {
            addConfiguredItem(inventory, holder,
                    itemSlots[i], base + items[i]);
        }

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
            Inventory inventory, MenuHolder holder,
            int slot, String path
    ) {
        if (!plugin.getConfig().contains(path + ".material")) return;

        Material material = getMaterial(path + ".material");
        if (material == null) return;

        int amount = plugin.getConfig().getInt(path + ".amount", 1);
        double price = plugin.getConfig().getDouble(path + ".price", 0);

        addShopItem(inventory, holder, slot,
                material, amount, price, path);
    }

    private void addExtraItem(
            Inventory inventory, MenuHolder holder,
            int slot, String configPath,
            Material fallback, int defaultAmount, double defaultPrice
    ) {
        String path = "shop.categories.cpvp.loadout." + configPath;

        Material material = getMaterial(path + ".material");
        if (material == null) material = fallback;

        int amount = plugin.getConfig().getInt(path + ".amount", defaultAmount);
        double price = plugin.getConfig().getDouble(path + ".price", defaultPrice);

        addShopItem(inventory, holder, slot,
                material, amount, price, path);
    }

    // ==========================================
    //              SHOP ITEM DISPLAY
    // ==========================================

    private void addShopItem(
            Inventory inventory, MenuHolder holder,
            int slot, Material material,
            int amount, double price, String path
    ) {
        if (!validItem(material, amount, price)) return;

        ItemStack item = createEnchantedItem(material, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setLore(List.of(
                color("&7Amount: &f" + amount),
                color("&7Price: &a$" + String.format("%.2f", price)),
                color("&eClick to purchase!")
        ));

        item.setItemMeta(meta);
        inventory.setItem(slot, item);

        holder.entries.put(slot,
                new ShopEntry(material, amount, price, path));
    }

    private ItemStack createEnchantedItem(Material material, int amount) {
        ItemStack item = new ItemStack(material, amount);

        if (material == Material.NETHERITE_HELMET
                || material == Material.NETHERITE_CHESTPLATE
                || material == Material.NETHERITE_LEGGINGS
                || material == Material.NETHERITE_BOOTS) {
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

        if (enchantment == null) return;

        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(enchantment, level, true);
        item.setItemMeta(meta);
    }

    // ==========================================
    //                 PURCHASES
    // ==========================================

    private boolean hasInventorySpace(Player player, List<ItemStack> items) {
        Inventory temporary = Bukkit.createInventory(null, 36);
        temporary.setContents(player.getInventory().getStorageContents());

        for (ItemStack item : items) {
            if (!temporary.addItem(item.clone()).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private void purchase(Player player, ShopEntry entry) {
        if (plugin.getEconomy() == null) {
            player.sendMessage(color("&cMoney system unavailable!"));
            return;
        }

        ItemStack item = createEnchantedItem(entry.material, entry.amount);

        if (!hasInventorySpace(player, List.of(item))) {
            player.sendMessage(color("&cYour inventory is full!"));
            return;
        }

        if (plugin.getEconomy().getBalance(player) < entry.price) {
            player.sendMessage(color("&cYou do not have enough money!"));
            return;
        }

        var response = plugin.getEconomy().withdrawPlayer(player, entry.price);

        if (!response.transactionSuccess()) {
            player.sendMessage(color("&cPayment failed!"));
            return;
        }

        player.getInventory().addItem(item);

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aPurchased "
                        + entry.amount + "x " + entry.material.name()
                        + " for $" + String.format("%.2f", entry.price)
        ));
    }

    private void purchaseFullLoadout(Player player, MenuHolder holder) {
        if (plugin.getEconomy() == null) {
            player.sendMessage(color("&cMoney system unavailable!"));
            return;
        }

        double total = 0;
        List<ItemStack> items = new ArrayList<>();

        for (ShopEntry entry : holder.entries.values()) {
            total += entry.price;
            items.add(createEnchantedItem(entry.material, entry.amount));
        }

        if (!Double.isFinite(total) || items.isEmpty()) {
            player.sendMessage(color("&cThe loadout is not configured correctly."));
            return;
        }

        if (!hasInventorySpace(player, items)) {
            player.sendMessage(color("&cYour inventory is too full!"));
            return;
        }

        if (plugin.getEconomy().getBalance(player) < total) {
            player.sendMessage(color(
                    "&cYou need $" + String.format("%.2f", total)
                            + " to buy the full loadout!"
            ));
            return;
        }

        var response = plugin.getEconomy().withdrawPlayer(player, total);

        if (!response.transactionSuccess()) {
            player.sendMessage(color("&cPayment failed!"));
            return;
        }

        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aFull CPvP loadout purchased for $"
                        + String.format("%.2f", total) + "!"
        ));
    }

    // ==========================================
    //                 GUI HELPERS
    // ==========================================

    private void addCategory(
            Inventory inventory, int slot,
            Material material, String name, String description
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

    private Material getMaterial(String path) {
        return Material.matchMaterial(
                plugin.getConfig().getString(path, "")
        );
    }

    private boolean validItem(Material material, int amount, double price) {
        return material != null
                && !material.isAir()
                && amount > 0
                && amount <= material.getMaxStackSize()
                && Double.isFinite(price)
                && price > 0;
    }

    private int validSize(int size) {
        if (size < 9 || size > 54 || size % 9 != 0) {
            return 54;
        }
        return size;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // ==========================================
    //                CLICK HANDLER
    // ==========================================

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        switch (holder.type) {
            case "main" -> {
                if (slot == 8 && player.isOp()) {
                    openEditorMenu(player);
                    return;
                }

                String category = categories.get(slot);
                if (category == null) return;

                if (category.equals("shard_shop")) {
                    plugin.getShardShopManager().openShop(player);
                } else if (category.equals("cpvp")) {
                    openCrystalShop(player);
                } else {
                    openCategory(player, category);
                }
            }

            case "category" -> {
                if (slot == event.getInventory().getSize() - 5) {
                    openMainMenu(player);
                    return;
                }

                ShopEntry entry = holder.entries.get(slot);
                if (entry != null) purchase(player, entry);
            }

            case "cpvp" -> {
                if (slot == 45) {
                    openMainMenu(player);
                    return;
                }

                if (slot == 49) {
                    purchaseFullLoadout(player, holder);
                    return;
                }

                ShopEntry entry = holder.entries.get(slot);
                if (entry != null) purchase(player, entry);
            }

            case "editor" -> {
                if (!player.isOp()) {
                    player.closeInventory();
                    return;
                }

                if (slot == 49) {
                    openMainMenu(player);
                    return;
                }

                String category = editorCategories.get(slot);
                if (category != null) {
                    openEditorItems(player, category);
                }
            }

            case "editor_items" -> {
                if (!player.isOp()) {
                    player.closeInventory();
                    return;
                }

                if (slot == 49) {
                    openEditorMenu(player);
                    return;
                }

                if (slot == 45) {
                    beginPrompt(player, "add_material",
                            holder.category, null);
                    return;
                }

                ShopEntry entry = holder.entries.get(slot);
                if (entry != null) {
                    openItemEditor(player, holder.category, entry);
                }
            }

            case "editor_item" -> {
                if (!player.isOp()) {
                    player.closeInventory();
                    return;
                }

                if (slot == 49) {
                    openEditorItems(player, holder.category);
                    return;
                }

                if (slot == 20) {
                    beginPrompt(player, "edit_material",
                            holder.category, holder.selectedPath);
                } else if (slot == 22) {
                    beginPrompt(player, "edit_amount",
                            holder.category, holder.selectedPath);
                } else if (slot == 24) {
                    beginPrompt(player, "edit_price",
                            holder.category, holder.selectedPath);
                } else if (slot == 31) {
                    deleteItem(player, holder);
                }
            }
        }
    }

    // ==========================================
    //                  HOLDERS
    // ==========================================

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

    private static class PendingEdit {
        private String action;
        private final String category;
        private final String path;
        private Material material;
        private int amount;

        PendingEdit(String action, String category, String path) {
            this.action = action;
            this.category = category;
            this.path = path;
        }
    }

    private static class MenuHolder implements InventoryHolder {
        private final String type;
        private final Map<Integer, ShopEntry> entries = new LinkedHashMap<>();
        private String category;
        private String selectedPath;
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
