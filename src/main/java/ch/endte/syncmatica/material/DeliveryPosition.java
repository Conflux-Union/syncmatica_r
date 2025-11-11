package ch.endte.syncmatica.material;

import cn.net.rms.syncmatica_r.ServerPosition;
import net.minecraft.util.math.BlockPos;

public class DeliveryPosition extends ServerPosition {

    private final int amount;

    public DeliveryPosition(final BlockPos pos, final String dim, final int amount) {
        super(pos, dim);
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }
}
