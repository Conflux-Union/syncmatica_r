package cn.net.rms.syncmatica_r.material;

import cn.net.rms.syncmatica_r.ServerPosition;
import ch.endte.syncmatica.material.DeliveryPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class SyncmaticaMaterialList {
    private final List<SyncmaticaMaterialEntry> list = new ArrayList<>();
    private ServerPosition deliveryPoint;

    public void updateFrom(final MaterialProgressState state) {
        list.clear();
        if (state == null || state.isEmpty()) {
            return;
        }
        for (final MaterialProgressEntry entry : state.getEntries()) {
            final SyncmaticaMaterialEntry snapshot = new SyncmaticaMaterialEntry();
            snapshot.setKey(entry.getKey());
            snapshot.setAmountRequired(entry.getRequiredAmount());
            snapshot.setStockingSupplied(entry.getStockingSupplied());

            final java.util.List<String> names = new java.util.ArrayList<>();
            for (final ch.endte.syncmatica.extended_core.PlayerIdentifier p : entry.getClaimants()) {
                names.add(p.getName());
            }
            snapshot.setClaimers(names);
            list.add(snapshot);
        }
    }

    public List<SyncmaticaMaterialEntry> getEntries() {
        return list;
    }

    public void setDeliveryPoint(final ServerPosition deliveryPoint) {
        this.deliveryPoint = deliveryPoint;
    }

    public SyncmaticaMaterialEntry getUnclaimedEntry() {
        final Optional<SyncmaticaMaterialEntry> unclaimed = list.parallelStream()
                .filter(SyncmaticaMaterialEntry.UNFINISHED)
                .findFirst();
        if (unclaimed.isPresent()) {
            return unclaimed.get();
        }
        return null;
    }

    public Collection<DeliveryPosition> getDeliveryPosition(final SyncmaticaMaterialEntry entry) {
        if (!list.contains(entry)) {
            throw new IllegalArgumentException();
        }
        final DeliveryPosition delivery = new DeliveryPosition(deliveryPoint.getBlockPosition(), deliveryPoint.getDimensionId(), entry.getAmountMissing());
        final ArrayList<DeliveryPosition> deliveryList = new ArrayList<>();
        deliveryList.add(delivery);
        return deliveryList;
    }
}
