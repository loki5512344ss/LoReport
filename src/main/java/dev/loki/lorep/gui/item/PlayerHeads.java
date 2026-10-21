package dev.loki.lorep.gui.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Player head helpers.
 *
 * <p>A head item without a profile is rendered by the client as a plain block,
 * so the profile always carries the player name: the client resolves the skin
 * by name and shows a real head.</p>
 */
public final class PlayerHeads {

    private PlayerHeads() {}

    /**
     * Creates a player head that points at the given player.
     *
     * @param uuid the player uuid, may be null
     * @param playerName the player name, may be null
     * @return the head item
     */
    public static ItemStack create(UUID uuid, String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        applyProfile(head, uuid, playerName);
        return head;
    }

    /**
     * Points a head item at the given player.
     *
     * @param item the item to update, may be null
     * @param uuid the player uuid, may be null
     * @param playerName the player name, may be null
     */
    public static void applyProfile(ItemStack item, UUID uuid, String playerName) {
        if (item == null || uuid == null || !(item.getItemMeta() instanceof SkullMeta skullMeta)) {
            return;
        }
        String name = resolveName(uuid, playerName);
        PlayerProfile profile = name.isEmpty()
            ? Bukkit.createProfile(uuid)
            : Bukkit.createProfile(uuid, name);
        skullMeta.setPlayerProfile(profile);
        item.setItemMeta(skullMeta);
    }

    /**
     * Resolves a usable player name for the profile.
     *
     * @param uuid the player uuid
     * @param playerName the known player name, may be null
     * @return the name or an empty string when it is unknown
     */
    private static String resolveName(UUID uuid, String playerName) {
        if (isValidName(playerName)) {
            return playerName;
        }
        String cached = Bukkit.getOfflinePlayer(uuid).getName();
        return isValidName(cached) ? cached : "";
    }

    /** Checks that the name is a valid Minecraft user name. */
    private static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) {
            return false;
        }
        for (char character : name.toCharArray()) {
            boolean allowed = Character.isLetterOrDigit(character) || character == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
