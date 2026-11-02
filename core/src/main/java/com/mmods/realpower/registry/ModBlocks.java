package com.mmods.realpower.registry;

import com.mmods.realpower.RealPower;
import com.mmods.realpower.api.tier.Tier;
import com.mmods.realpower.cable.CableBlock;
import com.mmods.realpower.cable.CableKind;
import java.util.LinkedHashMap;
import java.util.Map;
import com.mmods.realpower.multiblock.wind.TurbineCasingBlock;
import com.mmods.realpower.multiblock.wind.TurbinePostBlock;
import com.mmods.realpower.multiblock.wind.TurbinePowerPortBlock;
import com.mmods.realpower.multiblock.wind.WindStructureBlock;
import com.mmods.realpower.multiblock.wind.WindTurbineControllerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RealPower.MOD_ID);

    public static final DeferredBlock<Block> TURBINE_BASE = BLOCKS.registerBlock("turbine_base", Block::new,
            BlockBehaviour.Properties.of().strength(5.0F, 9.0F).sound(SoundType.STONE).requiresCorrectToolForDrops());

    public static final DeferredBlock<TurbineCasingBlock> TURBINE_CASING = BLOCKS.registerBlock("turbine_casing",
            TurbineCasingBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());

    /** Luminaria industrial: componente comum a todos os multiblocos (ilumina o interior da torre). */
    public static final DeferredBlock<Block> INDUSTRIAL_LIGHT = BLOCKS.registerBlock("industrial_light", Block::new,
            BlockBehaviour.Properties.of().strength(1.5F, 3.0F).sound(SoundType.METAL).lightLevel(state -> 15));

    /** Poste da porta de energia da turbina: fuste e topo com travessa. */
    public static final DeferredBlock<TurbinePostBlock> TURBINE_POST = BLOCKS.registerBlock("turbine_post",
            TurbinePostBlock::new, BlockBehaviour.Properties.of().strength(2.0F, 4.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<TurbinePowerPortBlock> TURBINE_POWER_PORT = BLOCKS.registerBlock("turbine_power_port",
            TurbinePowerPortBlock::new, BlockBehaviour.Properties.of().strength(2.0F, 4.0F).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<WindStructureBlock> ROTOR_FAIRING = BLOCKS.registerBlock("rotor_fairing",
            properties -> new WindStructureBlock(properties, false),
            BlockBehaviour.Properties.of().strength(2.5F, 5.0F).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<WindStructureBlock> TURBINE_BLADE = BLOCKS.registerBlock("turbine_blade",
            properties -> new WindStructureBlock(properties, false),
            BlockBehaviour.Properties.of().strength(2.0F, 4.0F).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<WindTurbineControllerBlock> WIND_TURBINE_CONTROLLER_BASIC =
            windController(Tier.BASIC);
    public static final DeferredBlock<WindTurbineControllerBlock> WIND_TURBINE_CONTROLLER_INDUSTRIAL =
            windController(Tier.INDUSTRIAL);

    private static DeferredBlock<WindTurbineControllerBlock> windController(Tier tier) {
        return BLOCKS.registerBlock("wind_turbine_controller_" + tier.getSerializedName(),
                properties -> new WindTurbineControllerBlock(properties, tier),
                BlockBehaviour.Properties.of().strength(4.0F, 8.0F).sound(SoundType.METAL));
    }

    /** Todos os nos da rede eletrica (cabo, rele, poste, torre), por id. */
    public static final Map<String, DeferredBlock<CableBlock>> NODES = new LinkedHashMap<>();

    static {
        for (CableKind kind : CableKind.values()) {
            for (Tier tier : Tier.values()) {
                if (offered(kind, tier)) {
                    NODES.put(nodeId(kind, tier), BLOCKS.registerBlock(nodeId(kind, tier),
                            properties -> new CableBlock(properties, tier, kind),
                            BlockBehaviour.Properties.of().strength(1.0F + tier.level() * 0.5F, 2.0F)
                                    .sound(SoundType.METAL).noOcclusion()));
                }
            }
        }
    }

    /** Postes vao ate o tier de alta tensao; torres comecam nele (cabos pesados em estruturas de aco). */
    public static boolean offered(CableKind kind, Tier tier) {
        return switch (kind) {
            case CABLE, RELAY -> true;
            case POLE -> tier.level() <= Tier.HIGH_VOLTAGE.level();
            case TOWER -> tier.level() >= Tier.HIGH_VOLTAGE.level();
        };
    }

    public static String nodeId(CableKind kind, Tier tier) {
        return kind.getSerializedName() + "_" + tier.getSerializedName();
    }

    public static DeferredBlock<CableBlock> node(CableKind kind, Tier tier) {
        return NODES.get(nodeId(kind, tier));
    }

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
