package thaumicenergistics.mixins.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.parts.reporting.AbstractTerminalPart;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;
import thaumicenergistics.common.parts.EssentiaTerminalPart;

/**
 * 在普通 ME 终端的 KeyTypeSelection 中始终禁用 AEssentiaKeyType。
 * ⚠ KeyTypeSelection.readFromNBT (line 101) 会先全部 setValue(false) 再读 NBT 恢复，
 * 所以仅仅在构造器 TAIL 注入是不够的——readFromNBT 会把构造器的修改完全清掉。
 * 需要同时在 readFromNBT TAIL 再注入一次，确保 essentia 始终被禁用。
 * EssentiaTerminalPart 覆盖了 getKeyTypeSelection() 返回自己的实例，不受影响。
 */
@Mixin(AbstractTerminalPart.class)
public abstract class MixinAbstractTerminalPart implements KeyTypeSelectionHost {

    /**
     * 构造器 TAIL：新放置的终端，在 keyTypeSelection 初始化后禁用 essentia。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void removeEssentiaFromRegularTerminal(CallbackInfo ci) {
        if ((Object) this instanceof EssentiaTerminalPart) return;
        doDisableEssentia();
    }

    /**
     * readFromNBT TAIL：从存档加载后，readFromNBT 会重置 keyTypeSelection，
     * 需要再次禁用 essentia 以确保不会被 NBT 恢复重新打开。
     */
    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void onReadFromNBT(CallbackInfo ci) {
        if ((Object) this instanceof EssentiaTerminalPart) return;
        doDisableEssentia();
    }

    private void doDisableEssentia() {
        try {
            KeyTypeSelection sel = this.getKeyTypeSelection();
            // 先检查是否包含 AEssentiaKeyType（可能 keyTypeSelection 还没完全初始化）
            try {
                if (sel.isEnabled(AEssentiaKeyType.INSTANCE)) {
                    sel.setEnabled(AEssentiaKeyType.INSTANCE, false);
                }
            } catch (IllegalArgumentException e) {
                // keyTypeSelection 中不包含 AEssentiaKeyType → 无需操作
            }
        } catch (Exception e) {
            // 构造阶段 host 为空，saveChanges() → getHost().markForSave() 会 NPE
            // setEnabled 内部的 map 修改已完成，catch 忽略
        }
    }
}
