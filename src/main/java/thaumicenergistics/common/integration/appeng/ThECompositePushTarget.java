package thaumicenergistics.common.integration.appeng;

import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderTarget;

import thaumicenergistics.common.integration.appeng.AEssentiaKey;

/**
 * T-M2-002：{@link ThEEssentiaPushTarget} 与 AE2 原生 target 的<b>复合包装</b>。
 * <p>
 * <b>为什么需要包装</b>：极罕见的「双能力机器」（既有 item/fluid 外部存储能力，又是 TC 源质端点）
 * 场景下，原 target 非 null——此时不能整体顶替（物品/流体须照常走 AE2 原生链路），
 * 改为按 key 类型分流：源质键 → 源质落点，其余 → 原 target。
 * <p>
 * <b>为什么不在 mixin 包</b>：普通运行期类被注入体直接 new，Mixin 声明包内类
 * 禁止被普通代码引用（IllegalClassLoadError），故与 {@link ThEEssentiaPushTarget}
 * 一并落 AE2 集成包。
 */
public record ThECompositePushTarget(PatternProviderTarget originalTarget, PatternProviderTarget essentiaTarget)
        implements PatternProviderTarget {

    @Override
    public long insert(AEKey what, long amount, Actionable type) {
        if (what instanceof AEssentiaKey) {
            return this.essentiaTarget.insert(what, amount, type);
        }
        return this.originalTarget.insert(what, amount, type);
    }

    @Override
    public boolean containsPatternInput(Set<AEKey> patternInputs) {
        // blocking 判定两侧并集：物品/流体交原 target（原生语义），源质交源质落点（广告查询）——
        // 两套键空间互不重叠，各自的 containsPatternInput 对异类键天然返 false
        return this.originalTarget.containsPatternInput(patternInputs)
                || this.essentiaTarget.containsPatternInput(patternInputs);
    }
}
