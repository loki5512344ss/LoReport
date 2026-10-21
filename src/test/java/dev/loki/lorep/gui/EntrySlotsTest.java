package dev.loki.lorep.gui.item;

import dev.loki.lorep.config.GuiItemConfig;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **Feature: lorep-report-plugin, Property 6: Pagination Correctness**
 * **Validates: Requirements 4.4**
 *
 * <p>The entries of a list must fill the free rows of the inventory and must
 * never overlap with the navigation buttons of the bottom row.</p>
 */
public class EntrySlotsTest {

    private static final int INVENTORY_SIZE = 54;
    private static final int FIRST_RESERVED_SLOT = 45;

    /**
     * Property 6: Pagination Correctness
     * Without configured slots the entries fill the rows above the navigation
     * row, so a full page never touches the buttons.
     */
    @Test
    void usesFreeRowsByDefault() {
        List<Integer> slots = EntrySlots.resolve(null, INVENTORY_SIZE);

        assertEquals(45, slots.size());
        assertEquals(0, slots.get(0));
        assertEquals(44, slots.get(slots.size() - 1));
        for (int slot : slots) {
            assertTrue(slot < FIRST_RESERVED_SLOT, "slot " + slot + " is reserved for the navigation");
        }
    }

    /**
     * Property 6: Pagination Correctness
     * The configured slots are used first, the remaining free slots follow so
     * that no entry of a page is lost.
     */
    @Test
    void prefersConfiguredSlots() {
        GuiItemConfig config = new GuiItemConfig(Material.PLAYER_HEAD, GuiItemConfig.NO_CUSTOM_MODEL,
            null, List.of(), List.of(20, 21), false);

        List<Integer> slots = EntrySlots.resolve(config, INVENTORY_SIZE);

        assertEquals(20, slots.get(0));
        assertEquals(21, slots.get(1));
        assertEquals(0, slots.get(2));
        assertEquals(45, slots.size());
    }

    /**
     * Property 6: Pagination Correctness
     * A slot of the navigation row is never used by an entry, no matter how it
     * is configured.
     */
    @Test
    void ignoresReservedSlots() {
        GuiItemConfig config = new GuiItemConfig(Material.PLAYER_HEAD, GuiItemConfig.NO_CUSTOM_MODEL,
            null, List.of(), List.of(45, 49, 53), false);

        List<Integer> slots = EntrySlots.resolve(config, INVENTORY_SIZE);

        for (int slot : slots) {
            assertTrue(slot < FIRST_RESERVED_SLOT, "slot " + slot + " is reserved for the navigation");
        }
    }

    /**
     * Property 6: Pagination Correctness
     * Every slot is used at most once, the entries never overlap.
     */
    @Test
    void usesEverySlotOnlyOnce() {
        GuiItemConfig config = new GuiItemConfig(Material.PLAYER_HEAD, GuiItemConfig.NO_CUSTOM_MODEL,
            null, List.of(), List.of(5, 5, 6), false);

        List<Integer> slots = EntrySlots.resolve(config, INVENTORY_SIZE);

        assertEquals(slots.size(), slots.stream().distinct().count());
        assertFalse(slots.contains(FIRST_RESERVED_SLOT));
    }
}
