package net.derfla.quickeconomy;

import net.derfla.quickeconomy.command.BalanceCommand;
import net.derfla.quickeconomy.command.BankCommand;
import net.derfla.quickeconomy.command.QuickeconomyCommand;
import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.listener.*;
import net.derfla.quickeconomy.util.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {


    public static boolean SQLMode = false;

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("QuickEconomy has been enabled!");

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

        int pluginID = 20985;
        Metrics metrics = new Metrics(this, pluginID);
        metrics.addCustomChart(new Metrics.SimplePie("sql_mode", () -> {
            return SQLMode ? "SQL-mode" : "File-mode";
        }));
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
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerChangeSettingsListener(), this);
    }

    private void setupFileMode() {
        getLogger().info("Running in file mode. See config to enable SQL mode.");
        BalanceFile.setup();
        BalanceFile.get().options().copyDefaults(true);
        BalanceFile.save();
        if(BalanceFile.get().contains("players.") && BalanceFile.checkFormat().equals("playerName"))
            BalanceFile.convertKeys();
    }

    private void setupSQLMode() {
        getLogger().info("Running in SQL mode. Attempting to connect to SQL server...");
        try {
            DatabaseManager.connectToDatabase();
            SQLMode = true;
            DatabaseManager.createTables();
            if (BalanceFile.get() != null) {
                if (DatabaseManager.migrateToDatabase()) {
                    if (BalanceFile.delete()) {
                        getLogger().info("balance.yml has been removed after successful migration.");
                    }
                } else {
                    getLogger().info("Migration unsuccessful. You can set balances manually with /bal set <playername>");
                }
            }
        } catch (Exception e) {
            getLogger().severe("Could not establish a database connection: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        DatabaseManager.closePool();
    }

    public static Main getInstance() {
        return getPlugin(Main.class);
    }
}
