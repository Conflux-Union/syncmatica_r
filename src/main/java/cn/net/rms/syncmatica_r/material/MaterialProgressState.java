package cn.net.rms.syncmatica_r.material;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MaterialProgressState {
    private final Map<MaterialKey, MaterialProgressEntry> entries = new LinkedHashMap<>();

    public Collection<MaterialProgressEntry> getEntries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public MaterialProgressEntry getOrCreate(final MaterialKey key, final int required) {
        return entries.computeIfAbsent(key, missing -> new MaterialProgressEntry(missing, required));
    }

    public MaterialProgressEntry get(final MaterialKey key) {
        return entries.get(key);
    }

    public void clear() {
        entries.clear();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
