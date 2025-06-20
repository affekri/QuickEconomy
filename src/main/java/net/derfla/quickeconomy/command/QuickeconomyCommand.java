package net.derfla.quickeconomy.command;

import net.derfla.quickeconomy.Main;
import net.derfla.quickeconomy.database.Migration;
import net.derfla.quickeconomy.database.System;
import net.derfla.quickeconomy.database.TableManagement;
import net.derfla.quickeconomy.database.Utility;
import net.derfla.quickeconomy.util.DerflaAPI;
import net.derfla.quickeconomy.util.Styles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QuickeconomyCommand implements TabExecutor {

    static Plugin plugin = Main.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String string, @NotNull String[] strings) {
        if (strings.length >= 1) {
            switch (strings[0].toLowerCase()){
                case "migrate":
                    if(!sender.hasPermission("quickeconomy.migrate") && sender instanceof Player) {
                        break;
                    }
                    if(Main.SQLMode) {
                        Migration.migrateToBalanceFile();
                        plugin.getConfig().set("database.enabled", false);
                        plugin.saveConfig();
                        Main.SQLMode = false;
                        sender.sendMessage(Component.translatable("qecommand.migrate.file").style(Styles.INFOSTYLE));
                        return true;
                    } else {
                        boolean connectedAndSetup = false;
                        try{
                            Utility.connectToDatabase();
                            TableManagement.createTables();
                            plugin.getConfig().set("database.enabled", true);
                            plugin.saveConfig();
                            Main.SQLMode = true;
                            sender.sendMessage(Component.translatable("qecommand.migrate.database").style(Styles.INFOSTYLE));
                            connectedAndSetup = true;
                        } catch (Exception e) {
                            sender.sendMessage(Component.translatable("qecommand.migrate.database.fail").style(Styles.ERRORSTYLE));
                            plugin.getLogger().warning("Failed to connect to database during migration attempt! " + e.getMessage());
                            plugin.getConfig().set("database.enabled", false);
                            plugin.saveConfig();
                            Main.SQLMode = false;
                        }

                        if (connectedAndSetup) {
                            Migration.migrateToDatabase();
                        }
                        return true;
                    }
                case "rollback":
                    if(!Main.SQLMode) {
                        // Rollback is only available when connected to a database
                        break;
                    }
                    if(!sender.hasPermission("quickeconomy.rollback") && sender instanceof Player) {
                        break;
                    }
                    // Ensure proper validation of input parameters
                    if (strings.length < 5) {
                        sender.sendMessage(Component.translatable("qecommand.rollback.date.fail").style(Styles.ERRORSTYLE));
                        return true;
                    }
                    
                    String timestampString;
                    try {
                        // Zero-pad month and day to ensure correct format
                        String year = strings[1];
                        String month = String.format("%02d", Integer.parseInt(strings[2]));
                        String day = String.format("%02d", Integer.parseInt(strings[3]));
                        String time = strings[4];
                        
                        timestampString = year + "-" + month + "-" + day + " " + time;
                        
                        // Validate that the timestamp can be parsed
                        Timestamp.valueOf(timestampString);
                    } catch (Exception e) {
                        sender.sendMessage(Component.translatable("qecommand.rollback.date.fail").style(Styles.ERRORSTYLE));
                        plugin.getLogger().info("Rollback failed: " + e.getMessage());
                        return true;
                    }
                    
                    // Execute rollback asynchronously and handle the result
                    System.rollback(timestampString).whenComplete((result, ex) -> {
                        if (ex != null) {
                            sender.sendMessage(Component.translatable("qecommand.rollback.fail").style(Styles.ERRORSTYLE));
                            plugin.getLogger().info("Rollback failed: " + ex.getMessage());
                        } else {
                            sender.sendMessage(Component.translatable("qecommand.rollback.success").style(Styles.INFOSTYLE));
                            plugin.getLogger().info("Rollback complete to " + timestampString);
                        }
                    });
                    return true;
                case "setup":
                    if(!sender.hasPermission("quickeconomy.setup") && sender instanceof Player) {
                        break;
                    }
                    String storageMethod = Main.SQLMode ? "SQL Server" : "File";
                    String connections = Main.SQLMode ? "(" + Utility.dataSource.getMaximumPoolSize() + ")" : "";
                    sender.sendMessage(Component.translatable("qecommand.setup", Component.text(storageMethod + " " + connections), Component.text(plugin.getPluginMeta().getVersion())).style(Styles.INFOSTYLE));
                    if(DerflaAPI.updateAvailable()) {
                        sender.sendMessage(Component.translatable("quickeconomy.update").style(Styles.INFOSTYLE).clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/quickeconomy/")));
                    }
                    return true;
            }
        }

        // Send applicable help messages based on permissions
        if (sender.hasPermission("quickeconomy.balance")) {
            sender.sendMessage(Component.translatable("qecommand.balance", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.balance.seeall")) {
            sender.sendMessage(Component.translatable("qecommand.balance.seeall", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.balance.modifyall")) {
            sender.sendMessage(Component.translatable("qecommand.balance.modifyall", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.shop.create")) {
            sender.sendMessage(Component.translatable("qecommand.shop.create", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.bank")) {
            sender.sendMessage(Component.translatable("qecommand.bank", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.bank.create")) {
            sender.sendMessage(Component.translatable("qecommand.bank.create", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.migrate")) {
            sender.sendMessage(Component.translatable("qecommand.migrate.info", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.rollback")) {
            sender.sendMessage(Component.translatable("qecommand.rollback.info", Styles.INFOSTYLE));
        }
        if (sender.hasPermission("quickeconomy.setup")) {
            sender.sendMessage(Component.translatable("qecommand.setup.info", Styles.INFOSTYLE));
        }
        return true;

    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if(strings.length == 1){
            List<String> returnList = new ArrayList<>();
            if(sender.hasPermission("quickeconomy.migrate")){
                returnList.add("migrate");
            }
            if(sender.hasPermission("quickeconomy.rollback") && Main.SQLMode){
                returnList.add("rollback");
            }
            if(sender.hasPermission("quickeconomy.setup")) {
                returnList.add("setup");
            }
            return returnList.stream()
                    .filter(subCommand -> subCommand.toLowerCase().startsWith(strings[0]))
                    .collect(Collectors.toList());
        }
        if(strings[0].equalsIgnoreCase("rollback") && sender.hasPermission("quickeconomy.rollback")) {
            switch (strings.length) {
                case 2:
                    return Stream.of(String.valueOf(LocalDate.now().getYear()))
                            .filter(subCommand -> subCommand.startsWith(strings[1]))
                            .collect(Collectors.toList());
                case 3:
                    return Stream.of(String.format("%02d", LocalDate.now().getMonthValue()))
                            .filter(subCommand -> subCommand.startsWith(strings[2]))
                            .collect(Collectors.toList());
                case 4:
                    return Stream.of(String.format("%02d", LocalDate.now().getDayOfMonth()))
                            .filter(subCommand -> subCommand.startsWith(strings[3]))
                            .collect(Collectors.toList());
                case 5:
                    return Collections.singletonList("hh:mm:ss");
            }
        }
        return Collections.emptyList();
    }
}
