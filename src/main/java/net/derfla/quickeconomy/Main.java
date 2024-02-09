package net.derfla.quickeconomy;

import net.derfla.quickeconomy.command.BalanceCommand;
import net.derfla.quickeconomy.command.QuickeconomyCommand;
import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.listener.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;


public final class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("QuickEconomy has been enabled!");

        // Set command executors
        getCommand("balance").setExecutor(new BalanceCommand());
        getCommand("bal").setExecutor(new BalanceCommand());
        getCommand("quickeconomy").setExecutor(new QuickeconomyCommand());

        // Register events
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerPlaceSignListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerClickSignListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new InventoryClickListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerOpenChestListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerBreakChestListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerBreakSignListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerPlaceChestListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerPlaceHopperListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new HopperMoveItemEvent(), this);

        // Config file
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        BalanceFile.setup();
        BalanceFile.get().options().copyDefaults(true);
        BalanceFile.save();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static Main getInstance() {
        return getPlugin(Main.class);
    }
}
