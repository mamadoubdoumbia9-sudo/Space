# -*- coding: utf-8 -*-
"""Patch rendu : ciel colore avec halo solaire, brume du niveau respectee,
intensites appliquees, saturation remontee, grain/vignette adoucis."""
import sys

ROOT = "/home/user/Space/android/src/java/com/velmora/lohen"
miss = []


def load(p):
    return [open(ROOT + p, encoding="utf-8").read()]


def rep(store, old, new, all_occ=False):
    if old not in store[0]:
        miss.append(old.splitlines()[0][:70])
        return
    if all_occ:
        store[0] = store[0].replace(old, new)
    else:
        store[0] = store[0].replace(old, new, 1)


# ---------------------------------------------------------------- ShaderLib
sl = load("/gl/ShaderLib.java")
rep(sl, """            + \"uniform float uExposure;\\n\"      /* +0,4 EV dans le faisceau */""",
    """            + \"uniform float uExposure;\\n\"      /* +0,4 EV dans le faisceau */
            + \"uniform float uFogSkip;\\n\"        /* 1 = dome du ciel : pas de brume */""")
rep(sl, """            + \"  float f = 1.0 - exp(-uFogDensity * dist);\\n\"
            + \"  f = clamp(f, 0.0, 1.0);\\n\"""",
    """            + \"  float f = 1.0 - exp(-uFogDensity * dist);\\n\"
            + \"  f = clamp(f, 0.0, 1.0) * (1.0 - uFogSkip);\\n\"""")

# ---------------------------------------------------------------- WorldRenderer
wr = load("/render/WorldRenderer.java")

rep(wr, """    private int uMvp, uModel, uCamPos, uSunDir, uSunColor, uAmbientSky,
            uAmbientGround, uFogColor, uFogDensity, uFogVisibility, uLampPos,""",
    """    private int uMvp, uModel, uCamPos, uSunDir, uSunColor, uAmbientSky,
            uAmbientGround, uFogColor, uFogDensity, uFogVisibility, uLampPos,
            uFogSkip,""")
rep(wr, """        uExposure = GlUtil.uniform(program, "uExposure");""",
    """        uExposure = GlUtil.uniform(program, "uExposure");
        uFogSkip = GlUtil.uniform(program, "uFogSkip");""")

rep(wr, """        buildSky();""", """        skyBuiltFor = null;
        buildSky(null);""")

