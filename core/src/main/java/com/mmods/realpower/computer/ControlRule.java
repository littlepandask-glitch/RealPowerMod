package com.mmods.realpower.computer;

import net.minecraft.nbt.CompoundTag;

/**
 * Regra automatica do computador: "se {@code quantity} estiver acima/abaixo de {@code threshold}, faca {@code action}".
 * Avaliada a cada segundo, na ordem; a ultima regra verdadeira prevalece. Para histerese use duas regras
 * (ex.: armazenado &gt; 90% desliga, armazenado &lt; 60% liga).
 */
public record ControlRule(Quantity quantity, boolean above, int threshold, Action action, int arg) {
    /** Grandeza observada: geracao e consumo em kW (1 FE/t = 1 kW), armazenado em % da capacidade total. */
    public enum Quantity {
        GENERATION(100, 30000, 1000), DELIVERED(100, 30000, 1000), STORED(5, 100, 90);

        public final int step;
        public final int max;
        public final int initial;

        Quantity(int step, int max, int initial) {
            this.step = step;
            this.max = max;
            this.initial = initial;
        }
    }

    /** O que fazer com os equipamentos controlaveis: desligar, ligar, limitar a saida (arg %) ou acionar o redstone. */
    public enum Action { OFF, ON, LIMIT, REDSTONE }

    public static final int MAX_RULES = 8;

    public static ControlRule initial() {
        return new ControlRule(Quantity.STORED, true, Quantity.STORED.initial, Action.OFF, 50);
    }

    public boolean holds(double generationKw, double deliveredKw, double storedPercent) {
        double value = switch (quantity) {
            case GENERATION -> generationKw;
            case DELIVERED -> deliveredKw;
            case STORED -> storedPercent;
        };
        return above ? value > threshold : value < threshold;
    }

    public ControlRule withQuantity(Quantity next) {
        return new ControlRule(next, above, next.initial, action, arg);
    }

    public ControlRule withAbove(boolean value) {
        return new ControlRule(quantity, value, threshold, action, arg);
    }

    public ControlRule withThreshold(int value) {
        return new ControlRule(quantity, above, Math.max(0, Math.min(quantity.max, value)), action, arg);
    }

    public ControlRule withAction(Action next) {
        return new ControlRule(quantity, above, threshold, next, arg);
    }

    public ControlRule withArg(int value) {
        return new ControlRule(quantity, above, threshold, action, Math.max(0, Math.min(100, value)));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Quantity", quantity.ordinal());
        tag.putBoolean("Above", above);
        tag.putInt("Threshold", threshold);
        tag.putInt("Action", action.ordinal());
        tag.putInt("Arg", arg);
        return tag;
    }

    public static ControlRule load(CompoundTag tag) {
        Quantity[] quantities = Quantity.values();
        Action[] actions = Action.values();
        return new ControlRule(quantities[Math.floorMod(tag.getInt("Quantity"), quantities.length)], tag.getBoolean("Above"),
                tag.getInt("Threshold"), actions[Math.floorMod(tag.getInt("Action"), actions.length)], tag.getInt("Arg"));
    }
}
