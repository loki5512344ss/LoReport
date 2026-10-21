package dev.loki.lorep.config;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * **Feature: lorep-report-plugin, Property 9: Configuration Parsing**
 * **Validates: Requirements 7.1, 7.3, 7.4**
 *
 * <p>The GUI items of the config must never fall back to a plain stone block:
 * an unknown or missing material has to be reported instead.</p>
 */
public class MaterialParsingTest {

    /**
     * Property 9: Configuration Parsing
     * A material name of the config resolves to the material it names, no
     * matter which of the accepted spellings is used.
     */
    @Test
    void resolvesPlainMaterialNames() {
        assertEquals(Material.PLAYER_HEAD, ConfigManager.resolveMaterial("PLAYER_HEAD"));
        assertEquals(Material.PAPER, ConfigManager.resolveMaterial("PAPER"));
        assertEquals(Material.STONE, ConfigManager.resolveMaterial("STONE"));
    }

    /**
     * Property 9: Configuration Parsing
     * A namespaced material name resolves to the same material as the plain
     * spelling of it.
     */
    @Test
    void resolvesNamespacedMaterialNames() {
        assertEquals(Material.PLAYER_HEAD, ConfigManager.resolveMaterial("minecraft:player_head"));
        assertEquals(Material.PAPER, ConfigManager.resolveMaterial("minecraft:paper"));
    }

    /**
     * Property 9: Configuration Parsing
     * The material lookup is case insensitive.
     */
    @Test
    void resolvesMaterialNamesCaseInsensitive() {
        assertEquals(Material.PLAYER_HEAD, ConfigManager.resolveMaterial("player_head"));
        assertEquals(Material.PLAYER_HEAD, ConfigManager.resolveMaterial("Player_Head"));
    }

    /**
     * Property 9: Configuration Parsing
     * A typo in the material name does not resolve to any material, so the
     * caller can report it and use a meaningful fallback.
     */
    @Test
    void rejectsUnknownMaterialNames() {
        assertNull(ConfigManager.resolveMaterial("PLAYERHEAD"));
        assertNull(ConfigManager.resolveMaterial("minecraft:playerhead"));
        assertNull(ConfigManager.resolveMaterial("not_a_material"));
    }

    /**
     * Property 9: Configuration Parsing
     * An empty material name does not resolve to any material either.
     */
    @Test
    void rejectsEmptyMaterialNames() {
        assertNull(ConfigManager.resolveMaterial(null));
        assertNull(ConfigManager.resolveMaterial(""));
        assertNull(ConfigManager.resolveMaterial("   "));
    }
}