# ciel par sequence
rep(wr, """    /**
     * 05.24 : le ciel n'est jamais bleu. C'est un gradient de brume, du bleu
     * de fond a l'horizon vers le noir-verre au zenith.
     */
    private void buildSky() {
        sky.clear();
        float r = 900f;
        int bands = 8;
        int seg = 16;
        for (int b = 0; b < bands; b++) {
            float t0 = b / (float) bands;
            float t1 = (b + 1) / (float) bands;
            float y0 = -0.25f + t0 * 1.25f;
            float y1 = -0.25f + t1 * 1.25f;
            int base = sky.vertexCount();
            for (int s = 0; s <= seg; s++) {
                float a0 = (float) (s * Math.PI * 2.0 / seg);
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float h0 = (float) Math.sqrt(Math.max(0f, 1f - y0 * y0));
                float h1 = (float) Math.sqrt(Math.max(0f, 1f - y1 * y1));
                int zen = ShaderLib.NIGHT;
                int hor = Palette.BLEU_BRUME;
                int low = skyColor(t0, zen, hor);
                int high = skyColor(t1, zen, hor);
                sky.vertex(c0 * h0 * r, y0 * r, s0 * h0 * r, -c0 * h0, -y0, -s0 * h0,
                        ShaderLib.r(low), ShaderLib.g(low), ShaderLib.b(low), 1f, 0f, 0f);
                sky.vertex(c0 * h1 * r, y1 * r, s0 * h1 * r, -c0 * h1, -y1, -s0 * h1,
                        ShaderLib.r(high), ShaderLib.g(high), ShaderLib.b(high), 1f, 0f, 0f);
            }
            for (int s = 0; s < seg; s++) {
                int o = base + s * 2;
                sky.quad(o, o + 1, o + 3, o + 2);
            }
        }
        sky.upload();
    }

    private static int skyColor(float t, int zen, int hor) {
        float k = Maths.smoothstep(0f, 1f, t);
        int r = (int) (ShaderLib.r(hor) * (1f - k) + ShaderLib.r(zen) * k);
        int g = (int) (ShaderLib.g(hor) * (1f - k) + ShaderLib.g(zen) * k);
        int b = (int) (ShaderLib.b(hor) * (1f - k) + ShaderLib.b(zen) * k);
        return MeshBuilder.pack(r, g, b, 1f);
    }""",
    """    private String skyBuiltFor;

    /**
     * Le ciel : un gradient CREPUSCULAIRE chaud — la brume claire du niveau a
     * l'horizon, un bleu profond au zenith, et le halo du soleil bas (05.27)
     * cuit dans les couleurs. Il est reconstruit a chaque sequence puisque
     * chaque sequence a sa propre brume.
     */
    private void buildSky(LevelData.SkySettings s) {
        sky.clear();
        float r = 900f;
        int bands = 10;
        int seg = 20;
        int zen = s != null && s.night ? 0xFF101A2E : 0xFF2A3E64;
        int hor = s == null ? Palette.BLEU_BRUME : s.fogColor;
        float yaw = (float) Math.toRadians(s == null ? -35f : s.sunYawDeg);
        float pitch = (float) Math.toRadians(s == null ? 12f : s.sunPitchDeg);
        float sx = -(float) (Math.sin(yaw) * Math.cos(pitch));
        float sy = (float) Math.sin(pitch);
        float sz = -(float) (Math.cos(yaw) * Math.cos(pitch));
        for (int b = 0; b < bands; b++) {
            float t0 = b / (float) bands;
            float t1 = (b + 1) / (float) bands;
            float y0 = -0.28f + t0 * 1.28f;
            float y1 = -0.28f + t1 * 1.28f;
            int base = sky.vertexCount();
            for (int sg = 0; sg <= seg; sg++) {
                float a0 = (float) (sg * Math.PI * 2.0 / seg);
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float h0 = (float) Math.sqrt(Math.max(0f, 1f - y0 * y0));
                float h1 = (float) Math.sqrt(Math.max(0f, 1f - y1 * y1));
                int low = skyColor(t0, zen, hor, c0 * h0, y0, s0 * h0, sx, sy, sz);
                int high = skyColor(t1, zen, hor, c0 * h1, y1, s0 * h1, sx, sy, sz);
                sky.vertex(c0 * h0 * r, y0 * r, s0 * h0 * r, -c0 * h0, -y0, -s0 * h0,
                        ShaderLib.r(low), ShaderLib.g(low), ShaderLib.b(low), 1f, 0f, 0.5f);
                sky.vertex(c0 * h1 * r, y1 * r, s0 * h1 * r, -c0 * h1, -y1, -s0 * h1,
                        ShaderLib.r(high), ShaderLib.g(high), ShaderLib.b(high), 1f, 0f, 0.5f);
            }
            for (int sg = 0; sg < seg; sg++) {
                int o = base + sg * 2;
                sky.quad(o, o + 1, o + 3, o + 2);
            }
        }
        sky.upload();
    }

    private static int skyColor(float t, int zen, int hor,
                                float dx, float dy, float dz,
                                float sx, float sy, float sz) {
        float k = Maths.smoothstep(0f, 1f, t);
        float r = ShaderLib.r(hor) * (1f - k) + ShaderLib.r(zen) * k;
        float g = ShaderLib.g(hor) * (1f - k) + ShaderLib.g(zen) * k;
        float b = ShaderLib.b(hor) * (1f - k) + ShaderLib.b(zen) * k;
        /* le halo : un disque chaud serre + une lueur large autour */
        float d = Math.max(0f, dx * sx + dy * sy + dz * sz);
        float glow = (float) Math.pow(d, 24f) * 1.5f + (float) Math.pow(d, 4f) * 0.30f;
        r += glow;
        g += glow * 0.76f;
        b += glow * 0.46f;
        return MeshBuilder.pack((int) (Math.min(1f, r) * 255f),
                (int) (Math.min(1f, g) * 255f), (int) (Math.min(1f, b) * 255f), 1f);
    }""")

