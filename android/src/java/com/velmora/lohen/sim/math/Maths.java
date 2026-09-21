/*
 * LOHEN — sim/math/Maths.java
 *
 * Boite a outils numerique : easing (05.10 ease_out_cubic, 14.07 ease_out_quint),
 * courbes de reponse d'entree (08.02 x^1.4, camera x^1.25), catenaire du
 * cable de grappin (05.24, 32 segments), interpolation d'altitude pour les
 * paliers musicaux (13.20), amortis independants du framerate.
 */
package com.velmora.lohen.sim.math;

public final class Maths {

    public static final float PI = (float) Math.PI;
    public static final float TWO_PI = (float) (Math.PI * 2.0);
    public static final float DEG = (float) (180.0 / Math.PI);
    public static final float RAD = (float) (Math.PI / 180.0);

    private Maths() { }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float invLerp(float a, float b, float v) {
        return (Math.abs(b - a) < 1e-8f) ? 0f : clamp01((v - a) / (b - a));
    }

    public static float remap(float v, float a0, float b0, float a1, float b1) {
        return lerp(a1, b1, invLerp(a0, b0, v));
    }

    public static float smoothstep(float e0, float e1, float v) {
        float t = clamp01((v - e0) / Math.max(1e-6f, e1 - e0));
        return t * t * (3f - 2f * t);
    }

    public static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /** 14.07 : toute animation d'UI est en ease_out_quint, 180 ms. */
    public static float easeOutQuint(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u * u * u;
    }

    public static float easeOutQuad(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u;
    }

    /** Amorti exponentiel stable quel que soit le pas de temps. */
    public static float damp(float current, float target, float lambda, float dt) {
        return lerp(current, target, 1f - (float) Math.exp(-lambda * dt));
    }

    /** Approche lineaire bornee (utilisée pour les lissages thermiques, 18.09). */
    public static float moveTowards(float current, float target, float maxDelta) {
        float d = target - current;
        if (Math.abs(d) <= maxDelta) {
            return target;
        }
        return current + Math.signum(d) * maxDelta;
    }

    /** Courbe de reponse d'entree : signe(x) * |x|^p (08.02, 08.23). */
    public static float responseCurve(float x, float power) {
        float a = Math.abs(clamp(x, -1f, 1f));
        return Math.signum(x) * (float) Math.pow(a, power);
    }

    /** Facteur d'amortissement exponentiel (frame-rate independent). */
    public static float dampFactor(float lambda, float dt) {
        return (float) Math.exp(-Math.max(0f, lambda) * Math.max(0f, dt));
    }

    /** Smoothstep normalise (0..1). */
    public static float smoothstep(float t) {
        float x = clamp01(t);
        return x * x * (3f - 2f * x);
    }

    /** Ramene un angle en DEGRES dans -180..180. */
    public static float wrapAngle(float degrees) {
        float a = degrees % 360f;
        if (a > 180f) {
            a -= 360f;
        } else if (a < -180f) {
            a += 360f;
        }
        return a;
    }

    /** Angle en radians ramene dans -PI..PI (alias lisible de wrapPi). */
    public static float wrapRadians(float angle) {
        return wrapPi(angle);
    }

    public static float wrapPi(float angle) {
        float a = angle;
        while (a > PI) {
            a -= TWO_PI;
        }
        while (a < -PI) {
            a += TWO_PI;
        }
        return a;
    }

    public static float lerpAngle(float a, float b, float t) {
        return a + wrapPi(b - a) * t;
    }

    public static float dampAngle(float a, float b, float lambda, float dt) {
        return a + wrapPi(b - a) * (1f - (float) Math.exp(-lambda * dt));
    }

    /**
     * Catenaire du cable de grappin (05.24) : 32 segments, fleche proportionnelle
     * a la tension. `sag` en metres au milieu du segment.
     */
    public static void catenary(Vec3 a, Vec3 b, float sag, int segments, float[] out) {
        int n = Math.max(2, segments);
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float dz = b.z - a.z;
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            /* parabole approchant la catenaire : suffisante a 32 segments et
               beaucoup moins couteuse qu'un cosh par sommet. */
            float k = 4f * sag * t * (1f - t);
            out[i * 3] = a.x + dx * t;
            out[i * 3 + 1] = a.y + dy * t - k;
            out[i * 3 + 2] = a.z + dz * t;
        }
    }

    /** Bruit deterministe 1D (hash) — vent, grain, scintillement de la lampe. */
    public static float hash1(float x) {
        float s = (float) Math.sin(x * 127.1f) * 43758.5453f;
        return s - (float) Math.floor(s);
    }

    public static float hash2(float x, float y) {
        float s = (float) Math.sin(x * 127.1f + y * 311.7f) * 43758.5453f;
        return s - (float) Math.floor(s);
    }

    /** Bruit de valeur lisse 1D. */
    public static float valueNoise(float x) {
        float i = (float) Math.floor(x);
        float f = x - i;
        float u = f * f * (3f - 2f * f);
        return lerp(hash1(i), hash1(i + 1f), u) * 2f - 1f;
    }

    /** Bruit de valeur lisse 2D (utilisé par le shader de verre cote CPU). */
    public static float valueNoise2(float x, float y) {
        float ix = (float) Math.floor(x);
        float iy = (float) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        float ux = fx * fx * (3f - 2f * fx);
        float uy = fy * fy * (3f - 2f * fy);
        float a = hash2(ix, iy);
        float b = hash2(ix + 1f, iy);
        float c = hash2(ix, iy + 1f);
        float d = hash2(ix + 1f, iy + 1f);
        return (lerp(lerp(a, b, ux), lerp(c, d, ux), uy) * 2f - 1f);
    }

    public static float fbm(float x, float y, int octaves) {
        float sum = 0f;
        float amp = 0.5f;
        float freq = 1f;
        for (int i = 0; i < octaves; i++) {
            sum += amp * valueNoise2(x * freq, y * freq);
            freq *= 2.03f;
            amp *= 0.5f;
        }
        return sum;
    }

    /** Palier d'altitude (13.20 : six paliers de M16). */
    public static int altitudeTier(float altitude, float[] bounds) {
        for (int i = 0; i < bounds.length; i++) {
            if (altitude < bounds[i]) {
                return i;
            }
        }
        return bounds.length;
    }

    public static float fovFromFocal(float focalMm, float sensorHeightMm) {
        return 2f * (float) Math.toDegrees(Math.atan(sensorHeightMm / (2f * focalMm)));
    }

    /** 38 mm equiv. -> 46 deg ; 52 mm -> 34 deg ; 28 mm -> 62 deg (05.10). */
    public static float fovForFocal35(float focalMm) {
        /* capteur 24 mm de haut (equivalent plein format) */
        return fovFromFocal(focalMm, 24f);
    }

    public static float approach(float current, float target, float rate, float dt) {
        return moveTowards(current, target, rate * dt);
    }

    public static boolean nearlyEqual(float a, float b, float eps) {
        return Math.abs(a - b) <= eps;
    }

    /** CRC32 (IEEE) — integrite des sauvegardes .vlm (02.20). */
    public static int crc32(byte[] data, int len) {
        int crc = 0xFFFFFFFF;
        for (int i = 0; i < len; i++) {
            crc ^= (data[i] & 0xFF);
            for (int k = 0; k < 8; k++) {
                crc = (crc >>> 1) ^ (0xEDB88320 & -(crc & 1));
            }
        }
        return ~crc;
    }
}
