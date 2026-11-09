
package com.enchanted.smp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SpawnerManager implements Listener {

    private static final int MAX_LEVEL = 100;
    private static final double UPGRADE_COST = 2000.0;
    private static final double BASE_EARNINGS = 2000.0;
    private static final int STORAGE_LIMIT = 10000;

    private final EnchantedSMP plugin;

    private final NamespacedKey spawnerItemKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey blockIdKey;
    private final NamespacedKey blockOwnerKey;

    private final Map<String, SpawnerData> spawners = new HashMap<>();

    private final File file;
    private final YamlConfiguration dataFile;

    public SpawnerManager(EnchantedSMP plugin) {
        this.plugin = plugin;

        spawnerItemKey = new NamespacedKey(plugin, "shard_spawner");
        actionKey = new NamespacedKey(plugin, "spawner_action");
        blockIdKey = new NamespacedKey(plugin, "managed_spawner_id");
        blockOwnerKey = new NamespacedKey(plugin, "managed_spawner_owner");

        file = new File(plugin.getDataFolder(), "spawners.yml");
        dataFile = YamlConfiguration.loadConfiguration(file);

        loadSpawners();

        Bukkit.getPluginManager().registerEvents(this, plugin);

        Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::generate,
                20L,
                20L
        );

        // Save periodically rather than writing to disk every second.
        Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::saveSpawners,
                1200L,
                1200L
        );
    }

    // ==========================================
    //             PLACE SPAWNERS
    // ==========================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {

        ItemStack item = event.getItemInHand();

        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta itemMeta = item.getItemMeta();

        String id = itemMeta.getPersistentDataContainer().get(
                spawnerItemKey,
                PersistentDataType.STRING
        );

        if (id == null) {
            return;
        }

        // Read the mob type from the actual purchased spawner item.
        if (!(itemMeta instanceof BlockStateMeta blockMeta)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color(
                    "&cInvalid spawner item!"
            ));
            return;
        }

        if (!(blockMeta.getBlockState() instanceof CreatureSpawner itemSpawner)) {
            event.setCancelled(true);
            return;
        }

        EntityType mob = itemSpawner.getSpawnedType();

        if (mob == null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color(
                    "&cThis spawner has no mob type!"
            ));
            return;
        }

        Block block = event.getBlockPlaced();

        if (!(block.getState() instanceof CreatureSpawner placedSpawner)) {
            return;
        }

        // Set the mob type directly on the placed block.
        placedSpawner.setSpawnedType(mob);

        // Save identifying information on the actual spawner block.
        placedSpawner.getPersistentDataContainer().set(
                blockIdKey,
                PersistentDataType.STRING,
                id
        );

        placedSpawner.getPersistentDataContainer().set(
                blockOwnerKey,
                PersistentDataType.STRING,
                event.getPlayer().getUniqueId().toString()
        );

        placedSpawner.update(true, false);

        SpawnerData data = new SpawnerData(
                mob,
                id,
                event.getPlayer().getUniqueId()
        );

        spawners.put(getKey(block), data);
        saveSpawners();

        event.getPlayer().sendMessage(color(
                "&5&lENCHANTEDSMP &8» &a"
                        + formatName(mob.name())
                        + " spawner placed!"
        ));
    }

    // ==========================================
    //             RIGHT-CLICK SPAWNER
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();

        if (block == null || block.getType() != Material.SPAWNER) {
            return;
        }

        String key = getKey(block);

        SpawnerData data = spawners.get(key);

        // Recover block information if the saved map entry is missing.
        if (data == null && block.getState() instanceof CreatureSpawner spawner) {

            String id = spawner.getPersistentDataContainer().get(
                    blockIdKey,
                    PersistentDataType.STRING
            );

            String ownerString = spawner.getPersistentDataContainer().get(
                    blockOwnerKey,
                    PersistentDataType.STRING
            );

            if (id != null && ownerString != null) {
                try {
                    UUID owner = UUID.fromString(ownerString);

                    data = new SpawnerData(
                            spawner.getSpawnedType(),
                            id,
                            owner
                    );

                    spawners.put(key, data);
                    saveSpawners();

                } catch (IllegalArgumentException ignored) {
                    return;
                }
            }
        }

        if (data == null) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();

        if (!data.owner.equals(player.getUniqueId())) {
            player.sendMessage(color(
                    "&cThis is not your spawner!"
            ));
            return;
        }

        openSpawnerGUI(player, block, data);
    }

    // ==========================================
    //                 SPAWNER GUI
    // ==========================================

    private void openSpawnerGUI(
            Player player,
            Block block,
            SpawnerData data
    ) {

        SpawnerHolder holder = new SpawnerHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                color("&5&lENCHANTED SPAWNER")
        );

        holder.inventory = inventory;
        holder.blockKey = getKey(block);

        // INFORMATION

        ItemStack info = new ItemStack(Material.SPAWNER);
        ItemMeta infoMeta = info.getItemMeta();

        infoMeta.setDisplayName(color("&d&lSpawner Information"));

        infoMeta.setLore(Arrays.asList(
                color("&7Mob: &f" + formatName(data.mob.name())),
                color("&7Level: &d" + data.level + "&7/&d100"),
                color("&7Earnings: &a$" + format(data.level * BASE_EARNINGS) + "/sec"),
                color("&7Stored Money: &a$" + format(data.storedMoney)),
                color("&7Stored Drops: &f" + getDropTotal(data)),
                color("&7Drops increase with every level!")
        ));

        info.setItemMeta(infoMeta);
        inventory.setItem(13, info);

        // UPGRADE

        ItemStack upgrade = new ItemStack(Material.NETHER_STAR);
        ItemMeta upgradeMeta = upgrade.getItemMeta();

        upgradeMeta.setDisplayName(color("&a&lUPGRADE SPAWNER"));

        upgradeMeta.setLore(Arrays.asList(
                color("&7Current Level: &f" + data.level),
                color("&7Next Level: &f" + Math.min(data.level + 1, MAX_LEVEL)),
                color("&7Upgrade Cost: &a$2,000"),
                color("&eEvery upgrade increases earnings and drops!"),
                color(data.level >= MAX_LEVEL
                        ? "&cMaximum level reached!"
                        : "&aClick to upgrade!")
        ));

        setAction(upgradeMeta, "upgrade");
        upgrade.setItemMeta(upgradeMeta);
        inventory.setItem(11, upgrade);

        // COLLECT MONEY

        ItemStack money = new ItemStack(Material.GOLD_INGOT);
        ItemMeta moneyMeta = money.getItemMeta();

        moneyMeta.setDisplayName(color("&a&lCOLLECT EARNINGS"));

        moneyMeta.setLore(Arrays.asList(
                color("&7Stored: &a$" + format(data.storedMoney)),
                color("&eClick to collect!")
        ));

        setAction(moneyMeta, "money");
        money.setItemMeta(moneyMeta);
        inventory.setItem(15, money);

        // STORED DROPS

        int slot = 0;

        for (Map.Entry<String, Integer> entry : data.drops.entrySet()) {

            if (slot >= 9) {
                break;
            }

            Material material = Material.matchMaterial(entry.getKey());

            if (material == null || entry.getValue() <= 0) {
                continue;
            }

            ItemStack drop = new ItemStack(
                    material,
                    Math.min(entry.getValue(), material.getMaxStackSize())
            );

            ItemMeta dropMeta = drop.getItemMeta();

            dropMeta.setDisplayName(color(
                    "&e" + formatName(material.name())
            ));

            dropMeta.setLore(Arrays.asList(
                    color("&7Stored: &f" + entry.getValue()),
                    color("&aClick to collect this drop!")
            ));

            setAction(dropMeta, "drop:" + material.name());

            drop.setItemMeta(dropMeta);
            inventory.setItem(slot++, drop);
        }

        // COLLECT ALL

        ItemStack collectAll = new ItemStack(Material.HOPPER);
        ItemMeta collectMeta = collectAll.getItemMeta();

        collectMeta.setDisplayName(color("&6&lCOLLECT ALL DROPS"));

        collectMeta.setLore(Arrays.asList(
                color("&7Collect every stored mob drop."),
                color("&eClick to collect!")
        ));

        setAction(collectMeta, "all");
        collectAll.setItemMeta(collectMeta);

        inventory.setItem(22, collectAll);

        player.openInventory(inventory);
    }

    // ==========================================
    //                GUI CLICKS
    // ==========================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        if (!(event.getInventory().getHolder() instanceof SpawnerHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        String action = clicked.getItemMeta()
                .getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING);

        if (action == null) {
            return;
        }

        SpawnerData data = spawners.get(holder.blockKey);

        if (data == null || !data.owner.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        switch (action) {
            case "upgrade" -> upgrade(player, data);
            case "money" -> collectMoney(player, data);
            case "all" -> collectAll(player, data);

            default -> {
                if (action.startsWith("drop:")) {
                    collectDrop(player, data, action.substring(5));
                }
            }
        }

        Block block = getBlock(holder.blockKey);

        if (block != null && block.getType() == Material.SPAWNER) {
            openSpawnerGUI(player, block, data);
        } else {
            player.closeInventory();
        }

        saveSpawners();
    }

    // ==========================================
    //                  UPGRADE
    // ==========================================

    private void upgrade(Player player, SpawnerData data) {

        if (data.level >= MAX_LEVEL) {
            player.sendMessage(color(
                    "&cYour spawner is already level 100!"
            ));
            return;
        }

        Economy economy = plugin.getEconomy();

        if (economy == null) {
            player.sendMessage(color("&cEconomy is unavailable!"));
            return;
        }

        if (!economy.has(player, UPGRADE_COST)) {
            player.sendMessage(color("&cYou need $2,000 to upgrade!"));
            return;
        }

        var response = economy.withdrawPlayer(player, UPGRADE_COST);

        if (!response.transactionSuccess()) {
            player.sendMessage(color("&cPayment failed!"));
            return;
        }

        data.level++;

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aSpawner upgraded to level &d"
                        + data.level + "&a!"
        ));
    }

    // ==========================================
    //              COLLECT MONEY
    // ==========================================

    private void collectMoney(Player player, SpawnerData data) {

        if (data.storedMoney <= 0) {
            player.sendMessage(color("&cThere are no earnings to collect!"));
            return;
        }

        Economy economy = plugin.getEconomy();

        if (economy == null) {
            player.sendMessage(color("&cEconomy is unavailable!"));
            return;
        }

        double amount = data.storedMoney;

        var response = economy.depositPlayer(player, amount);

        if (!response.transactionSuccess()) {
            player.sendMessage(color("&cPayment failed!"));
            return;
        }

        data.storedMoney = 0;

        player.sendMessage(color(
                "&aCollected $" + format(amount) + "!"
        ));
    }

    // ==========================================
    //               COLLECT ONE DROP
    // ==========================================

    private void collectDrop(
            Player player,
            SpawnerData data,
            String materialName
    ) {

        Material material = Material.matchMaterial(materialName);

        if (material == null) {
            return;
        }

        int amount = data.drops.getOrDefault(material.name(), 0);

        if (amount <= 0) {
            player.sendMessage(color("&cNo drops available!"));
            return;
        }

        int collected = giveItems(player, material, amount);

        if (collected <= 0) {
            player.sendMessage(color("&cYour inventory is full!"));
            return;
        }

        int remaining = amount - collected;

        if (remaining <= 0) {
            data.drops.remove(material.name());
        } else {
            data.drops.put(material.name(), remaining);
        }

        player.sendMessage(color(
                "&aCollected " + collected + " "
                        + formatName(material.name()) + "!"
        ));
    }

    // ==========================================
    //              COLLECT ALL DROPS
    // ==========================================

    private void collectAll(Player player, SpawnerData data) {

        if (data.drops.isEmpty()) {
            player.sendMessage(color("&cThere are no drops to collect!"));
            return;
        }

        int total = 0;

        for (String name : new ArrayList<>(data.drops.keySet())) {

            Material material = Material.matchMaterial(name);

            if (material == null) {
                continue;
            }

            int amount = data.drops.getOrDefault(name, 0);
            int collected = giveItems(player, material, amount);

            int remaining = amount - collected;

            if (remaining <= 0) {
                data.drops.remove(name);
            } else {
                data.drops.put(name, remaining);
            }

            total += collected;
        }

        player.sendMessage(color(
                "&aCollected " + total + " mob drops!"
        ));
    }

    private int giveItems(
            Player player,
            Material material,
            int amount
    ) {

        int remaining = amount;

        while (remaining > 0) {

            int stackSize = Math.min(
                    remaining,
                    material.getMaxStackSize()
            );

            ItemStack stack = new ItemStack(material, stackSize);

            Map<Integer, ItemStack> leftovers =
                    player.getInventory().addItem(stack);

            if (!leftovers.isEmpty()) {

                int leftoverAmount = leftovers.values().stream()
                        .mapToInt(ItemStack::getAmount)
                        .sum();

                return amount - remaining + stackSize - leftoverAmount;
            }

            remaining -= stackSize;
        }

        return amount;
    }

    // ==========================================
    //           GENERATE MONEY AND DROPS
    // ==========================================

    private void generate() {

        for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {

            Block block = getBlock(entry.getKey());

            if (block == null || !block.getChunk().isLoaded()) {
                continue;
            }

            if (block.getType() != Material.SPAWNER) {
                continue;
            }

            SpawnerData data = entry.getValue();

            data.storedMoney += data.level * BASE_EARNINGS;

            Material drop = getMobDrop(data.mob);

            if (drop != null) {

                int current = data.drops.getOrDefault(drop.name(), 0);

                int newAmount = Math.min(
                        STORAGE_LIMIT,
                        current + data.level
                );

                data.drops.put(drop.name(), newAmount);
            }
        }
    }

    private Material getMobDrop(EntityType mob) {

        return switch (mob) {

            case ZOMBIE, HUSK, DROWNED, ZOMBIFIED_PIGLIN ->
                    Material.ROTTEN_FLESH;

            case SKELETON, STRAY, BOGGED ->
                    Material.BONE;

            case CREEPER ->
                    Material.GUNPOWDER;

            case SPIDER, CAVE_SPIDER ->
                    Material.STRING;

            case BLAZE ->
                    Material.BLAZE_ROD;

            case ENDERMAN ->
                    Material.ENDER_PEARL;

            case WITCH ->
                    Material.REDSTONE;

            case SLIME ->
                    Material.SLIME_BALL;

            case MAGMA_CUBE ->
                    Material.MAGMA_CREAM;

            case GUARDIAN, ELDER_GUARDIAN ->
                    Material.PRISMARINE_SHARD;

            case PIG ->
                    Material.PORKCHOP;

            case COW, MOOSHROOM ->
                    Material.BEEF;

            case SHEEP ->
                    Material.MUTTON;

            case CHICKEN ->
                    Material.CHICKEN;

            case RABBIT ->
                    Material.RABBIT;

            case PHANTOM ->
                    Material.PHANTOM_MEMBRANE;

            case WITHER_SKELETON ->
                    Material.COAL;

            case PIGLIN ->
                    Material.GOLD_NUGGET;

            default -> null;
        };
    }

    // ==========================================
    //             BREAK SPAWNER
    // ==========================================

    @EventHandler
    public void onBreak(BlockBreakEvent event) {

        Block block = event.getBlock();

        if (block.getType() != Material.SPAWNER) {
            return;
        }

        String key = getKey(block);
        SpawnerData data = spawners.get(key);

        if (data == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!data.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(color(
                    "&cYou cannot break someone else's spawner!"
            ));
            return;
        }

        event.setDropItems(false);

        block.getWorld().dropItemNaturally(
                block.getLocation(),
                createSpawnerItem(data)
        );

        for (Map.Entry<String, Integer> entry : data.drops.entrySet()) {

            Material material = Material.matchMaterial(entry.getKey());

            if (material == null) {
                continue;
            }

            int remaining = entry.getValue();

            while (remaining > 0) {

                int amount = Math.min(
                        remaining,
                        material.getMaxStackSize()
                );

                block.getWorld().dropItemNaturally(
                        block.getLocation(),
                        new ItemStack(material, amount)
                );

                remaining -= amount;
            }
        }

        Economy economy = plugin.getEconomy();

        if (economy != null && data.storedMoney > 0) {
            economy.depositPlayer(player, data.storedMoney);
        }

        spawners.remove(key);
        saveSpawners();

        player.sendMessage(color(
                "&aSpawner broken. Your stored drops and money were returned!"
        ));
    }

    // ==========================================
    //             CREATE SPAWNER ITEM
    // ==========================================

    private ItemStack createSpawnerItem(SpawnerData data) {

        ItemStack item = new ItemStack(Material.SPAWNER);

        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();

        if (meta.getBlockState() instanceof CreatureSpawner spawner) {

            spawner.setSpawnedType(data.mob);
            meta.setBlockState(spawner);
        }

        meta.getPersistentDataContainer().set(
                spawnerItemKey,
                PersistentDataType.STRING,
                data.id
        );

        meta.setDisplayName(color(
                "&d&l" + formatName(data.mob.name()) + " Spawner"
        ));

        item.setItemMeta(meta);

        return item;
    }

    // ==========================================
    //                 SAVE DATA
    // ==========================================

    public void saveSpawners() {

        dataFile.set("spawners", null);

        for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {

            String path = "spawners." + entry.getKey();
            SpawnerData data = entry.getValue();

            dataFile.set(path + ".mob", data.mob.name());
            dataFile.set(path + ".id", data.id);
            dataFile.set(path + ".owner", data.owner.toString());
            dataFile.set(path + ".level", data.level);
            dataFile.set(path + ".money", data.storedMoney);
            dataFile.set(path + ".drops", data.drops);
        }

        try {
            dataFile.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe(
                    "Could not save spawners.yml: " + e.getMessage()
            );
        }
    }

    // ==========================================
    //                  LOAD DATA
    // ==========================================

    private void loadSpawners() {

        ConfigurationSection section =
                dataFile.getConfigurationSection("spawners");

        if (section == null) {
            return;
        }

        for (String savedKey : section.getKeys(false)) {

            String path = "spawners." + savedKey;

            try {

                EntityType mob = EntityType.valueOf(
                        dataFile.getString(path + ".mob")
                );

                String id = dataFile.getString(path + ".id");

                UUID owner = UUID.fromString(
                        dataFile.getString(path + ".owner")
                );

                SpawnerData data = new SpawnerData(mob, id, owner);

                data.level = Math.max(
                        1,
                        Math.min(
                                MAX_LEVEL,
                                dataFile.getInt(path + ".level", 1)
                        )
                );

                data.storedMoney = Math.max(
                        0,
                        dataFile.getDouble(path + ".money", 0)
                );

                ConfigurationSection drops =
                        dataFile.getConfigurationSection(path + ".drops");

                if (drops != null) {

                    for (String material : drops.getKeys(false)) {

                        int amount = Math.max(
                                0,
                                drops.getInt(material)
                        );

                        if (amount > 0) {
                            data.drops.put(material, amount);
                        }
                    }
                }

                String normalizedKey = normalizeKey(savedKey);

                spawners.put(normalizedKey, data);

            } catch (Exception e) {

                plugin.getLogger().warning(
                        "Could not load spawner data: " + savedKey
                );
            }
        }
    }

    // ==========================================
    //                   HELPERS
    // ==========================================

    private String getKey(Block block) {

        return block.getWorld().getUID() + ":"
                + block.getX() + ":"
                + block.getY() + ":"
                + block.getZ();
    }

    private String normalizeKey(String key) {

        String[] parts = key.contains(":")
                ? key.split(":")
                : key.split("_");

        if (parts.length != 4) {
            return key;
        }

        return parts[0] + ":"
                + parts[1] + ":"
                + parts[2] + ":"
                + parts[3];
    }

    private Block getBlock(String key) {

        try {

            String[] parts = key.contains(":")
                    ? key.split(":")
                    : key.split("_");

            if (parts.length != 4) {
                return null;
            }

            UUID worldId = UUID.fromString(parts[0]);

            World world = Bukkit.getWorld(worldId);

            if (world == null) {
                return null;
            }

            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);

            return world.getBlockAt(x, y, z);

        } catch (Exception e) {
            return null;
        }
    }

    private int getDropTotal(SpawnerData data) {

        return data.drops.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private void setAction(ItemMeta meta, String action) {

        meta.getPersistentDataContainer().set(
                actionKey,
                PersistentDataType.STRING,
                action
        );
    }

    private String formatName(String name) {

        String[] words = name.toLowerCase(Locale.ROOT).split("_");

        StringBuilder result = new StringBuilder();

        for (String word : words) {

            if (result.length() > 0) {
                result.append(" ");
            }

            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }

        return result.toString();
    }

    private String format(double amount) {
        return String.format(Locale.US, "%,.0f", amount);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // ==========================================
    //                  DATA CLASS
    // ==========================================

    private static class SpawnerData {

        private final EntityType mob;
        private final String id;
        private final UUID owner;

        private int level = 1;
        private double storedMoney = 0;

        private final Map<String, Integer> drops =
                new LinkedHashMap<>();

        private SpawnerData(
                EntityType mob,
                String id,
                UUID owner
        ) {
            this.mob = mob;
            this.id = id;
            this.owner = owner;
        }
    }

    // ==========================================
    //                 GUI HOLDER
    // ==========================================

    private static class SpawnerHolder implements InventoryHolder {

        private Inventory inventory;
        private String blockKey;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
