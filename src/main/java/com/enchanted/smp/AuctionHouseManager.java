
package com.enchanted.smp;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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

public class AuctionHouseManager implements Listener {

    private static final long EXPIRY_MILLIS =
            24L * 60 * 60 * 1000;

    private final EnchantedSMP plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey listingKey;

    public AuctionHouseManager(EnchantedSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctionhouse.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.listingKey = new NamespacedKey(plugin, "auction_listing");

        plugin.getDataFolder().mkdirs();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openAuctionHouse(Player player) {
        AuctionHolder holder = new AuctionHolder();

        Inventory inventory = Bukkit.createInventory(
                holder, 54, color("&5&lENCHANTEDSMP AUCTION HOUSE")
        );

        holder.inventory = inventory;

        ConfigurationSection section =
                data.getConfigurationSection("listings");

        if (section != null) {
            int slot = 0;

            for (String id : section.getKeys(false)) {
                if (slot >= 45) break;

                String path = "listings." + id;

                if (isExpired(path)) continue;

                ItemStack item = data.getItemStack(path + ".item");

                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }

                ItemStack display = item.clone();
                ItemMeta meta = display.getItemMeta();

                List<String> lore = meta.hasLore()
                        ? new ArrayList<>(meta.getLore())
                        : new ArrayList<>();

                lore.add(color("&8&m----------------"));
                lore.add(color("&7Seller: &f" +
                        data.getString(path + ".seller-name")));
                lore.add(color("&7Price: &a$" +
                        data.getDouble(path + ".price")));
                lore.add(color("&7Expires: &f" +
                        getRemainingTime(path)));
                lore.add(color("&eClick to purchase!"));

                meta.setLore(lore);

                meta.getPersistentDataContainer().set(
                        listingKey,
                        PersistentDataType.STRING,
                        id
                );

                display.setItemMeta(meta);
                inventory.setItem(slot++, display);
            }
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();

        infoMeta.setDisplayName(color("&d&lAUCTION HOUSE"));

        infoMeta.setLore(List.of(
                color("&7Buy items listed by other players."),
                color("&7Use &f/ah sell <price> &7to list an item."),
                color("&7Listings expire after 24 hours."),
                color("&7Use &f/ah collect &7to collect expired items.")
        ));

        info.setItemMeta(infoMeta);
        inventory.setItem(49, info);

        player.openInventory(inventory);
    }

    public boolean listItem(Player player, double price) {
        if (price <= 0 || !Double.isFinite(price)) {
            player.sendMessage(color("&cEnter a valid price."));
            return false;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.getType() == Material.AIR) {
            player.sendMessage(color("&cHold the item you want to sell."));
            return false;
        }

        String id = UUID.randomUUID().toString();
        String path = "listings." + id;

        data.set(path + ".seller", player.getUniqueId().toString());
        data.set(path + ".seller-name", player.getName());
        data.set(path + ".price", price);
        data.set(path + ".item", hand.clone());
        data.set(path + ".expires-at",
                System.currentTimeMillis() + EXPIRY_MILLIS);

        try {
            save();
        } catch (RuntimeException e) {
            data.set(path, null);
            player.sendMessage(color("&cCould not save your listing."));
            return false;
        }

        hand.setAmount(0);

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aYour item was listed for &f$"
                        + price + "&a!"
        ));

        player.sendMessage(color(
                "&7Your auction expires in 24 hours."
        ));

