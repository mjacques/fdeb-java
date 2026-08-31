package com.github.mjacques.fdeb;

/**
 * Lightweight immutable 2D vector for internal FDEB geometry calculations.
 * Avoids JTS Coordinate overhead in tight loops (force computation, compatibility).
 */
record Vec2(double x, double y) {

    Vec2 add(Vec2 o) {
        return new Vec2(x + o.x, y + o.y);
    }

    Vec2 sub(Vec2 o) {
        return new Vec2(x - o.x, y - o.y);
    }

    Vec2 scale(double s) {
        return new Vec2(x * s, y * s);
    }

    double dot(Vec2 o) {
        return x * o.x + y * o.y;
    }

    double length() {
        return Math.sqrt(x * x + y * y);
    }

    double lengthSq() {
        return x * x + y * y;
    }

    Vec2 normalize() {
        double len = length();
        if (len < 1e-12) return new Vec2(0, 0);
        return new Vec2(x / len, y / len);
    }

    static Vec2 midpoint(Vec2 a, Vec2 b) {
        return new Vec2((a.x + b.x) / 2, (a.y + b.y) / 2);
    }

    double distanceTo(Vec2 o) {
        return sub(o).length();
    }
}
