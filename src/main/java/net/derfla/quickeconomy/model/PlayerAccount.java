package net.derfla.quickeconomy.model;

/**
 * Represents a player's economic account in the QuickEconomy system.
 * This model class stores essential player account information including balance,
 * balance changes, player name, and account creation timestamp. It serves as a
 * cached representation of player data for improved performance.
 * 
 * <p>This class is used by the AccountCache system to store frequently accessed
 * player data in memory, reducing database queries and file I/O operations.</p>
 */
public class PlayerAccount {

    private double balance;
    private double change;
    private String name;
    private String createdTime;

    /**
     * Constructs a new PlayerAccount with the specified account information.
     * This constructor is used when creating new player accounts or loading existing
     * account data from storage (database or file system).
     * 
     * @param name The player's username/display name
     * @param balance The player's current balance. For new accounts, this should be 0.0
     * @param change The accumulated balance change since the player last left the server.
     *               For new accounts, this should be 0.0
     * @param createdTime ISO timestamp string indicating when the account was created
     *                    (format: "yyyy-MM-dd HH:mm:ss" in UTC)
     */
    public PlayerAccount(String name, double balance, double change, String createdTime) {
        this.balance = balance;
        this.change = change;
        this.name = name;
        this.createdTime = createdTime;
    }

    /**
     * Gets the player's current account balance.
     * 
     * @return The current balance as a double value
     */
    public double balance() {
        return balance;
    }

    /**
     * Gets the accumulated balance change since the player last left the server.
     * This value is used to track earnings/losses that occurred while the player
     * was offline and is displayed in welcome messages.
     * 
     * @return The balance change as a double value
     */
    public double change() {
        return change;
    }

    /**
     * Gets the player's username/display name.
     * 
     * @return The player's name as a String
     */
    public String name() {
        return name;
    }

    /**
     * Gets the timestamp when this account was created.
     * 
     * @return The creation timestamp as an ISO formatted string (UTC timezone)
     */
    public String createdTime() {
        return createdTime;
    }

    /**
     * Sets the player's account balance to a new value.
     * This method updates the cached balance and should be synchronized with
     * persistent storage (database or file) by the calling code.
     * 
     * @param balanceNew The new balance value to set
     */
    public void balance(double balanceNew) {
        this.balance = balanceNew;
    }

    /**
     * Sets the accumulated balance change value.
     * This tracks the net change in balance since the player last left the server,
     * typically reset to 0 when the player joins.
     * 
     * @param changeNew The new balance change value to set
     */
    public void change(double changeNew) {
        this.change = changeNew;
    }

    /**
     * Updates the player's name/username.
     * This method is typically called when a player changes their username
     * to keep the cached data synchronized.
     * 
     * @param nameNew The new player name to set
     */
    public void name(String nameNew) {
        this.name = nameNew;
    }

    /**
     * Returns a human-readable string representation of this PlayerAccount.
     * The format includes the player name, current balance, and account creation time.
     * Note: The balance change value is intentionally excluded from this representation.
     * 
     * <p>Example output: "PlayerName: 1250.50 (Created: 2024-01-15 14:30:22)"</p>
     * 
     * @return A formatted string representation of the PlayerAccount object
     */
    @Override
    public String toString() {
        String[] splitTime = createdTime.split(" ");
        return name + ": " + balance + " (Created: " + splitTime[0] + ")";
    }
}
