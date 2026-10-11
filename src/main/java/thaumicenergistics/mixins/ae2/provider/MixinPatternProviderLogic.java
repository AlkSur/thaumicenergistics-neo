package thaumicenergistics.mixins.ae2.provider;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

/**
 * T-M1-003 P1（<b>卡槽半边</b>）：给 AE2 <b>原版样板供应器</b>注入「源质访问卡」升级槽
 * （候选 A + AppFlux 兼容兜底策略）。
 * <p>
 * <b>为什么需要自建整套卡槽</b>：AE2 19.2.17 的 {@code PatternProviderLogic}（及 Part / BlockEntity /
 * Menu）全链路<b>无</b>任何升级卡基础设施（{@code upgrade}/{@code getUpgrades}/{@code isInstalled}
 * 引用数为 0）。{@code Upgrades.add} 只是把卡关联到「已存在 {@code IUpgradeInventory} 的宿主」，
 * 故须在此 Mixin 自建：本类持有升级清单，配套 Menu 注入槽位、Screen 渲染、NBT 持久化、掉落/清空。
 * <p>
 * <b>蓝本</b>：{@code library/ExtendedAE-appflux-1.21.1-neoforge/.../MixinPatternProviderLogic.java}
 * （AppFlux 已实机验证的同款实现）。照抄其结构，{@code @Unique} 前缀改我方 {@code thE$}，
 * NBT 子标签沿用 {@code "upgrades"}（与 AppFlux 同键，二者互斥不会同时写）。
 * AppFlux 私有件（{@code EnergyTicker}、工具箱）一律丢弃。
 * <p>
 * <b>构造器只注入 3-arg</b>：{@code PatternProviderLogic} 的 2-arg 构造器（:128）体内
 * {@code this(mainNode, host, 9)} 直接委托 3-arg（:132）；且 {@code PatternProviderBlockEntity:59} 与
 * {@code PatternProviderPart:78} 两个唯一调用点<b>都走 2-arg</b> → 3-arg 的 TAIL 恰好各触发一次。
 * 双注入会重复创建 {@code IUpgradeInventory} 与变更回调，故<b>禁止</b>（裁决 v0.32）。
 * <p>
 * <b>T-M1-015 拆分（裁决 v0.34）</b>：原单类已被拆为两组独立 Mixin——本类为<b>卡槽组</b>
 * （升级槽生命周期），{@link MixinPatternProviderLogicEssentia} 为<b>源质组</b>（O-A 拉 + O-B 吸力桥接）。
 * 拆分动因：装 AppFlux 时互斥对象应仅限「卡槽」（其已建同款槽位），源质能力须照常生效 →
 * 源质组移出本 config 恒应用。两组同目标挂载合法，注入成员零重叠（见源质组注释的核对表）。
 * <p>
 * <b>与 AppFlux 互斥</b>：本组 Mixin 位于 {@code thaumicenergistics.provider.mixins.json}，经
 * {@link ThEProviderMixinPlugin} 检测 {@code appflux}——已加载则整组跳过，由 AppFlux 的槽位兜底生效；
 * 源质组不受影响（其门控经公开 {@code IUpgradeableObject} 读取，AppFlux 槽位里插我方卡照样可判）。
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class MixinPatternProviderLogic implements IUpgradeableObject {

    /** 宿主（Part 或 BlockEntity），声明于本类自身（PatternProviderLogic.java:96）。 */
    @Final
    @Shadow
    private PatternProviderLogicHost host;

    /** 网格节点，声明于本类自身（PatternProviderLogic.java:97），用于插卡后唤醒 ticker。 */
    @Final
    @Shadow
    private IManagedGridNode mainNode;

    /** 升级清单（T-M1-003 P1）。初始为空只读，3-arg 构造器 TAIL 时替换为真实清单。 */
    @Unique
    private IUpgradeInventory thE$upgrades = UpgradeInventories.empty();

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.thE$upgrades;
    }

    /**
     * 仅注入 3-arg 构造器（见类注释：2-arg 委托 3-arg，两个唯一调用点均走 2-arg）。
     * 卡槽容量 1：AE2 原版未给供应器注册任何升级卡，我方只注册 1 张源质卡（max=1）→ 1 槽即足。
     */
    @Inject(
            method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL")
    )
    private void thE$initUpgrades(IManagedGridNode mainNode, PatternProviderLogicHost host,
            int patternInventorySize, CallbackInfo ci) {
        this.thE$upgrades = UpgradeInventories.forMachine(
                host.getTerminalIcon().getItem(), 1, this::thE$onUpgradesChanged);
    }

    /** 升级清单变更回调：存盘 + 唤醒 ticker（插卡后立即开始源质交换，拔卡立即停）。 */
    @Unique
    private void thE$onUpgradesChanged() {
        this.host.saveChanges();
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void thE$saveUpgrades(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.thE$upgrades.writeToNBT(tag, "upgrades", registries);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void thE$loadUpgrades(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.thE$upgrades.readFromNBT(tag, "upgrades", registries);
    }

    /** 破坏时把升级卡掉落（源质无物品形态，缓冲由源质组另行清空）。 */
    @Inject(method = "addDrops", at = @At("TAIL"))
    private void thE$dropUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        for (var is : this.thE$upgrades) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    /** 清空升级清单（缓冲清空归源质组）。 */
    @Inject(method = "clearContent", at = @At("TAIL"))
    private void thE$clearUpgrades(CallbackInfo ci) {
        this.thE$upgrades.clear();
    }
}
