package net.derfla.quickeconomy.model;

/**
 * This object is used to store data about the players account at a faster place.
 */
public class PlayerAccount {

    private double balance;
    private double change;
    private String name;
    private String createdTime;

    /**
     * Used when creating a new PlayerAccount.
     * @param name The player name
     * @param balance The players balance. When creating new accounts, this should be 0.
     * @param change The change in balance from when the player last left the game. When creating new accounts, this should be 0.
     * @param createdTime Timestamp from the time of account creation.
     */
    public PlayerAccount(String name, double balance, double change, String createdTime) {
        this.balance = balance;
        this.change = change;
        this.name = name;
        this.createdTime = createdTime;
    }

    public double balance() {
        return balance;
    }

    public double change() {
        return change;
    }

    public String name() {
        return name;
    }

    public String createdTime() {
        return createdTime;
    }

    public void balance(double balanceNew) {
        this.balance = balanceNew;
    }

    public void change(double changeNew) {
        this.change = changeNew;
    }

    public void name(String nameNew) {
        this.name = nameNew;
    }

    /**
     * Custom method. Returns the PlayerAccount in a more readable format. Does not include change.
     * @return A string representation of the PlayerAccount object.
     */
    @Override
    public String toString() {
        String[] splitTime = createdTime.split(" ");
        return name + ": " + balance + " (Created: " + splitTime[0] + ")";
    }
}
