package net.derfla.quickeconomy;

import net.derfla.quickeconomy.command.BalanceCommand;
import net.derfla.quickeconomy.command.BankCommand;
import net.derfla.quickeconomy.command.QuickeconomyCommand;
import net.derfla.quickeconomy.database.TableManagement;
import net.derfla.quickeconomy.database.Utility;
import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.listener.*;
import net.derfla.quickeconomy.util.AccountCache;
import net.derfla.quickeconomy.util.DerflaAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends JavaPlugin {


    public static boolean SQLMode = false;
    private static final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void onEnable() {

        // Set command executors
        getCommand("balance").setExecutor(new BalanceCommand());
        getCommand("bal").setExecutor(new BalanceCommand());
        getCommand("quickeconomy").setExecutor(new QuickeconomyCommand());
        getCommand("bank").setExecutor(new BankCommand());

        // Register events
        registerEvents();

        // Config file
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        // Database and file usage
        if (getConfig().getBoolean("database.enabled")) {
            setupSQLMode();
        } else {
            setupFileMode();
        }

        AccountCache.init();

        int pluginID = 20985;
        Metrics metrics = new Metrics(this, pluginID);
        metrics.addCustomChart(new Metrics.SimplePie("sql_mode", () -> {
            return SQLMode ? "SQL-mode" : "File-mode";
        }));

        // Plugin startup logic
        getLogger().info("QuickEconomy has been enabled!");
        if(DerflaAPI.updateAvailable()) getLogger().info("A new update is available! Download the latest at: https://modrinth.com/plugin/quickeconomy/");
    }

    private void registerEvents() {
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
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerCloseInventoryListener(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerLeaveListener(), this);
    }

    private void setupFileMode() {
        getLogger().info("Running in file mode. See /quickeconomy migrate to enable SQL mode.");
        BalanceFile.setup();
        BalanceFile.get().options().copyDefaults(true);
        BalanceFile.save();
        if(BalanceFile.get().contains("players.") && BalanceFile.checkFormat().equals("playerName"))
            BalanceFile.convertKeys();
    }

    private void setupSQLMode() {
        getLogger().info("Running in SQL mode. Attempting to connect to SQL server...");
        try {
            Utility.connectToDatabase();
            SQLMode = true;
            TableManagement.createTables();
        } catch (Exception e) {
            getLogger().severe("Could not establish a database connection: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Utility.closePool();
        Utility.shutdownExecutorService(); // Shutdown async thread handler (for database operations)
    }

    public static Main getInstance() {
        return getPlugin(Main.class);
    }

    public static ExecutorService getExecutorService() {
        return executorService;
    }
}
