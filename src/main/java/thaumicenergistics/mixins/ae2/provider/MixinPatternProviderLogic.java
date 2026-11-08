package thaumicenergistics.mixins.ae2.provider;

import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
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

/**
 * 为原版样板供应器补充升级库存及其存档、掉落生命周期。
 * 只注入三参数构造器，避免委托构造重复初始化；安装 AppFlux 时改用它的卡槽实现。
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class MixinPatternProviderLogic implements IUpgradeableObject {

    /** 宿主（Part 或 BlockEntity），声明于本类自身。 */
    @Final
    @Shadow
    private PatternProviderLogicHost host;

    /** 网格节点，声明于本类自身，用于插卡后唤醒 ticker。 */
    @Final
    @Shadow
    private IManagedGridNode mainNode;

    /** 升级清单。初始为空只读，3-arg 构造器 TAIL 时替换为真实清单。 */
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