# brume + intensites dans draw()
rep(wr, """        int fog = Palette.fogFor(seq);
        LevelData.SkySettings skySet = d == null ? null : d.sky;""",
    """        LevelData.SkySettings skySet = d == null ? null : d.sky;
        int fog = skySet != null ? skySet.fogColor : Palette.fogFor(seq);
        if (skySet != null && !seq.equals(skyBuiltFor)) {
            skyBuiltFor = seq;
            buildSky(skySet);
        }""")
rep(wr, """        GLES20.glUniform3f(uSunColor, ShaderLib.r(sunColor), ShaderLib.g(sunColor),
                ShaderLib.b(sunColor));
        GLES20.glUniform3f(uAmbientSky, ShaderLib.r(ambSky), ShaderLib.g(ambSky),
                ShaderLib.b(ambSky));
        GLES20.glUniform3f(uAmbientGround, ShaderLib.r(ambGround),
                ShaderLib.g(ambGround), ShaderLib.b(ambGround));
        GLES20.glUniform3f(uFogColor, ShaderLib.r(fog), ShaderLib.g(fog),
                ShaderLib.b(fog));
        GLES20.glUniform1f(uFogDensity, Palette.fogDensityFor(seq));""",
    """        float sunI = (skySet == null ? 1f : skySet.sunIntensity) * 1.05f;
        float ambI = (skySet == null ? 0.5f : skySet.ambientIntensity) * 1.55f;
        float ambGI = (skySet == null ? 0.45f : skySet.ambientIntensity) * 1.9f + 0.10f;
        GLES20.glUniform3f(uSunColor, ShaderLib.r(sunColor) * sunI,
                ShaderLib.g(sunColor) * sunI, ShaderLib.b(sunColor) * sunI);
        GLES20.glUniform3f(uAmbientSky, ShaderLib.r(ambSky) * ambI,
                ShaderLib.g(ambSky) * ambI, ShaderLib.b(ambSky) * ambI);
        GLES20.glUniform3f(uAmbientGround, ShaderLib.r(ambGround) * ambGI,
                ShaderLib.g(ambGround) * ambGI, ShaderLib.b(ambGround) * ambGI);
        GLES20.glUniform3f(uFogColor, ShaderLib.r(fog), ShaderLib.g(fog),
                ShaderLib.b(fog));
        GLES20.glUniform1f(uFogDensity, (skySet != null ? skySet.fogDensity
                : Palette.fogDensityFor(seq)) * 0.8f);""")
rep(wr, """        GLES20.glUniform1f(uExposure, exposureOf(game));""",
    """        GLES20.glUniform1f(uExposure, exposureOf(game)
                * (skySet == null ? 1.06f : skySet.exposure * 1.06f));""")

# ciel trace sans brouillard
rep(wr, """        /* 1. le ciel, sans test de profondeur */
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_CULL_FACE);""",
    """        /* 1. le ciel, sans test de profondeur et sans brume (uFogSkip) */
        GLES20.glUniform1f(uFogSkip, 1f);
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_CULL_FACE);""")
rep(wr, """        drawMesh(sky);
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_CULL_FACE);""",
    """        drawMesh(sky);
        GLES20.glUniform1f(uFogSkip, 0f);
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_CULL_FACE);""")

