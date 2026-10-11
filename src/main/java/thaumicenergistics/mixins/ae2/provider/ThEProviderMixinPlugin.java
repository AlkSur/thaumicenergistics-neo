package thaumicenergistics.mixins.ae2.provider;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

/**
 * T-M1-003 P2（T-M1-015 收窄作用域）：样板供应器<b>卡槽组</b> Mixin 与「应用通量」（AppFlux）<b>互斥生效</b>。
 * <p>
 * 背景：AppFlux 已为 AE2 样板供应器注入同一套升级卡槽机制（Logic 实现 {@code IUpgradeableObject} +
 * Menu 注入槽 + Screen 挂面板，其 4-Mixin 蓝图即我方蓝本）。二者若同时应用：
 * ① {@code PatternProviderLogic} 会被两处 {@code IUpgradeableObject} 实现/两套 {@code IUpgradeInventory}
 * 争夺（构造器 TAIL 各建一份，变更回调重复）；② 菜单会重复注入升级槽。故槽位必须互斥。
 * <p>
 * <b>互斥对象仅限「卡槽」</b>（T-M1-015，裁决 v0.34）：用户原话「有它则我们的<b>插卡槽</b>不生效」——
 * 互斥的是槽位而非卡的能力。故本插件只作用于 {@code thaumicenergistics.provider.mixins.json}
 * （卡槽组：Logic 升级半边 / LogicHost / Menu / Screen）；源质能力（O-A 拉 + O-B 吸力桥接）移入
 * {@code thaumicenergistics.provider.essentia.mixins.json}（<b>无插件</b>）→ <b>恒应用</b>。
 * 装 AppFlux 时槽位由 AppFlux 建，我方卡经 {@code Upgrades.add} 注册照样可插，源质门控经公开
 * {@code IUpgradeableObject} 读取照样可判——能力不受互斥影响。
 * <p>
 * 机制：本配置单独成文件（不并入 {@code thaumicenergistics.mixins.json}，避免其无插件的其余 Mixin
 * 也被卷入），由本插件在 mixin 应用期检测 {@code appflux}——已加载则本组跳过，由 AppFlux 的槽位兜底生效；
 * 缺席则我方 Mixin 全量生效。侧别无关（common/client 两组同受本判定）。
 * <p>
 * 蓝本：AppFlux {@code MixinConfig}（其按类名映射所需 modId；本插件只需一个全局判定，故简化为单一返回值）。
 */
public class ThEProviderMixinPlugin implements IMixinConfigPlugin {

    /** 应用通量的 modId（glodblock/ExtendedAE 系）。 */
    private static final String APPFLUX_MOD_ID = "appflux";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !isModLoaded(APPFLUX_MOD_ID);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    /** {@code ModList} 在 mixin 应用早期为 null，此时退回 {@code LoadingModList}（同 AppFlux 插件做法）。 */
    private static boolean isModLoaded(String modId) {
        var modList = ModList.get();
        if (modList == null) {
            return LoadingModList.get().getMods().stream()
                    .map(ModInfo::getModId)
                    .anyMatch(modId::equals);
        }
        return modList.isLoaded(modId);
    }
}
