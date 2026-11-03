package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.cable.CableBlock;
import com.mmods.realpower.cable.PowerStructureBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** As casas de forca que dividem a mesma barragem: as que aparecem ao caminhar pela linha da parede, de lado, a partir de uma delas. */
public final class DamUnits {
    private DamUnits() {
    }

    /** Bases das unidades (a propria primeiro) cuja parede traseira esta alinhada e emendada a desta, com a mesma orientacao. */
    public static List<BlockPos> find(Level level, BlockPos base, BlockState state) {
        List<BlockPos> bases = new ArrayList<>();
        bases.add(base);
        if (!(state.getBlock() instanceof HydroUnitBlock self)) {
            return bases;
        }
        Direction facing = state.getValue(HydroUnitBlock.FACING);
        BlockPos start = CableBlock.rotate(base, facing, 0, (HydroUnitBlock.size(self.tier())[2] - 1) / 2, 1);
        Direction lateral = facing.getClockWise();
        for (int sign = -1; sign <= 1; sign += 2) {
            for (int k = 1; k <= DamScan.MAX_HALF_WIDTH; k++) {
                BlockPos pos = start.relative(lateral, sign * k);
                if (!level.isLoaded(pos)) {
                    break;
                }
                BlockState cell = level.getBlockState(pos);
                if (cell.getBlock() instanceof PowerStructureBlock) {
                    BlockPos other = PowerStructureBlock.findBase(level, pos);
                    if (other != null && level.getBlockState(other).getBlock() instanceof HydroUnitBlock
                            && level.getBlockState(other).getValue(HydroUnitBlock.FACING) == facing) {
                        if (!bases.contains(other)) {
                            bases.add(other);
                        }
                        continue;
                    }
                    break;
                }
                if (!(cell.getBlock() instanceof DamBlock)) {
                    break;
                }
            }
        }
        return bases;
    }

    /** Soma das vazoes nominais (m3/s) das unidades da barragem. */
    public static double ratedFlow(Level level, BlockPos base, BlockState state) {
        double sum = 0.0;
        for (BlockPos unit : find(level, base, state)) {
            if (level.getBlockState(unit).getBlock() instanceof HydroUnitBlock block) {
                sum += HydroPhysics.spec(block.tier()).ratedFlow();
            }
        }
        return sum;
    }
}
