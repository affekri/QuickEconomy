package net.derfla.quickeconomy;

import net.derfla.quickeconomy.commands.balanceCommand;
import net.derfla.quickeconomy.commands.quickeconomyCommand;
import net.derfla.quickeconomy.files.balanceFile;
import net.derfla.quickeconomy.listeners.PlayerJoinListener;
import net.derfla.quickeconomy.listeners.inventoryClickListener;
import net.derfla.quickeconomy.listeners.playerClickSignListener;
import net.derfla.quickeconomy.listeners.playerPlaceSignListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;


public final class main extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("QuickEconomy has been enabled!");

        // Set command executors
        getCommand("balance").setExecutor(new balanceCommand());
        getCommand("bal").setExecutor(new balanceCommand());
        getCommand("quickeconomy").setExecutor(new quickeconomyCommand());

        // Register events
        Bukkit.getServer().getPluginManager().registerEvents(new playerPlaceSignListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new playerClickSignListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new inventoryClickListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);

        // Config file
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        balanceFile.setup();
        balanceFile.get().options().copyDefaults(true);
        balanceFile.save();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
