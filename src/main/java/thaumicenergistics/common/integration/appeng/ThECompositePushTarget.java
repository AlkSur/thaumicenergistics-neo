package thaumicenergistics.common.integration.appeng;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderTarget;
import java.util.Set;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;

/**
 * 按资源类型分流：源质交给源质目标，物品和流体保留原目标的行为。
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
