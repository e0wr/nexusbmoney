package ua.aestivus.nexusbmoney;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NexusBMoney extends JavaPlugin implements CommandExecutor, Listener {
    private FileConfiguration config;
    public static Economy econ = null;
    private Map<Player, Integer> blocksMinedMap;
    private Map<Player, Integer> lastRewardMap;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();

        setupEconomy();

        blocksMinedMap = new HashMap<>();
        lastRewardMap = new HashMap<>();

        getCommand("welcome").setExecutor(this);
        getCommand("nbm").setPermission("nexusbmoney.reload");

        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveConfig();
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            econ = economyProvider.getProvider();
        }

        return econ != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("nbm")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("nexusbmoney.reload")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.noPermission")));
                    return true;
                }
                reloadConfig();
                config = getConfig();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.configReloaded")));
                return true;
            }
        }

        if (cmd.getName().equalsIgnoreCase("welcome") && sender instanceof Player) {
            Player player = (Player) sender;
            double money;
            if (econ == null) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.error")));
            } else {
                money = econ.getBalance(player);
            }

            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.welcome")));

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("nbmench") && sender instanceof Player) {
            Player player = (Player) sender;
            ItemStack handItem = player.getInventory().getItemInMainHand();

            if (handItem != null && !handItem.getType().isAir()) {
                ItemMeta itemMeta = handItem.getItemMeta();
                if (itemMeta != null) {
                    String itemName = itemMeta.getDisplayName();
                    List<String> itemLore = itemMeta.getLore();

                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.enchantments")));
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eИмя: " + itemName));

                    if (itemLore != null && !itemLore.isEmpty()) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eОписание:"));
                        for (String loreLine : itemLore) {
                            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', loreLine));
                        }
                    } else {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eОписание: &cНет"));
                    }

                    Map<Enchantment, Integer> enchantments = handItem.getEnchantments();
                    if (!enchantments.isEmpty()) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.enchantments")));
                        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                            Enchantment enchantment = entry.getKey();
                            int level = entry.getValue();
                            String enchantmentName = enchantment.getName();
                            String message = ChatColor.translateAlternateColorCodes('&', config.getString("messages.enchantmentFormat"))
                                    .replace("%enchantment%", enchantmentName)
                                    .replace("%level%", String.valueOf(level));
                            sender.sendMessage(message);
                        }
                    } else {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.noEnchantments")));
                    }
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cОшибка получения данных предмета."));
                }
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.noItem")));
            }

            return true;
        }

        return false;
    }


    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (isInProtectedRegion(block, player)) {
            return;
        }

        onPlayerBlockBreak(player, block);
    }

    private boolean isInProtectedRegion(Block block, Player player) {
        WorldGuardPlugin worldGuardPlugin = WorldGuardPlugin.getPlugin(WorldGuardPlugin.class);

        if (worldGuardPlugin == null) {
            return false;
        }

        LocalPlayer localPlayer = worldGuardPlugin.wrapPlayer(player);
        ApplicableRegionSet regions = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(BukkitAdapter.adapt(block.getLocation()));

        for (ProtectedRegion region : regions) {
            if (!region.isOwner(localPlayer) && !region.isMember(localPlayer) && !region.getMembers().contains(localPlayer)) {
                return true;
            }
        }

        return false;
    }

    private void onPlayerBlockBreak(Player player, Block block) {
        if (shouldIgnoreBlock(block)) {
            return;
        }
        if (econ == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.error")));
            return;
        }

        int coinsPerBlock = config.getInt("rewards.coinsPerBlock");
        int blocksPerReward = config.getInt("rewards.blocksPerReward");

        int blocksMined = blocksMinedMap.getOrDefault(player, 0);
        blocksMined++;

        blocksMinedMap.put(player, blocksMined);

        if (hasKeywordInItemDescription(player)) {
            coinsPerBlock /= 5;
        }

        int rewardsCount = blocksMined / blocksPerReward;
        int lastReward = lastRewardMap.getOrDefault(player, 0);

        if (rewardsCount > lastReward) {
            int reward = (rewardsCount - lastReward) * coinsPerBlock;
            lastReward = rewardsCount;

            econ.depositPlayer(player, reward);
            sendActionBar(player, ChatColor.translateAlternateColorCodes('&', config.getString("messages.reward"))
                    .replace("%count%", String.valueOf(rewardsCount - lastReward))
                    .replace("%reward%", String.valueOf(reward)));
        }

        lastRewardMap.put(player, lastReward);
    }

    private boolean shouldIgnoreBlock(Block block) {
        return false;
    }

    private boolean hasKeywordInItemDescription(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem != null && handItem.hasItemMeta()) {
            ItemMeta itemMeta = handItem.getItemMeta();
            if (itemMeta.hasLore()) {
                List<String> lore = itemMeta.getLore();
                for (String line : lore) {
                    if (line.contains("Ров")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void checkRewards() {
        for (Player player : blocksMinedMap.keySet()) {
            int blocksMined = blocksMinedMap.getOrDefault(player, 0);
            int lastReward = lastRewardMap.getOrDefault(player, 0);
            int blocksPerReward = config.getInt("rewards.blocksPerReward");
            int coinsPerBlock = config.getInt("rewards.coinsPerBlock");

            int rewardsCount = blocksMined / blocksPerReward;

            if (rewardsCount > lastReward) {
                int reward = (rewardsCount - lastReward) * coinsPerBlock;
                lastReward = rewardsCount;

                econ.depositPlayer(player, reward);
                sendActionBar(player, ChatColor.translateAlternateColorCodes('&', config.getString("messages.reward"))
                        .replace("%count%", String.valueOf(rewardsCount - lastReward))
                        .replace("%reward%", String.valueOf(reward)));
            }

            lastRewardMap.put(player, lastReward);
        }
    }

    private void sendActionBar(Player player, String message) {
        player.sendActionBar(message);
    }
}
