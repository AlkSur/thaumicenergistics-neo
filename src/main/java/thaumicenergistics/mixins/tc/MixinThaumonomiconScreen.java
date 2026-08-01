package thaumicenergistics.mixins.tc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import thaumcraft.client.gui.ThaumonomiconData;
import thaumcraft.client.gui.ThaumonomiconScreen;
import thaumcraft.common.research.TCResearchManager;

/**
 * 研究节点"逐渐显现"（仅神秘能源分类）：只显示"已解锁研究节点的后继"
 * （parents 中存在已解锁节点）——无已解锁前置链的孤立节点隐藏。
 * 伪前置（PSEUDO_ 开头）不受过滤影响（默认显现、解锁跟随真实节点）。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(ThaumonomiconScreen.class)
public abstract class MixinThaumonomiconScreen {

    private static final String TH_E_CATEGORY = "THAUMICENERGISTICS";

    @Inject(method = "visibleEntries", at = @At("RETURN"), cancellable = true)
    private void thE$filterInaccessible(CallbackInfoReturnable<List<ThaumonomiconData.Entry>> cir) {
        List<ThaumonomiconData.Entry> all = cir.getReturnValue();
        if (all == null || all.isEmpty()) return;
        // 只过滤神秘能源分类（TC 原版分类保持原样）
        if (!TH_E_CATEGORY.equals(all.get(0).category())) return;

        var player = Minecraft.getInstance().player;
        if (player == null) return;
        Set<String> completed = new HashSet<>(TCResearchManager.completedKeys(player));

        List<ThaumonomiconData.Entry> filtered = new ArrayList<>(all.size());
        for (ThaumonomiconData.Entry e : all) {
            // 伪前置不受影响（默认显现）
            if (e.key().startsWith("PSEUDO_")) {
                filtered.add(e);
                continue;
            }
            if (completed.contains(e.key())) {
                filtered.add(e);
                continue;
            }
            // 可达：parents 中存在已解锁节点（已解锁节点的后继）
            if (e.parents().stream().anyMatch(completed::contains)) {
                filtered.add(e);
            }
        }
        cir.setReturnValue(filtered);
    }
}
