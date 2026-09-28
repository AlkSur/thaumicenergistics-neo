package tc_energy.common.integration.tc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ThE 研究接入（Ren 版 TC4）。
 * <p>
 * Ren 版研究系统是纯 data pack 驱动（{@code data/tc_energy/research/tc_energy.json}），
 * 不再支持 Java 注册（旧 ResearchItem/ResearchCategories/PseudoResearchItem 全部废弃）。
 * ThE 的研究分类 TC_ENERGY 与全部研究节点在 data pack 中定义；跨分类前置直接引用
 * Ren 版真实研究键（SCEPTRE / DISTILESSENTIA / TUBEFILTER / MIRROR / INFUSION /
 * WARDEDARCANA / ALCHEMICALDUPLICATION / FOCUSFIRE / VISPOWER / COREGATHER），
 * 伪前置节点机制不再需要。
 * <p>
 * 本类职责收敛为：
 * <ul>
 *   <li>init()：仅打日志确认 data pack 研究已随资源重载加载（调试锚点）。</li>
 *   <li>{@link #isResearchComplete(net.minecraft.world.entity.player.Player, String)}：
 *       设备端研究校验入口（奥术装配器/知识刻录仪/奥术合成终端等）。</li>
 * </ul>
 */
public final class ThEThaumcraft {
    private static final Logger LOG = LoggerFactory.getLogger("ThE-TC");

    private ThEThaumcraft() {}

    public static void init() {
        LOG.info("ThaumicEnergistics research integration ready (data pack: data/tc_energy/research/tc_energy.json).");
    }

    /**
     * 设备端研究校验（替代旧 MixinCraftingService 的 submitJob 拦截）。
     * key 形如 "ARCANEASSEMBLER"（ThE 分类，ns=tc_energy）或 "thaumcraft:SCEPTRE"。
     */
    public static boolean isResearchComplete(net.minecraft.world.entity.player.Player player, String key) {
        if (player == null || key == null || key.isEmpty()) {
            return true;
        }
        try {
            dev.tc4port.thaumcraft.api.research.ResearchKey researchKey =
                    dev.tc4port.thaumcraft.api.research.ResearchKey.parse(key);
            return dev.tc4port.thaumcraft.api.research.ResearchApi.isComplete(player, researchKey);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
