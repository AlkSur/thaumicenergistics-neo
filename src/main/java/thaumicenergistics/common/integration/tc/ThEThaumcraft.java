package thaumicenergistics.common.integration.tc;

import java.util.Map;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumcraft.api.events.ResearchCompletedEvent;
import thaumcraft.common.research.TCResearchManager;

/**
 * Thaumic Energistics research. The category and every research entry are datapack files under
 * data/thaumicenergistics/research_category and data/thaumicenergistics/research (TEL-03), so packs can move,
 * re-page or replace them like Thaumcraft's own.
 * <p>
 * What stays in code: the PSEUDO_ stand-ins in TE's tab mirror Thaumcraft research in other tabs (their
 * research files have no cost, so they cannot be started); completing the real research completes them.
 */
public final class ThEThaumcraft {
    private static final Logger LOG = LoggerFactory.getLogger("ThaumicEnergistics/Research");

    /** Real Thaumcraft research key → TE stand-in key. */
    private static final Map<String, String> REAL_TO_PSEUDO = Map.of(
            "DISTILESSENTIA", "PSEUDO_DISTILESSENTIA",
            "TUBEFILTER", "PSEUDO_TUBEFILTER",
            "ALCHEMICALDUPLICATION", "PSEUDO_ALCHEMICALDUPLICATION",
            "MIRROR", "PSEUDO_MIRROR",
            "INFUSION", "PSEUDO_INFUSION",
            "WARDEDARCANA", "PSEUDO_WARDEDARCANA",
            "FOCUSFIRE", "PSEUDO_FOCUSFIRE",
            "VISPOWER", "PSEUDO_VISPOWER",
            "SCEPTRE", "PSEUDO_SCEPTRE",
            "COREGATHER", "PSEUDO_COREGATHER");

    private ThEThaumcraft() {}

    public static void init() {
        registerPseudoSync();
        LOG.info("Thaumic Energistics research is loaded from data packs; pseudo prerequisite sync registered.");
    }

    public static Map<String, String> pseudoPrerequisites() {
        return REAL_TO_PSEUDO;
    }

    private static void registerPseudoSync() {
        // Players who finished the real research before this mod was added never fire the completion event, so
        // their pseudo prerequisites are filled in at login (TEA2-08).
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent login) -> {
                    var player = login.getEntity();
                    REAL_TO_PSEUDO.forEach((realKey, pseudoKey) -> {
                        try {
                            if (TCResearchManager.has(player, realKey) && !TCResearchManager.has(player, pseudoKey)) {
                                TCResearchManager.complete(player, pseudoKey, false);
                            }
                        } catch (Exception ignored) {
                        }
                    });
                });
        NeoForge.EVENT_BUS.addListener(ResearchCompletedEvent.class, (ResearchCompletedEvent rc) -> {
            String completedKey = rc.researchKey();
            if (completedKey.startsWith("@") || completedKey.startsWith("PSEUDO_")) return;
            String pseudoKey = REAL_TO_PSEUDO.get(completedKey);
            if (pseudoKey == null) return;
            try {
                TCResearchManager.complete(rc.player(), pseudoKey, false);
            } catch (Exception ignored) {
            }
        });
    }
}
