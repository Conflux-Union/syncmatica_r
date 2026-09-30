package cn.net.rms.syncmatica_r.communication;

/**
 * Opcodes and field order of the STOCKING_AREA_MANAGE packet. Both sides of
 * the connection compile this class from the same source set, which is what
 * keeps the hand-rolled reads and writes in sync.
 */
public final class StockingAreaManageOpcodes {
    public static final byte OP_CREATE = 0;
    public static final byte OP_UPDATE = 1;
    public static final byte OP_DELETE = 2;
    public static final byte OP_BIND = 3;
    public static final byte OP_UPDATE_DEFAULT = 4;

    private StockingAreaManageOpcodes() {
    }
}
