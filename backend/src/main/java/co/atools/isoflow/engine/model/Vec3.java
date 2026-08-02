// Vec3.java — 3D 좌표 값 객체. 내부 단위는 항상 mm
package co.atools.isoflow.engine.model;

public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 plus(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 minus(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 scale(double f) {
        return new Vec3(x * f, y * f, z * f);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** 영벡터면 그대로 반환한다(0으로 나누지 않는다) */
    public Vec3 normalized() {
        double n = length();
        return n < 1e-12 ? this : scale(1.0 / n);
    }

    public double distanceTo(Vec3 o) {
        return minus(o).length();
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3 cross(Vec3 o) {
        return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }
}