        return true;
    }

    public void collectExpired(Player player) {
        ConfigurationSection section =
                data.getConfigurationSection("listings");

        if (section == null) {
            player.sendMessage(color("&7You have no expired items to collect."));
            return;
        }

        int collected = 0;
        int remaining = 0;

        for (String id : new ArrayList<>(section.getKeys(false))) {
            String path = "listings." + id;

            if (!player.getUniqueId().toString().equals(
                    data.getString(path + ".seller"))) {
                continue;
            }

            if (!isExpired(path)) continue;

            ItemStack item = data.getItemStack(path + ".item");

            if (item == null || item.getType() == Material.AIR) {
                data.set(path, null);
                continue;
            }

            var leftovers = player.getInventory().addItem(item.clone());

            if (leftovers.isEmpty()) {
                data.set(path, null);
                collected += item.getAmount();
            } else {
                int leftoverAmount = 0;

                for (ItemStack leftover : leftovers.values()) {
                    leftoverAmount += leftover.getAmount();
                }

                data.set(path + ".item", leftovers.values().iterator().next());
                collected += item.getAmount() - leftoverAmount;
                remaining++;
            }
        }

        save();

        if (collected == 0 && remaining == 0) {
            player.sendMessage(color(
                    "&7You have no expired items to collect."
            ));
            return;
        }

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aCollected &f"
                        + collected + " &aitem(s) from expired auctions."
        ));

        if (remaining > 0) {
            player.sendMessage(color(
                    "&eYour inventory was full. Use &f/ah collect"
                            + " &eagain to collect the remaining items."
            ));
        }
    }

    @EventHandler
    public void onAuctionClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AuctionHolder)) {
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
                .get(listingKey, PersistentDataType.STRING);

        if (id == null) return;

        String path = "listings." + id;

        if (!data.contains(path)) {
            player.sendMessage(color("&cThis listing is no longer available."));
            openAuctionHouse(player);
            return;
        }

        if (isExpired(path)) {
            player.sendMessage(color("&cThis auction has expired!"));
            openAuctionHouse(player);
            return;
        }

        UUID sellerId;

        try {
            sellerId = UUID.fromString(
                    data.getString(path + ".seller", "")
            );
        } catch (IllegalArgumentException e) {
            player.sendMessage(color("&cInvalid seller data."));
            return;
        }

        if (sellerId.equals(player.getUniqueId())) {
            player.sendMessage(color("&cYou cannot buy your own listing."));
            return;
        }

        double price = data.getDouble(path + ".price");
        ItemStack item = data.getItemStack(path + ".item");

        if (item == null || item.getType() == Material.AIR
                || price <= 0 || !Double.isFinite(price)) {
            player.sendMessage(color("&cThis listing is invalid."));
            return;
        }

        Inventory preview = Bukkit.createInventory(null, 36);
        preview.setStorageContents(
                player.getInventory().getStorageContents()
        );

        if (!preview.addItem(item.clone()).isEmpty()) {
            player.sendMessage(color("&cYour inventory is full!"));
            return;
        }

        if (plugin.getEconomy().getBalance(player) < price) {
            player.sendMessage(color("&cYou don't have enough money."));
            return;
        }

        EconomyResponse payment =
                plugin.getEconomy().withdrawPlayer(player, price);

        if (!payment.transactionSuccess()) {
            player.sendMessage(color("&cPayment failed."));
            return;
        }

        EconomyResponse payout = plugin.getEconomy().depositPlayer(
                Bukkit.getOfflinePlayer(sellerId), price
        );

        if (!payout.transactionSuccess()) {
            plugin.getEconomy().depositPlayer(player, price);
            player.sendMessage(color(
                    "&cPurchase failed. Your money was refunded."
            ));
            return;
        }

        data.set(path, null);

        try {
            save();
        } catch (RuntimeException e) {
            plugin.getEconomy().withdrawPlayer(
                    Bukkit.getOfflinePlayer(sellerId), price
            );
            plugin.getEconomy().depositPlayer(player, price);
            player.sendMessage(color("&cPurchase failed. Your money was refunded."));
            return;
        }

        player.getInventory().addItem(item.clone());

        player.sendMessage(color(
                "&5&lENCHANTEDSMP &8» &aYou bought the item for &f$"
                        + price + "&a!"
        ));

        OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerId);

        if (seller.isOnline()) {
            seller.getPlayer().sendMessage(color(
                    "&5&lENCHANTEDSMP &8» &aYour auction sold for &f$"
                            + price + "&a!"
            ));
        }

        openAuctionHouse(player);
    }

    private boolean isExpired(String path) {
        long expiresAt = data.getLong(path + ".expires-at", 0);

        // Give older listings a fresh 24-hour expiry.
        if (expiresAt <= 0) {
            expiresAt = System.currentTimeMillis() + EXPIRY_MILLIS;
            data.set(path + ".expires-at", expiresAt);
        }

        return System.currentTimeMillis() >= expiresAt;
    }

    private String getRemainingTime(String path) {
        long expiresAt = data.getLong(path + ".expires-at", 0);
        long remaining = Math.max(0,
                expiresAt - System.currentTimeMillis());

        long hours = remaining / 3600000;
        long minutes = (remaining % 3600000) / 60000;

        return hours + "h " + minutes + "m";
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save auctionhouse.yml!", e
            );
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static class AuctionHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
