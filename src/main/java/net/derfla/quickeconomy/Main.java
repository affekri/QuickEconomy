package net.derfla.quickeconomy;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.derfla.quickeconomy.command.BalanceCommand;
import net.derfla.quickeconomy.command.BankCommand;
import net.derfla.quickeconomy.command.QuickeconomyCommand;
import net.derfla.quickeconomy.database.TableManagement;
import net.derfla.quickeconomy.database.UpgradeUtility;
import net.derfla.quickeconomy.database.Utility;
import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.listener.*;
import net.derfla.quickeconomy.util.AccountCache;
import net.derfla.quickeconomy.util.DerflaAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * QuickEconomy Plugin Main Class
 * 
 * This is the main plugin class for QuickEconomy, a Minecraft economy plugin
 * that provides balance management, banking, and chest-based shop functionality.
 * The plugin supports both file-based and SQL database storage modes.
 * 
 * @author Derfla
 * @version See plugin.yml
 * @since 1.0.0
 */
public class Main extends JavaPlugin {


    /** Indicates whether the plugin is running in SQL mode (true) or file mode (false) */
    public static boolean SQLMode = false;
    
    /** Executor service for handling asynchronous tasks using virtual threads */
    private static final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Called when the plugin is enabled. Initializes commands, events, configuration,
     * database/file storage, account cache, metrics, and performs startup checks.
     */
    @Override
    public void onEnable() {

        // Register commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(BankCommand.createCommand().build());
            commands.registrar().register(QuickeconomyCommand.createCommand().build());
            commands.registrar().register(BalanceCommand.createCommand().build());
            commands.registrar().register(BalanceCommand.createShortCommand().build());
        });

        // Register events
        registerEvents();

        // Config file
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
        if(DerflaAPI.updateAvailable()) getLogger().info("A new version of QuickEconomy is available! Download the latest at: https://modrinth.com/plugin/quickeconomy/");
    }

    /**
     * Registers all event listeners for the plugin.
     * This includes listeners for sign placement/interaction, inventory operations,
     * player join/leave events, chest operations, and hopper interactions.
     */
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

    /**
     * Initializes the plugin in file-based storage mode.
     * Sets up the balance file, copies defaults, and handles format conversion if needed.
     */
    private void setupFileMode() {
        getLogger().info("Running in file mode. See /quickeconomy migrate to enable SQL mode.");
        BalanceFile.setup();
        BalanceFile.get().options().copyDefaults(true);
        BalanceFile.save();
        if(BalanceFile.get().contains("players.") && BalanceFile.checkFormat().equals("playerName"))
            BalanceFile.convertKeys();
    }

    /**
     * Initializes the plugin in SQL database storage mode.
     * Attempts to connect to the database, creates necessary tables,
     * and disables the plugin if connection fails.
     * 
     * @throws Exception if database connection or table creation fails
     */
    private void setupSQLMode() {
        getLogger().info("Running in SQL mode. Attempting to connect to SQL server...");
        try {
            Utility.connectToDatabase();
            SQLMode = true;
            TableManagement.createTables();
            if (UpgradeUtility.requiresUpgrade()) UpgradeUtility.startUpgrades();
        } catch (Exception e) {
            getLogger().severe("Could not establish a database connection: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Called when the plugin is disabled. Performs cleanup operations
     * including closing database connections and shutting down executor services.
     */
    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Utility.closePool();
        Utility.shutdownExecutorService(); // Shutdown async thread handler (for database operations)
    }

    /**
     * Gets the singleton instance of the Main plugin class.
     * 
     * @return the Main plugin instance
     */
    public static Main getInstance() {
        return getPlugin(Main.class);
    }

    /**
     * Gets the shared executor service for asynchronous task execution.
     * This executor uses virtual threads for improved performance.
     * 
     * @return the executor service for async operations
     */
    public static ExecutorService getExecutorService() {
        return executorService;
    }
}
