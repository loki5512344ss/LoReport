package dev.loki.lorep.gui.item;

import dev.loki.lorep.config.GuiItemConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Slot layout of the paginated entry lists.
 *
 * <p>The bottom row of the inventory is reserved for the navigation buttons,
 * the entries fill the rows above it. When the config declares its own slots
 * for an entry item those slots win, the remaining free slots of the grid
 * follow. The grid keeps the entries aligned instead of letting them bleed
 * into the navigation row.</p>
 */
public final class EntrySlots {

    /** Inventory rows reserved for the navigation buttons. */
    public static final int RESERVED_ROWS = 1;

    /** Inventory columns per row. */
    public static final int COLUMNS = 9;

    private EntrySlots() {}

    /**
     * Resolves the slots the entries of a page are placed in.
     *
     * @param config the entry item config, may be null
     * @param inventorySize the size of the inventory in slots
     * @return the ordered slot list
     */
    public static List<Integer> resolve(GuiItemConfig config, int inventorySize) {
        int rows = Math.max(1, inventorySize / COLUMNS);
        int firstEntrySlot = (rows - RESERVED_ROWS) * COLUMNS;
        Set<Integer> used = new LinkedHashSet<>();
        List<Integer> slots = new ArrayList<>();

        if (config != null) {
            for (int slot : config.slots()) {
                if (slot >= 0 && slot < firstEntrySlot && used.add(slot)) {
                    slots.add(slot);
                }
            }
        }
        for (int slot = 0; slot < firstEntrySlot; slot++) {
            if (used.add(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }
}
