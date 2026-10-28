package thaumicenergistics.common.monitor;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import thaumicenergistics.ThaumicEnergistics;

/** 显示实体的创建、恢复、更新和移除；不负责风险采集或网络状态。 */
public final class InfusionMonitorDisplay {
    private static final String BUBBLE_TAG = "thaumicenergistics:monitor_bubble";
    private static final int BUBBLE_BG_COLOR = 0xB0333333;
    private static final int BUBBLE_LINE_WIDTH = 320;
    private final Supplier<Level> levelSupplier;
    private final BlockPos position;
    private net.minecraft.world.entity.Display.TextDisplay bubble;
    private Component lastText;

    public InfusionMonitorDisplay(Supplier<Level> levelSupplier, BlockPos position) {
        this.levelSupplier = levelSupplier;
        this.position = position.immutable();
    }

    public void update(boolean visible, Component text) {
        var level = levelSupplier.get();
        if (level == null || level.isClientSide()) return;
        if (!visible) { discard(); return; }
        ensureBubble(level, text);
        if (bubble != null && !text.equals(lastText)) {
            applyBubbleNbt(level, text);
            lastText = text;
        }
    }

    /** 确保气泡实体存在（不存在则生成；区块重载后按 Tag 找回旧实体）。 */
    private void ensureBubble(Level level, Component text) {
        if (bubble != null && bubble.isAlive()) return;
        bubble = findBubble(level);
        if (bubble != null) return;
        bubble = EntityType.TEXT_DISPLAY.create(level);
        if (bubble == null) return;
        bubble.setPos(position.getX() + 0.5, position.getY() + 1.2, position.getZ() + 0.5);
        // 通过公开实体保存/加载 API 配置 TextDisplay 的文本与显示字段。
        applyBubbleNbt(level, text);
        bubble.addTag(BUBBLE_TAG);
        level.addFreshEntity(bubble);
    }

    private void applyBubbleNbt(Level level, Component text) {
        if (bubble == null || level == null) return;
        try {
            // 保存完整实体状态，公共 load 路径不会把位置、UUID 等字段重置为默认值。
            CompoundTag nbt = bubble.saveWithoutId(new CompoundTag());
            nbt.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
            nbt.putBoolean("default_background", false);
            nbt.putInt("background", BUBBLE_BG_COLOR);
            nbt.putString("billboard", "vertical");
            nbt.putInt("line_width", BUBBLE_LINE_WIDTH);
            nbt.putBoolean("shadow", true);
            nbt.putBoolean("see_through", false);
            // 必须写 alignment：TextDisplay.readAdditionalSaveData 无条件 decode "alignment"，
            // 缺失时 tag.get 返回 null → Align.CODEC(字符串) decode 失败 → 服务端疯狂刷 "Display entityNot a string"。
            nbt.putString("alignment", "center");
            // 文字调小：transformation.scale 整体缩到 0.5（text_display 无字号参数，靠实体缩放）
            CompoundTag transform = new CompoundTag();
            transform.put("scale", floatList(0.5F, 0.5F, 0.5F));
            transform.put("translation", floatList(0F, 0F, 0F));
            transform.put("left_rotation", floatList(0F, 0F, 0F, 1F));
            transform.put("right_rotation", floatList(0F, 0F, 0F, 1F));
            nbt.put("transformation", transform);
            bubble.load(nbt);
        } catch (Exception e) {
            ThaumicEnergistics.LOG.warn("[Monitor] 气泡配置失败: {}", e.toString());
        }
    }

    /** 构造 FloatTag 列表（1.21.1 CompoundTag 无 putFloatArray，需 ListTag）。 */
    private static net.minecraft.nbt.ListTag floatList(float... values) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (float v : values) {
            list.add(net.minecraft.nbt.FloatTag.valueOf(v));
        }
        return list;
    }

    /** 按 Tag + 方块附近 AABB 查找已存在的气泡。 */
    private net.minecraft.world.entity.Display.TextDisplay findBubble(Level level) {
        if (level == null) return null;
        AABB aabb = new AABB(position).inflate(0.15, 2.0, 0.15);
        List<net.minecraft.world.entity.Display.TextDisplay> list = level.getEntitiesOfClass(
                net.minecraft.world.entity.Display.TextDisplay.class, aabb,
                e -> e.getTags().contains(BUBBLE_TAG)
                        && e.distanceToSqr(position.getX() + 0.5, position.getY() + 1.2, position.getZ() + 0.5) < 0.01);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 销毁气泡（方块拆除/条件不满足时）。 */
    public void discard() {
        Level level = levelSupplier.get();
        if (bubble != null && bubble.isAlive()) {
            bubble.discard();
        } else {
            net.minecraft.world.entity.Display.TextDisplay found = findBubble(level);
            if (found != null) {
                found.discard();
            }
        }
        bubble = null;
        lastText = null;
    }

}
