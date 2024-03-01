package net.derfla.quickeconomy.command;

import net.derfla.quickeconomy.util.Styles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class QuickeconomyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String string, @NotNull String[] strings) {
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
        return true;
    }
}
