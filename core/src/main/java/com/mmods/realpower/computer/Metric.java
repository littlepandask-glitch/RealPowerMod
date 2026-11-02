package com.mmods.realpower.computer;

import net.minecraft.nbt.CompoundTag;

/**
 * Historico de uma grandeza em dois niveis: {@value #FINE_SIZE} amostras de 1 s (ultimos 5 min) e
 * {@value #COARSE_SIZE} medias de 5 min (ultimas 24 h). Anel de tamanho fixo, persistido em NBT.
 */
public final class Metric {
    public static final int FINE_SIZE = 300;
    public static final int COARSE_SIZE = 288;

    private final Ring fine = new Ring(FINE_SIZE);
    private final Ring coarse = new Ring(COARSE_SIZE);
    private double accumulated;
    private int accumulatedCount;

    public void add(float value) {
        fine.add(value);
        accumulated += value;
        if (++accumulatedCount == FINE_SIZE) {
            coarse.add((float) (accumulated / FINE_SIZE));
            accumulated = 0.0;
            accumulatedCount = 0;
        }
    }

    /** Amostras de 1 s, da mais antiga para a mais nova. */
    public float[] fine() {
        return fine.values();
    }

    /** Medias de 5 min, da mais antiga para a mais nova. */
    public float[] coarse() {
        return coarse.values();
    }

    public void clear() {
        fine.fromInts(new int[0]);
        coarse.fromInts(new int[0]);
        accumulated = 0.0;
        accumulatedCount = 0;
    }

    public float latest() {
        return fine.latest();
    }

    public void save(CompoundTag tag, String key) {
        CompoundTag own = new CompoundTag();
        own.putIntArray("Fine", fine.toInts());
        own.putIntArray("Coarse", coarse.toInts());
        own.putDouble("Acc", accumulated);
        own.putInt("AccCount", accumulatedCount);
        tag.put(key, own);
    }

    public void load(CompoundTag tag, String key) {
        CompoundTag own = tag.getCompound(key);
        fine.fromInts(own.getIntArray("Fine"));
        coarse.fromInts(own.getIntArray("Coarse"));
        accumulated = own.getDouble("Acc");
        accumulatedCount = Math.min(own.getInt("AccCount"), FINE_SIZE - 1);
    }

    /** Anel circular de floats. */
    private static final class Ring {
        private final float[] data;
        private int count;
        private int next;

        Ring(int size) {
            data = new float[size];
        }

        void add(float value) {
            data[next] = value;
            next = (next + 1) % data.length;
            count = Math.min(count + 1, data.length);
        }

        float latest() {
            return count == 0 ? 0.0F : data[(next - 1 + data.length) % data.length];
        }

        float[] values() {
            float[] out = new float[count];
            int start = (next - count + data.length) % data.length;
            for (int i = 0; i < count; i++) {
                out[i] = data[(start + i) % data.length];
            }
            return out;
        }

        int[] toInts() {
            float[] values = values();
            int[] out = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                out[i] = Float.floatToIntBits(values[i]);
            }
            return out;
        }

        void fromInts(int[] ints) {
            count = 0;
            next = 0;
            for (int i = Math.max(0, ints.length - data.length); i < ints.length; i++) {
                add(Float.intBitsToFloat(ints[i]));
            }
        }
    }
}
