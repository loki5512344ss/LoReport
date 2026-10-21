package dev.loki.lorep.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Small helper for the chat messages of the plugin. The colouring is done by
 * {@link TextFormat}, the deprecated Bukkit colour API is not used.
 */
public class MessageUtil {

    private static final Component PREFIX = Component.text()
        .append(Component.text("[", NamedTextColor.DARK_GRAY))
        .append(Component.text("lorep", NamedTextColor.RED))
        .append(Component.text("]", NamedTextColor.DARK_GRAY))
        .append(Component.space())
        .build();

    private MessageUtil() {}

    /**
     * Colours a message.
     *
     * @param message the raw message
     * @return the coloured component
     */
    public static Component colorize(String message) {
        return TextFormat.component(message);
    }

    /**
     * Sends a prefixed message.
     *
     * @param sender the receiver
     * @param message the raw message
     */
    public static void send(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(colorize(message)));
    }

    /**
     * Sends a message without the prefix.
     *
     * @param sender the receiver
     * @param message the raw message
     */
    public static void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));
    }

    /**
     * Sends a message without the prefix.
     *
     * @param player the receiver
     * @param message the raw message
     */
    public static void sendNoPrefix(Player player, String message) {
        player.sendMessage(colorize(message));
    }

    /**
     * Colours a message and expands the placeholders.
     *
     * @param message the raw message
     * @param replacements the placeholder pairs
     * @return the coloured component
     */
    public static Component format(String message, String... replacements) {
        String result = message;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1]);
        }
        return colorize(result);
    }
}
