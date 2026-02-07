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
import net.derfla.quickeconomy.util.AbbreviationUtil;
import net.derfla.quickeconomy.util.Styles;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A custom Brigadier argument type that parses monetary amounts with optional
 * abbreviation suffixes (K, M, B, T).
 *
 * <p>Converts string input such as {@code "1.5K"} or {@code "200"} into a
 * {@link Double} value via {@link AbbreviationUtil#fromString(String)}.
 * Invalid formats or non-positive values produce a localised error message.</p>
 *
 * @author QuickEconomy
 * @since 1.3
 * @see AbbreviationUtil
 */
public final class MoneyArgument implements CustomArgumentType.Converted<Double, String> {

    private static final DynamicCommandExceptionType ERROR_INVALID = new DynamicCommandExceptionType(input ->
            MessageComponentSerializer.message().serialize(
                    Component.translatable("balcommand.invalidnumber", Styles.ERRORSTYLE)
            )
    );

    private static final DynamicCommandExceptionType ERROR_BAD_ABBREVIATION = new DynamicCommandExceptionType(input ->
            MessageComponentSerializer.message().serialize(
                    Component.translatable("balcommand.abbreviation.invalid",
                            Component.text(input.toString())).style(Styles.ERRORSTYLE)
            )
    );

    /** Common amount suggestions shown to players during tab-completion. */
    private static final List<String> SUGGESTIONS = List.of(
            "1", "10", "100", "1K", "10K", "100K", "1M", "10M", "100M", "1B"
    );

    @Override
    public Double convert(String nativeType) throws CommandSyntaxException {
        try {
            return AbbreviationUtil.fromString(nativeType);
        } catch (NumberFormatException e) {
            throw ERROR_INVALID.create(nativeType);
        } catch (IllegalArgumentException e) {
            throw ERROR_BAD_ABBREVIATION.create(nativeType);
        }
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(CommandContext context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        SUGGESTIONS.stream()
                .filter(s -> s.toLowerCase().startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}
