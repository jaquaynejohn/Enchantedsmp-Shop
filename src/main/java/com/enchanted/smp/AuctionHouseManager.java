
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
    private final NamespacedKey actionKey;

    public AuctionHouseManager(EnchantedSMP plugin) {
        this.plugin = plugin;

        file = new File(plugin.getDataFolder(), "auctionhouse.yml");
        plugin.getDataFolder().mkdirs();

        data = YamlConfiguration.loadConfiguration(file);

        listingKey = new NamespacedKey(plugin, "auction_listing");
        actionKey = new NamespacedKey(plugin, "auction_action");

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // MAIN AUCTION HOUSE

    public void openAuctionHouse(Player player) {
        AuctionHolder holder = new AuctionHolder(false);

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
                lore.add(color("&7Seller: &f"
                        + data.getString(path + ".seller-name")));
                lore.add(color("&7Price: &a$"
                        + data.getDouble(path + ".price")));
                lore.add(color("&7Expires: &f"
                        + getRemainingTime(path)));
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

        ItemStack collectionButton =
                new ItemStack(Material.CHEST);

        ItemMeta collectionMeta =
                collectionButton.getItemMeta();

        collectionMeta.setDisplayName(
                color("&d&lEXPIRED ITEMS")
        );

        collectionMeta.setLore(List.of(
                color("&7View your expired auctions."),
                color("&7Collect your unsold items."),
                color("&eClick to open!")
        ));

        collectionMeta.getPersistentDataContainer().set(
                actionKey,
                PersistentDataType.STRING,
                "open_collection"
        );

        collectionButton.setItemMeta(collectionMeta);
        inventory.setItem(48, collectionButton);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();

        infoMeta.setDisplayName(color("&d&lAUCTION HOUSE"));

        infoMeta.setLore(List.of(
                color("&7Buy items listed by other players."),
                color("&7Use &f/ah sell <price> &7to list an item."),
                color("&7Listings expire after 24 hours."),
                color("&7Collect expired items using the chest.")
        ));

        info.setItemMeta(infoMeta);
        inventory.setItem(49, info);

        player.openInventory(inventory);
    }

    // EXPIRED ITEM COLLECTION GUI

    public void openCollectionMenu(Player player) {
        AuctionHolder holder = new AuctionHolder(true);

        Inventory inventory = Bukkit.createInventory(
                holder, 54, color("&5&lEXPIRED AUCTIONS")
        );

        holder.inventory = inventory;

        ConfigurationSection section =
                data.getConfigurationSection("listings");

        int slot = 0;

        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (slot >= 45) break;

                String path = "listings." + id;

                if (!player.getUniqueId().toString().equals(
                        data.getString(path + ".seller"))) {
                    continue;
                }

                if (!isExpired(path)) continue;

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
                lore.add(color("&cAuction expired!"));
                lore.add(color("&7Unsold item: &f"
                        + item.getAmount()));
                lore.add(color("&eClick to collect!"));

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

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();

        backMeta.setDisplayName(color("&c&lBACK"));

        backMeta.setLore(List.of(
                color("&7Return to the Auction House.")
        ));

        backMeta.getPersistentDataContainer().set(
                actionKey,
                PersistentDataType.STRING,
                "back"
        );

        back.setItemMeta(backMeta);
        inventory.setItem(45, back);

        ItemStack collectAll = new ItemStack(Material.HOPPER);
        ItemMeta collectMeta = collectAll.getItemMeta();

        collectMeta.setDisplayName(color("&a&lCOLLECT ALL"));

        collectMeta.setLore(List.of(
                color("&7Collect all your expired items."),
                color("&7Items that do not fit stay saved."),
                color("&eClick to collect everything!")
        ));

        collectMeta.getPersistentDataContainer().set(
                actionKey,
                PersistentDataType.STRING,
                "collect_all"
        );

        collectAll.setItemMeta(collectMeta);
        inventory.setItem(49, collectAll);

        player.openInventory(inventory);
    }

    // CREATE AUCTION

    public boolean listItem(Player player, double price) {
        if (price <= 0 || !Double.isFinite(price)) {
            player.sendMessage(color("&cEnter a valid price."));
            return false;
        }

        ItemStack hand =
                player.getInventory().getItemInMainHand();

        if (hand.getType() == Material.AIR) {
            player.sendMessage(color(
                    "&cHold the item you want to sell."
            ));
            return false;
        }

        String id = UUID.randomUUID().toString();
        String path = "listings." + id;

        data.set(path + ".seller",
                player.getUniqueId().toString());

        data.set(path + ".seller-name", player.getName());
        data.set(path + ".price", price);
        data.set(path + ".item", hand.clone());

        data.set(path + ".expires-at",
                System.currentTimeMillis() + EXPIRY_MILLIS);

        try {
            save();
        } catch (RuntimeException e) {
            data.set(path, null);
            player.sendMessage(color(
                    "&cCould not save your listing."
            ));
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

    // COLLECT ONE EXPIRED LISTING

    private boolean collectListing(Player player, String id) {
        String path = "listings." + id;

        if (!data.contains(path)) {
            player.sendMessage(color(
                    "&cThis expired item is no longer available."
            ));
            return false;
        }

        if (!player.getUniqueId().toString().equals(
                data.getString(path + ".seller"))) {
            player.sendMessage(color(
                    "&cThat item does not belong to you."
            ));
            return false;
        }

        if (!isExpired(path)) {
            player.sendMessage(color(
                    "&cThis auction has not expired yet."
            ));
            return false;
        }

        ItemStack item = data.getItemStack(path + ".item");

        if (item == null || item.getType() == Material.AIR) {
            data.set(path, null);
            save();
            return false;
        }

        var leftovers =
                player.getInventory().addItem(item.clone());

        if (leftovers.isEmpty()) {
            data.set(path, null);
            save();

            player.sendMessage(color(
                    "&aYou collected your expired item!"
            ));

            return true;
        }

        int remaining = 0;

        for (ItemStack leftover : leftovers.values()) {
            remaining += leftover.getAmount();
        }

        ItemStack savedItem = item.clone();
        savedItem.setAmount(remaining);

        data.set(path + ".item", savedItem);
        save();

        player.sendMessage(color(
                "&eYour inventory is full. The remaining item was saved."
        ));

        return false;
    }

    // COLLECT ALL EXPIRED LISTINGS

    public void collectExpired(Player player) {
        ConfigurationSection section =
                data.getConfigurationSection("listings");

        if (section == null) {
            player.sendMessage(color(
                    "&7You have no expired items to collect."
            ));
            return;
        }

        int collected = 0;
        int remaining = 0;

        for (String id :
                new ArrayList<>(section.getKeys(false))) {

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

            var leftovers =
                    player.getInventory().addItem(item.clone());

            if (leftovers.isEmpty()) {
                data.set(path, null);
                collected += item.getAmount();
            } else {
                int leftoverAmount = 0;

                for (ItemStack leftover : leftovers.values()) {
                    leftoverAmount += leftover.getAmount();
                }

                ItemStack savedItem = item.clone();
                savedItem.setAmount(leftoverAmount);

                data.set(path + ".item", savedItem);

                collected += item.getAmount() - leftoverAmount;
                remaining++;
            }
        }

        save();

        player.sendMessage(color(
                "&aCollected &f" + collected
                        + " &aitem(s) from expired auctions."
        ));

        if (remaining > 0) {
            player.sendMessage(color(
                    "&eSome items remain saved because your inventory is full."
            ));
        }
    }

    // INVENTORY CLICK HANDLER

    @EventHandler
    public void onAuctionClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder()
                instanceof AuctionHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot < 0 ||
                slot >= event.getInventory().getSize()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        if (holder.collection) {
            handleCollectionClick(player, clicked);
            return;
        }

        ItemMeta meta = clicked.getItemMeta();

        String action = meta.getPersistentDataContainer().get(
                actionKey,
                PersistentDataType.STRING
        );

        if ("open_collection".equals(action)) {
            openCollectionMenu(player);
            return;
        }

        String id = meta.getPersistentDataContainer().get(
                listingKey,
                PersistentDataType.STRING
        );

        if (id == null) return;

        purchaseListing(player, id);
    }

    private void handleCollectionClick(
            Player player,
            ItemStack clicked
    ) {
        ItemMeta meta = clicked.getItemMeta();

        String action = meta.getPersistentDataContainer().get(
                actionKey,
                PersistentDataType.STRING
        );

        if ("back".equals(action)) {
            openAuctionHouse(player);
            return;
        }

        if ("collect_all".equals(action)) {
            collectExpired(player);
            openCollectionMenu(player);
            return;
        }

        String id = meta.getPersistentDataContainer().get(
                listingKey,
                PersistentDataType.STRING
        );

        if (id == null) return;

        collectListing(player, id);
        openCollectionMenu(player);
    }

    // PURCHASE LISTING

    private void purchaseListing(Player player, String id) {
        String path = "listings." + id;

        if (!data.contains(path)) {
            player.sendMessage(color(
                    "&cThis listing is no longer available."
            ));
            openAuctionHouse(player);
            return;
        }

        if (isExpired(path)) {
            player.sendMessage(color(
                    "&cThis auction has expired!"
            ));
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
            player.sendMessage(color(
                    "&cYou cannot buy your own listing."
            ));
            return;
        }

        double price = data.getDouble(path + ".price");
        ItemStack item = data.getItemStack(path + ".item");

        if (item == null || item.getType() == Material.AIR
                || price <= 0 || !Double.isFinite(price)) {
            player.sendMessage(color("&cThis listing is invalid."));
            return;
        }

        if (plugin.getEconomy() == null) {
            player.sendMessage(color(
                    "&cThe money system is unavailable."
            ));
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
            player.sendMessage(color(
                    "&cYou don't have enough money."
            ));
            return;
        }

        EconomyResponse payment =
                plugin.getEconomy().withdrawPlayer(player, price);

        if (!payment.transactionSuccess()) {
            player.sendMessage(color("&cPayment failed."));
            return;
        }

        OfflinePlayer seller =
                Bukkit.getOfflinePlayer(sellerId);

        EconomyResponse payout =
                plugin.getEconomy().depositPlayer(seller, price);

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
            plugin.getEconomy().withdrawPlayer(seller, price);
            plugin.getEconomy().depositPlayer(player, price);

            player.sendMessage(color(
                    "&cPurchase failed. Your money was refunded."
            ));
            return;
        }

        player.getInventory().addItem(item.clone());

        player.sendMessage(color(
                "&aYou bought the item for &f$" + price + "&a!"
        ));

        if (seller.isOnline()) {
            seller.getPlayer().sendMessage(color(
                    "&aYour auction sold for &f$" + price + "&a!"
            ));
        }

        openAuctionHouse(player);
    }

    // EXPIRY HELPERS

    private boolean isExpired(String path) {
        long expiresAt =
                data.getLong(path + ".expires-at", 0);

        if (expiresAt <= 0) {
            expiresAt =
                    System.currentTimeMillis() + EXPIRY_MILLIS;

            data.set(path + ".expires-at", expiresAt);
        }

        return System.currentTimeMillis() >= expiresAt;
    }

    private String getRemainingTime(String path) {
        long expiresAt =
                data.getLong(path + ".expires-at", 0);

        long remaining = Math.max(
                0, expiresAt - System.currentTimeMillis()
        );

        long hours = remaining / 3600000;
        long minutes = (remaining % 3600000) / 60000;

        return hours + "h " + minutes + "m";
    }

    // SAVE DATA

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

    private static class AuctionHolder
            implements InventoryHolder {

        private final boolean collection;
        private Inventory inventory;

        AuctionHolder(boolean collection) {
            this.collection = collection;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
