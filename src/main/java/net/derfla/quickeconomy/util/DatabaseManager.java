package net.derfla.quickeconomy.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.file.BalanceFile;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

import java.io.FileWriter;
import java.io.IOException;

public class DatabaseManager {

    static Plugin plugin = Main.getInstance();
    private static HikariDataSource dataSource;

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection(); // Get a connection from the pool
    }


    public static void connectToDatabase() {
        HikariConfig config = new HikariConfig();

        String type = plugin.getConfig().getString("database.type");
        if ("mysql".equalsIgnoreCase(type)) {
            String host = plugin.getConfig().getString("database.host");
            int port = plugin.getConfig().getInt("database.port");
            String database = plugin.getConfig().getString("database.database");
            String user = plugin.getConfig().getString("database.username");
            String password = plugin.getConfig().getString("database.password");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database;

            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(password);

            // Optional HikariCP settings
            config.setMaximumPoolSize(10); // Max number of connections in the pool
            config.setConnectionTimeout(30000); // 30 seconds timeout for getting a connection
            config.setIdleTimeout(600000); // 10 minutes before an idle connection is closed
            config.setMaxLifetime(1800000); // 30 minutes max lifetime for a connection
        } else if ("sqlite".equalsIgnoreCase(type)) {
            String filePath = plugin.getConfig().getString("database.file");
            String url = "jdbc:sqlite:" + filePath;
            config.setJdbcUrl(url);
        }

        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("Database connection pool established.");
    }

    public static void closePool() {
        if (dataSource != null) {
            dataSource.close(); // Close the pool when shutting down the plugin
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    public static void createTables() {
        try (Connection conn = getConnection();
             Statement statement = conn.createStatement()) {
            // Create PlayerAccounts table
            String sqlPlayerAccounts = "CREATE TABLE IF NOT EXISTS PlayerAccounts ("
                    + "  UUID char(32) NOT NULL,"
                    + "  AccountDatetime varchar(23) NOT NULL,"
                    + "  PlayerName varchar(16) NOT NULL,"
                    + "  Balance float NOT NULL DEFAULT 0,"
                    + "  BalChange float NOT NULL DEFAULT 0,"
                    + "  PRIMARY KEY (UUID)"
                    + ");";
            statement.executeUpdate(sqlPlayerAccounts);

            String sqlTransactions = "CREATE TABLE IF NOT EXISTS Transactions ("
                    + "  TransactionID bigint NOT NULL AUTO_INCREMENT,"
                    + "  TransactionDatetime varchar(23) NOT NULL,"
                    + "  TransactionType varchar(16) NOT NULL,"
                    + "  Induce varchar(16) NOT NULL,"
                    + "  Source char(32),"
                    + "  Destination char(32),"
                    + "  NewSourceBalance float,"
                    + "  NewDestinationBalance float,"
                    + "  Amount float NOT NULL,"
                    + "  Passed tinyint(1),"
                    + "  PassedReason varchar(16) DEFAULT NULL,"
                    + "  TransactionMessage varchar(32),"
                    + "  PRIMARY KEY (TransactionID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)"
                    + ");";
            statement.executeUpdate(sqlTransactions);

            String sqlAutopays = "CREATE TABLE IF NOT EXISTS Autopays ("
                    + "  AutopayID bigint NOT NULL AUTO_INCREMENT,"
                    + "  AutopayDatetime DATETIME NOT NULL,"
                    + "  Active tinyint(1) NOT NULL DEFAULT 1,"
                    + "  AutopayName varchar(16),"
                    + "  Source char(32),"
                    + "  Destination char(32),"
                    + "  Amount float NOT NULL,"
                    + "  InverseFrequency int NOT NULL,"
                    + "  TimesLeft int,"
                    + "  PRIMARY KEY (AutopayID),"
                    + "  FOREIGN KEY (Source) REFERENCES PlayerAccounts(UUID),"
                    + "  FOREIGN KEY (Destination) REFERENCES PlayerAccounts(UUID)"
                    + ");";
            statement.executeUpdate(sqlAutopays);

            plugin.getLogger().info("Tables created or already exist.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }

    public static void addAccount(@NotNull String uuid, @NotNull String playerName, double balance, double change) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentTimeString = sdf.format(currentTime);

        if (accountExists(trimmedUuid)) {
            plugin.getLogger().info("Account already exists for player with UUID: " + trimmedUuid);
        } else {
            String insertSql = "INSERT INTO PlayerAccounts (UUID, AccountDatetime, PlayerName, Balance, BalChange) "
                    + "VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, trimmedUuid);
                pstmt.setString(2, currentTimeString);
                pstmt.setString(3, playerName);
                pstmt.setDouble(4, balance);
                pstmt.setDouble(5, change);
                int rowsInserted = pstmt.executeUpdate();

                if (rowsInserted > 0) {
                    plugin.getLogger().info("New player account added successfully for " + playerName);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding player account: " + e.getMessage());
            }
        }

        createTransactionsView(trimmedUuid);
    }

    public static void executeTransaction(@NotNull String transactType, @NotNull String induce, String source,
                                          String destination, double amount, String transactionMessage) {
        String trimmedSource = TypeChecker.trimUUID(source);
        String trimmedDestination = TypeChecker.trimUUID(destination);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());

        // SQL to handle the transaction
        String sqlUpdateSource = "UPDATE PlayerAccounts SET Balance = Balance - ? WHERE UUID = ?";
        String sqlUpdateDestination = "UPDATE PlayerAccounts SET Balance = Balance + ? WHERE UUID = ?";
        String sqlInsertTransaction = "INSERT INTO Transactions (TransactionDatetime, TransactionType, Induce, Source, Destination, NewSourceBalance, NewDestinationBalance, Amount, Passed, TransactionMessage)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);  // Begin transaction

            // Update source account balance if applicable
            Double newSourceBalance = null;
            if (trimmedSource != null) {
                try (PreparedStatement pstmtUpdateSource = conn.prepareStatement(
                        "SELECT Balance FROM PlayerAccounts WHERE UUID = ?")) {
                    pstmtUpdateSource.setString(1, trimmedSource);
                    ResultSet rs = pstmtUpdateSource.executeQuery();
                    if (rs.next()) {
                        newSourceBalance = rs.getDouble(1) - amount;  // Calculate new source balance
                    }
                }

                try (PreparedStatement pstmtUpdateSource = conn.prepareStatement(sqlUpdateSource)) {
                    pstmtUpdateSource.setDouble(1, amount);
                    pstmtUpdateSource.setString(2, trimmedSource);
                    pstmtUpdateSource.executeUpdate();
                }
            }

            // Update destination account balance if applicable
            Double newDestinationBalance = null;
            if (trimmedDestination != null) {
                try (PreparedStatement pstmtUpdateDestination = conn.prepareStatement(
                        "SELECT Balance FROM PlayerAccounts WHERE UUID = ?")) {
                    pstmtUpdateDestination.setString(1, trimmedDestination);
                    ResultSet rs = pstmtUpdateDestination.executeQuery();
                    if (rs.next()) {
                        newDestinationBalance = rs.getDouble(1) + amount;  // Calculate new destination balance
                    }
                }

                try (PreparedStatement pstmtUpdateDestination = conn.prepareStatement(sqlUpdateDestination)) {
                    pstmtUpdateDestination.setDouble(1, amount);
                    pstmtUpdateDestination.setString(2, trimmedDestination);
                    pstmtUpdateDestination.executeUpdate();
                }
            }

            // Insert into Transactions table
            try (PreparedStatement pstmtInsertTransaction = conn.prepareStatement(sqlInsertTransaction)) {
                pstmtInsertTransaction.setString(1, currentTime.toString());         // TransactionDatetime
                pstmtInsertTransaction.setString(2, transactType);                   // TransactionType
                pstmtInsertTransaction.setString(3, induce);                         // Induce
                pstmtInsertTransaction.setString(4, trimmedSource);                  // Source
                pstmtInsertTransaction.setString(5, trimmedDestination);             // Destination
                pstmtInsertTransaction.setObject(6, newSourceBalance);               // NewSourceBalance (nullable)
                pstmtInsertTransaction.setObject(7, newDestinationBalance);          // NewDestinationBalance (nullable)
                pstmtInsertTransaction.setDouble(8, amount);                         // Amount
                pstmtInsertTransaction.setInt(9, 1);                              // Passed (always 1 if successful)
                pstmtInsertTransaction.setString(10, transactionMessage);            // TransactionMessage
                pstmtInsertTransaction.executeUpdate();
            }

            // Commit the transaction
            conn.commit();
            Balances.addPlayerBalanceChange(trimmedDestination, (float) amount);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error executing transaction: " + e.getMessage());
        }
    }

    private static void createTransactionsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_Transactions_" + trimmedUuid;
        String databaseName = plugin.getConfig().getString("database.database");
        String checkSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(checkSQL)) {
            preparedStatement.setString(1, viewName);
            preparedStatement.setString(2, databaseName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) > 0) {
                    return;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking for existing transaction view: " + e.getMessage());
            return;
        }

        try (Connection conn = getConnection();
             Statement statement = conn.createStatement()) {
            String sql = "CREATE VIEW " + viewName + " AS "
                    + "SELECT "
                    + "    t.TransactionDatetime, "
                    + "    t.Amount, "
                    + "    sourcePlayer.PlayerName AS SourcePlayerName, "
                    + "    destinationPlayer.PlayerName AS DestinationPlayerName, "
                    + "    t.TransactionMessage AS Message, "
                    + "    CASE "
                    + "        WHEN t.Passed = 1 THEN 'Passed' "
                    + "        ELSE 'Failed' "
                    + "    END AS Passed, "
                    + "    t.PassedReason "
                    + "FROM Transactions t "
                    + "LEFT JOIN PlayerAccounts sourcePlayer ON t.Source = sourcePlayer.UUID "
                    + "LEFT JOIN PlayerAccounts destinationPlayer ON t.Destination = destinationPlayer.UUID "
                    + "WHERE t.Source = '" + trimmedUuid + "' OR t.Destination = '" + trimmedUuid + "' "
                    + "ORDER BY t.TransactionDatetime DESC;";

            statement.executeUpdate(sql);
            plugin.getLogger().info("Transaction view created for UUID: " + uuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating transaction view: " + e.getMessage());
        }
    }

    public static double displayBalance(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String sql = "SELECT Balance FROM PlayerAccounts WHERE UUID = ?";
        double balance = 0.0;
    
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmedUuid);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    balance = rs.getDouble("Balance");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error viewing balance for UUID " + untrimmedUuid + ": " + e.getMessage());
        }
        return balance;
    }

    public static String displayTransactionsView(@NotNull String uuid, String playerName, Boolean displayPassed) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String viewName = "vw_Transactions_" + trimmedUuid;
        StringBuilder transactions = new StringBuilder();

        String sql;
        if (displayPassed == null) {
            // Display all transactions
            sql = "SELECT TransactionDatetime, Amount, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " ORDER BY TransactionDateTime DESC";
        } else if (displayPassed) {
            // Display only passed transactions
            sql = "SELECT TransactionDatetime, Amount, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " WHERE Passed = 'Passed' ORDER BY TransactionDateTime DESC";
        } else {
            // Display only failed transactions
            sql = "SELECT TransactionDatetime, Amount, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " WHERE Passed = 0 ORDER BY TransactionDateTime DESC";
        }

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            // Iterate over the result set
            while (rs.next()) {
                String dateTime = rs.getString("TransactionDateTime");
                Double amount = rs.getDouble("Amount");
                String source = rs.getString("SourcePlayerName");
                String destination = rs.getString("DestinationPlayerName");
                String transactionMessage = rs.getString("Message");

                transactions.append(dateTime).append(" ").append(amount);
                if (source.equalsIgnoreCase(playerName)) {
                    transactions.append(" -> ").append(destination);
                }else {
                    transactions.append(" <- ").append(source);
                }
                if(transactionMessage != null) {
                    transactions.append(transactionMessage);
                }
                transactions.append("\n");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error viewing transactions for UUID " + uuid + ": " + e.getMessage());
        }

        return transactions.toString();
    }

    public static void addAutopay(String autopayName, @NotNull String uuid, @NotNull String destination,
                                  double amount, int inverseFrequency, int endsAfter) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String trimmedDestination = TypeChecker.trimUUID(destination);

        if (amount < 0) {
            plugin.getLogger().severe("Error: Amount must be greater than 0.");
            return;
        }
        if (inverseFrequency < 0) {
            plugin.getLogger().severe("Error: InverseFrequency must be greater than 0.");
            return;
        }
        if (endsAfter <= 0) {
            plugin.getLogger().severe("Error: EndsAfter must be 0 (for continuous) or greater.");
            return;
        }
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentTimeString = sdf.format(currentTime);

        String sql = "DECLARE @AutopayName varchar(16) = ?;"
                + "DECLARE @UUID char(32) = ?;"
                + "AutopayDatetime DATETIME = ?;"
                + "DECLARE @Destination char(32) = ?;"
                + "DECLARE @Amount float = ?;"
                + "DECLARE @InverseFrequency int NOT NULL = ?;"
                + "DECLARE @EndsAfter int NOT NULL = ?;"
                + "BEGIN TRY"
                + "    INSERT INTO Autopays ("
                + "        Active, AutopayDatetime, AutopayName, Source, Destination,"
                + "        Amount, InverseFrequency, EndsAfter"
                + "    )"
                + "    VALUES ("
                + "        1, GETDATE(), @AutopayName, @UUID, @Destination,"
                + "        @Amount, @InverseFrequency, @EndsAfter"
                + "    );"
                + "    PRINT 'Autopay created successfully.';"
                + "END TRY"
                + "BEGIN CATCH"
                + "    PRINT 'Error occurred while creating autopay:';"
                + "    PRINT ERROR_MESSAGE();"
                + "END CATCH;";

        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, autopayName);
            pstmt.setString(2, trimmedUuid);
            pstmt.setString(3, currentTimeString);
            pstmt.setString(4, trimmedDestination);
            pstmt.setDouble(5, amount);
            pstmt.setInt(6, inverseFrequency);
            pstmt.setInt(7, endsAfter);

            pstmt.executeUpdate();
            plugin.getLogger().info("Autopay added successfully");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding autopay: " + e.getMessage());
        }
    }

    public static void stateChangeAutopay(boolean activeState, int autopayID, @NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "UPDATE Autopays SET Active = ? WHERE AutopayID = ? AND Source = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, activeState);
            pstmt.setInt(2, autopayID);
            pstmt.setString(3, trimmedUuid);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                plugin.getLogger().info("Autopay updated successfully");
            } else {
                plugin.getLogger().info("Autopay not found. No update was performed.");
            }
        } catch (SQLException e) {
            String action = activeState ? "activating" : "deactivating";
            plugin.getLogger().severe("Error " + action + " autopay: " + e.getMessage());
        }
    }

    public static void deleteAutopay(int autopayID, @NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "DELETE FROM Autopays WHERE AutopayID = ? AND Source = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, autopayID);
            pstmt.setString(2, trimmedUuid);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                plugin.getLogger().info("Autopay deleted successfully");
            } else {
                plugin.getLogger().info("Autopay not found. No deletion was performed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting autopay: " + e.getMessage());
        }
    }

    public static List<Map<String, Object>> viewAutopays(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String sql = "SELECT a.AutopayID, a.AutopayName, a.Amount, pa.PlayerName AS DestinationName, a.InverseFrequency, a.TimesLeft " +
                "FROM Autopays a " +
                "JOIN PlayerAccounts pa ON a.Destination = pa.UUID " +
                "WHERE a.Source = ? " +
                "ORDER BY a.AutopayDatetime DESC";

        List<Map<String, Object>> autopays = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmedUuid);

            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> autopay = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        autopay.put(columnName, value);
                    }
                    autopays.add(autopay);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error viewing autopays for UUID " + untrimmedUuid + ": " + e.getMessage());
        }
        return autopays;
    }


    public static String listAllAccounts() {
        String sql = "SELECT PlayerName, Balance, BalChange, AccountDatetime AS Created FROM PlayerAccounts ORDER BY PlayerName ASC";
        StringBuilder accounts = new StringBuilder();

        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                accounts.append(rs.getString("PlayerName")).append(": ").append(rs.getDouble("Balance")).append("\n");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error listing all accounts: " + e.getMessage());
        }

        return accounts.toString();
    }

    public static void rollback(String targetDateTime) {
        try (Connection conn = getConnection()) {
            // Disable auto-commit to ensure transaction consistency
            conn.setAutoCommit(false);

            try {
                // 1. First, get all transactions after the target datetime that were successful
                String getTransactionsSQL =
                        "SELECT * FROM Transactions " +
                                "WHERE TransactionDatetime > ? AND Passed = 1 " +
                                "ORDER BY TransactionDatetime DESC";

                try (PreparedStatement pstmt = conn.prepareStatement(getTransactionsSQL)) {
                    pstmt.setString(1, targetDateTime);
                    ResultSet rs = pstmt.executeQuery();

                    // 2. Reverse each transaction
                    while (rs.next()) {
                        String source = rs.getString("Source");
                        String destination = rs.getString("Destination");
                        float amount = rs.getFloat("Amount");
                        String transactionType = rs.getString("TransactionType");

                        // Update source balance if exists
                        if (source != null) {
                            String updateSourceSQL =
                                    "UPDATE PlayerAccounts SET Balance = Balance + ?, " +
                                            "BalChange = BalChange + ? " +
                                            "WHERE UUID = ?";
                            try (PreparedStatement updateSource = conn.prepareStatement(updateSourceSQL)) {
                                updateSource.setFloat(1, amount);
                                updateSource.setFloat(2, amount);
                                updateSource.setString(3, source);
                                updateSource.executeUpdate();
                            }
                        }

                        // Update destination balance if exists
                        if (destination != null) {
                            String updateDestSQL =
                                    "UPDATE PlayerAccounts SET Balance = Balance - ?, " +
                                            "BalChange = BalChange - ? " +
                                            "WHERE UUID = ?";
                            try (PreparedStatement updateDest = conn.prepareStatement(updateDestSQL)) {
                                updateDest.setFloat(1, amount);
                                updateDest.setFloat(2, amount);
                                updateDest.setString(3, destination);
                                updateDest.executeUpdate();
                            }
                        }
                    }
                }

                // 3. Delete transactions after target datetime
                String deleteTransactionsSQL = "DELETE FROM Transactions WHERE TransactionDatetime > ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteTransactionsSQL)) {
                    pstmt.setString(1, targetDateTime);
                    pstmt.executeUpdate();
                }

                // 4. Handle Autopays
                // Option 1: Delete autopays created after target datetime
                String deleteAutopaysSQL = "DELETE FROM Autopays WHERE AutopayDatetime > ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteAutopaysSQL)) {
                    pstmt.setString(1, targetDateTime);
                    pstmt.executeUpdate();
                }

                // 5. Reset account creation dates that are after target datetime
                String resetAccountsSQL =
                        "UPDATE PlayerAccounts SET AccountDatetime = ? " +
                                "WHERE AccountDatetime > ?";
                try (PreparedStatement pstmt = conn.prepareStatement(resetAccountsSQL)) {
                    pstmt.setString(1, targetDateTime);
                    pstmt.setString(2, targetDateTime);
                    pstmt.executeUpdate();
                }

                // If everything succeeded, commit the transaction
                conn.commit();
                plugin.getLogger().info("Successfully rolled back database to " + targetDateTime);

            } catch (SQLException e) {
                // If anything fails, roll back all changes
                conn.rollback();
                plugin.getLogger().severe("Error during rollback, changes reverted: " + e.getMessage());
                throw e;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database rollback failed: " + e.getMessage());
        }
    }

    public static void setPlayerBalance(@NotNull String uuid, double balance, double change) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String sql = "UPDATE PlayerAccounts SET Balance = ?, BalChange = ? WHERE UUID = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, balance);
            pstmt.setDouble(2, change);
            pstmt.setString(3, trimmedUuid);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                plugin.getLogger().info("Balance updated successfully for UUID: " + untrimmedUuid);
            } else {
                plugin.getLogger().info("No account found for UUID: " + untrimmedUuid);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating balance for UUID " + untrimmedUuid + ": " + e.getMessage());
        }
    }

    public static boolean migrateToDatabase() {
        try {
            FileConfiguration balanceConfig = BalanceFile.get();
            ConfigurationSection playersSection = balanceConfig.getConfigurationSection("players");
            if (playersSection == null) {
                plugin.getLogger().info("No player balances found. Migration complete.");
                return true;
            }
            for (String key : playersSection.getKeys(false)) {
                double balance = playersSection.getDouble(key + ".balance");
                double change = playersSection.getDouble(key + ".change");
                String playerName = playersSection.getString(key + ".name");
                String trimmedUuid = TypeChecker.trimUUID(key);

                if (!accountExists(trimmedUuid)) {
                    addAccount(trimmedUuid, playerName, balance, change);
                } else {
                    setPlayerBalance(trimmedUuid, balance, change);
                }
            }
            plugin.getLogger().info("Migration to database completed successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error during migration to database: " + e.getMessage());
            return false;
        }
    }

    public static void migrateToBalanceFile() {
        String sqlCount = "SELECT COUNT(*) AS playerCount FROM PlayerAccounts";
        String sql = "SELECT * FROM PlayerAccounts";

        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement pstmtCount = conn.prepareStatement(sqlCount);
            ResultSet rsCount = pstmtCount.executeQuery();
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()) {

            int playerCount = 0;
            if (rsCount.next()) {
                playerCount = rsCount.getInt("playerCount");
            }

            BalanceFile.setup();
            BalanceFile.get().options().copyDefaults(true);
            BalanceFile.save();


            FileConfiguration balanceConfig = BalanceFile.get();
            for (String key : balanceConfig.getKeys(false)) {
                balanceConfig.set(key, null);
            }

            balanceConfig.set("format", "uuid");

            while (rs.next()) {
                String uuid = rs.getString("UUID");
                String untrimmedUuid = TypeChecker.untrimUUID(uuid);
                String playerName = rs.getString("PlayerName");
                double balance = rs.getDouble("Balance");
                double change = rs.getDouble("BalChange");

                // Validate data before writing
                if (uuid != null && playerName != null && balance >= 0) {
                    balanceConfig.set("players." + uuid + ".name", playerName);
                    balanceConfig.set("players." + uuid + ".balance", balance);
                    balanceConfig.set("players." + uuid + ".change", change);
                } else {
                    plugin.getLogger().warning("Invalid data for UUID: " + untrimmedUuid);
                }
            }

            // Save the updated configuration to balance.yml
            BalanceFile.save();
            plugin.getLogger().info("Successfully migrated " + playerCount + " players to balance.yml");

        } catch (SQLException e) {
            plugin.getLogger().severe("Error migrating to balance.yml: " + e.getMessage());
        }
    }

    public static boolean accountExists(@NotNull String uuid) {
        String sql = "SELECT COUNT(*) FROM PlayerAccounts WHERE UUID = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, TypeChecker.trimUUID(uuid));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0; // Return true if count is greater than 0
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking account existence: " + e.getMessage());
        }
        return false;
    }

    public static void updatePlayerName(String uuid, String playerName) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String sql = "UPDATE PlayerAccounts SET PlayerName = ? WHERE UUID = ?";
    
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.setString(2, trimmedUuid);
            int rowsUpdated = pstmt.executeUpdate();
    
            if (rowsUpdated > 0) {
                plugin.getLogger().info("Player name updated successfully for UUID: " + trimmedUuid);
            } else {
                plugin.getLogger().info("No account found for UUID: " + trimmedUuid);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating player name for UUID " + trimmedUuid + ": " + e.getMessage());
        }
    }

    public static void exportDatabase() {
        String sqlAccounts = "SELECT * FROM PlayerAccounts";
        String sqlTransactions = "SELECT * FROM Transactions";
        String sqlAutopays = "SELECT * FROM Autopays";
        String csvFilePath = "QuickEconomyExport.csv"; // Path to save the CSV file

        try (Connection conn = getConnection();
             FileWriter csvWriter = new FileWriter(csvFilePath)) {

            // Export PlayerAccounts table
            try (PreparedStatement pstmt = conn.prepareStatement(sqlAccounts);
                 ResultSet rs = pstmt.executeQuery()) {

                csvWriter.append("PlayerAccounts Table\n");
                writeResultSetToCSV(rs, csvWriter);
                csvWriter.append("\n"); // Separate tables with a blank line
            }

            // Export Transactions table
            try (PreparedStatement pstmt = conn.prepareStatement(sqlTransactions);
                 ResultSet rs = pstmt.executeQuery()) {

                csvWriter.append("Transactions Table\n");
                writeResultSetToCSV(rs, csvWriter);
                csvWriter.append("\n"); // Separate tables with a blank line
            }

            // Export Autopays table
            try (PreparedStatement pstmt = conn.prepareStatement(sqlAutopays);
                 ResultSet rs = pstmt.executeQuery()) {

                csvWriter.append("Autopays Table\n");
                writeResultSetToCSV(rs, csvWriter);
                csvWriter.append("\n"); // Separate tables with a blank line
            }

            plugin.getLogger().info("Database exported to " + csvFilePath + " successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error exporting database: " + e.getMessage());
        } catch (IOException e) {
            plugin.getLogger().severe("Error writing CSV file: " + e.getMessage());
        }
    }

    private static void writeResultSetToCSV(ResultSet rs, FileWriter csvWriter) throws SQLException, IOException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        // Write the header row
        for (int i = 1; i <= columnCount; i++) {
            csvWriter.append(metaData.getColumnName(i));
            if (i < columnCount) csvWriter.append(","); // Separate columns with a comma
        }
        csvWriter.append("\n");

        // Write the data rows
        while (rs.next()) {
            for (int i = 1; i <= columnCount; i++) {
                csvWriter.append(rs.getString(i));
                if (i < columnCount) csvWriter.append(","); // Separate columns with a comma
            }
            csvWriter.append("\n");
        }
    }

    public static double getPlayerBalanceChange(String uuid){
        String trimmedUUID = TypeChecker.trimUUID(uuid);
        Double change = 0.0;
        String sql = "SELECT BalChange FROM PlayerAccounts WHERE UUID = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)){
            pstmt.setString(1, trimmedUUID);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    change = rs.getDouble("BalChange");
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Could not get change for player: " + e.getMessage());
        }
        return change;
    }

    public static void setPlayerBalanceChange(@NotNull String uuid, double change) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String sql = "UPDATE PlayerAccounts SET  BalChange = ? WHERE UUID = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, change);
            pstmt.setString(2, trimmedUuid);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating change for UUID " + untrimmedUuid + ": " + e.getMessage());
        }
    }


}