# ---------------------------------------------------------------- GameRenderer
gr = load("/render/GameRenderer.java")
rep(gr, """        float saturation = Palette.saturationFor(game.sequence());
        if (game.echo.active()) {
            /* 09.15 : l'Echo de la salle de bal monte a 0,70, le pic absolu */
            saturation = game.echo.mode() == 'C' ? 0.30f : 0.70f;
        }""",
    """        float saturation = Palette.saturationFor(game.sequence());
        if (game.level() != null) {
            saturation = game.level().sky.saturation;
        }
        if (game.echo.active()) {
            /* 09.15 : l'Echo de la salle de bal reste le pic absolu */
            saturation = game.echo.mode() == 'C' ? 0.34f : 0.85f;
        }""")
rep(gr, """        float grain = game.options.grain && !game.options.reducedFlashes ? 0.035f : 0f;
        float vignette = game.options.vignette ? 0.20f : 0f;""",
    """        float grain = game.options.grain && !game.options.reducedFlashes ? 0.012f : 0f;
        float vignette = game.options.vignette ? 0.08f : 0f;""")

# ---------------------------------------------------------------- Palette (repli)
pa = load("/render/Palette.java")
rep(pa, """        if ("S1".equals(seq)) return 0.30f;
        if ("S2".equals(seq)) return 0.28f;
        if ("S3".equals(seq)) return 0.55f;
        if ("S4".equals(seq)) return 0.34f;
        if ("S5".equals(seq)) return 0.24f;
        if ("S6".equals(seq)) return 0.42f;
        if ("S7".equals(seq)) return 0.26f;
        if ("S8".equals(seq)) return 0.40f;
        return 0.35f;""",
    """        if ("S1".equals(seq)) return 0.62f;
        if ("S2".equals(seq)) return 0.58f;
        if ("S3".equals(seq)) return 0.75f;
        if ("S4".equals(seq)) return 0.65f;
        if ("S5".equals(seq)) return 0.52f;
        if ("S6".equals(seq)) return 0.72f;
        if ("S7".equals(seq)) return 0.55f;
        if ("S8".equals(seq)) return 0.80f;
        return 0.62f;""")
rep(pa, """        if ("S5".equals(seq)) return 0xFF080C12;      /* conduits : presque noir */
        if ("S6".equals(seq)) return 0xFF0A1018;      /* salle scellee */
        if ("S7".equals(seq)) return 0xFF101A26;      /* pluie battante */
        if ("S8".equals(seq)) return 0xFF2A3A4E;      /* aube : la brume monte */
        if ("S3".equals(seq)) return 0xFF1B2A3C;      /* marche : brume tiede */
        return BLEU_BRUME;""",
    """        if ("S5".equals(seq)) return 0xFF1E2836;      /* conduits : penombre bleue */
        if ("S6".equals(seq)) return 0xFF202C3A;      /* salle scellee */
        if ("S7".equals(seq)) return 0xFF3A4C60;      /* pluie battante */
        if ("S8".equals(seq)) return 0xFF7A8CA2;      /* aube : la brume monte */
        if ("S3".equals(seq)) return 0xFF4A5A6E;      /* marche : brume tiede */
        return 0xFF40536A;""")
rep(pa, """        if ("S5".equals(seq)) return 0.055f;
        if ("S7".equals(seq)) return 0.030f;
        if ("S2".equals(seq)) return 0.016f;
        if ("S8".equals(seq)) return 0.008f;
        return 0.012f;""",
    """        if ("S5".equals(seq)) return 0.040f;
        if ("S7".equals(seq)) return 0.022f;
        if ("S2".equals(seq)) return 0.013f;
        if ("S8".equals(seq)) return 0.006f;
        return 0.010f;""")

for p, store in (("/gl/ShaderLib.java", sl), ("/render/WorldRenderer.java", wr),
                 ("/render/GameRenderer.java", gr), ("/render/Palette.java", pa)):
    open(ROOT + p, "w", encoding="utf-8").write(store[0])

if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("patch rendu ok")
