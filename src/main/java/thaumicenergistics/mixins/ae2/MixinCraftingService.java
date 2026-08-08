package thaumicenergistics.mixins.ae2;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;

import net.minecraft.world.entity.player.Player;

import thaumcraft.common.research.TCResearchManager;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.integration.tc.PatternResearchHelper;

/**
 * 使用端研究限制：玩家从 AE 终端发起合成请求时（submitJob），
 * 若请求涉及尚未解锁对应 TC 研究的样板（奥术/蒸馏），则拒绝该请求。
 *
 * 机器自动合成（IActionSource 无玩家）不拦截。
 */
@Mixin(targets = "appeng.me.service.CraftingService", remap = false)
public abstract class MixinCraftingService {

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, remap = false)
    private void thaumicenergistics$checkResearchBeforeSubmit(
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            ICraftingCPU target,
            boolean prioritizePower,
            IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        // 仅玩家发起的请求需要研究校验；机器自动合成放行
        if (src == null || src.player().isEmpty()) {
            return;
        }
        Player player = src.player().get();
        if (player == null) {
            return;
        }

        for (Map.Entry<IPatternDetails, Long> entry : job.patternTimes().entrySet()) {
            IPatternDetails pattern = entry.getKey();
            String research = PatternResearchHelper.getResearch(pattern);
            if (research == null || research.isEmpty()) {
                continue; // 无研究要求
            }
            if (!TCResearchManager.has(player, research)) {
                ThaumicEnergistics.LOG.info(
                        "[AE2ResearchGate] 玩家 {} 未解锁研究 {}，拒绝合成请求 (pattern={})",
                        player.getName().getString(), research, pattern.getDefinition());
                // 通过自定义网络包发送研究键到客户端（AE2 errorDetail 不跨网络传输）
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new thaumicenergistics.common.network.ResearchGateErrorPacket(research));
                }
                // 拒绝提交（CPU_BUSY 触发 AE2 错误屏；具体提示由客户端网络包渲染）
                cir.setReturnValue(CraftingSubmitResult.simpleError(CraftingSubmitErrorCode.CPU_BUSY));
                return;
            }
        }
    }
}
