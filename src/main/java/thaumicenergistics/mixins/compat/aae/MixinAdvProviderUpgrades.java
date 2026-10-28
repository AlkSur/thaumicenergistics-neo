package thaumicenergistics.mixins.compat.aae;

import java.util.List;
import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AAE 独立逻辑的升级库存；与 AAE 自带 AppFlux 卡槽组互斥。 */
@Mixin(value = AdvPatternProviderLogic.class, remap = false)
public abstract class MixinAdvProviderUpgrades implements IUpgradeableObject {
    @Shadow @Final private AdvPatternProviderLogicHost host;
    @Shadow @Final private IManagedGridNode mainNode;
    @Unique private IUpgradeInventory thE$upgrades = UpgradeInventories.empty();

    @Override public IUpgradeInventory getUpgrades() { return thE$upgrades; }

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;I)V", at = @At("TAIL"))
    private void thE$init(IManagedGridNode node, AdvPatternProviderLogicHost owner, int size, CallbackInfo ci) {
        thE$upgrades = UpgradeInventories.forMachine(owner.getTerminalIcon().getItem(), 1, () -> {
            host.saveChanges();
            mainNode.ifPresent((grid, gridNode) -> grid.getTickManager().alertDevice(gridNode));
        });
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void thE$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        thE$upgrades.writeToNBT(tag, "upgrades", registries);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void thE$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        thE$upgrades.readFromNBT(tag, "upgrades", registries);
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void thE$drops(List<ItemStack> drops, CallbackInfo ci) {
        for (var upgrade : thE$upgrades) if (!upgrade.isEmpty()) drops.add(upgrade.copy());
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void thE$clear(CallbackInfo ci) { thE$upgrades.clear(); }
}
