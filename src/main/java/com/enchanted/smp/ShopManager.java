
package com.enchanted.smp;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopManager implements Listener {

    private final EnchantedSMP plugin;

    private final Map<Integer, String> categories = new LinkedHashMap<>();
    private final Map<Integer, String> editorCategories = new LinkedHashMap<>();
    private final Map<UUID, PendingEdit> pendingEdits = new ConcurrentHashMap<>();

    private final NamespacedKey editorKey;

    public ShopManager(EnchantedSMP plugin) {
        this.plugin = plugin;
        this.editorKey = new NamespacedKey(plugin, "shop_editor_item");

        categories.put(10, "building");
        categories.put(12, "redstone");
        categories.put(14, "traps");
        categories.put(16, "end");
        categories.put(19, "nether");
        categories.put(21, "cpvp");
        categories.put(23, "mob_drops");
        categories.put(25, "farming");
        categories.put(31, "shard_shop");

        editorCategories.putAll(categories);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // =====================================================
    // MAIN SHOP
    // =====================================================

    public void openShop(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder("main"),
                54,
                color("&5&lENCHANTEDSMP SHOP")
        );

        for (Map.Entry<Integer, String> entry : categories.entrySet()) {
            String category = entry.getValue();

            ItemStack item = new ItemStack(getCategoryIcon(category));
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName(color("&d&l" + formatName(category)));
            meta.setLore(List.of(
                    color("&7Click to view this category"),
                    color("&5ENCHANTEDSMP")
            ));

            item.setItemMeta(meta);
            inventory.setItem(entry.getKey(), item);
        }

        if (player.isOp()) {
            ItemStack editor = new ItemStack(Material.COMMAND_BLOCK);
            ItemMeta meta = editor.getItemMeta();

            meta.setDisplayName(color("&c&lSHOP EDITOR"));
            meta.setLore(List.of(
                    color("&7Open the admin editor"),
                    color("&eOP only")
            ));

            editor.setItemMeta(meta);
            inventory.setItem(8, editor);
        }

        player.openInventory(inventory);
    }

    private Material getCategoryIcon(String category) {
        return switch (category) {
            case "building" -> Material.BRICKS;
            case "redstone" -> Material.REDSTONE;
            case "traps" -> Material.COBWEB;
            case "end" -> Material.END_STONE;
            case "nether" -> Material.NETHERRACK;
            case "cpvp" -> Material.END_CRYSTAL;
            case "mob_drops" -> Material.ROTTEN_FLESH;
            case "farming" -> Material.WHEAT;
            case "shard_shop" -> Material.SPAWNER;
            default -> Material.CHEST;
        };
    }

    private void openCategory(Player player, String category) {
        if (category.equals("shard_shop")) {
            plugin.getShardShopManager().openShop(player);
            return;
        }

        if (category.equals("cpvp")) {
            openCpvpShop(player);
            return;
        }

        String path = "shop.categories." + category;
        String title = plugin.getConfig().getString(
                path + ".title",
                "&5&l" + formatName(category)
        );

        int size = validSize(plugin.getConfig().getInt(path + ".size", 54));

        MenuHolder holder = new MenuHolder("category");
        holder.category = category;

        Inventory inventory = Bukkit.createInventory(
                holder,
                size,
                color(title)
        );

        holder.inventory = inventory;

        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection(path + ".items");

        if (section != null) {
            int slot = 0;

            for (String id : section.getKeys(false)) {
                if (slot >= size - 9) break;

                String itemPath = path + ".items." + id;

                ItemStack item = configuredStack(itemPath);
                if (item == null) continue;

                Material material = item.getType();
                int amount = item.getAmount();
                double price = plugin.getConfig().getDouble(itemPath + ".price", 0);

                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(color("&d&l" + formatName(id)));
                meta.setLore(List.of(
                        color("&7Amount: &f" + amount),
                        color("&7Price: &a$" + formatMoney(price)),
                        color("&eClick to purchase")
                ));

                item.setItemMeta(meta);
                inventory.setItem(slot, item);

                holder.entries.put(
                        slot,
                        new ShopEntry(material, amount, price, itemPath)
                );

                slot++;
            }
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(color("&cBack"));
        back.setItemMeta(backMeta);
        inventory.setItem(size - 5, back);

        player.openInventory(inventory);
    }

    // =====================================================
    // CPVP SHOP
    // =====================================================

    private void openCpvpShop(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder("cpvp"),
                54,
                color("&5&lCPVP LOADOUT")
        );

        String base = "shop.categories.cpvp.loadout";

        addConfiguredItem(inventory, base + ".armor.helmet", 10, "Helmet");
        addConfiguredItem(inventory, base + ".armor.chestplate", 12, "Chestplate");
        addConfiguredItem(inventory, base + ".armor.leggings", 14, "Leggings");
        addConfiguredItem(inventory, base + ".armor.boots", 16, "Boots");

        addConfiguredItem(inventory, base + ".items.end_crystal", 19, "End Crystals");
        addConfiguredItem(inventory, base + ".items.obsidian", 21, "Obsidian");
        addConfiguredItem(inventory, base + ".items.totem_of_undying", 23, "Totems");
        addConfiguredItem(inventory, base + ".items.ender_pearl", 25, "Ender Pearls");
        addConfiguredItem(inventory, base + ".items.golden_apple", 28, "Golden Apples");
        addConfiguredItem(inventory, base + ".items.experience_bottle", 30, "Experience Bottles");

        addExtraItem(inventory, Material.NETHERITE_SWORD, 32, "&d&lEnchanted Sword");
        addExtraItem(inventory, Material.NETHERITE_PICKAXE, 34, "&d&lEnchanted Pickaxe");

        ItemStack buyAll = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = buyAll.getItemMeta();
        meta.setDisplayName(color("&a&lBUY FULL LOADOUT"));
        meta.setLore(List.of(color("&7Click to purchase everything")));
        buyAll.setItemMeta(meta);
        inventory.setItem(49, buyAll);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(color("&cBack"));
        back.setItemMeta(backMeta);
        inventory.setItem(45, back);

        player.openInventory(inventory);
    }

    private void addConfiguredItem(Inventory inventory, String path, int slot, String name) {
        ItemStack item = configuredStack(path);
        if (item == null) return;

        double price = plugin.getConfig().getDouble(path + ".price", 0);

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&d&l" + name));
        meta.setLore(List.of(
                color("&7Amount: &f" + item.getAmount()),
                color("&7Price: &a$" + formatMoney(price)),
                color("&eClick to purchase")
        ));

        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private void addExtraItem(Inventory inventory, Material material, int slot, String name) {
        ItemStack item = createEnchantedItem(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(color(name));
        meta.setLore(List.of(color("&7Included in the CPvP loadout")));

        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private ItemStack createEnchantedItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (material == Material.NETHERITE_SWORD) {
            enchant(meta, Enchantment.SHARPNESS, 5);
            enchant(meta, Enchantment.FIRE_ASPECT, 2);
            enchant(meta, Enchantment.LOOTING, 3);
            enchant(meta, Enchantment.UNBREAKING, 3);
            enchant(meta, Enchantment.MENDING, 1);
        } else if (material == Material.NETHERITE_PICKAXE) {
            enchant(meta, Enchantment.EFFICIENCY, 5);
            enchant(meta, Enchantment.UNBREAKING, 3);
            enchant(meta, Enchantment.FORTUNE, 3);
            enchant(meta, Enchantment.MENDING, 1);
        } else {
            enchant(meta, Enchantment.PROTECTION, 4);
            enchant(meta, Enchantment.UNBREAKING, 3);
            enchant(meta, Enchantment.MENDING, 1);
        }

        item.setItemMeta(meta);
        return item;
    }

    private void enchant(ItemMeta meta, Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
    }

    // =====================================================
    // ADMIN EDITOR MENU
    // =====================================================

    public void openEditorMenu(Player player) {
        if (!player.isOp()) {
            player.sendMessage(color("&cYou cannot use the editor."));
            return;
        }

        if (!editorEnabled("enabled")) {
            player.sendMessage(color("&cThe editor is disabled in the config."));
            return;
        }

        MenuHolder holder = new MenuHolder("editor");

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color(plugin.getConfig().getString(
                        "editor.title",
                        "&5&lENCHANTEDSMP ADMIN EDITOR"
                ))
        );

        holder.inventory = inventory;

        for (Map.Entry<Integer, String> entry : editorCategories.entrySet()) {
            String category = entry.getValue();

            ItemStack item = new ItemStack(getCategoryIcon(category));
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName(color("&d&l" + formatName(category)));
            meta.setLore(List.of(color("&7Edit this shop category")));
            item.setItemMeta(meta);

            inventory.setItem(entry.getKey(), item);
        }

        ItemStack money = new ItemStack(Material.GOLD_INGOT);
        ItemMeta moneyMeta = money.getItemMeta();
        moneyMeta.setDisplayName(color("&6&lMONEY MANAGER"));
        moneyMeta.setLore(List.of(color("&7Give, take or set player money")));
        money.setItemMeta(moneyMeta);
        inventory.setItem(38, money);

        ItemStack shards = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta shardMeta = shards.getItemMeta();
        shardMeta.setDisplayName(color("&d&lSHARD MANAGER"));
        shardMeta.setLore(List.of(color("&7Give, take or set player shards")));
        shards.setItemMeta(shardMeta);
        inventory.setItem(42, shards);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(color("&cBack to shop"));
        back.setItemMeta(backMeta);
        inventory.setItem(49, back);

        player.openInventory(inventory);
    }

    private boolean editorEnabled(String path) {
        return plugin.getConfig().getBoolean("editor." + path, true);
    }

    // =====================================================
    // CPVP EDITOR MENU
    // =====================================================

    private void openCpvpEditor(Player player) {
        MenuHolder holder = new MenuHolder("cpvp_editor");

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                color("&5&lCPVP EDITOR")
        );

        holder.inventory = inventory;

        ItemStack armor = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta armorMeta = armor.getItemMeta();
        armorMeta.setDisplayName(color("&d&lEDIT ARMOR"));
        armorMeta.setLore(List.of(color("&7Edit CPvP armor and prices")));
        armor.setItemMeta(armorMeta);
        inventory.setItem(11, armor);

        ItemStack items = new ItemStack(Material.END_CRYSTAL);
        ItemMeta itemsMeta = items.getItemMeta();
        itemsMeta.setDisplayName(color("&d&lEDIT ITEMS"));
        itemsMeta.setLore(List.of(color("&7Edit CPvP items and prices")));
        items.setItemMeta(itemsMeta);
        inventory.setItem(15, items);

        addEditorButton(inventory, 22, Material.ARROW, "&cBack");

        player.openInventory(inventory);
    }

    // =====================================================
    // EDITOR ITEM LIST
    // =====================================================

    private void openEditorItems(Player player, String category) {
        if (category.equals("cpvp")) {
            openCpvpEditor(player);
            return;
        }

        if (category.equals("shard_shop")) {
            openSpawnerEditor(player);
            return;
        }

        String path = "shop.categories." + category + ".items";

        if (!editorEnabled("shop-editor.enabled")) {
            player.sendMessage(color("&cThe shop editor is disabled."));
            return;
        }

        openEditorList(player, category, path, "editor_items");
    }

    private void openEditorList(Player player, String category, String path, String type) {
        MenuHolder holder = new MenuHolder(type);
        holder.category = category;
        holder.basePath = path;

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&5&lEDIT " + formatName(category))
        );

        holder.inventory = inventory;

        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection(path);

        if (section != null) {
            int slot = 0;

            for (String id : section.getKeys(false)) {
                if (slot >= 45) break;

                String itemPath = path + "." + id;

                ItemStack item = configuredStack(itemPath);
                if (item == null) continue;

                Material material = item.getType();
                int amount = item.getAmount();
                double price = plugin.getConfig().getDouble(itemPath + ".price", 0);

                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(color("&d&l" + formatName(id)));
                meta.setLore(List.of(
                        color("&7Amount: &f" + amount),
                        color("&7Price: &a$" + formatMoney(price)),
                        color("&eClick to edit")
                ));
                item.setItemMeta(meta);

                inventory.setItem(slot, item);
                holder.entries.put(slot, new ShopEntry(material, amount, price, itemPath));
                slot++;
            }
        }

        addEditorButton(inventory, 45, Material.LIME_DYE, "&a&lADD ITEM");
        addEditorButton(inventory, 49, Material.ARROW, "&cBack");

        player.openInventory(inventory);
    }

    private void openCpvpEditorList(Player player, String type) {
        String path = type.equals("armor")
                ? "shop.categories.cpvp.loadout.armor"
                : "shop.categories.cpvp.loadout.items";

        openEditorList(player, "cpvp_" + type, path, "editor_items");
    }

    // =====================================================
    // ITEM EDITOR
    // =====================================================

    private void openItemEditor(Player player, ShopEntry entry, String category) {
        MenuHolder holder = new MenuHolder("editor_item");
        holder.category = category;
        holder.selectedPath = entry.path;

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&5&lEDIT ITEM")
        );

        holder.inventory = inventory;

        ItemStack current = configuredStack(entry.path);

        if (current == null) {
            current = new ItemStack(entry.material, Math.max(1, entry.amount));
        }

        ItemMeta currentMeta = current.getItemMeta();
        currentMeta.setDisplayName(color("&d&lCURRENT ITEM"));
        currentMeta.setLore(List.of(
                color("&7Material: &f" + entry.material.name()),
                color("&7Amount: &f" + entry.amount),
                color("&7Price: &a$" + formatMoney(entry.price))
        ));
        current.setItemMeta(currentMeta);
        inventory.setItem(4, current);

        addEditorButton(inventory, 20, Material.ANVIL, "&eEDIT MATERIAL");
        addEditorButton(inventory, 22, Material.CHEST, "&eEDIT AMOUNT");
        addEditorButton(inventory, 24, Material.GOLD_INGOT, "&eEDIT PRICE");
        addEditorButton(inventory, 31, Material.BARRIER, "&cDELETE ITEM");
        addEditorButton(inventory, 49, Material.ARROW, "&cBack");

        player.openInventory(inventory);
    }

    private void addEditorButton(Inventory inventory, int slot, Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    // =====================================================
    // ADD ITEM FROM MAIN HAND
    // =====================================================

    private void beginAddFromHand(Player player, String category, String path) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (heldItem == null || heldItem.getType().isAir()) {
            player.sendMessage(color("&cHold the item you want to add in your main hand first."));
            return;
        }

        ItemStack savedItem = heldItem.clone();

        PendingEdit pending = new PendingEdit("add_price", category, path);
        pending.itemStack = savedItem;
        pending.material = savedItem.getType();
        pending.amount = savedItem.getAmount();

        pendingEdits.put(player.getUniqueId(), pending);

        player.closeInventory();
        player.sendMessage(color("&dEnter the price for this item."));
        player.sendMessage(color("&7The item in your hand will not be removed."));
        player.sendMessage(color("&7Type &ccancel &7to stop."));
    }

    private String createUniqueItemId(String path, ItemStack item) {
        String name = item.getType().name();

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            name = PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }

        String id = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        if (id.isBlank()) {
            id = item.getType().name().toLowerCase(Locale.ROOT);
        }

        String uniqueId = id;
        int number = 2;

        while (plugin.getConfig().contains(path + "." + uniqueId)) {
            uniqueId = id + "_" + number;
            number++;
        }

        return uniqueId;
    }

    // =====================================================
    // SHARD SPAWNER EDITOR
    // =====================================================

    private void openSpawnerEditor(Player player) {
        if (!editorEnabled("shard-shop-editor.enabled")) {
            player.sendMessage(color("&cThe shard shop editor is disabled."));
            return;
        }

        String path = "shop.categories.shard_shop.spawners";

        MenuHolder holder = new MenuHolder("spawner_editor");
        holder.category = "shard_shop";
        holder.basePath = path;

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&5&lSPAWNER EDITOR")
        );

        holder.inventory = inventory;

        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection(path);

        if (section != null) {
            int slot = 0;

            for (String id : section.getKeys(false)) {
                if (slot >= 45) break;

                String spawnerPath = path + "." + id;
                String mob = plugin.getConfig().getString(spawnerPath + ".mob", "ZOMBIE");
                double price = plugin.getConfig().getDouble(spawnerPath + ".price", 0);

                ItemStack item = new ItemStack(Material.SPAWNER);
                ItemMeta meta = item.getItemMeta();

                meta.setDisplayName(color("&d&l" + formatName(id)));
                meta.setLore(List.of(
                        color("&7Mob: &f" + mob),
                        color("&7Price: &d" + formatMoney(price) + " Shards"),
                        color("&eClick to edit")
                ));

                item.setItemMeta(meta);
                inventory.setItem(slot, item);

                holder.entries.put(
                        slot,
                        new ShopEntry(Material.SPAWNER, 1, price, spawnerPath)
                );

                slot++;
            }
        }

        addEditorButton(inventory, 45, Material.LIME_DYE, "&a&lADD SPAWNER");
        addEditorButton(inventory, 49, Material.ARROW, "&cBack");

        player.openInventory(inventory);
    }

    private void openSpawnerItemEditor(Player player, ShopEntry entry) {
        MenuHolder holder = new MenuHolder("spawner_item");
        holder.selectedPath = entry.path;
        holder.category = "shard_shop";

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                color("&5&lEDIT SPAWNER")
        );

        holder.inventory = inventory;

        String mob = plugin.getConfig().getString(entry.path + ".mob", "ZOMBIE");
        double price = plugin.getConfig().getDouble(entry.path + ".price", 0);

        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();
        meta.setDisplayName(color("&d&lCURRENT SPAWNER"));
        meta.setLore(List.of(
                color("&7Mob: &f" + mob),
                color("&7Price: &d" + formatMoney(price) + " Shards")
        ));
        spawner.setItemMeta(meta);
        inventory.setItem(4, spawner);

        addEditorButton(inventory, 11, Material.ZOMBIE_HEAD, "&eEDIT MOB TYPE");
        addEditorButton(inventory, 15, Material.AMETHYST_SHARD, "&eEDIT SHARD PRICE");
        addEditorButton(inventory, 22, Material.BARRIER, "&cDELETE SPAWNER");
        addEditorButton(inventory, 26, Material.ARROW, "&cBack");

        player.openInventory(inventory);
    }

    // =====================================================
    // MONEY AND SHARD MANAGERS
    // =====================================================

    private void openBalanceManager(Player player, boolean money) {
        String type = money ? "money_manager" : "shard_manager";

        MenuHolder holder = new MenuHolder(type);

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                color(money ? "&6&lMONEY MANAGER" : "&d&lSHARD MANAGER")
        );

        holder.inventory = inventory;

        addEditorButton(
                inventory,
                10,
                money ? Material.GOLD_INGOT : Material.AMETHYST_SHARD,
                "&aGIVE " + (money ? "MONEY" : "SHARDS")
        );

        addEditorButton(
                inventory,
                13,
                Material.REDSTONE,
                "&cTAKE " + (money ? "MONEY" : "SHARDS")
        );

        addEditorButton(
                inventory,
                16,
                Material.DIAMOND,
                "&eSET " + (money ? "MONEY" : "SHARDS")
        );

        addEditorButton(inventory, 22, Material.ARROW, "&cBack");

        player.openInventory(inventory);
    }

    private void beginBalanceEdit(Player player, boolean money, String action) {
        if (money && !editorEnabled("money-manager.allow-" + action)) {
            player.sendMessage(color("&cThat money action is disabled."));
            return;
        }

        if (!money && !editorEnabled("shard-manager.allow-" + action)) {
            player.sendMessage(color("&cThat shard action is disabled."));
            return;
        }

        PendingEdit pending = new PendingEdit(
                (money ? "money_" : "shard_") + action,
                money ? "money" : "shards",
                ""
        );

        pending.stage = "player";
        pendingEdits.put(player.getUniqueId(), pending);

        player.closeInventory();
        player.sendMessage(color("&dEnter the player's username in chat."));
        player.sendMessage(color("&7Type &ccancel &7to stop."));
    }

    // =====================================================
    // CHAT EDITING
    // =====================================================

    private void beginPrompt(
            Player player,
            String action,
            String category,
            String path
    ) {
        PendingEdit pending = new PendingEdit(action, category, path);
        pendingEdits.put(player.getUniqueId(), pending);

        player.closeInventory();

        switch (action) {
            case "add_material" ->
                    player.sendMessage(color("&dEnter the material name."));
            case "edit_material" ->
                    player.sendMessage(color("&dEnter the new material name."));
            case "edit_amount" ->
                    player.sendMessage(color("&dEnter the new item amount."));
            case "edit_price" ->
                    player.sendMessage(color("&dEnter the new price."));
            case "add_spawner_mob" ->
                    player.sendMessage(color("&dEnter the mob type, for example ZOMBIE."));
            case "edit_spawner_mob" ->
                    player.sendMessage(color("&dEnter the new mob type."));
            case "add_spawner_price", "edit_spawner_price" ->
                    player.sendMessage(color("&dEnter the shard price."));
        }

        player.sendMessage(color("&7Type &ccancel &7to stop."));
    }

    @EventHandler
    public void onEditorChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingEdit pending = pendingEdits.get(player.getUniqueId());

        if (pending == null) return;

        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                pendingEdits.remove(player.getUniqueId());
                return;
            }

            if (message.equalsIgnoreCase("cancel")) {
                pendingEdits.remove(player.getUniqueId());
                player.sendMessage(color("&cEditing cancelled."));
                openEditorMenu(player);
                return;
            }

            handleEditorInput(player, pending, message);
        });
    }

    private void handleEditorInput(Player player, PendingEdit pending, String input) {
        try {
            switch (pending.action) {
                case "add_material" -> {
                    Material material = getMaterial(input);

                    if (material == null || !material.isItem()) {
                        player.sendMessage(color("&cInvalid item material. Try again."));
                        return;
                    }

                    pending.material = material;
                    pending.action = "add_amount";
                    player.sendMessage(color("&dEnter the amount."));
                }

                case "add_amount" -> {
                    int amount = Integer.parseInt(input);

                    if (amount < 1 || amount > 2304) {
                        player.sendMessage(color("&cAmount must be between 1 and 2304."));
                        return;
                    }

                    pending.amount = amount;
                    pending.action = "add_price";
                    player.sendMessage(color("&dEnter the price."));
                }

                case "add_price" -> {
                    double price = Double.parseDouble(input);

                    if (price < 0 || !Double.isFinite(price)) {
                        player.sendMessage(color("&cEnter a valid price."));
                        return;
                    }

                    if (pending.itemStack == null) {
                        player.sendMessage(color("&cThe item could not be found. Start again."));
                        pendingEdits.remove(player.getUniqueId());
                        return;
                    }

                    String id = createUniqueItemId(pending.path, pending.itemStack);
                    String itemPath = pending.path + "." + id;

                    ItemStack savedItem = pending.itemStack.clone();

                    plugin.getConfig().set(itemPath + ".material", savedItem.getType().name());
                    plugin.getConfig().set(itemPath + ".amount", savedItem.getAmount());
                    plugin.getConfig().set(itemPath + ".price", price);
                    plugin.getConfig().set(itemPath + ".item", savedItem);

                    saveChanges();
                    pendingEdits.remove(player.getUniqueId());

                    player.sendMessage(color("&aItem added and saved!"));
                    openEditorItems(player, pending.category);
                }

                case "edit_material" -> {
                    Material material = getMaterial(input);

                    if (material == null || !material.isItem()) {
                        player.sendMessage(color("&cInvalid material. Try again."));
                        return;
                    }

                    plugin.getConfig().set(pending.path + ".material", material.name());

                    ItemStack saved = plugin.getConfig().getItemStack(pending.path + ".item");

                    if (saved != null) {
                        saved.setType(material);
                        plugin.getConfig().set(pending.path + ".item", saved);
                    }

                    saveEdit(player, pending);
                }

                case "edit_amount" -> {
                    int amount = Integer.parseInt(input);

                    if (amount < 1 || amount > 2304) {
                        player.sendMessage(color("&cAmount must be between 1 and 2304."));
                        return;
                    }

                    plugin.getConfig().set(pending.path + ".amount", amount);

                    ItemStack saved = plugin.getConfig().getItemStack(pending.path + ".item");

                    if (saved != null) {
                        saved.setAmount(Math.min(amount, saved.getMaxStackSize()));
                        plugin.getConfig().set(pending.path + ".item", saved);
                    }

                    saveEdit(player, pending);
                }

                case "edit_price" -> {
                    double price = Double.parseDouble(input);

                    if (price < 0 || !Double.isFinite(price)) {
                        player.sendMessage(color("&cEnter a valid price."));
                        return;
                    }

                    plugin.getConfig().set(pending.path + ".price", price);
                    saveEdit(player, pending);
                }

                case "add_spawner_mob" -> {
                    EntityType mob = parseMob(input);

                    if (mob == null) {
                        player.sendMessage(color("&cInvalid mob type. Try again."));
                        return;
                    }

                    pending.material = Material.SPAWNER;
                    pending.targetName = mob.name().toLowerCase(Locale.ROOT);
                    pending.action = "add_spawner_price";

                    player.sendMessage(color("&dEnter the shard price."));
                }

                case "add_spawner_price" -> {
                    double price = Double.parseDouble(input);

                    if (price <= 0 || !Double.isFinite(price)) {
                        player.sendMessage(color("&cEnter a valid price greater than 0."));
                        return;
                    }

                    String id = pending.targetName;
                    String path = "shop.categories.shard_shop.spawners." + id;

                    plugin.getConfig().set(path + ".mob", pending.targetName.toUpperCase(Locale.ROOT));
                    plugin.getConfig().set(path + ".price", price);
                    plugin.getConfig().set(path + ".material", "SPAWNER");

                    saveChanges();
                    pendingEdits.remove(player.getUniqueId());

                    player.sendMessage(color("&aSpawner added and saved!"));
                    openSpawnerEditor(player);
                }

                case "edit_spawner_mob" -> {
                    EntityType mob = parseMob(input);

                    if (mob == null) {
                        player.sendMessage(color("&cInvalid mob type. Try again."));
                        return;
                    }

                    plugin.getConfig().set(pending.path + ".mob", mob.name());
                    saveSpawnerEdit(player, pending);
                }

                case "edit_spawner_price" -> {
                    double price = Double.parseDouble(input);

                    if (price <= 0 || !Double.isFinite(price)) {
                        player.sendMessage(color("&cEnter a valid price greater than 0."));
                        return;
                    }

                    plugin.getConfig().set(pending.path + ".price", price);
                    saveSpawnerEdit(player, pending);
                }

                case "money_give", "money_take", "money_set",
                     "shard_give", "shard_take", "shard_set" -> {
                    if (pending.stage.equals("player")) {
                        OfflinePlayer target = findPlayer(input);

                        if (target == null) {
                            player.sendMessage(color("&cThat player could not be found."));
                            return;
                        }

                        pending.target = target;
                        pending.stage = "amount";

                        player.sendMessage(color("&dEnter the amount."));
                    } else {
                        double amount = Double.parseDouble(input);

                        if (amount <= 0 || !Double.isFinite(amount)) {
                            player.sendMessage(color("&cEnter an amount greater than 0."));
                            return;
                        }

                        applyBalanceEdit(player, pending, amount);
                    }
                }

                default -> {
                    pendingEdits.remove(player.getUniqueId());
                    player.sendMessage(color("&cUnknown editor action."));
                }
            }
        } catch (NumberFormatException exception) {
            player.sendMessage(color("&cInvalid number. Try again."));
        }
    }

    private void saveEdit(Player player, PendingEdit pending) {
        saveChanges();
        pendingEdits.remove(player.getUniqueId());

        player.sendMessage(color("&aChanges saved!"));

        if (pending.category.equals("shard_shop")) {
            openSpawnerEditor(player);
        } else {
            openEditorItems(player, pending.category);
        }
    }

    private void saveSpawnerEdit(Player player, PendingEdit pending) {
        saveChanges();
        pendingEdits.remove(player.getUniqueId());

        player.sendMessage(color("&aSpawner changes saved!"));
        openSpawnerEditor(player);
    }

    private void applyBalanceEdit(Player player, PendingEdit pending, double amount) {
        OfflinePlayer target = pending.target;
        String action = pending.action;

        if (action.startsWith("money_")) {
            Economy economy = plugin.getEconomy();

            if (economy == null) {
                player.sendMessage(color("&cVault economy is unavailable."));
                return;
            }

            EconomyResponse response;

            if (action.equals("money_give")) {
                response = economy.depositPlayer(target, amount);
            } else if (action.equals("money_take")) {
                response = economy.withdrawPlayer(target, amount);
            } else {
                double current = economy.getBalance(target);

                if (current > 0) {
                    EconomyResponse withdraw = economy.withdrawPlayer(target, current);

                    if (!withdraw.transactionSuccess()) {
                        player.sendMessage(color("&cCould not clear the player's balance."));
                        return;
                    }
                }

                response = economy.depositPlayer(target, amount);
            }

            if (!response.transactionSuccess()) {
                player.sendMessage(color("&cMoney transaction failed: " + response.errorMessage));
                return;
            }

        } else {
            if (action.equals("shard_give")) {
                plugin.getShardManager().giveShards(target, amount);
            } else if (action.equals("shard_take")) {
                if (!plugin.getShardManager().takeShards(target, amount)) {
                    player.sendMessage(color("&cThat player does not have enough shards."));
                    return;
                }
            } else {
                plugin.getShardManager().setShards(target, amount);
            }

            plugin.getShardManager().save();
        }

        pendingEdits.remove(player.getUniqueId());

        player.sendMessage(color("&aSuccessfully updated "
                + target.getName() + "'s "
                + (action.startsWith("money_") ? "money" : "shards") + "!"));

        openEditorMenu(player);
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);

        if (online != null) return online;

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);

        if (offline.getName() == null && !offline.hasPlayedBefore()) {
            return null;
        }

        return offline;
    }

    private EntityType parseMob(String input) {
        try {
            EntityType type = EntityType.valueOf(input.toUpperCase(Locale.ROOT));
            return type == EntityType.UNKNOWN ? null : type;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // =====================================================
    // DELETE SUPPORT
    // =====================================================

    private void deleteItem(Player player, String path, String category) {
        if (!editorEnabled("shop-editor.allow-delete-items")) {
            player.sendMessage(color("&cDeleting items is disabled."));
            return;
        }

        plugin.getConfig().set(path, null);
        saveChanges();

        player.sendMessage(color("&aItem deleted and saved!"));
        openEditorItems(player, category);
    }

    private void deleteSpawner(Player player, String path) {
        if (!editorEnabled("shard-shop-editor.allow-delete-spawners")) {
            player.sendMessage(color("&cDeleting spawners is disabled."));
            return;
        }

        plugin.getConfig().set(path, null);
        saveChanges();

        player.sendMessage(color("&aSpawner deleted and saved!"));
        openSpawnerEditor(player);
    }

    private void saveChanges() {
        plugin.saveConfig();
    }

    // =====================================================
    // PURCHASES
    // =====================================================

    private void purchase(Player player, ShopEntry entry) {
        Economy economy = plugin.getEconomy();

        if (economy == null) {
            player.sendMessage(color("&cThe economy is unavailable."));
            return;
        }

        if (economy.getBalance(player) < entry.price) {
            player.sendMessage(color("&cYou don't have enough money!"));
            return;
        }

        ItemStack item;

        if (entry.path.equals("extra_sword")) {
            item = createEnchantedItem(Material.NETHERITE_SWORD);
        } else if (entry.path.equals("extra_pickaxe")) {
            item = createEnchantedItem(Material.NETHERITE_PICKAXE);
        } else {
            item = configuredStack(entry.path);

            if (item == null) {
                item = new ItemStack(
                        entry.material,
                        Math.max(1, Math.min(entry.amount, entry.material.getMaxStackSize()))
                );
            }
        }

        if (!hasInventorySpace(player, item)) {
            player.sendMessage(color("&cYour inventory is full!"));
            return;
        }

        EconomyResponse response = economy.withdrawPlayer(player, entry.price);

        if (!response.transactionSuccess()) {
            player.sendMessage(color("&cPurchase failed."));
            return;
        }

        player.getInventory().addItem(item);

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aPurchased "
                        + item.getAmount() + "x " + formatName(item.getType().name())
                        + " for &a$" + formatMoney(entry.price) + "&a!"
        ));
    }

    private void purchaseFullLoadout(Player player) {
        Economy economy = plugin.getEconomy();

        if (economy == null) {
            player.sendMessage(color("&cThe economy is unavailable."));
            return;
        }

        String base = "shop.categories.cpvp.loadout";
        List<ItemStack> items = new ArrayList<>();
        double total = 0;

        ConfigurationSection armor =
                plugin.getConfig().getConfigurationSection(base + ".armor");

        if (armor != null) {
            for (String key : armor.getKeys(false)) {
                String path = base + ".armor." + key;
                ItemStack item = configuredStack(path);

                if (item != null) {
                    items.add(item);
                    total += plugin.getConfig().getDouble(path + ".price", 0);
                }
            }
        }

        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection(base + ".items");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                String path = base + ".items." + key;
                ItemStack item = configuredStack(path);

                if (item != null) {
                    items.add(item);
                    total += plugin.getConfig().getDouble(path + ".price", 0);
                }
            }
        }

        items.add(createEnchantedItem(Material.NETHERITE_SWORD));
        items.add(createEnchantedItem(Material.NETHERITE_PICKAXE));

        total += 18000;

        if (economy.getBalance(player) < total) {
            player.sendMessage(color("&cYou need $" + formatMoney(total) + " for the full loadout."));
            return;
        }

        for (ItemStack item : items) {
            if (!hasInventorySpace(player, item)) {
                player.sendMessage(color("&cYour inventory does not have enough space."));
                return;
            }
        }

        EconomyResponse response = economy.withdrawPlayer(player, total);

        if (!response.transactionSuccess()) {
            player.sendMessage(color("&cPurchase failed."));
            return;
        }

        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }

        player.sendMessage(color("&aPurchased the full CPvP loadout for $" + formatMoney(total) + "!"));
    }

    private ItemStack configuredStack(String path) {
        ItemStack saved = plugin.getConfig().getItemStack(path + ".item");

        if (saved != null && !saved.getType().isAir()) {
            ItemStack result = saved.clone();

            int configuredAmount = plugin.getConfig().getInt(
                    path + ".amount",
                    result.getAmount()
            );

            result.setAmount(Math.max(
                    1,
                    Math.min(configuredAmount, result.getMaxStackSize())
            ));

            return result;
        }

        Material material = getMaterial(
                plugin.getConfig().getString(path + ".material", "")
        );

        if (material == null) return null;

        int amount = plugin.getConfig().getInt(path + ".amount", 1);

        return new ItemStack(
                material,
                Math.max(1, Math.min(amount, material.getMaxStackSize()))
        );
    }

    private boolean hasInventorySpace(Player player, ItemStack item) {
        int remaining = item.getAmount();
        int maxStack = item.getMaxStackSize();

        for (ItemStack contents : player.getInventory().getStorageContents()) {
            if (contents == null || contents.getType().isAir()) {
                remaining -= maxStack;
            } else if (contents.isSimilar(item)) {
                remaining -= Math.max(0, maxStack - contents.getAmount());
            }

            if (remaining <= 0) return true;
        }

        return false;
    }

    // =====================================================
    // INVENTORY CLICK HANDLER
    // =====================================================

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        switch (holder.type) {
            case "main" -> {
                if (slot == 8 && player.isOp()) {
                    openEditorMenu(player);
                    return;
                }

                String category = categories.get(slot);

                if (category != null) {
                    openCategory(player, category);
                }
            }

            case "category" -> {
                if (slot == event.getInventory().getSize() - 5) {
                    openShop(player);
                    return;
                }

                ShopEntry entry = holder.entries.get(slot);

                if (entry != null) {
                    purchase(player, entry);
                }
            }

            case "cpvp" -> {
                if (slot == 45) {
                    openShop(player);
                    return;
                }

                if (slot == 49) {
                    purchaseFullLoadout(player);
                    return;
                }

                ShopEntry entry = getCpvpEntry(slot);

                if (entry != null) {
                    purchase(player, entry);
                }
            }

            case "editor" -> {
                if (!player.isOp()) return;

                if (slot == 49) {
                    openShop(player);
                    return;
                }

                if (slot == 38) {
                    if (editorEnabled("money-manager.enabled")) {
                        openBalanceManager(player, true);
                    }
                    return;
                }

                if (slot == 42) {
                    if (editorEnabled("shard-manager.enabled")) {
                        openBalanceManager(player, false);
                    }
                    return;
                }

                String category = editorCategories.get(slot);

                if (category != null) {
                    openEditorItems(player, category);
                }
            }

            case "cpvp_editor" -> {
                if (!player.isOp()) return;

                if (slot == 22) {
                    openEditorMenu(player);
                } else if (slot == 11) {
                    openCpvpEditorList(player, "armor");
                } else if (slot == 15) {
                    openCpvpEditorList(player, "items");
                }
            }

            case "editor_items" -> {
                if (!player.isOp()) return;

                if (slot == 49) {
                    if (holder.category.startsWith("cpvp_")) {
                        openCpvpEditor(player);
                    } else {
                        openEditorMenu(player);
                    }
                    return;
                }

                if (slot == 45) {
                    if (!editorEnabled("shop-editor.allow-add-items")) {
                        player.sendMessage(color("&cAdding items is disabled."));
                        return;
                    }

                    beginAddFromHand(player, holder.category, holder.basePath);
                    return;
                }

                ShopEntry entry = holder.entries.get(slot);

                if (entry != null) {
                    if (!editorEnabled("shop-editor.allow-edit-items")) {
                        player.sendMessage(color("&cEditing items is disabled."));
                        return;
                    }

                    openItemEditor(player, entry, holder.category);
                }
            }

            case "editor_item" -> {
                if (!player.isOp()) return;

                if (slot == 49) {
                    openEditorItems(player, holder.category);
                } else if (slot == 20) {
                    beginPrompt(player, "edit_material", holder.category, holder.selectedPath);
                } else if (slot == 22) {
                    beginPrompt(player, "edit_amount", holder.category, holder.selectedPath);
                } else if (slot == 24) {
                    beginPrompt(player, "edit_price", holder.category, holder.selectedPath);
                } else if (slot == 31) {
                    deleteItem(player, holder.selectedPath, holder.category);
                }
            }

            case "spawner_editor" -> {
                if (!player.isOp()) return;

                if (slot == 49) {
                    openEditorMenu(player);
                    return;
                }

                if (slot == 45) {
                    if (!editorEnabled("shard-shop-editor.allow-add-spawners")) {
                        player.sendMessage(color("&cAdding spawners is disabled."));
                        return;
                    }

                    beginPrompt(player, "add_spawner_mob", "shard_shop", holder.basePath);
                    return;
                }

                ShopEntry entry = holder.entries.get(slot);

                if (entry != null) {
                    if (!editorEnabled("shard-shop-editor.allow-edit-spawners")) {
                        player.sendMessage(color("&cEditing spawners is disabled."));
                        return;
                    }

                    openSpawnerItemEditor(player, entry);
                }
            }

            case "spawner_item" -> {
                if (!player.isOp()) return;

                if (slot == 26) {
                    openSpawnerEditor(player);
                } else if (slot == 11) {
                    beginPrompt(player, "edit_spawner_mob", "shard_shop", holder.selectedPath);
                } else if (slot == 15) {
                    beginPrompt(player, "edit_spawner_price", "shard_shop", holder.selectedPath);
                } else if (slot == 22) {
                    deleteSpawner(player, holder.selectedPath);
                }
            }

            case "money_manager", "shard_manager" -> {
                if (!player.isOp()) return;

                boolean money = holder.type.equals("money_manager");

                if (slot == 22) {
                    openEditorMenu(player);
                } else if (slot == 10) {
                    beginBalanceEdit(player, money, "give");
                } else if (slot == 13) {
                    beginBalanceEdit(player, money, "take");
                } else if (slot == 16) {
                    beginBalanceEdit(player, money, "set");
                }
            }
        }
    }

    private ShopEntry getCpvpEntry(int slot) {
        String base = "shop.categories.cpvp.loadout";

        Map<Integer, String> paths = new LinkedHashMap<>();
        paths.put(10, base + ".armor.helmet");
        paths.put(12, base + ".armor.chestplate");
        paths.put(14, base + ".armor.leggings");
        paths.put(16, base + ".armor.boots");
        paths.put(19, base + ".items.end_crystal");
        paths.put(21, base + ".items.obsidian");
        paths.put(23, base + ".items.totem_of_undying");
        paths.put(25, base + ".items.ender_pearl");
        paths.put(28, base + ".items.golden_apple");
        paths.put(30, base + ".items.experience_bottle");

        if (slot == 32) {
            return new ShopEntry(Material.NETHERITE_SWORD, 1, 10000, "extra_sword");
        }

        if (slot == 34) {
            return new ShopEntry(Material.NETHERITE_PICKAXE, 1, 8000, "extra_pickaxe");
        }

        String path = paths.get(slot);

        if (path == null) return null;

        ItemStack item = configuredStack(path);

        if (item == null) return null;

        return new ShopEntry(
                item.getType(),
                item.getAmount(),
                plugin.getConfig().getDouble(path + ".price", 0),
                path
        );
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private Material getMaterial(String name) {
        if (name == null || name.isBlank()) return null;

        Material material = Material.matchMaterial(name.trim());

        if (material == null) {
            material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        }

        return material;
    }

    private int validSize(int size) {
        if (size < 9) return 54;
        if (size > 54) return 54;
        return (int) (Math.ceil(size / 9.0) * 9);
    }

    private String formatName(String name) {
        String[] words = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;

            if (result.length() > 0) result.append(" ");

            result.append(Character.toUpperCase(word.charAt(0)));

            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }

        return result.toString();
    }

    private String formatMoney(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // =====================================================
    // INNER CLASSES
    // =====================================================

    private static class ShopEntry {
        private final Material material;
        private final int amount;
        private final double price;
        private final String path;

        private ShopEntry(Material material, int amount, double price, String path) {
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
        private String stage;
        private String targetName;
        private OfflinePlayer target;
        private ItemStack itemStack;

        private PendingEdit(String action, String category, String path) {
            this.action = action;
            this.category = category;
            this.path = path;
        }
    }

    private static class MenuHolder implements InventoryHolder {
        private final String type;
        private final Map<Integer, ShopEntry> entries = new LinkedHashMap<>();

        private String category;
        private String basePath;
        private String selectedPath;
        private Inventory inventory;

        private MenuHolder(String type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
