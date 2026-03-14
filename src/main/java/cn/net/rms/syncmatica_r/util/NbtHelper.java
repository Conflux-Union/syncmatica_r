package cn.net.rms.syncmatica_r.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Central utility for NBT operations to avoid scattered preprocessor blocks.
 * Handles API differences between MC versions (especially 1.21.8+ Optional returns).
 */
public final class NbtHelper {

    private NbtHelper() {
    }

    /**
     * Get a compound from parent, returns null if not present or wrong type.
     */
    public static NbtCompound getCompound(final NbtCompound parent, final String key) {
        if (parent == null) {
            return null;
        }
        //#if MC >= 12106
        //$$ return parent.getCompound(key).orElse(null);
        //#else
        if (!parent.contains(key, NbtElement.COMPOUND_TYPE)) {
            return null;
        }
        return parent.getCompound(key);
        //#endif
    }

    /**
     * Get a compound from NbtList at index, returns null if not present.
     */
    public static NbtCompound getCompound(final NbtList list, final int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        //#if MC >= 12106
        //$$ return list.getCompound(index).orElse(null);
        //#else
        return list.getCompound(index);
        //#endif
    }

    /**
     * Get a string from compound, returns empty string if not present or wrong type.
     */
    public static String getString(final NbtCompound nbt, final String key) {
        if (nbt == null) {
            return "";
        }
        //#if MC >= 12106
        //$$ return nbt.getString(key, "");
        //#else
        if (!nbt.contains(key, NbtElement.STRING_TYPE)) {
            return "";
        }
        return nbt.getString(key);
        //#endif
    }

    /**
     * Get a byte from compound, returns 0 if not present or wrong type.
     */
    public static byte getByte(final NbtCompound nbt, final String key) {
        if (nbt == null) {
            return 0;
        }
        //#if MC >= 12106
        //$$ return nbt.getByte(key, (byte) 0);
        //#else
        if (!nbt.contains(key, NbtElement.NUMBER_TYPE)) {
            return 0;
        }
        return nbt.getByte(key);
        //#endif
    }

    /**
     * Get an int from compound, returns 0 if not present or wrong type.
     */
    public static int getInt(final NbtCompound nbt, final String key) {
        if (nbt == null) {
            return 0;
        }
        //#if MC >= 12106
        //$$ return nbt.getInt(key, 0);
        //#else
        if (!nbt.contains(key, NbtElement.NUMBER_TYPE)) {
            return 0;
        }
        return nbt.getInt(key);
        //#endif
    }

    /**
     * Get a list from compound, returns null if not present or wrong type.
     */
    public static NbtList getList(final NbtCompound nbt, final String key) {
        if (nbt == null) {
            return null;
        }
        //#if MC >= 12106
        //$$ return nbt.getList(key).orElse(null);
        //#else
        if (!nbt.contains(key, NbtElement.LIST_TYPE)) {
            return null;
        }
        return nbt.getList(key, NbtElement.COMPOUND_TYPE);
        //#endif
    }

    /**
     * Get a list from compound with specific element type (for pre-1.21.10).
     */
    public static NbtList getList(final NbtCompound nbt, final String key, final int elementType) {
        if (nbt == null) {
            return null;
        }
        //#if MC >= 12106
        //$$ return nbt.getList(key).orElse(null);
        //#else
        if (!nbt.contains(key, NbtElement.LIST_TYPE)) {
            return null;
        }
        return nbt.getList(key, elementType);
        //#endif
    }

    /**
     * Get int array from compound, returns null if not present.
     */
    public static int[] getIntArray(final NbtCompound nbt, final String key) {
        if (nbt == null) {
            return null;
        }
        //#if MC >= 12106
        //$$ return nbt.getIntArray(key).orElse(null);
        //#else
        if (!nbt.contains(key, NbtElement.INT_ARRAY_TYPE)) {
            return null;
        }
        return nbt.getIntArray(key);
        //#endif
    }

    /**
     * Get long array from compound, returns null if not present.
     */
    public static long[] getLongArray(final NbtCompound nbt, final String key) {
        if (nbt == null) {
            return null;
        }
        //#if MC >= 12106
        //$$ return nbt.getLongArray(key).orElse(null);
        //#else
        if (!nbt.contains(key, NbtElement.LONG_ARRAY_TYPE)) {
            return null;
        }
        return nbt.getLongArray(key);
        //#endif
    }

    /**
     * Check if compound contains a key with string type.
     */
    public static boolean containsString(final NbtCompound nbt, final String key) {
        if (nbt == null) {
            return false;
        }
        //#if MC >= 12106
        //$$ return nbt.contains(key) && !getString(nbt, key).isEmpty();
        //#else
        return nbt.contains(key, NbtElement.STRING_TYPE);
        //#endif
    }

    /**
     * Check if compound contains a key with compound type.
     */
    public static boolean containsCompound(final NbtCompound nbt, final String key) {
        if (nbt == null) {
            return false;
        }
        //#if MC >= 12106
        //$$ return nbt.contains(key) && nbt.getCompound(key).isPresent();
        //#else
        return nbt.contains(key, NbtElement.COMPOUND_TYPE);
        //#endif
    }

    /**
     * Check if compound contains a key with list type.
     */
    public static boolean containsList(final NbtCompound nbt, final String key) {
        if (nbt == null) {
            return false;
        }
        //#if MC >= 12106
        //$$ return nbt.contains(key) && nbt.getList(key).isPresent();
        //#else
        return nbt.contains(key, NbtElement.LIST_TYPE);
        //#endif
    }

    /**
     * Check if compound contains a key with number type.
     */
    public static boolean containsNumber(final NbtCompound nbt, final String key) {
        if (nbt == null) {
            return false;
        }
        //#if MC >= 12106
        //$$ return nbt.contains(key);
        //#else
        return nbt.contains(key, NbtElement.NUMBER_TYPE);
        //#endif
    }

    /**
     * Execute action if list is present.
     */
    public static void ifListPresent(final NbtCompound nbt, final String key, final Consumer<NbtList> action) {
        final NbtList list = getList(nbt, key);
        if (list != null) {
            action.accept(list);
        }
    }

    /**
     * Execute action if compound is present.
     */
    public static void ifCompoundPresent(final NbtCompound nbt, final String key, final Consumer<NbtCompound> action) {
        final NbtCompound compound = getCompound(nbt, key);
        if (compound != null) {
            action.accept(compound);
        }
    }
}
