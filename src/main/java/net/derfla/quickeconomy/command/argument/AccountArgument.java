package net.derfla.quickeconomy.command.argument;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.derfla.quickeconomy.model.PlayerAccount;
import net.derfla.quickeconomy.util.AccountCache;
import net.kyori.adventure.text.Component;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Custom command argument. Only accepts player names that have an account in the Account Cache.
 * Suggests all accounts where letters match.
 */
public final class AccountArgument implements CustomArgumentType.Converted<PlayerAccount, String> {


    private static final DynamicCommandExceptionType ERROR_NOT_ACCOUNT = new DynamicCommandExceptionType(name -> {
        return MessageComponentSerializer.message().serialize(Component.text(name + " does not seem to exist on this server!"));
    });

    @Override
    public PlayerAccount convert(String nativeType) throws CommandSyntaxException {
        if (AccountCache.accountExistsName(nativeType)) {
            return AccountCache.getPlayerAccount(AccountCache.getUUID(nativeType));
        }
        throw ERROR_NOT_ACCOUNT.create(nativeType);
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(CommandContext context, SuggestionsBuilder builder) {
        AccountCache.getAllPlayerNames().stream()
                .filter(player -> player.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                .collect(Collectors.toList())
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}
