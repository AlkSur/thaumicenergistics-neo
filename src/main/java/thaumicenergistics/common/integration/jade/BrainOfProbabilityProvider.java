package thaumicenergistics.common.integration.jade;

import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.tiles.BrainOfProbabilityBlockEntity;

/**
 * 概率之脑 Jade 显示（T-M6-GACHA-REV），两行：
 * <ol>
 *   <li>「本轮奖励」：最近一次出货明细（要素本地化名 + 点数；无出货历史显示暂无）。</li>
 *   <li>「下次抽奖」：当前周期剩余秒数（空闲显示 —）。</li>
 * </ol>
 * 服务端把 BE 持久化的 lastPayout 与 turnSeconds 写入 NBT，客户端渲染。
 */
public enum BrainOfProbabilityProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String KEY = "BrainProbPayout";
    private static final String TAG_TURN_REMAINING = "TurnRemaining";

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "brain_of_probability");
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof BrainOfProbabilityBlockEntity brain) {
            data.putInt(TAG_TURN_REMAINING, brain.turnSecondsRemaining());
            Map<String, Integer> payout = brain.lastPayout();
            if (!payout.isEmpty()) {
                ListTag saved = new ListTag();
                for (Map.Entry<String, Integer> entry : payout.entrySet()) {
                    CompoundTag item = new CompoundTag();
                    item.putString("tag", entry.getKey());
                    item.putInt("points", entry.getValue());
                    saved.add(item);
                }
                data.put(KEY, saved);
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var serverData = accessor.getServerData();
        if (!serverData.contains(TAG_TURN_REMAINING)) {
            return;
        }

        // 行 1：本轮奖励（无出货历史 = 暂无）
        MutableComponent parts = Component.empty();
        boolean first = true;
        if (serverData.contains(KEY)) {
            ListTag saved = serverData.getList(KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < saved.size(); i++) {
                CompoundTag item = saved.getCompound(i);
                if (!first) {
                    parts = parts.append(Component.translatable("thaumicenergistics.brain_prob.payout_sep"));
                }
                first = false;
                parts = parts.append(Component.translatable("tc.aspect." + item.getString("tag")))
                        .append(Component.literal(" " + item.getInt("points")));
            }
        }
        Component payoutDisplay = first
                ? Component.translatable("thaumicenergistics.jade.brain_prob.none")
                : parts;
        tooltip.add(Component.translatable("thaumicenergistics.jade.brain_prob.last_payout", payoutDisplay));

        // 行 2：下次抽奖倒计时（空闲 = —）
        int remaining = serverData.getInt(TAG_TURN_REMAINING);
        Component countdown = remaining > 0
                ? Component.literal(String.valueOf(remaining))
                : Component.literal("—");
        tooltip.add(Component.translatable("thaumicenergistics.jade.brain_prob.next_draw", countdown));
    }
}
