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
            String sql = "CREATE TABLE IF NOT EXISTS PlayerAccounts ("
                    + "  UUID CHAR(32) NOT NULL,"
                    + "  AccountCreationDate DATE NOT NULL,"
                    + "  PlayerName VARCHAR(16) NOT NULL,"
                    + "  Balance INT NOT NULL DEFAULT 0,"
                    + "  PRIMARY KEY (UUID)"
                    + ");"
                    + ""
                    + "CREATE TABLE IF NOT EXISTS Transactions ("
                    + "  TransactionID DATETIME NOT NULL,"
                    + "  TransactionType VARCHAR(255) NOT NULL,"
                    + "  Induce VARCHAR(16) NOT NULL,"
                    + "  Source CHAR(32),"
                    + "  Destination CHAR(32),"
                    + "  Amount INT NOT NULL,"
                    + "  TransactionMessage VARCHAR(32),"
                    + "  PRIMARY KEY (TransactionID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)"
                    + ");"
                    + ""
                    + "CREATE TABLE IF NOT EXISTS FailedTransactions ("
                    + "  TransactionID DATETIME NOT NULL,"
                    + "  TransactionType VARCHAR(255) NOT NULL,"
                    + "  Induce VARCHAR(16) NOT NULL,"
                    + "  Source CHAR(32),"
                    + "  Destination CHAR(32),"
                    + "  Amount INT NOT NULL,"
                    + "  Reason VARCHAR(32) NOT NULL,"
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

    public void executeTransaction(String transactType, String induce, String source, String destination, int amount, String transactionMessage) {
        String insertTransactionSQL = "INSERT INTO Transactions (TransactionID, TransactionType, Induce, Source, Destination, Amount, TransactionMessage) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String updateSourceSQL = "UPDATE PlayerAccounts SET Balance = Balance - ? WHERE UUID = ? AND Balance >= ?";
        String updateDestinationSQL = "UPDATE PlayerAccounts SET Balance = Balance + ? WHERE UUID = ?";
        String insertFailedTransactionSQL = "INSERT INTO FailedTransactions (TransactionID, TransactionType, Induce, Source, Destination, Amount, Reason) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement insertTransaction = conn.prepareStatement(insertTransactionSQL);
             PreparedStatement updateSource = conn.prepareStatement(updateSourceSQL);
             PreparedStatement updateDestination = conn.prepareStatement(updateDestinationSQL);
             PreparedStatement insertFailedTransaction = conn.prepareStatement(insertFailedTransactionSQL)) {

            conn.setAutoCommit(false);

            // Insert transaction
            insertTransaction.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            insertTransaction.setString(2, transactType);
            insertTransaction.setString(3, induce);
            insertTransaction.setString(4, source);
            insertTransaction.setString(5, destination);
            insertTransaction.setInt(6, amount);
            insertTransaction.setString(7, transactionMessage);
            insertTransaction.executeUpdate();

            // Update source account if applicable
            if (source != null) {
                updateSource.setInt(1, amount);
                updateSource.setString(2, source);
                updateSource.setInt(3, amount);
                int rowsAffected = updateSource.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("Insufficient funds in the source account");
                }
            }

            // Update destination account if applicable
            if (destination != null) {
                updateDestination.setInt(1, amount);
                updateDestination.setString(2, destination);
                updateDestination.executeUpdate();
            }

            conn.commit();
            getLogger().info("Transaction completed successfully");

        } catch (SQLException e) {
            getLogger().severe("Error executing transaction: " + e.getMessage());
            try (Connection conn = getConnection();
                 PreparedStatement insertFailedTransaction = conn.prepareStatement(insertFailedTransactionSQL)) {
                
                insertFailedTransaction.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                insertFailedTransaction.setString(2, transactType);
                insertFailedTransaction.setString(3, induce);
                insertFailedTransaction.setString(4, source);
                insertFailedTransaction.setString(5, destination);
                insertFailedTransaction.setInt(6, amount);
                insertFailedTransaction.setString(7, e.getMessage().contains("Insufficient funds") ? "low_balance" : "unexpected_error");
                insertFailedTransaction.executeUpdate();
                
                getLogger().info("Failed transaction recorded");
            } catch (SQLException ex) {
                getLogger().severe("Error recording failed transaction: " + ex.getMessage());
            }
        }
    }

    public void createAutogiro(String uuid, String autoGiroName, String destination, int amount, int inverseFrequency, Integer endsAfter) {
        String sql = "INSERT INTO Autogiros (Active, CreationDate, AutogiroName, Source, Destination, Amount, InverseFrequency, EndsAfter, TransactionCount) " +
                     "VALUES (1, CURRENT_DATE, ?, ?, ?, ?, ?, ?, 0)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, autoGiroName);
            pstmt.setString(2, uuid);
            pstmt.setString(3, destination);
            pstmt.setInt(4, amount);
            pstmt.setInt(5, inverseFrequency);
            if (endsAfter != null && endsAfter > 0) {
                pstmt.setInt(6, endsAfter);
            } else {
                pstmt.setNull(6, java.sql.Types.INTEGER);
            }
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int autoGiroId = generatedKeys.getInt(1);
                        getLogger().info("Autogiro created successfully with ID: " + autoGiroId);
                    }
                }
            }
        } catch (SQLException e) {
            getLogger().severe("Error creating autogiro: " + e.getMessage());
        }
    }

    public void updateAutogiroState(int autoGiroId, String uuid, boolean active) {
        String sql = "UPDATE Autogiros SET Active = ? WHERE AutogiroID = ? AND Source = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setBoolean(1, active);
            pstmt.setInt(2, autoGiroId);
            pstmt.setString(3, uuid);
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                getLogger().info("Autogiro " + autoGiroId + " " + (active ? "activated" : "deactivated") + " successfully.");
            } else {
                getLogger().info("Autogiro not found or no changes made.");
            }
        } catch (SQLException e) {
            getLogger().severe("Error updating autogiro state: " + e.getMessage());
        }
    }

    public void deleteAutogiro(int autoGiroId, String uuid) {
        String sql = "DELETE FROM Autogiros WHERE AutogiroID = ? AND Source = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, autoGiroId);
            pstmt.setString(2, uuid);
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                getLogger().info("Autogiro " + autoGiroId + " deleted successfully.");
            } else {
                getLogger().info("Autogiro not found or no deletion performed.");
            }
        } catch (SQLException e) {
            getLogger().severe("Error deleting autogiro: " + e.getMessage());
        }
    }
