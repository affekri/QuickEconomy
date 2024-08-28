package net.derfla.quickeconomy;

import net.derfla.quickeconomy.command.BalanceCommand;
import net.derfla.quickeconomy.command.BankCommand;
import net.derfla.quickeconomy.command.QuickeconomyCommand;
import net.derfla.quickeconomy.file.BalanceFile;
import net.derfla.quickeconomy.listener.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


public final class Main extends JavaPlugin {

    private Connection connection;

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

        // Config file
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        BalanceFile.setup();
        BalanceFile.get().options().copyDefaults(true);
        BalanceFile.save();

        int pluginID = 20985;
        Metrics metrics = new Metrics(this, pluginID);

        if(getConfig().getBoolean("database.enabled")) {
            getLogger().info("Running in SQL mode. Attempting to connect to SQL server...");
            try {
                connectToDatabase();
            } catch (SQLException e) {
                getLogger().severe("Could not establish a database connection: " + e.getMessage());
                getServer().getPluginManager().disablePlugin(this);
            }

            createTable();

        } else {
            getLogger().info("Running in file mode. See config to enable SQL mode.");
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                getLogger().severe("Error closing the database connection: " + e.getMessage());
            }
        }
    }

    public static Main getInstance() {
        return getPlugin(Main.class);
    }

    private void connectToDatabase() throws SQLException {
        String type = getConfig().getString("database.type");
        if ("mysql".equalsIgnoreCase(type)) {
            String host = getConfig().getString("database.host");
            int port = getConfig().getInt("database.port");
            String database = getConfig().getString("database.database");
            String user = getConfig().getString("database.username");
            String password = getConfig().getString("database.password");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
            connection = DriverManager.getConnection(url, user, password);
        } else if ("sqlite".equalsIgnoreCase(type)) {
            String filePath = getConfig().getString("database.file");
            String url = "jdbc:sqlite:" + filePath;
            connection = DriverManager.getConnection(url);
        }

        getLogger().info("Database connection established.");
    }

    public Connection getConnection() {
        return connection;
    }

    public void createTable() {
        try (Statement statement = connection.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS economy ("
                    + "uuid VARCHAR(36) PRIMARY KEY,"
                    + "balance DOUBLE"
                    + ");";
            statement.executeUpdate(sql);
            getLogger().info("Economy table created or already exists.");
        } catch (SQLException e) {
            getLogger().severe("Error creating table: " + e.getMessage());
        }
    }
}
