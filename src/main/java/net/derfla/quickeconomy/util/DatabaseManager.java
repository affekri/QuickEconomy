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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import java.io.FileWriter;
import java.io.IOException;

public class DatabaseManager {

    static Plugin plugin = Main.getInstance();
    private static HikariDataSource dataSource;

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource is not initialized. Call connectToDatabase() first.");
        }
        return dataSource.getConnection();
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

    private static boolean tableExists(@NotNull String tableName) {
        // Check if table exists and return true if it does, false otherwise
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?")) {
            pstmt.setString(1, tableName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking table " + tableName + ": " + e.getMessage());
        }
        return false;
    }

    public static void createTables() {
        List<String> tableCreationQueries = new ArrayList<>();

        String PlayerAccounts = "CREATE TABLE IF NOT EXISTS PlayerAccounts ("
                + "  UUID char(32) NOT NULL,"
                + "  AccountDatetime varchar(23) NOT NULL,"
                + "  PlayerName varchar(16) NOT NULL,"
                + "  Balance float NOT NULL DEFAULT 0,"
                + "  BalChange float NOT NULL DEFAULT 0,"
                + "  PRIMARY KEY (UUID)"
                + ");";
        tableCreationQueries.add(PlayerAccounts);

        String Transactions = "CREATE TABLE IF NOT EXISTS Transactions ("
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
        tableCreationQueries.add(Transactions);

        String Autopays = "CREATE TABLE IF NOT EXISTS Autopays ("
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
        tableCreationQueries.add(Autopays);

        String EmptyShops = "CREATE TABLE IF NOT EXISTS EmptyShops ("
                + "  Coordinates varchar(32) NOT NULL,"
                + "  Owner1 char(32),"
                + "  Owner2 char(32),"
                + "  PRIMARY KEY (Coordinates)"
                + ");";
        tableCreationQueries.add(EmptyShops);

        // Execute the SQL statements to create tables
        try (Connection conn = getConnection();
             Statement statement = conn.createStatement()) {
            for (String query : tableCreationQueries) {
                if (!tableExists(query)) {
                    statement.executeUpdate(query); // Execute table creation query
                } else {
                    plugin.getLogger().info("Table " + query + " already exists in database.");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }

    public static void addAccount(@NotNull String uuid, @NotNull String playerName, double balance, double change) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        Instant currentTime = Instant.now(); // Use Instant for UTC time
        String currentTimeString = TypeChecker.convertToUTC(currentTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); 

        if (accountExists(trimmedUuid)) {
            plugin.getLogger().info("Account already exists for player with UUID: " + trimmedUuid);
        } else {
            String insertSql = "INSERT INTO PlayerAccounts (UUID, AccountDatetime, PlayerName, Balance, BalChange) "
                    + "VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, trimmedUuid);
                pstmt.setString(2, currentTimeString); // Use the converted UTC time
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

    public static boolean insertEmptyShop(@NotNull String coordinates, String owner1, String owner2) {
        // Return true if a new empty shop was created, false if an existing shop was updated
        if (emptyShopExists(coordinates)) {
            // Update the owners of the existing shop
            String updateSql = "UPDATE EmptyShops SET Owner1 = ?, Owner2 = ? WHERE Coordinates = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setString(1, owner1);
                pstmt.setString(2, owner2);
                pstmt.setString(3, coordinates);
                pstmt.executeUpdate();
                plugin.getLogger().info("Empty shop at " + coordinates + " updated with new owners.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error updating empty shop: " + e.getMessage());
            }
            return false;
        } else {
            // Insert a new empty shop
            String insertSql = "INSERT INTO EmptyShops (Coordinates, Owner1, Owner2) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setString(1, coordinates);
                pstmt.setString(2, owner1);
                pstmt.setString(3, owner2);
                pstmt.executeUpdate();
                plugin.getLogger().info("Empty shop registered at " + coordinates);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error inserting empty shop: " + e.getMessage());
            }
        }

        createEmptyShopsView(owner1);
        if (owner2 != null) {
            createEmptyShopsView(owner2);
        }

        return true;
    }

    private static boolean emptyShopExists(@NotNull String coordinates) {
        String sql = "SELECT COUNT(*) FROM EmptyShops WHERE Coordinates = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, coordinates);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0; // Return true if the shop exists
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking for empty shop: " + e.getMessage());
        }
        return false; // Return false if the shop does not exist or an error occurred
    }

    public static void executeTransaction(@NotNull String transactType, @NotNull String induce, String source,
                                          String destination, double amount, String transactionMessage) {
        String trimmedSource = TypeChecker.trimUUID(source);
        String trimmedDestination = TypeChecker.trimUUID(destination);
        Instant currentTime = Instant.now(); // Use Instant for UTC time

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

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC); // Move this line here
        
            // Insert into Transactions table
            try (PreparedStatement pstmtInsertTransaction = conn.prepareStatement(sqlInsertTransaction)) {
                pstmtInsertTransaction.setString(1, TypeChecker.convertToUTC(formatter.format(currentTime))); // Updated to use convertToUTC
                pstmtInsertTransaction.setString(2, transactType);                   // TransactionType
                pstmtInsertTransaction.setString(3, induce);                         // Induce
                pstmtInsertTransaction.setString(4, trimmedSource);                  // Source
                pstmtInsertTransaction.setString(5, trimmedDestination);             // Destination
                pstmtInsertTransaction.setObject(6, newSourceBalance);               // NewSourceBalance (nullable)
                pstmtInsertTransaction.setObject(7, newDestinationBalance);          // NewDestinationBalance (nullable)
                pstmtInsertTransaction.setDouble(8, amount);                         // Amount
                pstmtInsertTransaction.setInt(9, 1);                               // Passed (always 1 if successful)
                pstmtInsertTransaction.setString(10, transactionMessage);            // TransactionMessage
                pstmtInsertTransaction.executeUpdate();
            }

            // Commit the transaction
            conn.commit();
            Balances.addPlayerBalanceChange(trimmedDestination, (float) amount);
        } catch (SQLException e) {
            if(trimmedDestination != null && trimmedSource != null) {
                plugin.getLogger().severe("Error executing transaction from " + trimmedSource + " to " + trimmedDestination + ": " + e.getMessage());
            } else if (trimmedSource != null) {
                plugin.getLogger().severe("Error executing transaction from " + trimmedSource + ": " + e.getMessage());
            } else if (trimmedDestination != null) {
                plugin.getLogger().severe("Error executing transaction to " + trimmedDestination + ": " + e.getMessage());
            } else {
                plugin.getLogger().severe("Error executing transaction: " + e.getMessage());
            }
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
                    return; // Exit if the view for that player already exists
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
            plugin.getLogger().info("Transaction view created for UUID: " + untrimmedUuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating transaction view: " + e.getMessage());
        }
    }

    private static void createEmptyShopsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_EmptyShops_" + trimmedUuid;
        String databaseName = plugin.getConfig().getString("database.database");
        String checkSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(checkSQL)) {
            preparedStatement.setString(1, viewName);
            preparedStatement.setString(2, databaseName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) > 0) {
                    return; // Exit if the view for that player already exists
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking for existing empty shops view: " + e.getMessage());
            return;
        }

        try (Connection conn = getConnection();
             Statement statement = conn.createStatement()) {
            String sql = "CREATE VIEW " + viewName + " AS "
                    + "SELECT "
                    + "    e.Coordinates "
                    + "FROM EmptyShops e "
                    + "WHERE Owner1 = ? OR Owner2 = ?;";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, trimmedUuid);
                pstmt.setString(2, trimmedUuid);
                pstmt.executeUpdate();
            }
            
            plugin.getLogger().info("Empty shops view created for UUID: " + untrimmedUuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating empty shops view: " + e.getMessage());
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

    public static String displayTransactionsView(@NotNull String uuid, String playerName, Boolean displayPassed, int page, int pageSize) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_Transactions_" + trimmedUuid;
        StringBuilder transactions = new StringBuilder();

        // Calculate the offset for pagination
        int offset = (page - 1) * pageSize;

        String sql;
        if (displayPassed == null) {
            // Display all transactions
            sql = "SELECT TransactionDatetime, Amount, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " ORDER BY TransactionDateTime DESC LIMIT ? OFFSET ?";
        } else if (displayPassed) {
            // Display only passed transactions
            sql = "SELECT TransactionDatetime, Amount, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " WHERE Passed = 'Passed' ORDER BY TransactionDateTime DESC LIMIT ? OFFSET ?";
        } else {
            // Display only failed transactions
            sql = "SELECT TransactionDatetime, Amount, SourcePlayerName, DestinationPlayerName, Message FROM " + viewName + " WHERE Passed = 0 ORDER BY TransactionDateTime DESC LIMIT ? OFFSET ?";
        }

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, pageSize); // Set the page size
            pstmt.setInt(2, offset); // Set the offset for pagination
            ResultSet rs = pstmt.executeQuery();

            // Iterate over the result set
            while (rs.next()) {
                String dateTimeUTC = rs.getString("TransactionDateTime");
                String dateTimeLocal = TypeChecker.convertToLocalTime(dateTimeUTC); // Convert to local time
                Double amount = rs.getDouble("Amount");
                String source = rs.getString("SourcePlayerName");
                String destination = rs.getString("DestinationPlayerName");
                String transactionMessage = rs.getString("Message");

                transactions.append(dateTimeLocal).append(" ").append(amount);
                if (source.equalsIgnoreCase(playerName)) {
                    transactions.append(" -> ").append(destination);
                } else {
                    transactions.append(" <- ").append(source);
                }
                if (transactionMessage != null) {
                    transactions.append(" ").append(transactionMessage);
                }
                transactions.append("\n");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error viewing transactions for UUID " + untrimmedUuid + ": " + e.getMessage());
        }

        return transactions.toString();
    }

    public static List<String> displayEmptyShopsView(@NotNull String uuid) {
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        String viewName = "vw_EmptyShops_" + trimmedUuid;
        List<String> emptyShops = new ArrayList<>();

        // Check if the view for that player exists
        String checkViewSQL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = ?";
        String databaseName = plugin.getConfig().getString("database.database");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(checkViewSQL)) {
            pstmt.setString(1, viewName);
            pstmt.setString(2, databaseName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    return null; // Return null if the view does not exist
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking for empty shops view: " + e.getMessage());
            return null;
        }

        // If the view exists, proceed to retrieve the coordinates
        String sql = "SELECT Coordinates FROM " + viewName + ";";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String coordinates = rs.getString("Coordinates");
                emptyShops.add(coordinates);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error retrieving empty shops for UUID " + untrimmedUuid + ": " + e.getMessage());
        }

        if (emptyShops.isEmpty()) {
            return null;
        }

        return emptyShops;
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
        
        Instant currentTime = Instant.now(); // Use Instant for UTC time
        String currentTimeUTC = TypeChecker.convertToUTC(currentTime.toString()); // Convert to UTC using TypeChecker

        String sql = "INSERT INTO Autopays (" +
                "    Active, AutopayDatetime, AutopayName, Source, Destination," +
                "    Amount, InverseFrequency, EndsAfter" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, 1); // Active
            pstmt.setString(2, currentTimeUTC); // AutopayDatetime converted to UTC
            pstmt.setString(3, autopayName); // AutopayName
            pstmt.setString(4, trimmedUuid); // Source
            pstmt.setString(5, trimmedDestination); // Destination
            pstmt.setDouble(6, amount); // Amount
            pstmt.setInt(7, inverseFrequency); // InverseFrequency
            pstmt.setInt(8, endsAfter); // EndsAfter

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


    public static List<String> listAllAccounts() {
        String sql = "SELECT PlayerName, Balance, BalChange, AccountDatetime AS Created FROM PlayerAccounts ORDER BY PlayerName ASC";
        List<String> accounts = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String playerName = rs.getString("PlayerName");
                double balance = rs.getDouble("Balance");
                String accountDatetimeUTC = rs.getString("Created"); // Get the UTC datetime
                String accountDatetimeLocal = TypeChecker.convertToLocalTime(accountDatetimeUTC); // Convert to local time

                String accountInfo = playerName + ": " + balance + " (Created: " + accountDatetimeLocal + ")";
                accounts.add(accountInfo);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error listing accounts: " + e.getMessage());
        }

        return accounts;
    }

    private static boolean validateRollbackInput(String targetDateTime) {
        // Validate targetDateTime
        try {
            // Convert the targetDateTime to UTC format
            String targetDateTimeUTC = TypeChecker.convertToUTC(targetDateTime); // Use convertToUTC method

            // Check if there are any transactions to roll back
            String checkTransactionsSQL = "SELECT COUNT(*) FROM Transactions WHERE TransactionDatetime > ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(checkTransactionsSQL)) {
                pstmt.setString(1, targetDateTimeUTC); // Use the converted UTC time
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next() && rs.getInt(1) == 0) {
                    plugin.getLogger().info("No transactions found to roll back after " + targetDateTime);
                    return false; // Exit the method if no transactions are found
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error checking for transactions: " + e.getMessage());
                return false; // Exit the method if there's an error checking transactions
            }
        } catch (DateTimeParseException e) {
            plugin.getLogger().severe("Invalid targetDateTime format: " + targetDateTime);
            return false; // Exit the method if the format is invalid
        }
        
        return true;
    }
    
    public static void rollback(String targetDateTime) {
        // Validate input
        if (!validateRollbackInput(targetDateTime)) {
            return;
        }

        try (Connection conn = getConnection()) {
            // Disable auto-commit to ensure transaction consistency
            conn.setAutoCommit(false);

            try {
                // 1. Get all successful transactions after the target datetime
                String getTransactionsSQL =
                        "SELECT * FROM Transactions " +
                        "WHERE TransactionDatetime > ? AND Passed = 1 " +
                        "ORDER BY TransactionDatetime DESC LIMIT ? OFFSET ?"; // Add LIMIT and OFFSET

                // Convert targetDateTime to UTC
                String targetDateTimeUTC = TypeChecker.convertToUTC(targetDateTime);

                int batchSize = 100; // Define a suitable batch size
                int offset = 0; // Initialize offset
                boolean hasMoreRows = true;

                while (hasMoreRows) {
                    try (PreparedStatement pstmt = conn.prepareStatement(getTransactionsSQL)) {
                        pstmt.setString(1, targetDateTimeUTC); // Set target datetime
                        pstmt.setInt(2, batchSize); // Set limit
                        pstmt.setInt(3, offset); // Set offset
                        ResultSet rs = pstmt.executeQuery();

                        int processedRows = 0; // Count processed rows in this batch

                        // 2. Reverse each transaction in the batch
                        while (rs.next() && processedRows < batchSize) {
                            String source = rs.getString("Source");
                            String destination = rs.getString("Destination");
                            float amount = rs.getFloat("Amount");

                            // Update source balance if exists
                            if (source != null) {
                                Double newBalance = displayBalance(source) + amount;
                                Double newChange = getPlayerBalanceChange(source) + amount;
                                setPlayerBalance(source, newBalance, newChange);
                            }

                            // Update destination balance if exists
                            if (destination != null) {
                                Double newBalance = displayBalance(destination) - amount;
                                Double newChange = getPlayerBalanceChange(destination) - amount;
                                setPlayerBalance(destination, newBalance, newChange);
                            }

                            processedRows++;
                        }

                        // Check if we processed fewer rows than the batch size
                        if (processedRows < batchSize) {
                            hasMoreRows = false; // No more rows to process
                        }
                    }
                    offset += batchSize; // Increment the offset for the next batch
                }

                // 3. Delete transactions after target datetime
                String deleteTransactionsSQL = "DELETE FROM Transactions WHERE TransactionDatetime > ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteTransactionsSQL)) {
                    pstmt.setString(1, targetDateTimeUTC);
                    int rowsDeleted = pstmt.executeUpdate(); // Store the number of deleted rows
                    plugin.getLogger().info(rowsDeleted + " transactions deleted after " + targetDateTime); // Log the count
                }

                // 4. Delete autopays created after target datetime
                String deleteAutopaysSQL = "DELETE FROM Autopays WHERE AutopayDatetime > ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteAutopaysSQL)) {
                    pstmt.setString(1, targetDateTimeUTC);
                    pstmt.executeUpdate();
                }

                // 5. Reset account creation dates that are after target datetime
                String resetAccountsSQL =
                        "UPDATE PlayerAccounts SET AccountDatetime = ? " +
                        "WHERE AccountDatetime > ?";
                try (PreparedStatement pstmt = conn.prepareStatement(resetAccountsSQL)) {
                    pstmt.setString(1, targetDateTimeUTC);
                    pstmt.setString(2, targetDateTimeUTC);
                    pstmt.executeUpdate();
                }

                // If everything succeeded, commit the transaction
                conn.commit();
                plugin.getLogger().info("Successfully rolled back database to " + targetDateTime);

            } catch (SQLException e) {
                // If anything fails, roll back all changes
                conn.rollback();
                plugin.getLogger().severe("Error during rollback, changes reverted: " + e.getMessage());
                throw e; // Rethrow to handle it at a higher level
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

            int batchSize = 100; // Define a suitable batch size
            List<String> keys = new ArrayList<>(playersSection.getKeys(false));
            int totalKeys = keys.size();

            for (int i = 0; i < totalKeys; i++) {
                String key = keys.get(i);
                double balance = playersSection.getDouble(key + ".balance");
                double change = playersSection.getDouble(key + ".change");
                String playerName = playersSection.getString(key + ".name");
                String trimmedUuid = TypeChecker.trimUUID(key);

                if (!accountExists(trimmedUuid)) {
                    addAccount(trimmedUuid, playerName, balance, change);
                } else {
                    setPlayerBalance(trimmedUuid, balance, change);
                }

                // Commit in batches
                if ((i + 1) % batchSize == 0 || i == totalKeys - 1) {
                    plugin.getLogger().info("Processed " + (i + 1) + " player accounts.");
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
        String sql = "SELECT * FROM PlayerAccounts LIMIT ? OFFSET ?"; // Use LIMIT and OFFSET for pagination

        int batchSize = 100; // Define the batch size
        int offset = 0; // Initialize offset
        int playerCount = 0;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmtCount = conn.prepareStatement(sqlCount);
             ResultSet rsCount = pstmtCount.executeQuery()) {

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

            // Loop to fetch and process records in batches
            while (offset < playerCount) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, batchSize); // Set the batch size
                    pstmt.setInt(2, offset); // Set the current offset
                    ResultSet rs = pstmt.executeQuery();

                    while (rs.next()) {
                        String uuid = rs.getString("UUID");
                        String trimmedUuid = TypeChecker.trimUUID(uuid);
                        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
                        String playerName = rs.getString("PlayerName");
                        double balance = rs.getDouble("Balance");
                        double change = rs.getDouble("BalChange");

                        // Validate data before writing
                        if (uuid != null && playerName != null && balance >= 0) {
                            balanceConfig.set("players." + trimmedUuid + ".name", playerName);
                            balanceConfig.set("players." + trimmedUuid + ".balance", balance);
                            balanceConfig.set("players." + trimmedUuid + ".change", change);
                        } else {
                            plugin.getLogger().warning("Invalid data for UUID: " + untrimmedUuid);
                        }
                    }
                }

                offset += batchSize; // Increment the offset for the next batch
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
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, trimmedUuid);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0; // Return true if count is greater than 0
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking account existence for UUID " + untrimmedUuid + ": " + e.getMessage());
        }
        return false;
    }

    public static void updatePlayerName(String uuid, String playerName) {
        String sql = "UPDATE PlayerAccounts SET PlayerName = ? WHERE UUID = ?";
        String trimmedUuid = TypeChecker.trimUUID(uuid);
        String untrimmedUuid = TypeChecker.untrimUUID(uuid);
    
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.setString(2, trimmedUuid);
            int rowsUpdated = pstmt.executeUpdate();
    
            if (rowsUpdated > 0) {
                plugin.getLogger().info("Player name updated successfully for UUID: " + untrimmedUuid);
            } else {
                plugin.getLogger().info("No account found for UUID: " + untrimmedUuid);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating player name for UUID " + untrimmedUuid + ": " + e.getMessage());
        }
    }

    public static void exportDatabase() {
        String sqlAccounts = "SELECT * FROM PlayerAccounts";
        String sqlTransactions = "SELECT * FROM Transactions";
        String sqlAutopays = "SELECT * FROM Autopays";
        String sqlEmptyShops = "SELECT * FROM EmptyShops";
        String csvFilePath = "QE_DatabaseExport.csv"; // Path to save the CSV file
        int batchSize = 100; // Define a suitable batch size

        try (Connection conn = getConnection();
             FileWriter csvWriter = new FileWriter(csvFilePath)) {

            exportTableToCSV(conn, sqlAccounts, "PlayerAccounts Table", csvWriter, batchSize);
            exportTableToCSV(conn, sqlTransactions, "Transactions Table", csvWriter, batchSize);
            exportTableToCSV(conn, sqlAutopays, "Autopays Table", csvWriter, batchSize);
            exportTableToCSV(conn, sqlEmptyShops, "EmptyShops Table", csvWriter, batchSize);

            plugin.getLogger().info("Database exported to " + csvFilePath + " successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error exporting database: " + e.getMessage());
        } catch (IOException e) {
            plugin.getLogger().severe("Error writing CSV file: " + e.getMessage());
        }
    }

    private static void exportTableToCSV(Connection conn, String sql, String tableName, FileWriter csvWriter, int batchSize) throws SQLException, IOException {
        csvWriter.append(tableName + "\n");
        
        int offset = 0;
        boolean hasMoreRows = true;

        while (hasMoreRows) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql + " LIMIT ? OFFSET ?")) {
                pstmt.setInt(1, batchSize);
                pstmt.setInt(2, offset);
                ResultSet rs = pstmt.executeQuery();

                // Write the result set to CSV
                if (!rs.isBeforeFirst()) { // Check if the result set is empty
                    hasMoreRows = false; // No more rows to process
                    break;
                }

                writeResultSetToCSV(rs, csvWriter);
                offset += batchSize; // Increment the offset for the next batch
                csvWriter.append("\n"); // Separate batches with a blank line
                
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL error while exporting table '" + tableName + "' at offset " + offset + ": " + e.getMessage());
                throw e; // Rethrow the exception to handle it at a higher level
            } catch (IOException e) {
                plugin.getLogger().severe("IO error while writing to CSV for table '" + tableName + "' at offset " + offset + ": " + e.getMessage());
                throw e; // Rethrow the exception to handle it at a higher level
            } catch (Exception e) {
                plugin.getLogger().severe("Unexpected error while exporting table '" + tableName + "' at offset " + offset + ": " + e.getMessage());
                throw e; // Rethrow the exception to handle it at a higher level
            }
        }
    }

    private static void writeResultSetToCSV(ResultSet rs, FileWriter csvWriter) throws SQLException, IOException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        // Write the header row
        try {
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
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while writing result set to CSV: " + e.getMessage());
            throw e; // Rethrow the exception to handle it at a higher level
        } catch (IOException e) {
            plugin.getLogger().severe("IO error while writing to CSV: " + e.getMessage());
            throw e; // Rethrow the exception to handle it at a higher level
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error while writing result set to CSV: " + e.getMessage());
            throw e; // Rethrow the exception to handle it at a higher level
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

    public static void removeEmptyShop(String coordinates) {
        String sql = "DELETE FROM EmptyShops WHERE Coordinates = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, coordinates);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                plugin.getLogger().info("Empty shop at " + coordinates + " removed from the database.");
            } else {
                plugin.getLogger().info("No empty shop found with coordinates " + coordinates + " to remove.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing empty shop: " + e.getMessage());
        }
    }


}
