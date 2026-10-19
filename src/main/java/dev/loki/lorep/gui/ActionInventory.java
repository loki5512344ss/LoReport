package dev.loki.lorep.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

/**
 * Inventory that exposes slot-keyed click actions. Both {@link ReportGui} and
 * {@link AnticheatGui} implement it so {@link GuiClickListener} can cancel,
 * dispatch and paginate generically for either view.
 */
public interface ActionInventory extends InventoryHolder {

    /** Slot index to action string mapping used for click dispatch. */
    Map<Integer, String> slotActions();

    /** Zero-based page currently shown in the list view. */
    int getPage();

    /** Whether the current view is a detail view. */
    boolean isDetailView();

    /** Open the list view of this inventory family at the given zero-based page. */
    void openPage(Player player, int page);

    /** Total pages available in the list view, at least 1. */
    int getTotalPages();
}
