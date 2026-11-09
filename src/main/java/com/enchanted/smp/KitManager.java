
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class KitManager implements Listener {

    private final EnchantedSMP plugin;
    private LuckPerms luckPerms;

    private final File file;
    private final YamlConfiguration data;

    private final Map<String, Group> menuGroups = new HashMap<>();

    private static final long COOLDOWN = TimeUnit.MINUTES.toMillis(15);

    public KitManager(EnchantedSMP plugin) {
        this.plugin = plugin;

        LuckPerms found;
        try {
            found = LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            found = null;
            plugin.getLogger().warning("LuckPerms was not found. Rank kits are disabled.");
        }
        this.luckPerms = found;

        file = new File(plugin.getDataFolder(), "kits.yml");
        data = YamlConfiguration.loadConfiguration(file);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openKits(Player player) {
        if (luckPerms == null) {
            player.sendMessage(ChatColor.RED + "LuckPerms is not available.");
            return;
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());

        if (user == null) {
            player.sendMessage(ChatColor.RED + "Your rank could not be loaded.");
            return;
        }

        Group highest = getHighestRank(user);

        if (highest == null && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "No rank was found for you.");
            return;
        }

        List<Group> groups = new ArrayList<>(
                luckPerms.getGroupManager().getLoadedGroups()
        );

        groups.sort(
                Comparator.comparingInt(this::getWeight).reversed()
        );

        menuGroups.clear();

        KitHolder holder = new KitHolder();
        Inventory inventory = Bukkit.createInventory(
                holder, 54, ChatColor.DARK_GREEN + "EnchantedSMP Kits"
        );
        holder.inventory = inventory;

        int slot = 0;

        for (Group group : groups) {
            if (slot >= 45) break;

            boolean unlocked = player.isOp()
                    || (highest != null && highest.getName().equalsIgnoreCase(group.getName()));

            ItemStack icon;

            if (unlocked) {
                icon = new ItemStack(Material.EMERALD_BLOCK);
            } else {
                icon = new ItemStack(Material.BARRIER);
            }

            ItemMeta meta = icon.getItemMeta();

            meta.setDisplayName(
                    getRankColor(group) + ChatColor.BOLD
                            + capitalize(group.getName())
            );

            List<String> lore = new ArrayList<>();

            if (unlocked) {
                lore.add(ChatColor.GREEN + "Unlocked!");
                lore.add(ChatColor.YELLOW + "Click to claim this kit");
            } else {
                lore.add(ChatColor.RED + "LOCKED");
                lore.add(ChatColor.GRAY + "You cannot claim this rank's kit.");
            }

            if (player.isOp()) {
                lore.add(ChatColor.DARK_PURPLE + "OP access");
            }

            meta.setLore(lore);
            icon.setItemMeta(meta);

            inventory.setItem(slot, icon);
            menuGroups.put(group.getName().toLowerCase(), group);

            slot++;
        }

        player.openInventory(inventory);
    }

    private Group getHighestRank(User user) {
        return user.getInheritedGroups(
                QueryOptions.defaultContextualOptions()
        ).stream()
                .max(Comparator.comparingInt(this::getWeight))
                .orElseGet(() ->
                        luckPerms.getGroupManager().getGroup(user.getPrimaryGroup())
                                .orElse(null)
                );
    }

    private int getWeight(Group group) {
        return group.getWeight().orElse(0);
    }

    @EventHandler
    public void onKitClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof KitHolder)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (luckPerms == null) return;

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());

        if (user == null) return;

        Group highest = getHighestRank(user);

        Group selected = null;

        for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
            if (group.getName().equalsIgnoreCase(
                    ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                            .replace(" ", "")
            )) {
                selected = group;
                break;
            }
        }

        if (selected == null) {
            selected = findGroupFromIcon(clicked);
        }

        if (selected == null) return;

        boolean unlocked = player.isOp()
                || (highest != null && highest.getName().equalsIgnoreCase(selected.getName()));

        if (!unlocked) {
            player.sendMessage(ChatColor.RED + "That rank's kit is locked!");
            return;
        }

        if (!player.isOp() && !hasNoCooldown(selected)
                && !canClaim(player, selected)) {
            long remaining = getRemaining(player, selected);
            player.sendMessage(ChatColor.RED + "You must wait "
                    + formatTime(remaining) + " before claiming again.");
            return;
        }

        giveKit(player, selected);

        if (!player.isOp() && !hasNoCooldown(selected)) {
            setCooldown(player, selected);
        }

        player.sendMessage(ChatColor.GREEN + "You claimed the "
                + capitalize(selected.getName()) + " kit!");

        player.closeInventory();
    }

    private Group findGroupFromIcon(ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return null;

        String display = ChatColor.stripColor(
                clicked.getItemMeta().getDisplayName()
        );

        if (display == null) return null;

        String name = display.replace(" ", "");

        return luckPerms.getGroupManager().getLoadedGroups().stream()
                .filter(group -> capitalize(group.getName()).equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private boolean hasNoCooldown(Group group) {
        Group admin = luckPerms.getGroupManager().getGroup("admin").orElse(null);

        return admin != null && getWeight(group) >= getWeight(admin);
    }

    private boolean isNetheriteRank(Group group) {
        Group mod = luckPerms.getGroupManager().getGroup("mod").orElse(null);

        return mod != null && getWeight(group) >= getWeight(mod);
    }

    private boolean canClaim(Player player, Group group) {
        return getRemaining(player, group) <= 0;
    }

    private long getRemaining(Player player, Group group) {
        String path = getCooldownPath(player, group);
        long last = data.getLong(path, 0);

        return Math.max(0, COOLDOWN - (System.currentTimeMillis() - last));
    }

    private void setCooldown(Player player, Group group) {
        data.set(
                getCooldownPath(player, group),
                System.currentTimeMillis()
        );
        save();
    }

    private String getCooldownPath(Player player, Group group) {
        return "cooldowns." + player.getUniqueId() + "."
                + group.getName().toLowerCase();
    }

    private void giveKit(Player player, Group group) {
        boolean netherite = isNetheriteRank(group);

        Material armorMaterial = netherite
                ? Material.NETHERITE_CHESTPLATE
                : Material.DIAMOND_CHESTPLATE;

        Material[] armor = netherite
                ? new Material[]{
                    Material.NETHERITE_HELMET,
                    Material.NETHERITE_CHESTPLATE,
                    Material.NETHERITE_LEGGINGS,
                    Material.NETHERITE_BOOTS
                }
                : new Material[]{
                    Material.DIAMOND_HELMET,
                    Material.DIAMOND_CHESTPLATE,
                    Material.DIAMOND_LEGGINGS,
                    Material.DIAMOND_BOOTS
                };

        for (Material material : armor) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            addEnchant(meta, "protection", 4);
            addEnchant(meta, "unbreaking", 3);
            addEnchant(meta, "mending", 1);

            if (material.name().endsWith("_BOOTS")) {
                addEnchant(meta, "feather_falling", 4);
                addEnchant(meta, "depth_strider", 3);
            }

            if (material.name().endsWith("_HELMET")) {
                addEnchant(meta, "respiration", 3);
                addEnchant(meta, "aqua_affinity", 1);
            }

            if (meta instanceof ArmorMeta armorMeta) {
                TrimMaterial trimMaterial = getTrimMaterial(group);
                armorMeta.setTrim(new ArmorTrim(
                        trimMaterial,
                        TrimPattern.SENTRY
                ));
            }

            item.setItemMeta(meta);
            give(player, item);
        }

        give(player, enchanted(Material.NETHERITE_SWORD,
                "sharpness", 5, "unbreaking", 3, "mending", 1));

        give(player, enchanted(Material.NETHERITE_PICKAXE,
                "efficiency", 5, "unbreaking", 3, "mending", 1));

        give(player, enchanted(Material.SHIELD,
                "unbreaking", 3, "mending", 1));

        give(player, new ItemStack(Material.END_CRYSTAL, 64));
        give(player, new ItemStack(Material.OBSIDIAN, 64));
        give(player, new ItemStack(Material.TOTEM_OF_UNDYING, 8));
        give(player, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8));
        give(player, new ItemStack(Material.GOLDEN_APPLE, 32));
        give(player, new ItemStack(Material.ENDER_PEARL, 16));
        give(player, new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
    }

    private ItemStack enchanted(
            Material material,
            Object... enchantments
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        for (int i = 0; i < enchantments.length; i += 2) {
            addEnchant(meta,
                    (String) enchantments[i],
                    (Integer) enchantments[i + 1]);
        }

        item.setItemMeta(meta);
        return item;
    }

    private void addEnchant(ItemMeta meta, String name, int level) {
        Enchantment enchantment = Enchantment.getByKey(
                NamespacedKey.minecraft(name)
        );

        if (enchantment != null) {
            meta.addEnchant(enchantment, level, true);
        }
    }

    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);

        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private TrimMaterial getTrimMaterial(Group group) {
        String color = getRankColor(group).toString();

        if (color.contains("RED")) return TrimMaterial.REDSTONE;
        if (color.contains("BLUE")) return TrimMaterial.LAPIS;
        if (color.contains("GREEN")) return TrimMaterial.EMERALD;
        if (color.contains("GOLD") || color.contains("YELLOW")) return TrimMaterial.GOLD;
        if (color.contains("AQUA")) return TrimMaterial.DIAMOND;
        if (color.contains("LIGHT_PURPLE")) return TrimMaterial.AMETHYST;
        if (color.contains("DARK_PURPLE")) return TrimMaterial.AMETHYST;
        if (color.contains("WHITE")) return TrimMaterial.QUARTZ;
        if (color.contains("GRAY")) return TrimMaterial.IRON;

        return TrimMaterial.IRON;
    }

    private ChatColor getRankColor(Group group) {
        String prefix = "";

        try {
            prefix = group.getCachedData()
                    .getMetaData(QueryOptions.defaultContextualOptions())
                    .getPrefix();
        } catch (Exception ignored) {
        }

        if (prefix == null) prefix = "";

        String lower = prefix.toLowerCase();

        if (lower.contains("&c") || lower.contains("§c") || lower.contains("<red>"))
            return ChatColor.RED;

        if (lower.contains("&9") || lower.contains("§9") || lower.contains("<blue>"))
            return ChatColor.BLUE;

        if (lower.contains("&a") || lower.contains("§a") || lower.contains("<green>"))
            return ChatColor.GREEN;

        if (lower.contains("&6") || lower.contains("§6") || lower.contains("<gold>"))
            return ChatColor.GOLD;

        if (lower.contains("&b") || lower.contains("§b") || lower.contains("<aqua>"))
            return ChatColor.AQUA;

        if (lower.contains("&d") || lower.contains("§d") || lower.contains("<light_purple>"))
            return ChatColor.LIGHT_PURPLE;

        if (lower.contains("&f") || lower.contains("§f") || lower.contains("<white>"))
            return ChatColor.WHITE;

        if (lower.contains("&7") || lower.contains("§7") || lower.contains("<gray>"))
            return ChatColor.GRAY;

        return ChatColor.GREEN;
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;

        return text.substring(0, 1).toUpperCase()
                + text.substring(1).toLowerCase();
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;

        return minutes + "m " + seconds + "s";
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save kits.yml!");
            exception.printStackTrace();
        }
    }

    private static class KitHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
