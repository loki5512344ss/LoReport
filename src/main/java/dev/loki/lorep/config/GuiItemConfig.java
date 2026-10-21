package dev.loki.lorep.config;

import dev.loki.lorep.util.TextFormat;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Configuration driven GUI item: material, custom model data, display name,
 * lore, target slots and the glowing flag.
 *
 * <p>The display name and the lore are kept as raw config strings (ampersand
 * colour codes included) so the GUI code can expand the placeholders before
 * the text is turned into Adventure components.</p>
 */
public record GuiItemConfig(
    Material material,
    int customModelData,
    String displayName,
    List<String> lore,
    List<Integer> slots,
    boolean glowing
) {
    public static final int NO_CUSTOM_MODEL = -1;

    /** Builds the item with the configured name and lore. */
    public ItemStack createItem() {
        return createItem(displayName, lore);
    }

    /**
     * Builds the item with the given name and lore.
     *
     * @param name the display name, may be null
     * @param loreLines the lore lines, may be null
     * @return the ready to place item
     */
    public ItemStack createItem(String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (customModelData != NO_CUSTOM_MODEL) {
                meta.setCustomModelData(customModelData);
            }
            if (name != null) {
                meta.displayName(TextFormat.component(name));
            }
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(TextFormat.lore(loreLines));
            }
            if (glowing) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
