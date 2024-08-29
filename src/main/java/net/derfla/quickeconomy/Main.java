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

    public void addAccount(String uuid, String playerName) {
        String sql = "INSERT INTO PlayerAccounts (UUID, AccountCreationDate, PlayerName, Balance) " +
                     "VALUES (?, CURRENT_DATE, ?, 0) " +
                     "ON DUPLICATE KEY UPDATE PlayerName = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.setString(2, playerName);
            pstmt.setString(3, uuid);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                getLogger().info(rowsAffected == 1 ? "New player account added successfully for " + playerName :
                                                     "Player account updated for " + playerName);
            } else {
                getLogger().info("No changes made for player account: " + playerName);
            }
        } catch (SQLException e) {
            getLogger().severe("Error adding/updating player account: " + e.getMessage());
        }
    }

    public void createTable() {
        try (Statement statement = connection.createStatement()) {
            String sql = "CREATE table IF NOT EXISTS PlayerAccounts ("
                    + "  UUID char(32) NOT NULL,"
                    + "  AccountCreationDate date NOT NULL,"
                    + "  PlayerName varchar(16) NOT NULL,"
                    + "  Balance int NOT NULL DEFAULT 0,"
                    + "  PRIMARY KEY (UUID)"
                    + ");"
                    + ""
                    + "CREATE table IF NOT EXISTS Transactions ("
                    + "  TransactionID datetime NOT NULL,"
                    + "  TransactionType varchar NOT NULL,"
                    + "  Induce varchar(16) NOT NULL,"
                    + "  Source char(32),"
                    + "  Destination char(32),"
                    + "  Amount int NOT NULL,"
                    + "  TransactionMessage varchar(32),"
                    + "  PRIMARY KEY (TransactionID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)"
                    + ");"
                    + ""
                    + "CREATE table IF NOT EXISTS FailedTransactions ("
                    + "  TransactionID datetime NOT NULL,"
                    + "  TransactionType varchar NOT NULL,"
                    + "  Induce varchar(16) NOT NULL,"
                    + "  Source char(32),"
                    + "  Destination char(32),"
                    + "  Amount int NOT NULL,"
                    + "  Reason varchar(32) NOT NULL,"
                    + "  PRIMARY KEY (TransactionID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)"
                    + ");"
                    + ""
                    + "CREATE table IF NOT EXISTS Autogiros ("
                    + "  AutogiroID int NOT NULL AUTO_INCREMENT,"
                    + "  Active tinyint(1) NOT NULL DEFAULT 1,"
                    + "  CreationDate date,"
                    + "  AutogiroName varchar(16),"
                    + "  Source char(32),"
                    + "  Destination char(32),"
                    + "  Amount int NOT NULL,"
                    + "  InverseFrequency int NOT NULL,"
                    + "  EndsAfter int,"
                    + "  TransactionCount int NOT NULL DEFAULT 0,"
                    + "  Failed datetime,"
                    + "  PRIMARY KEY (AutogiroID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Failed) REFERENCES FailedTransactions(TransactionID)"
                    + ");";
            statement.executeUpdate(sql);
            getLogger().info("Economy tables created or already exist.");
        } catch (SQLException e) {
            getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }
}
