# -*- coding: utf-8 -*-
"""Patch textures : atlas de matieres triplanar dans le shader SCENE, id de
materiau porte par aParam.x, images de fond (menu, boot, journal, lettre)."""
import sys

ROOT = "/home/user/Space/android/src/java/com/velmora/lohen"
miss = []


def load(p):
    return [open(p if p.startswith("/home") else ROOT + p, encoding="utf-8").read()]


def rep(store, old, new):
    if old not in store[0]:
        miss.append(old.splitlines()[0][:70])
        return
    store[0] = store[0].replace(old, new, 1)


# ---------------------------------------------------------------- ShaderLib
sl = load("/gl/ShaderLib.java")
rep(sl, """    public static final String SCENE_FS = \"\"
            + \"precision mediump float;\\n\"""",
    """    public static final String SCENE_FS = \"\"
            + \"#ifdef GL_FRAGMENT_PRECISION_HIGH\\n\"
            + \"precision highp float;\\n\"
            + \"#else\\n\"
            + \"precision mediump float;\\n\"
            + \"#endif\\n\"""")
rep(sl, """            + \"uniform float uFogSkip;\\n\"        /* 1 = dome du ciel : pas de brume */""",
    """            + \"uniform float uFogSkip;\\n\"        /* 1 = dome du ciel : pas de brume */
            + \"uniform sampler2D uAtlas;\\n\"      /* atlas 4x4 des matieres */
            + \"uniform float uAtlasOn;\\n\"""")
rep(sl, """            + \"  vec3 col = vColor.rgb * (sun + amb);\\n\"""",
    """            + \"  vec3 col = vColor.rgb * (sun + amb);\\n\"
            /* aParam.x porte l'id de materiau : 0..14 = tuile de l'atlas,
               15 = verre de la Maree, param 0 = aplats sans texture */
            + \"  float mid = floor(vParam.x * 16.0 + 0.5) - 1.0;\\n\"
            + \"  if (uAtlasOn > 0.5 && mid > -0.5 && mid < 14.5) {\\n\"
            + \"    vec3 an = abs(n);\\n\"
            + \"    an /= (an.x + an.y + an.z + 1e-4);\\n\"
            + \"    vec2 off = vec2(mod(mid, 4.0), floor(mid * 0.25)) * 0.25;\\n\"
            + \"    float sc = 0.45;\\n\"
            + \"    vec3 tx = texture2D(uAtlas, off + 0.001 + fract(vWorld.zy * sc) * 0.248).rgb * an.x\\n\"
            + \"            + texture2D(uAtlas, off + 0.001 + fract(vWorld.xz * sc) * 0.248).rgb * an.y\\n\"
            + \"            + texture2D(uAtlas, off + 0.001 + fract(vWorld.xy * sc) * 0.248).rgb * an.z;\\n\"
            + \"    col *= 0.55 + 0.95 * tx;\\n\"
            + \"  }\\n\"""")
rep(sl, """            /* le verre de la Maree : reflet rasant, jamais un miroir net */
            + \"  if (vParam.x > 0.5) {\\n\"""",
    """            /* le verre de la Maree : reflet rasant, jamais un miroir net */
            + \"  if (mid > 14.5) {\\n\"""")

# ---------------------------------------------------------------- MeshBuilder : ramp texturee
mb = load("/gl/MeshBuilder.java")
rep(mb, """    public void ramp(float x, float y, float z, float w, float h, float d,
                     float yawDeg, float steps, int argb) {""",
    """    public void ramp(float x, float y, float z, float w, float h, float d,
                     float yawDeg, float steps, int argb) {
        ramp(x, y, z, w, h, d, yawDeg, steps, argb, 0f);
    }

    public void ramp(float x, float y, float z, float w, float h, float d,
                     float yawDeg, float steps, int argb, float texParam) {""")
rep(mb, """            box(x + wx, ly, z + wz, w * 0.5f, rise * 0.5f, run * 0.55f, yawDeg,
                    pack(r * 0.9f, g * 0.9f, b * 0.9f, a), 0f, 0f);""",
    """            box(x + wx, ly, z + wz, w * 0.5f, rise * 0.5f, run * 0.55f, yawDeg,
                    pack(r * 0.9f, g * 0.9f, b * 0.9f, a), texParam, 0f);""")

# ---------------------------------------------------------------- WorldRenderer
wr = load("/render/WorldRenderer.java")
rep(wr, """    private int uMvp, uModel, uCamPos, uSunDir, uSunColor, uAmbientSky,
            uAmbientGround, uFogColor, uFogDensity, uFogVisibility, uLampPos,
            uFogSkip,""",
    """    private int uMvp, uModel, uCamPos, uSunDir, uSunColor, uAmbientSky,
            uAmbientGround, uFogColor, uFogDensity, uFogVisibility, uLampPos,
            uFogSkip, uAtlas, uAtlasOn,""")
rep(wr, """        uFogSkip = GlUtil.uniform(program, "uFogSkip");""",
    """        uFogSkip = GlUtil.uniform(program, "uFogSkip");
        uAtlas = GlUtil.uniform(program, "uAtlas");
        uAtlasOn = GlUtil.uniform(program, "uAtlasOn");""")
