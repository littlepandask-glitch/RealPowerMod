package com.mmods.realpower.quarry;

import com.mmods.realpower.RealPower;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(RealPowerQuarry.MOD_ID)
public class RealPowerQuarry {
    public static final String MOD_ID = "realpower_quarry";

    public RealPowerQuarry(IEventBus modEventBus) {
        RealPower.LOGGER.info("Real Power: Quarry carregado (core: {})", RealPower.MOD_ID);
    }
}
