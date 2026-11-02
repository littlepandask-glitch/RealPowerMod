package com.mmods.realpower.screen;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Retangulo de telas: blocos {@link ScreenBlock} coplanares, contiguos e de mesmo FACING formam um painel unico de
 * ate {@value #MAX_WIDTH} x {@value #MAX_HEIGHT}. Se o conjunto nao for um retangulo cheio, cada bloco fica sozinho (1x1).
 * O "mestre" e o canto superior esquerdo visto de frente.
 */
public record ScreenGroup(BlockPos master, Direction facing, int width, int height) {
    public static final int MAX_WIDTH = 8;
    public static final int MAX_HEIGHT = 6;
    private static final int MAX_BLOCKS = MAX_WIDTH * MAX_HEIGHT;

    /** Direcao da direita de quem olha a tela de frente. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static boolean sameScreen(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ScreenBlock && state.getValue(ScreenBlock.FACING) == facing;
    }

    private static int along(BlockPos pos, Direction right) {
        return pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ();
    }

    public static ScreenGroup of(BlockGetter level, BlockPos pos) {
        Direction facing = level.getBlockState(pos).getValue(ScreenBlock.FACING);
        Direction right = right(facing);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(pos);
        queue.add(pos);
        int minRight = along(pos, right);
        int maxRight = minRight;
        int minY = pos.getY();
        int maxY = minY;
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction step : new Direction[] {right, right.getOpposite(), Direction.UP, Direction.DOWN}) {
                BlockPos next = current.relative(step);
                if (seen.size() < MAX_BLOCKS && !seen.contains(next) && sameScreen(level, next, facing)) {
                    seen.add(next);
                    queue.add(next);
                    minRight = Math.min(minRight, along(next, right));
                    maxRight = Math.max(maxRight, along(next, right));
                    minY = Math.min(minY, next.getY());
                    maxY = Math.max(maxY, next.getY());
                }
            }
        }
        int width = maxRight - minRight + 1;
        int height = maxY - minY + 1;
        if (width > MAX_WIDTH || height > MAX_HEIGHT || seen.size() != width * height) {
            return new ScreenGroup(pos, facing, 1, 1);
        }
        BlockPos master = pos.relative(right.getOpposite(), along(pos, right) - minRight).above(maxY - pos.getY());
        return new ScreenGroup(master, facing, width, height);
    }

    /** Todos os blocos do retangulo. */
    public java.util.List<BlockPos> positions() {
        Direction right = right(facing);
        java.util.List<BlockPos> all = new java.util.ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                all.add(master.relative(right, x).below(y));
            }
        }
        return all;
    }
}