rep(wr, """    private final MeshBuilder sky = new MeshBuilder(64, 96);""",
    """    private final MeshBuilder sky = new MeshBuilder(64, 96);
    private TextureLib textures;

    public void setTextures(TextureLib textures) {
        this.textures = textures;
    }""")

# lier l'atlas de la sequence avant le trace
rep(wr, """        GLES20.glUniform1f(uExposure, exposureOf(game)
                * (skySet == null ? 1.06f : skySet.exposure * 1.06f));""",
    """        GLES20.glUniform1f(uExposure, exposureOf(game)
                * (skySet == null ? 1.06f : skySet.exposure * 1.06f));

        /* l'atlas de matieres de la sequence (images generees, tex/) */
        int atlasTex = textures == null || seq == null ? 0 : textures.get(atlasFor(seq));
        GLES20.glUniform1i(uAtlas, 0);
        GLES20.glUniform1f(uAtlasOn, atlasTex != 0 ? 1f : 0f);
        if (atlasTex != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex);
        }""")
rep(wr, """    private static float exposureOf(LohenGame game) {""",
    """    /** Quatre atmospheres de matieres : base, marche chaud, conduits, aube. */
    public static String atlasFor(String seq) {
        if ("S3".equals(seq)) {
            return "content/tex/atlas_chaud.png";
        }
        if ("S5".equals(seq)) {
            return "content/tex/atlas_sombre.png";
        }
        if ("S8".equals(seq)) {
            return "content/tex/atlas_aube.png";
        }
        return "content/tex/atlas_base.png";
    }

    /* Tuiles de l'atlas (voir tools/make_textures.py, meme ordre) :
     * 0 pierre seche · 1 pierre humide · 2 brique · 3 platre · 4 calcaire
     * 5 bois · 6 bois goudronne · 7 metal · 8 gravier · 9 tapis · 10 eau
     * 11 papier · 12 pierre moussue · 13 metal rouille · 14 nuage · 15 verre */
    private static float texParam(int mat, boolean glass, int seed) {
        if (glass) {
            return 1f;                       /* id 15 : verre, reflet rasant */
        }
        int tile;
        switch (mat) {
            case Geom.MAT_WOOD:     tile = seed % 5 == 4 ? 6 : 5; break;
            case Geom.MAT_WOOD_WET: tile = 6; break;
            case Geom.MAT_STONE:
                tile = seed % 11 == 7 ? 12 : (seed % 3 == 1 ? 4
                        : (seed % 3 == 2 ? 3 : 0));
                break;
            case Geom.MAT_STONE_WET: tile = seed % 7 == 3 ? 12 : 1; break;
            case Geom.MAT_GRAVEL:   tile = seed % 7 == 3 ? 12 : 8; break;
            case Geom.MAT_GLASS:    tile = 10; break;
            case Geom.MAT_METAL:    tile = seed % 4 == 3 ? 13 : 7; break;
            case Geom.MAT_CARPET:   tile = 9; break;
            case Geom.MAT_WATER:    tile = 10; break;
            default:                tile = 0; break;
        }
        return (tile + 1) / 16f;
    }

    private static float exposureOf(LohenGame game) {""")

# solides : id de materiau au lieu du simple drapeau verre
rep(wr, """            level.box(d.solidX(i), d.solidY(i), d.solidZ(i), d.solidHX(i),
                    d.solidHY(i), d.solidHZ(i), d.solidYaw(i), base,
                    glass ? 1f : 0f, emit);""",
    """            level.box(d.solidX(i), d.solidY(i), d.solidZ(i), d.solidHX(i),
                    d.solidHY(i), d.solidHZ(i), d.solidYaw(i), base,
                    texParam(mat, glass, i), emit);""")
rep(wr, """            level.ramp(d.stairX(i), d.stairY(i), d.stairZ(i), d.stairW(i),
                    d.stairH(i), d.stairD(i), d.stairYaw(i), d.stairSteps(i),
                    Palette.material(d.stairMaterial(i)));""",
    """            level.ramp(d.stairX(i), d.stairY(i), d.stairZ(i), d.stairW(i),
                    d.stairH(i), d.stairD(i), d.stairYaw(i), d.stairSteps(i),
                    Palette.material(d.stairMaterial(i)),
                    texParam(d.stairMaterial(i), false, i + 3));""")
rep(wr, """            level.box(x, y, z, len * 0.5f, 0.06f, 0.14f, yaw,
                    Palette.interactiveTint(Palette.METAL), 0f, 0f);""",
    """            level.box(x, y, z, len * 0.5f, 0.06f, 0.14f, yaw,
                    Palette.interactiveTint(Palette.METAL), 8f / 16f, 0f);""")
