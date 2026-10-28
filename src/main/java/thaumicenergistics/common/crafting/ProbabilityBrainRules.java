package thaumicenergistics.common.crafting;

import net.minecraft.util.RandomSource;

/** 概率之脑的抽奖数值与随机规则，独立于网格和方块生命周期。 */
public final class ProbabilityBrainRules {
    private ProbabilityBrainRules() { }

    // ===== 骰子数值（CE GachaOdds 定稿，用户可调） =====
    /** 最快的一次转动，也是赔率最好的一次。 */
    public static final int MIN_SECONDS = 30;
    /** 无加速卡时最慢的转动。 */
    public static final int LONGEST_SECONDS = 180;
    /** 每张加速卡把最长转动缩短的秒数。 */
    public static final int SECONDS_PER_CARD = 25;
    /** 加速卡最多把最长转动压到的秒数（第五张起不再变化）。 */
    public static final int LONGEST_WITH_CARDS = 80;
    /** 最快转动的给奖赔率。 */
    public static final double CHANCE_AT_MIN_SECONDS = 0.75;
    /** 最慢转动的给奖赔率。 */
    public static final double CHANCE_AT_LONGEST_SECONDS = 0.45;
    /** 给奖转动抽取次数下限。 */
    public static final int MIN_DRAWS = 5;
    /** 给奖转动抽取次数上限。 */
    public static final int MAX_DRAWS = 7;
    /** 单次抽取点数上限（1~3）。 */
    public static final int MAX_POINTS_PER_DRAW = 3;

    /** 装这么多张卡时可能掷出的最长转动。 */
    public static int longestSeconds(int cards) {
        return Math.max(LONGEST_WITH_CARDS, LONGEST_SECONDS - SECONDS_PER_CARD * cards);
    }

    /** 这个时长的转动给奖的赔率，只看时长。 */
    public static double payoutChance(int seconds) {
        double along = (double) (seconds - MIN_SECONDS) / (LONGEST_SECONDS - MIN_SECONDS);
        return CHANCE_AT_MIN_SECONDS + (CHANCE_AT_LONGEST_SECONDS - CHANCE_AT_MIN_SECONDS) * along;
    }

    /** 掷出的一次转动：跑多少秒，给不给奖。 */
    public record Turn(int seconds, boolean pays) {}

    /** 一次抽取定下两半：落到的时长决定这次转动的赔率。 */
    public static Turn roll(RandomSource random, int cards) {
        int seconds = MIN_SECONDS + random.nextInt(longestSeconds(cards) - MIN_SECONDS + 1);
        return new Turn(seconds, random.nextDouble() < payoutChance(seconds));
    }

}
