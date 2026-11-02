package com.mmods.realpower.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Malha de quadrilateros com cor e normal por face. Os dados ficam em arrays planos:
 * 12 floats de posicao, 3 de normal e 3 de cor por quad.
 */
public final class Mesh {
    public final float[] positions;
    public final float[] normals;
    public final float[] colors;
    /** Por quad: brilha (luz maxima, independente do ambiente), como LEDs e conectores. */
    public final boolean[] emissive;

    private Mesh(float[] positions, float[] normals, float[] colors, boolean[] emissive) {
        this.positions = positions;
        this.normals = normals;
        this.colors = colors;
        this.emissive = emissive;
    }

    public int quadCount() {
        return normals.length / 3;
    }

    /** Acumula quads; a normal de cada um e orientada para longe do ponto interno informado. */
    static final class Builder {
        private final List<float[]> quads = new ArrayList<>();
        private boolean glow;

        /** Os quads acrescentados a seguir passam a brilhar (ou deixam de). */
        void glow(boolean on) {
            glow = on;
        }

        void quad(Vec a, Vec b, Vec c, Vec d, Vec inside, float[] rgb) {
            Vec normal = c.sub(a).cross(d.sub(b)).normalized();
            Vec centroid = Vec.average(a, b, c, d);
            if (normal.dot(centroid.sub(inside)) < 0) {
                normal = normal.scale(-1);
            }
            quads.add(new float[] {
                    (float) a.x(), (float) a.y(), (float) a.z(),
                    (float) b.x(), (float) b.y(), (float) b.z(),
                    (float) c.x(), (float) c.y(), (float) c.z(),
                    (float) d.x(), (float) d.y(), (float) d.z(),
                    (float) normal.x(), (float) normal.y(), (float) normal.z(),
                    rgb[0], rgb[1], rgb[2], glow ? 1.0F : 0.0F
            });
        }

        /** Faixa entre aneis consecutivos de vertices (todos com o mesmo numero de pontos). */
        void strip(List<Vec[]> rings, float[] rgb) {
            for (int i = 0; i + 1 < rings.size(); i++) {
                Vec[] r0 = rings.get(i);
                Vec[] r1 = rings.get(i + 1);
                Vec inside = Vec.average(Vec.average(r0), Vec.average(r1));
                for (int j = 0; j < r0.length; j++) {
                    int k = (j + 1) % r0.length;
                    quad(r0[j], r0[k], r1[k], r1[j], inside, rgb);
                }
            }
        }

        /** Tampa em leque ligando o anel a um ponto central. */
        void cap(Vec[] ring, Vec center, Vec inside, float[] rgb) {
            for (int j = 0; j < ring.length; j++) {
                int k = (j + 1) % ring.length;
                quad(center, ring[j], ring[k], ring[k], inside, rgb);
            }
        }

        void box(Vec center, double sx, double sy, double sz, float[] rgb) {
            double hx = sx / 2, hy = sy / 2, hz = sz / 2;
            Vec[] v = new Vec[8];
            for (int i = 0; i < 8; i++) {
                v[i] = center.add(new Vec((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz));
            }
            int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
            for (int[] f : faces) {
                quad(v[f[0]], v[f[1]], v[f[2]], v[f[3]], center, rgb);
            }
        }

        /** Viga de secao quadrada entre dois pontos. */
        void beam(Vec a, Vec b, double thickness, float[] rgb) {
            Vec dir = b.sub(a).normalized();
            Vec ref = Math.abs(dir.y()) > 0.9 ? new Vec(1, 0, 0) : new Vec(0, 1, 0);
            Vec u = dir.cross(ref).normalized().scale(thickness / 2);
            Vec w = dir.cross(u).normalized().scale(thickness / 2);
            Vec[] ra = {a.add(u).add(w), a.sub(u).add(w), a.sub(u).sub(w), a.add(u).sub(w)};
            Vec[] rb = {b.add(u).add(w), b.sub(u).add(w), b.sub(u).sub(w), b.add(u).sub(w)};
            Vec mid = Vec.average(a, b);
            strip(List.of(ra, rb), rgb);
            quad(ra[0], ra[1], ra[2], ra[3], mid, rgb);
            quad(rb[0], rb[1], rb[2], rb[3], mid, rgb);
        }

        Mesh build() {
            int n = quads.size();
            float[] positions = new float[n * 12];
            float[] normals = new float[n * 3];
            float[] colors = new float[n * 3];
            boolean[] emissive = new boolean[n];
            for (int i = 0; i < n; i++) {
                float[] q = quads.get(i);
                System.arraycopy(q, 0, positions, i * 12, 12);
                System.arraycopy(q, 12, normals, i * 3, 3);
                System.arraycopy(q, 15, colors, i * 3, 3);
                emissive[i] = q[18] > 0.5F;
            }
            return new Mesh(positions, normals, colors, emissive);
        }
    }
}
