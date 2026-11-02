package com.mmods.realpower.computer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Alarmes derivados do retrato do computador: estruturas fora do ar, equipamentos desligados, buffers cheios. */
public final class ComputerAlarms {
    public enum Level { INFO, WARNING, ERROR }

    public record Alarm(Level level, Component text) {
    }

    /** Fracao de buffer (%) a partir da qual o gerador esta desperdicando energia. */
    public static final int FULL_PERCENT = 95;

    private ComputerAlarms() {
    }

    public static List<Alarm> of(ComputerSnapshot snapshot) {
        List<Alarm> alarms = new ArrayList<>();
        if (snapshot.devices().isEmpty()) {
            alarms.add(new Alarm(Level.INFO, Component.translatable("alarm.realpower.no_devices")));
        }
        for (ComputerSnapshot.Device device : snapshot.devices()) {
            if (!device.online()) {
                alarms.add(new Alarm(Level.ERROR, Component.translatable("alarm.realpower.offline", device.name())));
            } else if (!device.enabled()) {
                alarms.add(new Alarm(Level.WARNING, Component.translatable("alarm.realpower.disabled", device.name())));
            } else if (device.capacity() > 0 && device.stored() * 100L >= device.capacity() * FULL_PERCENT) {
                alarms.add(new Alarm(Level.WARNING, Component.translatable("alarm.realpower.full", device.name())));
            }
        }
        return alarms;
    }
}
