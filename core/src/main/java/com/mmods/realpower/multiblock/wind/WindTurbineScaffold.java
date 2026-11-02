package com.mmods.realpower.multiblock.wind;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Posicoes do andaime minimo valido de uma turbina; usado pelo overlay fantasma e pelos testes. */
public final class WindTurbineScaffold {
    public enum Kind { CONTROLLER, TOWER, BLADE }

    public record Part(BlockPos pos, Kind kind) {
    }

    private WindTurbineScaffold() {
    }

    /**
     * Andaime minimo que o validador aceita: torre, cubo a frente do controlador e 4 bracos de pa.
     * Nao inclui o solo, que precisa existir sob a base da torre.
     */
    public static List<Part> minimum(BlockPos controller, Direction facing) {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(controller, Kind.CONTROLLER));
        for (int i = 1; i <= WindTurbineScan.MIN_TOWER_HEIGHT; i++) {
            parts.add(new Part(controller.below(i), Kind.TOWER));
        }
        BlockPos hub = controller.relative(facing);
        parts.add(new Part(hub, Kind.BLADE));
        Direction[] arms = {Direction.UP, Direction.DOWN, facing.getClockWise(), facing.getCounterClockWise()};
        for (Direction arm : arms) {
            for (int i = 1; i <= WindTurbineScan.MIN_BLADE_LENGTH; i++) {
                parts.add(new Part(hub.relative(arm, i), Kind.BLADE));
            }
        }
        return parts;
    }
}
