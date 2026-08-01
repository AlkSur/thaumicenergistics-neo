package thaumicenergistics.common.grid;

/**
 * 表示连接到 AE2 网格的部件。
 * 对应 1.7.10 AEPartGridBlock——为 1.21.1 简化。
 * TODO: 通过 AE2 19.x API 完整连接 IGridNode。
 */
public class AEPartGridBlock {
    private final Object part; // 占位 PartBase 或 IPartItem

    public AEPartGridBlock(Object part) { this.part = part; }

    public double getIdlePowerUsage() {
        // TODO: Delegate to part
        return 1.0;
    }

    public Object getGridNode() {
        // TODO: AE2 IGridNode
        return null;
    }
}
