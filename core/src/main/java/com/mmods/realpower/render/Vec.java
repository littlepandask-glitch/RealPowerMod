package com.mmods.realpower.render;

/** Vetor 3D imutavel usado so na geracao de malhas (sem dependencia do Minecraft). */
record Vec(double x, double y, double z) {
    static final Vec ZERO = new Vec(0, 0, 0);

    Vec add(Vec o) {
        return new Vec(x + o.x, y + o.y, z + o.z);
    }

    Vec sub(Vec o) {
        return new Vec(x - o.x, y - o.y, z - o.z);
    }

    Vec scale(double k) {
        return new Vec(x * k, y * k, z * k);
    }

    double dot(Vec o) {
        return x * o.x + y * o.y + z * o.z;
    }

    Vec cross(Vec o) {
        return new Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    double length() {
        return Math.sqrt(dot(this));
    }

    Vec normalized() {
        double len = length();
        return len < 1e-12 ? ZERO : scale(1.0 / len);
    }

    static Vec average(Vec... points) {
        Vec sum = ZERO;
        for (Vec p : points) {
            sum = sum.add(p);
        }
        return sum.scale(1.0 / points.length);
    }
}
