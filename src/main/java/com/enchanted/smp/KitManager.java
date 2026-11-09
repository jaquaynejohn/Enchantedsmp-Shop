
package com.enchanted.smp;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KitManager implements Listener {

    private final EnchantedSMP plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey kitKey;

    private LuckPerms luckPerms;

    private static final long COOLDOWN = 15 * 60 * 1000L;

    public KitManager(EnchantedSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.kitKey = new NamespacedKey(plugin, "kit_action");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not connect to LuckPerms.");
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openKits(Player player) {
        if (luckPerms == null) {
            player.sendMessage(ChatColor.RED + "LuckPerms is required for kits.");
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                new KitHolder(),
                54,
                ChatColor.DARK_GREEN + "EnchantedSMP Kits"
        );

        List<Group> groups = new ArrayList<>(
                luckPerms.getGroupManager().getLoadedGroups()
        );

        groups.sort(
                Comparator.comparingInt(this::getWeight).reversed()
        );

        int slot = 10;

        for (Group group : groups) {
            if (slot >= 44) break;

            String name = group.getName();
            boolean allowed = player.isOp() || isPlayersRank(player, name);

            ItemStack icon = new ItemStack(
                    allowed ? getRankIcon(name) : Material.GRAY_DYE
            );

            ItemMeta meta = icon.getItemMeta();

            meta.setDisplayName(
                    getRankColor(group) + ChatColor.BOLD.toString() + name
            );

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to claim this rank's kit");

            if (!allowed) {
                lore.add(ChatColor.RED + "You cannot claim this kit.");
            } else if (player.isOp() || hasNoCooldown(group)) {
                lore.add(ChatColor.GREEN + "No cooldown");
            } else {
                lore.add(ChatColor.YELLOW + "15 minute cooldown");
            }

            meta.setLore(lore);

            meta.getPersistentDataContainer().set(
                    kitKey,
                    PersistentDataType.STRING,
                    name
            );

            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);

            slot++;

            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
            if (slot == 35) slot = 37;
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
    }

    @EventHandler
    public void onKitClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof KitHolder)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.hasItemMeta()) return;

        String rank = clicked.getItemMeta()
                .getPersistentDataContainer()
                .get(kitKey, PersistentDataType.STRING);

        if (rank == null) return;

        claimKit(player, rank);
    }

    private void claimKit(Player player, String rank) {
        Group group = luckPerms.getGroupManager().getGroup(rank);

        if (group == null) {
            player.sendMessage(ChatColor.RED + "That rank no longer exists.");
            return;
        }

        if (!player.isOp() && !isPlayersRank(player, rank)) {
            player.sendMessage(ChatColor.RED + "You cannot claim this kit.");
            return;
        }

        boolean bypass = player.isOp() || hasNoCooldown(group);
        String path = "cooldowns." + player.getUniqueId() + "." + rank;

        if (!bypass) {
            long lastClaim = data.getLong(path, 0);
            long remaining = COOLDOWN - (System.currentTimeMillis() - lastClaim);

            if (remaining > 0) {
                long minutes = (remaining + 59999) / 60000;

                player.sendMessage(
                        ChatColor.RED + "Wait " + minutes
                                + " minute(s) before claiming again."
                );
                return;
            }
        }

        giveKit(player, group);

        if (!bypass) {
            data.set(path, System.currentTimeMillis());
            save();
        }

        player.closeInventory();
        player.sendMessage(
                ChatColor.GREEN + "You claimed the " + rank + " kit!"
        );
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    private void giveKit(Player player, Group group) {
        boolean netherite = isNetheriteRank(group);

        Material helmet = netherite ? Material.NETHERITE_HELMET : Material.DIAMOND_HELMET;
        Material chest = netherite ? Material.NETHERITE_CHESTPLATE : Material.DIAMOND_CHESTPLATE;
        Material leggings = netherite ? Material.NETHERITE_LEGGINGS : Material.DIAMOND_LEGGINGS;
        Material boots = netherite ? Material.NETHERITE_BOOTS : Material.DIAMOND_BOOTS;

        TrimMaterial trim = getTrimMaterial(group);

        giveArmor(player, helmet, trim, 0);
        giveArmor(player, chest, trim, 1);
        giveArmor(player, leggings, trim, 2);
        giveArmor(player, boots, trim, 3);

        give(player, enchanted(
                Material.NETHERITE_SWORD,
                Enchantment.SHARPNESS, 5,
                Enchantment.UNBREAKING, 3,
                Enchantment.FIRE_ASPECT, 2
        ));

        give(player, enchanted(
                Material.NETHERITE_PICKAXE,
                Enchantment.EFFICIENCY, 5,
                Enchantment.UNBREAKING, 3,
                Enchantment.MENDING, 1
        ));

        give(player, named(Material.END_CRYSTAL, 64, "CPvP Crystals"));
        give(player, named(Material.OBSIDIAN, 64, "CPvP Obsidian"));
        give(player, named(Material.TOTEM_OF_UNDYING, 8, "Totems"));
        give(player, named(Material.GOLDEN_APPLE, 32, "Golden Apples"));
        give(player, named(Material.ENCHANTED_GOLDEN_APPLE, 4, "Enchanted Apples"));
        give(player, named(Material.ENDER_PEARL, 16, "Ender Pearls"));
        give(player, named(Material.RESPAWN_ANCHOR, 16, "Respawn Anchors"));
        give(player, named(Material.GLOWSTONE, 32, "Glowstone"));
        give(player, named(Material.EXPERIENCE_BOTTLE, 64, "Experience Bottles"));
    }

    private void giveArmor(Player player, Material material, TrimMaterial trim, int type) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(trim, TrimPattern.SENTRY));
        }

        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.THORNS, 3, true);

        if (type == 3) {
            meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
            meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
            meta.addEnchant(Enchantment.SOUL_SPEED, 3, true);
        }

        if (type == 1) {
            meta.addEnchant(Enchantment.SWIFT_SNEAK, 3, true);
        }

        item.setItemMeta(meta);
        give(player, item);
    }

    private ItemStack enchanted(
            Material material,
            Enchantment enchantment1, int level1,
            Enchantment enchantment2, int level2,
            Enchantment enchantment3, int level3
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.addEnchant(enchantment1, level1, true);
        meta.addEnchant(enchantment2, level2, true);
        meta.addEnchant(enchantment3, level3, true);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + name);
        item.setItemMeta(meta);

        return item;
    }

    private void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(
                leftover -> player.getWorld().dropItemNaturally(
                        player.getLocation(), leftover
                )
        );
    }

    private boolean isPlayersRank(Player player, String rank) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());

        if (user == null) return false;

        return user.getInheritedGroups(
                QueryOptions.defaultContextualOptions()
        ).stream().anyMatch(group ->
                group.getName().equalsIgnoreCase(rank)
        );
    }

    private int getWeight(Group group) {
        return group.getWeight().orElse(0);
    }

    private Group findThreshold(String rankName) {
        return luckPerms.getGroupManager().getGroup(rankName);
    }

    private boolean hasNoCooldown(Group group) {
        Group admin = findThreshold("admin");

        if (admin == null) {
            String name = group.getName().toLowerCase();
            return name.contains("owner") || name.contains("admin");
        }

        return getWeight(group) >= getWeight(admin);
    }

    private boolean isNetheriteRank(Group group) {
        Group mod = findThreshold("mod");

        if (mod == null) {
            String name = group.getName().toLowerCase();
            return name.contains("owner") || name.contains("admin")
                    || name.contains("mod");
        }

        return getWeight(group) >= getWeight(mod);
    }

    private Material getRankIcon(String rank) {
        String name = rank.toLowerCase();

        if (name.contains("owner")) return Material.COMMAND_BLOCK;
        if (name.contains("admin")) return Material.ENCHANTED_GOLDEN_APPLE;
        if (name.contains("mod")) return Material.NETHERITE_CHESTPLATE;

        return Material.DIAMOND_CHESTPLATE;
    }

    private ChatColor getRankColor(Group group) {
        String prefix = group.getCachedData()
                .getMetaData(QueryOptions.defaultContextualOptions())
                .getPrefix();

        if (prefix == null) prefix = "";

        String value = prefix.toLowerCase();

        if (value.contains("dark_red") || value.contains("<red>")) return ChatColor.RED;
        if (value.contains("gold") || value.contains("<gold>")) return ChatColor.GOLD;
        if (value.contains("yellow") || value.contains("<yellow>")) return ChatColor.YELLOW;
        if (value.contains("green") || value.contains("<green>")) return ChatColor.GREEN;
        if (value.contains("aqua") || value.contains("<aqua>")) return ChatColor.AQUA;
        if (value.contains("blue") || value.contains("<blue>")) return ChatColor.BLUE;
        if (value.contains("light_purple") || value.contains("<light_purple>")) return ChatColor.LIGHT_PURPLE;
        if (value.contains("dark_purple") || value.contains("<dark_purple>")) return ChatColor.DARK_PURPLE;
        if (value.contains("white") || value.contains("<white>")) return ChatColor.WHITE;
        if (value.contains("gray") || value.contains("<gray>")) return ChatColor.GRAY;

        return ChatColor.GREEN;
    }

    private TrimMaterial getTrimMaterial(Group group) {
        ChatColor color = getRankColor(group);

        return switch (color) {
            case RED, DARK_RED -> TrimMaterial.REDSTONE;
            case GOLD, YELLOW -> TrimMaterial.GOLD;
            case GREEN, DARK_GREEN -> TrimMaterial.EMERALD;
            case AQUA, DARK_AQUA -> TrimMaterial.DIAMOND;
            case BLUE, DARK_BLUE -> TrimMaterial.LAPIS;
            case LIGHT_PURPLE, DARK_PURPLE -> TrimMaterial.AMETHYST;
            case WHITE -> TrimMaterial.QUARTZ;
            case GRAY, DARK_GRAY -> TrimMaterial.IRON;
            default -> TrimMaterial.NETHERITE;
        };
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save kits.yml!");
        }
    }

    private static class KitHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
