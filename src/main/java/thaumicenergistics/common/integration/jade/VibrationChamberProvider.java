package thaumicenergistics.common.integration.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;
import appeng.util.Platform;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.tiles.TileEssentiaVibrationChamber;

public enum VibrationChamberProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "vibration_chamber");
    private static final String TAG_STORED = "StoredEssentia";
    private static final String TAG_MAX = "MaxEssentia";
    private static final String TAG_BURN_TICKS = "BurnTicks";
    private static final String TAG_TOTAL_BURN = "TotalBurnTicks";
    private static final String TAG_ASPECT = "AspectTag";
    private static final String TAG_AE_PER_TICK = "AEPerTick";
    private static final String TAG_ENERGY_STORED = "StoredEnergy";
    private static final String TAG_ENERGY_MAX = "MaxEnergy";
    private static final String TAG_ENERGY_OUTPUT = "MaxOutput";
    private static final String TAG_GENERATING = "Generating";

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || data.isEmpty()) return;

        // 吸力类型
        if (data.contains(TAG_ASPECT)) {
            String aspect = data.getString(TAG_ASPECT);
            String aspectKey = "thaumicenergistics.aspect." + aspect;
            tooltip.add(Component.translatable("thaumicenergistics.jade.suction_type",
                    Component.translatable(aspectKey)));
        }

        // 源质存储量
        int stored = data.getInt(TAG_STORED);
        int max = data.getInt(TAG_MAX);
        tooltip.add(Component.translatable("thaumicenergistics.jade.essentia_stored", stored, max));

        // 能量槽（ME 控制器格式：已存储 X AE / Y kAE）
        double energyStored = data.getDouble(TAG_ENERGY_STORED);
        double energyMax = data.getDouble(TAG_ENERGY_MAX);
        double maxOutput = data.getDouble(TAG_ENERGY_OUTPUT);
        tooltip.add(Component.translatable("thaumicenergistics.jade.energy_stored",
                Platform.formatPower(energyStored, false),
                Platform.formatPower(energyMax, false)));
        // 可视化能量条 + 内嵌文字（FE 单位，1 AE = 2 FE）；样式复刻 Jade 内置 EnergyStorageProvider.PROGRESS_BAR（深红渐变 + 灰色描边）
        if (energyMax > 0) {
            float pct = (float) Math.min(1.0, Math.max(0.0, energyStored / energyMax));
            String feText = formatFE(energyStored * 2) + " / " + formatFE(energyMax * 2);
            IElementHelper helper = IElementHelper.get();
            tooltip.add(helper.progress(pct, Component.literal(feText),
                    helper.progressStyle().color(0xFFAA0000, 0xFF660000),
                    BoxStyle.getNestedBox(), true));
        }
        tooltip.add(Component.translatable("thaumicenergistics.jade.energy_output",
                String.format("%.0f", maxOutput)));

        boolean generating = data.getBoolean(TAG_GENERATING);
        if (generating && energyStored < energyMax - 0.01) {
            int burnTicks = data.getInt(TAG_BURN_TICKS);
            int totalTicks = data.getInt(TAG_TOTAL_BURN);
            double aePerTick = data.getDouble(TAG_AE_PER_TICK);
            if (burnTicks > 0 && totalTicks > 0) {
                double progress = 1.0 - (double) burnTicks / totalTicks;
                int percent = (int) (progress * 100);
                tooltip.add(Component.translatable("thaumicenergistics.jade.burning", percent, aePerTick));
            }
        } else if (energyStored >= energyMax - 0.01) {
            tooltip.add(Component.translatable("thaumicenergistics.jade.tank_full"));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof TileEssentiaVibrationChamber chamber) {
            data.putInt(TAG_STORED, chamber.getStoredEssentia());
            data.putInt(TAG_MAX, chamber.getMaxEssentia());
            data.putInt(TAG_BURN_TICKS, chamber.getBurnTicksRemaining());
            data.putInt(TAG_TOTAL_BURN, chamber.getTotalBurnTicks());
            data.putDouble(TAG_AE_PER_TICK, chamber.getAePerTick());
            data.putDouble(TAG_ENERGY_STORED, chamber.getStoredEnergy());
            data.putDouble(TAG_ENERGY_MAX, chamber.getMaxEnergyStorage());
            data.putDouble(TAG_ENERGY_OUTPUT, chamber.getMaxOutputPerTick());
            data.putBoolean(TAG_GENERATING, chamber.isActive());
            if (chamber.getSuctionType(null) != null) {
                data.putString(TAG_ASPECT, chamber.getSuctionType(null).getTag());
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /**
     * 将 FE 能量值格式化为无空格缩写：0 → "0FE"，16000 → "16KFE"，12345 → "12.3KFE"。
     */
    private static String formatFE(double fe) {
        final String[] prefixes = {"", "K", "M", "G", "T", "P"};
        int idx = 0;
        double v = fe;
        while (v >= 1000 && idx < prefixes.length - 1) {
            v /= 1000;
            idx++;
        }
        String num = (v == Math.floor(v) && !Double.isInfinite(v))
                ? String.valueOf((long) v)
                : String.format("%.1f", v);
        return num + prefixes[idx] + "FE";
    }
}