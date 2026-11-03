package com.mmods.realpower.multiblock.hydro;

import com.mmods.realpower.cable.CableBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tanque (lago de acumulacao) que a barragem precisa: area para a vazao nominal de todas as unidades da parede e profundidade para
 * a queda minima, limitado a {@link #MAX_SIDE} x {@link #MAX_SIDE} por casa de forca e a altura da propria barragem.
 * Coordenadas locais da unidade-referencia: lx lateral, lz para montante (+), y acima do chao da usina.
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
    /** Lado maximo (blocos) do tanque por casa de forca: desempenho da varredura e dos chunks carregados. */
    public static final int MAX_SIDE = 64;
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
        int wanted = 0;
        int unitHeight = 0;
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
            wanted = Math.max(wanted, HydroUnitBlock.turbineHeight(block.tier()) + (int) Math.ceil(spec.minHead()) + 2);
            unitHeight = Math.max(unitHeight, HydroUnitBlock.size(block.tier())[1]);
        }
        int depth = Math.max(1, Math.min(wanted, damHeight(level, base, facing, lo, hi, halfD, unitHeight) - 1));
        int needed = (int) Math.ceil(rated / HydroPhysics.FLOW_PER_SURFACE_BLOCK);
        int span = hi - lo + 1 + 2 * MARGIN;
        int width = Math.min(Math.max(span, (int) Math.ceil(Math.sqrt(needed))), Math.max(MAX_SIDE, span));
        int length = (int) Math.ceil(needed / (double) width);
        int center = Math.floorDiv(lo + hi, 2);
        int first = center - (width - 1) / 2;
        return new TankPlan(base, facing, units.size(), needed, first, first + width - 1, halfD + 1, halfD + length,
                halfD - HydroLayout.wallCells(HydroUnitBlock.size(self.tier())[2]) + 1, depth);
    }

    /**
     * Altura da barragem (camadas): a das colunas de modulos emendadas aos dois lados das unidades, no plano da parede, e no minimo a
     * da propria casa de forca.
     */
    private static int damHeight(Level level, BlockPos base, Direction facing, int lo, int hi, int halfD, int unitHeight) {
        int height = unitHeight;
        for (int lx : new int[]{lo - 1, hi + 1}) {
            int rows = 0;
            while (rows < DamScan.MAX_ROWS && level.isLoaded(CableBlock.rotate(base, facing, lx, halfD, rows))
                    && level.getBlockState(CableBlock.rotate(base, facing, lx, halfD, rows)).getBlock() instanceof DamBlock) {
                rows++;
            }
            height = Math.max(height, rows);
        }
        return height;
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
