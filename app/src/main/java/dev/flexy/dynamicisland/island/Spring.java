package dev.flexy.dynamicisland.island;

public final class Spring {
    private static final float DEFAULT_VALUE_THRESHOLD = 0.5f;

    private float value;
    private float velocity;
    private float target;
    private double omega0;
    private double zeta;
    private float valueThreshold = DEFAULT_VALUE_THRESHOLD;
    private boolean atRest = true;

    public Spring(float initial, float response, float dampingRatio) {
        value = initial;
        target = initial;
        setParams(response, dampingRatio);
    }

    public Spring setParams(float response, float dampingRatio) {
        omega0 = 2.0 * Math.PI / Math.max(0.016, response);
        zeta = Math.max(0.05, dampingRatio);
        return this;
    }

    public Spring setValueThreshold(float threshold) {
        valueThreshold = threshold;
        return this;
    }

    public float value() {
        return value;
    }

    public float velocity() {
        return velocity;
    }

    public float target() {
        return target;
    }

    public boolean isAtRest() {
        return atRest;
    }

    public void animateTo(float newTarget) {
        if (newTarget == target && atRest) return;
        target = newTarget;
        atRest = false;
    }

    public void animateTo(float newTarget, float response, float dampingRatio) {
        setParams(response, dampingRatio);
        animateTo(newTarget);
    }

    public void impulse(float addVelocity) {
        velocity += addVelocity;
        atRest = false;
    }

    public void snapTo(float v) {
        value = v;
        target = v;
        velocity = 0f;
        atRest = true;
    }

    public boolean step(float dtSeconds) {
        if (atRest) return false;
        double dt = Math.min(Math.max(dtSeconds, 0.0), 0.064);
        double x0 = value - target;
        double v0 = velocity;
        double x;
        double v;
        double w = omega0;
        if (zeta > 1.0) {
            double s = w * Math.sqrt(zeta * zeta - 1.0);
            double gPlus = -zeta * w + s;
            double gMinus = -zeta * w - s;
            double b = (gMinus * x0 - v0) / (gMinus - gPlus);
            double a = x0 - b;
            double eM = Math.exp(gMinus * dt);
            double eP = Math.exp(gPlus * dt);
            x = a * eM + b * eP;
            v = a * gMinus * eM + b * gPlus * eP;
        } else if (zeta == 1.0) {
            double a = x0;
            double b = v0 + w * x0;
            double e = Math.exp(-w * dt);
            x = (a + b * dt) * e;
            v = (a + b * dt) * e * -w + b * e;
        } else {
            double wd = w * Math.sqrt(1.0 - zeta * zeta);
            double cosC = x0;
            double sinC = (zeta * w * x0 + v0) / wd;
            double e = Math.exp(-zeta * w * dt);
            double cos = Math.cos(wd * dt);
            double sin = Math.sin(wd * dt);
            x = e * (cosC * cos + sinC * sin);
            v = x * -zeta * w + e * (-wd * cosC * sin + wd * sinC * cos);
        }
        value = (float) (target + x);
        velocity = (float) v;
        if (Math.abs(x) < valueThreshold && Math.abs(v) < valueThreshold * 60f) {
            value = target;
            velocity = 0f;
            atRest = true;
            return false;
        }
        return true;
    }
}
