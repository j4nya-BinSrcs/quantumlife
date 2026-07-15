package com.particlelife.math;

/**
 * A mutable 3D vector of doubles.
 *
 * <p>Mutability is a deliberate performance choice: the physics hot path
 * executes millions of vector operations per second, and allocating a new
 * vector per operation would flood the garbage collector. All mutating
 * operations return {@code this} to allow fluent chaining; methods that must
 * not mutate their receiver are clearly named ({@link #copy()},
 * {@link #distanceTo(Vector3)} etc.).
 *
 * <p>This class is <strong>not</strong> thread-safe; each thread must operate
 * on its own instances (the engine gives every worker thread scratch vectors).
 */
public final class Vector3 {

    public double x;
    public double y;
    public double z;

    /** Creates the zero vector. */
    public Vector3() {
    }

    /** Creates a vector with the given components. */
    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Creates a copy of {@code other}. */
    public Vector3(Vector3 other) {
        this(other.x, other.y, other.z);
    }

    /** Sets all three components. */
    public Vector3 set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /** Copies the components of {@code other} into this vector. */
    public Vector3 set(Vector3 other) {
        return set(other.x, other.y, other.z);
    }

    /** Sets all components to zero. */
    public Vector3 setZero() {
        return set(0.0, 0.0, 0.0);
    }

    /** Adds {@code other} to this vector. */
    public Vector3 add(Vector3 other) {
        x += other.x;
        y += other.y;
        z += other.z;
        return this;
    }

    /** Adds the given components to this vector. */
    public Vector3 add(double dx, double dy, double dz) {
        x += dx;
        y += dy;
        z += dz;
        return this;
    }

    /** Adds {@code other * scale} to this vector (fused multiply-add). */
    public Vector3 addScaled(Vector3 other, double scale) {
        x += other.x * scale;
        y += other.y * scale;
        z += other.z * scale;
        return this;
    }

    /** Subtracts {@code other} from this vector. */
    public Vector3 sub(Vector3 other) {
        x -= other.x;
        y -= other.y;
        z -= other.z;
        return this;
    }

    /** Multiplies each component by {@code factor}. */
    public Vector3 scale(double factor) {
        x *= factor;
        y *= factor;
        z *= factor;
        return this;
    }

    /** Returns the squared Euclidean length (no square root). */
    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    /** Returns the Euclidean length. */
    public double length() {
        return Math.sqrt(lengthSquared());
    }

    /**
     * Normalizes this vector to unit length. The zero vector is left
     * unchanged (there is no meaningful direction to preserve).
     */
    public Vector3 normalize() {
        double lenSq = lengthSquared();
        if (lenSq > 0.0) {
            double inv = 1.0 / Math.sqrt(lenSq);
            scale(inv);
        }
        return this;
    }

    /**
     * Clamps the length of this vector to {@code maxLength}; shorter vectors
     * are unchanged.
     */
    public Vector3 clampLength(double maxLength) {
        double lenSq = lengthSquared();
        if (lenSq > maxLength * maxLength) {
            scale(maxLength / Math.sqrt(lenSq));
        }
        return this;
    }

    /** Returns the dot product with {@code other}. */
    public double dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /** Returns the squared distance to {@code other}. */
    public double distanceSquaredTo(Vector3 other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Returns the distance to {@code other}. */
    public double distanceTo(Vector3 other) {
        return Math.sqrt(distanceSquaredTo(other));
    }

    /** Returns an independent copy of this vector. */
    public Vector3 copy() {
        return new Vector3(this);
    }

    /** Returns whether every component is finite (no NaN / infinity). */
    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Vector3 v
                && Double.compare(x, v.x) == 0
                && Double.compare(y, v.y) == 0
                && Double.compare(z, v.z) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        result = 31 * result + Double.hashCode(z);
        return result;
    }

    @Override
    public String toString() {
        return "(%.4f, %.4f, %.4f)".formatted(x, y, z);
    }
}