# ciel : tuile 14 (nuage)
rep(wr, """                sky.vertex(c0 * h0 * r, y0 * r, s0 * h0 * r, -c0 * h0, -y0, -s0 * h0,
                        ShaderLib.r(low), ShaderLib.g(low), ShaderLib.b(low), 1f, 0f, 0.5f);
                sky.vertex(c0 * h1 * r, y1 * r, s0 * h1 * r, -c0 * h1, -y1, -s0 * h1,
                        ShaderLib.r(high), ShaderLib.g(high), ShaderLib.b(high), 1f, 0f, 0.5f);""",
    """                sky.vertex(c0 * h0 * r, y0 * r, s0 * h0 * r, -c0 * h0, -y0, -s0 * h0,
                        ShaderLib.r(low), ShaderLib.g(low), ShaderLib.b(low), 1f,
                        15f / 16f, 0.5f);
                sky.vertex(c0 * h1 * r, y1 * r, s0 * h1 * r, -c0 * h1, -y1, -s0 * h1,
                        ShaderLib.r(high), ShaderLib.g(high), ShaderLib.b(high), 1f,
                        15f / 16f, 0.5f);""")

# ---------------------------------------------------------------- GameRenderer
gr = load("/render/GameRenderer.java")
rep(gr, """import com.velmora.lohen.gl.PostProcess;
import com.velmora.lohen.gl.TextAtlas;""",
    """import com.velmora.lohen.gl.PostProcess;
import com.velmora.lohen.gl.TextAtlas;
import com.velmora.lohen.gl.TextureLib;""")
rep(gr, """    private final PostProcess post = new PostProcess();""",
    """    private final PostProcess post = new PostProcess();
    private final TextureLib textures = new TextureLib();""")
rep(gr, """    public void setAssets(android.content.res.AssetManager assets) {
        atlasReady = atlas.load(assets);
    }""",
    """    public void setAssets(android.content.res.AssetManager assets) {
        atlasReady = atlas.load(assets);
        textures.setAssets(assets);
        hud.setTextures(textures);
        world.setTextures(textures);
    }""")

# ---------------------------------------------------------------- HudRenderer : images
hd = load("/render/HudRenderer.java")
rep(hd, """    private int uTex, uTextured, uOpacity, uResolution;""",
    """    private int uTex, uTextured, uOpacity, uResolution;
    private TextureLib textures;

    public void setTextures(TextureLib textures) {
        this.textures = textures;
    }

    /**
     * Une VRAIE image dans le jeu (retour joueur) : fond peint pour le menu,
     * l'accueil, le journal et le papier de la lettre. Vide d'abord les quads
     * en attente, puis trace l'image avec sa propre texture.
     */
    public void imageQuad(int w, int h, String path, float x0, float y0,
                          float x1, float y1, int argb) {
        if (textures == null || batch == null) {
            return;
        }
        int tex = textures.getHQ(path);
        if (tex == 0) {
            return;
        }
        flush(w, h, true);
        batch.beginFrame();
        batch.clear();
        batch.quad(x0, y0, x1, y1, 0f, 0f, 1f, 1f, argb);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, w, h);
        GLES20.glUniform1f(uOpacity, 1f);
        GLES20.glUniform1f(uTextured, 1f);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        batch.draw(program, aPos, aUv, aColor, w, h);
        GLES20.glDisable(GLES20.GL_BLEND);
    }""")
rep(hd, """    private void drawMainMenu(LohenGame game, MenuModel menu, int w, int h) {
        rect(0, 0, w, h, Palette.ECRAN_NUIT, 0.55f);""",
    """    private void drawMainMenu(LohenGame game, MenuModel menu, int w, int h) {
        imageQuad(w, h, "content/tex/menu.png", 0f, 0f, w, h, 0xFFFFFFFF);
        rect(0, 0, w, h, Palette.ECRAN_NUIT, 0.38f);""")
rep(hd, """        batch.beginFrame();
        batch.clear();
        rect(0, 0, w, h, 0xFF04060A, 1f);
        if (atlas != null) {
            /* le titre, en petites capitales, sans aucune decoration */""",
    """        batch.beginFrame();
        batch.clear();
        imageQuad(w, h, "content/tex/boot.png", 0f, 0f, w, h, 0xFFFFFFFF);
        rect(0, 0, w, h, 0xFF04060A, 0.55f);
        if (atlas != null) {
            /* le titre, en petites capitales, sans aucune decoration */""")
rep(hd, """        rect(0, 0, w, h, Palette.ENCRE, 0.94f);""",
    """        imageQuad(w, h, "content/tex/journal.png", 0f, 0f, w, h, 0xFFFFFFFF);
        rect(0, 0, w, h, Palette.ENCRE, 0.62f);""")
rep(hd, """            rect(px, py, px + paperW, py + paperH, Palette.PAPIER, 0.98f);""",
    """            imageQuad(w, h, "content/tex/lettre.png", px, py, px + paperW,
                    py + paperH, 0xFFFFFFFF);
            rect(px, py, px + paperW, py + paperH, Palette.PAPIER, 0.20f);""")

for p, store in (("/gl/ShaderLib.java", sl), ("/gl/MeshBuilder.java", mb),
                 ("/render/WorldRenderer.java", wr), ("/render/GameRenderer.java", gr),
                 ("/render/HudRenderer.java", hd)):
    open(ROOT + p, "w", encoding="utf-8").write(store[0])

if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("patch textures ok")
