package dev.loki.lorep.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **Feature: lorep-report-plugin, Property 9: Configuration Parsing**
 * **Validates: Requirements 7.1, 7.3, 7.4**
 *
 * <p>The ampersand colour codes of the config have to be turned into
 * components, player provided text must stay literal text.</p>
 */
public class TextFormatTest {

    /**
     * Property 9: Configuration Parsing
     * A colour code colours the text that follows it.
     */
    @Test
    void coloursTextWithCode() {
        Component component = TextFormat.component("&cHello");

        List<? extends Component> children = component.children();
        assertEquals(1, children.size());
        assertEquals("Hello", ((TextComponent) children.get(0)).content());
        assertEquals(NamedTextColor.RED, children.get(0).color());
    }

    /**
     * Property 9: Configuration Parsing
     * A decoration code decorates the text that follows it.
     */
    @Test
    void decoratesTextWithCode() {
        Component component = TextFormat.component("&lBold");

        List<? extends Component> children = component.children();
        assertEquals(TextDecoration.State.TRUE, children.get(0).decoration(TextDecoration.BOLD));
    }

    /**
     * Property 9: Configuration Parsing
     * A text without a colour code stays plain text.
     */
    @Test
    void keepsPlainText() {
        Component component = TextFormat.component("plain text");

        List<? extends Component> children = component.children();
        assertEquals(1, children.size());
        assertEquals("plain text", ((TextComponent) children.get(0)).content());
    }

    /**
     * Property 9: Configuration Parsing
     * A text of a player is never interpreted as a formatting instruction.
     */
    @Test
    void keepsPlayerTextLiteral() {
        Component component = TextFormat.component("<red>not a tag &x also not");

        List<? extends Component> children = component.children();
        assertEquals(1, children.size());
        assertEquals("<red>not a tag &x also not", ((TextComponent) children.get(0)).content());
        assertTrue(children.get(0).color() == null, "player text must not be coloured");
    }

    /**
     * Property 9: Configuration Parsing
     * The colour codes can be removed again.
     */
    @Test
    void stripsColourCodes() {
        assertEquals("Hello", TextFormat.strip("&cHello"));
        assertEquals("", TextFormat.strip(null));
    }
}
