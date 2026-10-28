package thaumicenergistics.common.container;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.networking.IGridNode;
import appeng.api.upgrades.IUpgradeInventory;

/**
 * 奥术终端宿主契约：统一独立无线奥术终端与 AE2WT 通用终端奥术模式两种宿主，
 * 供 {@code ContainerArcaneCraftingTerminal} 识别 vis 充能、奇点槽与法杖保存能力，
 * 替代对具体宿主类的 instanceof 判断。
 * <p>
 * 奇点子库存 id 由各宿主自报（独立宿主为本模组命名空间，AE2WT 宿主为
 * {@code ae2wtlib:singularity} 字符串软查，避免可选 API 类型泄漏到普通加载路径）；
 * 返回 null 表示无奇点槽。
 */
public interface IArcaneTerminalInventoryHost extends ISegmentedInventory {

    /** ae2wtlib modid（奇点准入等 ModList 守护用）。 */
    String AE2WTLIB_MODID = "ae2wtlib";

    /** 升级卡库存（vis 连接卡检测；与 AE2 IUpgradeableObject 同名方法兼容）。 */
    IUpgradeInventory getUpgrades();

    /** 网络操作节点（vis 充能源；与 AE2 IActionHost 同名方法兼容）。 */
    @Nullable
    IGridNode getActionableNode();

    /** 法杖槽内容写回终端物品组件（菜单驱动充能后显式调用）。 */
    void saveWandInv();

    /** Refresh a passive connection without charging menu idle power a second time. */
    void refreshConnection();

    /** 奇点子库存 id（getSubInventory 寻址）；null 表示本宿主无奇点槽。 */
    @Nullable
    ResourceLocation getSingularityInvId();

    /**
     * 奇点槽语义分支：AE2WT 宿主返回 true——奇点槽挂 ae2wtlib 官方 SINGULARITY 组
     * （滚动升级面板取槽时要求奇点居首且独立成组，并自带量子桥卡门控显隐）；
     * 独立无线宿主保持默认 false——奇点槽并入 UPGRADE 组由普通升级面板渲染。
     * 经契约虚调用分发，菜单侧不引用 ae2wtlib 类型（该宿主能实例化即证明 ae2wtlib 在场）。
     */
    default boolean useAE2WTSingularitySemantics() {
        return false;
    }

}
