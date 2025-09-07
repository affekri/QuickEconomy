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
import net.derfla.quickeconomy.util.Styles;
import net.kyori.adventure.text.Component;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * A custom Brigadier command argument type for handling player account names in commands.
 * <p>
 * This argument type validates that the provided player name corresponds to an existing
 * account in the AccountCache and converts the string input to a {@link PlayerAccount} object.
 * It provides auto-completion suggestions for valid player names that match the input.
 * </p>
 * <p>
 * The argument only accepts player names that have an account in the Account Cache and
 * suggests all accounts where the input letters match the beginning of the player name
 * (case-insensitive).
 * </p>
 *
 * @author QuickEconomy
 * @since 1.0
 * @see PlayerAccount
 * @see AccountCache
 * @see CustomArgumentType.Converted
 */
public final class AccountArgument implements CustomArgumentType.Converted<PlayerAccount, String> {


    /**
     * Exception type thrown when a player name does not correspond to an existing account.
     * <p>
     * This exception is used when the convert method receives a player name that cannot
     * be found in the AccountCache. The exception message is localized using the
     * "player.notexists" translation key.
     * </p>
     */
    private static final DynamicCommandExceptionType ERROR_NOT_ACCOUNT = new DynamicCommandExceptionType(name -> {
        return MessageComponentSerializer.message().serialize(Component.translatable("player.notexists", Component.text(name.toString())).style(Styles.ERRORSTYLE));
    });

    /**
     * Converts a string player name to a {@link PlayerAccount} object.
     * <p>
     * This method validates that the provided player name exists in the AccountCache
     * and returns the corresponding PlayerAccount object. If the player name is not
     * found, a CommandSyntaxException is thrown with an appropriate error message.
     * </p>
     *
     * @param nativeType the player name string to convert
     * @return the PlayerAccount object corresponding to the player name
     * @throws CommandSyntaxException if the player name does not correspond to an existing account
     * @see AccountCache#accountExistsName(String)
     * @see AccountCache#getPlayerAccount(java.util.UUID)
     * @see AccountCache#getUUID(String)
     */
    @Override
    public PlayerAccount convert(String nativeType) throws CommandSyntaxException {
        if (AccountCache.accountExistsName(nativeType)) {
            return AccountCache.getPlayerAccount(AccountCache.getUUID(nativeType));
        }
        throw ERROR_NOT_ACCOUNT.create(nativeType);
    }

    /**
     * Returns the native Brigadier argument type used for parsing.
     * <p>
     * This method returns a {@link StringArgumentType#word()} which accepts a single
     * word (no spaces) as input. This is appropriate for player names which typically
     * do not contain spaces.
     * </p>
     *
     * @return a StringArgumentType that accepts single words
     * @see StringArgumentType#word()
     */
    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    /**
     * Provides auto-completion suggestions for player names.
     * <p>
     * This method returns a list of player names from the AccountCache that start with
     * the currently typed text (case-insensitive matching). The suggestions help users
     * quickly find and select valid player accounts without having to type the full name.
     * </p>
     *
     * @param context the command context (not used in this implementation, uses raw type as required by interface)
     * @param builder the suggestions builder used to construct the suggestion list
     * @return a CompletableFuture containing the suggestions
     * @see AccountCache#getAllPlayerNames()
     * @see SuggestionsBuilder#getRemainingLowerCase()
     * @see SuggestionsBuilder#suggest(String)
     */
    @Override
    public CompletableFuture<Suggestions> listSuggestions(CommandContext context, SuggestionsBuilder builder) {
        AccountCache.getAllPlayerNames().stream()
                .filter(player -> player.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                .collect(Collectors.toList())
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}
