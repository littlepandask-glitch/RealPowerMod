package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.cable.CableBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tanque (lago de acumulacao) que a barragem precisa: area para a vazao nominal de todas as unidades da parede e profundidade para
 * a queda minima. Coordenadas locais da unidade-referencia: lx lateral, lz para montante (+), y acima do chao da usina.
 *
 * @param lxMin     primeiro lx da agua
 * @param lxMax     ultimo lx da agua
 * @param lzFirst   primeiro lz da agua (logo atras da parede da unidade)
 * @param lzLast    ultimo lz da agua
 * @param slabFirst primeiro lz da laje da parede da barragem (espessura da parede da unidade)
 * @param depth     camadas de agua acima do chao
 */
public record TankPlan(BlockPos base, Direction facing, int units, int neededArea, int lxMin, int lxMax, int lzFirst, int lzLast,
                       int slabFirst, int depth) {
    /** Espessura das paredes laterais e do fundo do tanque. */
    public static final int WALL = 2;
    /** Margem lateral, alem das unidades, para os cantos. */
    private static final int MARGIN = 4;

    public static TankPlan of(Level level, BlockPos base, BlockState state) {
        Direction facing = state.getValue(HydroUnitBlock.FACING);
        HydroUnitBlock self = (HydroUnitBlock) state.getBlock();
        int halfD = (HydroUnitBlock.size(self.tier())[2] - 1) / 2;
        List<BlockPos> units = DamUnits.find(level, base, state);
        double rated = 0.0;
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        int depth = 0;
        for (BlockPos unit : units) {
            if (!(level.getBlockState(unit).getBlock() instanceof HydroUnitBlock block)) {
                continue;
            }
            HydroPhysics.Spec spec = HydroPhysics.spec(block.tier());
            rated += spec.ratedFlow();
            int halfW = (HydroUnitBlock.size(block.tier())[0] - 1) / 2;
            int lx = CableBlock.unrotate(base, facing, unit)[0];
            lo = Math.min(lo, lx - halfW);
            hi = Math.max(hi, lx + halfW);
            depth = Math.max(depth, HydroUnitBlock.turbineHeight(block.tier()) + (int) Math.ceil(spec.minHead()) + 2);
        }
        int needed = (int) Math.ceil(rated / HydroPhysics.FLOW_PER_SURFACE_BLOCK);
        int half = Math.max((hi - lo) / 2 + MARGIN, (int) Math.ceil(Math.sqrt(needed) / 2.0));
        int center = Math.floorDiv(lo + hi, 2);
        int width = 2 * half + 1;
        int length = (int) Math.ceil(needed / (double) width);
        return new TankPlan(base, facing, units.size(), needed, center - half, center + half, halfD + 1, halfD + length,
                halfD - HydroLayout.wallCells(HydroUnitBlock.size(self.tier())[2]) + 1, depth);
    }

    public int width() {
        return lxMax - lxMin + 1;
    }

    public int length() {
        return lzLast - lzFirst + 1;
    }

    public long volume() {
        return (long) width() * length() * depth;
    }

    public BlockPos world(int lx, int lz, int y) {
        return CableBlock.rotate(base, facing, lx, lz, y);
    }
}
