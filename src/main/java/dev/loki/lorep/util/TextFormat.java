package dev.loki.lorep.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the ampersand colour codes used inside the config files into
 * Adventure components.
 *
 * <p>The parser is written by hand on purpose: the deprecated Bukkit colour
 * API is not used and MiniMessage tags are never interpreted, so player
 * provided text (player names, report reasons) cannot smuggle formatting or
 * click actions into a GUI item.</p>
 */
public final class TextFormat {

    private static final char CODE_PREFIX = '&';
    private static final char LEGACY_CODE_PREFIX = '§';

    private static final Map<Character, NamedTextColor> COLORS = new HashMap<>();
    private static final Map<Character, TextDecoration> DECORATIONS = new HashMap<>();

    static {
        COLORS.put('0', NamedTextColor.BLACK);
        COLORS.put('1', NamedTextColor.DARK_BLUE);
        COLORS.put('2', NamedTextColor.DARK_GREEN);
        COLORS.put('3', NamedTextColor.DARK_AQUA);
        COLORS.put('4', NamedTextColor.DARK_RED);
        COLORS.put('5', NamedTextColor.DARK_PURPLE);
        COLORS.put('6', NamedTextColor.GOLD);
        COLORS.put('7', NamedTextColor.GRAY);
        COLORS.put('8', NamedTextColor.DARK_GRAY);
        COLORS.put('9', NamedTextColor.BLUE);
        COLORS.put('a', NamedTextColor.GREEN);
        COLORS.put('b', NamedTextColor.AQUA);
        COLORS.put('c', NamedTextColor.RED);
        COLORS.put('d', NamedTextColor.LIGHT_PURPLE);
        COLORS.put('e', NamedTextColor.YELLOW);
        COLORS.put('f', NamedTextColor.WHITE);

        DECORATIONS.put('k', TextDecoration.OBFUSCATED);
        DECORATIONS.put('l', TextDecoration.BOLD);
        DECORATIONS.put('m', TextDecoration.STRIKETHROUGH);
        DECORATIONS.put('n', TextDecoration.UNDERLINED);
        DECORATIONS.put('o', TextDecoration.ITALIC);
    }

    private TextFormat() {}

    /**
     * Parses a single config line into a component.
     *
     * @param text the raw line, may be null
     * @return the parsed component, empty for null or blank input
     */
    public static Component component(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        List<Component> segments = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        NamedTextColor color = null;
        Map<TextDecoration, TextDecoration.State> decorations = new HashMap<>();

        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            char code = codeAt(text, index);
            if (code != 0) {
                flush(segments, buffer, color, decorations);
                if (COLORS.containsKey(code)) {
                    color = COLORS.get(code);
                    decorations.clear();
                } else if (DECORATIONS.containsKey(code)) {
                    decorations.put(DECORATIONS.get(code), TextDecoration.State.TRUE);
                } else {
                    color = null;
                    decorations.clear();
                }
                index += 2;
                continue;
            }
            buffer.append(current);
            index++;
        }
        flush(segments, buffer, color, decorations);
        return Component.text().append(segments).build();
    }

    /**
     * Parses config lore lines into components.
     *
     * @param lines the raw lines, may be null
     * @return the parsed lines, empty for null or empty input
     */
    public static List<Component> lore(List<String> lines) {
        List<Component> lore = new ArrayList<>();
        if (lines == null) {
            return lore;
        }
        for (String line : lines) {
            lore.add(component(line));
        }
        return lore;
    }

    /**
     * Removes every colour code from a config line.
     *
     * @param text the raw line, may be null
     * @return the plain text
     */
    public static String strip(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder plain = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            if (codeAt(text, index) != 0) {
                index += 2;
                continue;
            }
            plain.append(text.charAt(index));
            index++;
        }
        return plain.toString();
    }

    /**
     * Reads the colour code at the given position.
     *
     * @return the lower case code character or 0 when there is no code
     */
    private static char codeAt(String text, int index) {
        if (index + 1 >= text.length()) {
            return 0;
        }
        char prefix = text.charAt(index);
        if (prefix != CODE_PREFIX && prefix != LEGACY_CODE_PREFIX) {
            return 0;
        }
        char code = Character.toLowerCase(text.charAt(index + 1));
        boolean known = COLORS.containsKey(code) || DECORATIONS.containsKey(code) || code == 'r';
        return known ? code : 0;
    }

    /** Appends the buffered text as a segment with the given style. */
    private static void flush(List<Component> segments, StringBuilder buffer,
                              NamedTextColor color, Map<TextDecoration, TextDecoration.State> decorations) {
        if (buffer.isEmpty()) {
            return;
        }
        TextComponent segment = Component.text(buffer.toString()).color(color);
        for (Map.Entry<TextDecoration, TextDecoration.State> decoration : decorations.entrySet()) {
            segment = segment.decoration(decoration.getKey(), decoration.getValue());
        }
        segments.add(segment);
        buffer.setLength(0);
    }
}
