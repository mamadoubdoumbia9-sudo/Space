/*
 * LOHEN — render/WorldRenderer.java
 *
 * Dessine le monde : la geometrie du niveau generee depuis `level_sN.json`,
 * la Maree de verre, le Phare et son faisceau, les 147 objets narratifs, les
 * personnages (Lohen et son gréement de 19 os, les 34 habitants du marche, les
 * deux Figures et le Verrier), la sphere de revelation d'un Echo.
 *
 * Tout est genere au chargement, rien n'est telecharge : les 774 solides du
 * chapitre tiennent dans un seul VBO par sequence (02.10 : deux cellules
 * actives, 4 ms de tranche de streaming).
 *
 * 05.01 [OBL] : aucun contour, aucune surbrillance, aucune fleche. Un objet
 * interactif est simplement plus humide que son voisin.
 */
package com.velmora.lohen.render;

import android.opengl.GLES20;

import com.velmora.lohen.gl.GlUtil;
import com.velmora.lohen.gl.MeshBuilder;
import com.velmora.lohen.gl.ShaderLib;
import com.velmora.lohen.sim.ai.Echassier;
import com.velmora.lohen.sim.ai.Figure;
import com.velmora.lohen.sim.ai.Mueur;
import com.velmora.lohen.sim.ai.VerrierBoss;
import com.velmora.lohen.sim.anim.CharacterRig;
import com.velmora.lohen.sim.core.ContentDb;
import com.velmora.lohen.sim.core.LevelData;
import com.velmora.lohen.sim.core.LohenGame;
import com.velmora.lohen.sim.math.Geom;
import com.velmora.lohen.sim.math.Mat4;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.player.Lohen;
import com.velmora.lohen.sim.world.Lighthouse;
import com.velmora.lohen.sim.world.Npc;

import java.util.List;

public final class WorldRenderer {

    private int program;
    private int aPos, aNormal, aColor, aParam;
    private int uMvp, uModel, uCamPos, uSunDir, uSunColor, uAmbientSky,
            uAmbientGround, uFogColor, uFogDensity, uFogVisibility, uLampPos,
            uFogSkip,
            uLampColor, uLampRange, uBeamPos, uBeamDir, uBeamStrength,
            uExposure, uTime;

    private final MeshBuilder level = new MeshBuilder(60000, 90000);
    private final MeshBuilder dynamic = new MeshBuilder(9000, 14000);
    private final MeshBuilder sky = new MeshBuilder(64, 96);

    private String builtFor = "";
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];
    private final float[] viewProj = new float[16];

    public void init() {
        program = GlUtil.program(ShaderLib.SCENE_VS, ShaderLib.SCENE_FS);
        aPos = GlUtil.attrib(program, "aPos");
        aNormal = GlUtil.attrib(program, "aNormal");
        aColor = GlUtil.attrib(program, "aColor");
        aParam = GlUtil.attrib(program, "aParam");
        uMvp = GlUtil.uniform(program, "uMvp");
        uModel = GlUtil.uniform(program, "uModel");
        uCamPos = GlUtil.uniform(program, "uCamPos");
        uSunDir = GlUtil.uniform(program, "uSunDir");
        uSunColor = GlUtil.uniform(program, "uSunColor");
        uAmbientSky = GlUtil.uniform(program, "uAmbientSky");
        uAmbientGround = GlUtil.uniform(program, "uAmbientGround");
        uFogColor = GlUtil.uniform(program, "uFogColor");
        uFogDensity = GlUtil.uniform(program, "uFogDensity");
        uFogVisibility = GlUtil.uniform(program, "uFogVisibility");
        uLampPos = GlUtil.uniform(program, "uLampPos");
        uLampColor = GlUtil.uniform(program, "uLampColor");
        uLampRange = GlUtil.uniform(program, "uLampRange");
        uBeamPos = GlUtil.uniform(program, "uBeamPos");
        uBeamDir = GlUtil.uniform(program, "uBeamDir");
        uBeamStrength = GlUtil.uniform(program, "uBeamStrength");
        uExposure = GlUtil.uniform(program, "uExposure");
        uFogSkip = GlUtil.uniform(program, "uFogSkip");
        uTime = GlUtil.uniform(program, "uTime");
        skyBuiltFor = null;
        buildSky(null);
    }

    /* ------------------------------------------------------------------ */
    /* Ciel                                                                */
    /* ------------------------------------------------------------------ */

    private String skyBuiltFor;

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
    }

    /* ------------------------------------------------------------------ */
    /* Geometrie du niveau                                                 */
    /* ------------------------------------------------------------------ */

    /** Reconstruit le VBO du niveau quand la sequence change. */
    public void buildLevel(LohenGame game) {
        LevelData d = game.level();
        String seq = game.sequence();
        if (d == null || seq == null || seq.equals(builtFor)) {
            return;
        }
        level.release();
        level.clear();
        builtFor = seq;

        /* 1. les solides : quais, passerelles, murs, toits, conduits */
        for (int i = 0; i < d.solidCount; i++) {
            int mat = d.solidMaterial(i);
            int flags = d.solidFlags(i);
            int base = Palette.material(mat);
            boolean glass = (flags & Geom.FLAG_GLASS) != 0;
            if ((flags & Geom.FLAG_INTERACTIVE) != 0) {
                base = Palette.interactiveTint(base);      /* 05.01 : humidite */
            }
            float emit = 0f;
            if ((flags & Geom.FLAG_HEAT_WELL) != 0) {
                emit = 0.10f;                              /* 09.11 : le verre ramollit */
            }
            if ((flags & Geom.FLAG_LAMP_ROOM) != 0) {
                emit = 0.05f;
            }
            level.box(d.solidX(i), d.solidY(i), d.solidZ(i), d.solidHX(i),
                    d.solidHY(i), d.solidHZ(i), d.solidYaw(i), base,
                    glass ? 1f : 0f, emit);
        }

        /* 2. les escaliers : le kit K3, le plus utilise du jeu (09.41) */
        for (int i = 0; i < d.stairCount; i++) {
            level.ramp(d.stairX(i), d.stairY(i), d.stairZ(i), d.stairW(i),
                    d.stairH(i), d.stairD(i), d.stairYaw(i), d.stairSteps(i),
                    Palette.material(d.stairMaterial(i)));
        }

        /* 3. les prises : une barre de 12 cm, jamais une poignee jaune */
        for (int i = 0; i < d.ledgeCount; i++) {
            float x = d.ledgeData[i * 6];
            float y = d.ledgeData[i * 6 + 1];
            float z = d.ledgeData[i * 6 + 2];
            float len = d.ledgeData[i * 6 + 3];
            float yaw = d.ledgeData[i * 6 + 4];
            level.box(x, y, z, len * 0.5f, 0.06f, 0.14f, yaw,
                    Palette.interactiveTint(Palette.METAL), 0f, 0f);
        }

        /* 4. les ancres de harpon : un anneau de relayeur (08.08) */
        for (int i = 0; i < d.anchorCount; i++) {
            float x = d.anchorData[i * 4];
            float y = d.anchorData[i * 4 + 1];
            float z = d.anchorData[i * 4 + 2];
            level.ring(x, y, z, 0.34f, Palette.METAL, 0.04f);
        }

        /* 5. les 147 objets narratifs, places un par un (BLOC 06) */
        ContentDb db = game.db;
        for (int i = 0; i < d.propCount; i++) {
            float x = d.propData[i * 5];
            float y = d.propData[i * 5 + 1];
            float z = d.propData[i * 5 + 2];
            float yaw = d.propData[i * 5 + 3];
            float scale = d.propData[i * 5 + 4];
            ContentDb.PropRecord p = db == null ? null : db.prop(d.propIds[i]);
            int color = propColor(p);
            float[] size = propSize(p, scale);
            float emit = 0f;
            if (p != null && p.echoId != null && p.echoId.length() > 0) {
                /* un objet a Echo n'est JAMAIS surligne : il est simplement
                 * plus humide, et c'est le joueur qui le remarque (05.01). */
                color = Palette.interactiveTint(color);
                emit = 0.02f;
            }
            level.box(x, y + size[1] * 0.5f, z, size[0] * 0.5f, size[1] * 0.5f,
                    size[2] * 0.5f, yaw, color, 0f, emit);
        }

        /* 6. la Maree de verre, quand la sequence la borde (09.10) */
        if ("S1".equals(seq) || "S2".equals(seq) || "S7".equals(seq)
                || "S8".equals(seq)) {
            level.plane(0f, 0.02f, 0f, 420f, 420f, Palette.NOIR_VERRE, 1f, 0f);
        }

        /* 7. le Phare : 212 m, visible de partout (09.31) */
        level.cylinder(0f, 0f, -430f, 196f, 7.4f, 20, Palette.PIERRE_HUMIDE, 0f);
        level.cylinder(0f, 196f, -430f, 206f, 5.2f, 16, Palette.CALCAIRE, 0f);
        level.box(0f, 208f, -430f, 3.4f, 2.0f, 3.4f, 0f, Palette.LANTERNE_2700K,
                0f, 0.55f);

        level.upload();
    }

    private static int propColor(ContentDb.PropRecord p) {
        if (p == null) {
            return Palette.PIERRE_HUMIDE;
        }
        String a = p.asset == null ? "" : p.asset.toLowerCase();
        if (a.contains("letter") || a.contains("lettre") || a.contains("paper")
                || a.contains("envelope")) {
            return Palette.PAPIER;
        }
        if (a.contains("lantern") || a.contains("lampe") || a.contains("lamp")) {
            return Palette.LANTERNE_2700K;
        }
        if (a.contains("glass") || a.contains("verre") || a.contains("bottle")) {
            return 0xFF22303C;
        }
        if (a.contains("wood") || a.contains("bois") || a.contains("crate")
                || a.contains("bench")) {
            return Palette.BOIS_GOUDRON;
        }
        if (a.contains("metal") || a.contains("winch") || a.contains("rail")
                || a.contains("cleat")) {
            return Palette.METAL;
        }
        if (a.contains("cloth") || a.contains("linge") || a.contains("coat")
                || a.contains("vest")) {
            return 0xFF59503F;
        }
        if (a.contains("brick") || a.contains("brique") || a.contains("roof")) {
            return Palette.BRIQUE;
        }
        return Palette.PIERRE_HUMIDE;
    }

    private static float[] propSize(ContentDb.PropRecord p, float scale) {
        float s = scale <= 0.01f ? 1f : scale;
        if (p != null && p.climbable) {
            return new float[]{1.2f * s, 2.2f * s, 1.2f * s};
        }
        if (p != null && p.pickup) {
            return new float[]{0.36f * s, 0.30f * s, 0.36f * s};
        }
        if (p != null && p.readable) {
            return new float[]{0.30f * s, 0.04f * s, 0.42f * s};
        }
        return new float[]{0.7f * s, 0.7f * s, 0.7f * s};
    }

    public String builtFor() {
        return builtFor;
    }

    public int levelVertices() {
        return level.vertexCount();
    }

    public int levelTriangles() {
        return level.indexCount() / 3;
    }

    /* ------------------------------------------------------------------ */
    /* Tracé                                                               */
    /* ------------------------------------------------------------------ */

    public void draw(LohenGame game, float[] view, float[] proj, float time) {
        if (program == 0) {
            return;
        }
        buildLevel(game);
        Mat4.multiply(proj, view, viewProj);

        GLES20.glUseProgram(program);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glDisable(GLES20.GL_BLEND);

        LevelData d = game.level();
        String seq = game.sequence();
        LevelData.SkySettings skySet = d == null ? null : d.sky;
        int fog = skySet != null ? skySet.fogColor : Palette.fogFor(seq);
        if (skySet != null && !seq.equals(skyBuiltFor)) {
            skyBuiltFor = seq;
            buildSky(skySet);
        }

        float[] sunDir = {0.42f, -0.28f, 0.86f};
        if (skySet != null) {
            float yaw = (float) Math.toRadians(skySet.sunYawDeg);
            float pitch = (float) Math.toRadians(skySet.sunPitchDeg);
            sunDir[0] = (float) (Math.sin(yaw) * Math.cos(pitch));
            sunDir[1] = (float) -Math.sin(pitch);
            sunDir[2] = (float) (Math.cos(yaw) * Math.cos(pitch));
        }
        int sunColor = skySet == null ? Palette.SOLEIL_5200K : skySet.sunColor;
        int ambSky = skySet == null ? Palette.CIEL_7800K : skySet.ambientSky;
        int ambGround = skySet == null ? Palette.PIERRE_HUMIDE : skySet.ambientGround;

        com.velmora.lohen.sim.math.Vec3 cam = game.cameraPosition();
        GLES20.glUniform3f(uCamPos, cam.x, cam.y, cam.z);
        GLES20.glUniform3f(uSunDir, sunDir[0], sunDir[1], sunDir[2]);
        float sunI = (skySet == null ? 1f : skySet.sunIntensity) * 1.05f;
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
                : Palette.fogDensityFor(seq)) * 0.8f);
        GLES20.glUniform1f(uFogVisibility, Palette.fogVisibilityFor(seq));
        GLES20.glUniform1f(uTime, time);
        GLES20.glUniform1f(uExposure, exposureOf(game)
                * (skySet == null ? 1.06f : skySet.exposure * 1.06f));

        /* la lampe portee (08.11) : 2700 K, 4 m, elle repousse les Mueurs */
        float lampRange = 0f;
        float lx = game.lohen.x, ly = game.lohen.y + 1.1f, lz = game.lohen.z;
        if (game.carry.carrying() && "lantern".equals(game.carry.carriedId())) {
            lampRange = game.carry.repelRadius();
        } else if (game.carry.nearestLightDistance(game.lohen) < 12f) {
            lampRange = 6f;
        }
        GLES20.glUniform3f(uLampPos, lx, ly, lz);
        GLES20.glUniform3f(uLampColor, ShaderLib.r(Palette.LANTERNE_2700K),
                ShaderLib.g(Palette.LANTERNE_2700K), ShaderLib.b(Palette.LANTERNE_2700K));
        GLES20.glUniform1f(uLampRange, lampRange);

        /* le faisceau du Phare : 900 m, balayage de 20 s (09.31) */
        Lighthouse phare = game.lighthouse;
        float sweep = phare.angleDeg();
        float rad = (float) Math.toRadians(sweep);
        GLES20.glUniform3f(uBeamPos, phare.x(), 208f, phare.z());
        GLES20.glUniform3f(uBeamDir, (float) Math.sin(rad), -0.06f, (float) Math.cos(rad));
        GLES20.glUniform1f(uBeamStrength, phare.beamCovers(game.lohen.x, game.lohen.y,
                game.lohen.z) ? 1f : 0.55f);

        /* 1. le ciel, sans test de profondeur et sans brume (uFogSkip) */
        GLES20.glUniform1f(uFogSkip, 1f);
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        Mat4.identity(model);
        model[12] = cam.x;
        model[13] = cam.y;
        model[14] = cam.z;
        Mat4.multiply(viewProj, model, mvp);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        drawMesh(sky);
        GLES20.glUniform1f(uFogSkip, 0f);
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        /* 2. le niveau */
        Mat4.identity(model);
        Mat4.multiply(viewProj, model, mvp);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        drawMesh(level);

        /* 3. ce qui bouge : personnages, faisceau, sphere d'Echo, cable */
        buildDynamic(game, time);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        drawMesh(dynamic);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_BLEND);
        MeshBuilder.unbind(aPos, aNormal, aColor, aParam);
    }

    private static float exposureOf(LohenGame game) {
        float ev = 1f;
        /* 09.31 : +0,4 EV quand le faisceau passe sur le joueur */
        if (game.lighthouse.beamCovers(game.lohen.x, game.lohen.y, game.lohen.z)) {
            ev += 0.4f;
        }
        ev += game.lighthouse.dawnEv();
        if (game.echo.active()) {
            ev *= 0.92f;
        }
        return ev;
    }

    private void drawMesh(MeshBuilder m) {
        if (!m.uploaded()) {
            return;
        }
        m.bind(aPos, aNormal, aColor, aParam);
        m.draw();
    }

    /* ------------------------------------------------------------------ */
    /* Dynamique : personnages, effets                                     */
    /* ------------------------------------------------------------------ */

    private void buildDynamic(LohenGame game, float time) {
        dynamic.clear();

        /* le faisceau du Phare : un cone de lumiere, jamais un projecteur net */
        Lighthouse phare = game.lighthouse;
        float rad = (float) Math.toRadians(phare.angleDeg());
        float dx = (float) Math.sin(rad);
        float dz = (float) Math.cos(rad);
        beamCone(phare.x(), 208f, phare.z(), dx, -0.06f, dz, 900f, 0.055f,
                0xFFFFE9C4, 0.10f);

        /* Lohen, depuis les 19 os du gréement (07.02, 02.07 : 1,74 m) */
        drawRig(game.rig, Palette.LOHEN_COAT, Palette.LOHEN_TROUSER,
                Palette.LOHEN_SKIN, game.lohen.crouch ? 0.94f : 1f);

        /* la lanterne portee, si elle est dans sa main */
        if (game.carry.carrying()) {
            float hx = game.rig.boneX(CharacterRig.HAND_R);
            float hy = game.rig.boneY(CharacterRig.HAND_R);
            float hz = game.rig.boneZ(CharacterRig.HAND_R);
            dynamic.box(hx, hy - 0.16f, hz, 0.10f, 0.14f, 0.10f, 0f,
                    Palette.LANTERNE_2700K, 0f, 0.85f);
            /* l'objet porte : caisse, dossier, corps (08.11) */
            String id = game.carry.carriedId();
            if (!"lantern".equals(id)) {
                float[] s = carriedSize(id);
                dynamic.box(hx, hy - 0.05f, hz, s[0], s[1], s[2],
                        game.lohen.yaw, carriedColor(id), 0f, 0f);
            }
        }

        /* le cable du harpon (08.08) */
        if (game.grapple.active()) {
            dynamic.beam(game.lohen.x, game.lohen.y + 1.2f, game.lohen.z,
                    game.grapple.hookX(), game.grapple.hookY(),
                    game.grapple.hookZ(), 0.022f, 0xFF2B2F33, 0f);
        }

        /* les habitants (34 au marche suspendu, 09.52) */
        List<Npc> people = game.npcs.npcs();
        for (int i = 0; i < people.size(); i++) {
            Npc n = people.get(i);
            if (!n.visible) {
                continue;
            }
            drawPerson(n.x, n.y, n.z, n.yaw, Palette.npcTint(n.archetype, i),
                    1.62f + (i % 5) * 0.03f, time + i * 1.7f, n.currentActivity());
        }

        /* les Figures : le cyan, et rien d'autre (05.07) */
        List<Figure> figs = game.figures;
        for (int i = 0; i < figs.size(); i++) {
            Figure f = figs.get(i);
            if (!f.alive()) {
                continue;
            }
            if (f instanceof Echassier) {
                drawEchassier((Echassier) f, time);
            } else if (f instanceof Mueur) {
                drawMueur((Mueur) f, time);
            } else if (f instanceof VerrierBoss) {
                drawVerrier((VerrierBoss) f, time);
            }
        }

        /* la sphere de revelation d'un Echo (11.05 : 3,5 m/s, 18 m max) */
        if (game.echo.active()) {
            float r = game.echo.revealRadius();
            if (r > 0.05f) {
                revealSphere(game.lohen.x, game.lohen.y + 1.1f, game.lohen.z, r,
                        game.echo.mode() == 'C' ? Palette.CYAN_FIGURE
                                : Palette.AMBRE_ESTEBAN,
                        game.echo.mode() == 'C' ? 0.16f : 0.10f);
            }
        }

        dynamic.uploadDynamic();
    }

    private static float[] carriedSize(String id) {
        if (id == null) {
            return new float[]{0.18f, 0.14f, 0.18f};
        }
        if (id.contains("chest") || id.contains("coffre")) {
            return new float[]{0.30f, 0.22f, 0.24f};
        }
        if (id.contains("body") || id.contains("corps")) {
            return new float[]{0.24f, 0.30f, 0.24f};
        }
        if (id.contains("dossier") || id.contains("folder")) {
            return new float[]{0.17f, 0.22f, 0.03f};
        }
        if (id.contains("letter") || id.contains("lettre")) {
            return new float[]{0.11f, 0.15f, 0.01f};
        }
        if (id.contains("lever") || id.contains("levier")) {
            return new float[]{0.05f, 0.42f, 0.05f};
        }
        return new float[]{0.18f, 0.14f, 0.18f};
    }

    private static int carriedColor(String id) {
        if (id == null) {
            return Palette.BOIS_GOUDRON;
        }
        if (id.contains("letter") || id.contains("lettre") || id.contains("dossier")) {
            return Palette.PAPIER;
        }
        if (id.contains("body") || id.contains("corps")) {
            return 0xFF3A3F44;
        }
        if (id.contains("lever") || id.contains("levier")) {
            return Palette.METAL;
        }
        return Palette.BOIS_GOUDRON;
    }

    /**
     * Un corps humain simple : bassin, torse, tete, deux bras, deux jambes.
     * Assez pour lire une silhouette a 3,4 m de camera (08.23), pas assez
     * pour tricher sur l'animation — c'est le gréement qui decide des
     * positions, jamais le rendu.
     */
    private void drawPerson(float x, float y, float z, float yaw, int cloth,
                            float height, float phase, String activity) {
        float yawR = yaw;
        float cs = (float) Math.cos(yawR);
        float sn = (float) Math.sin(yawR);
        boolean walking = activity != null && (activity.contains("walk")
                || activity.contains("marche") || activity.contains("carry"));
        float swing = walking ? (float) Math.sin(phase * 2.2f) * 0.34f : 0f;
        float bob = walking ? (float) Math.abs(Math.sin(phase * 2.2f)) * 0.03f : 0f;
        float hip = height * 0.50f + bob;
        float torso = height * 0.30f;
        float head = height * 0.12f;
        int skin = Palette.LOHEN_SKIN;
        /* jambes */
        limb(x, y, z, cs, sn, -0.09f, swing, hip, 0.062f, 0.048f,
                Palette.LOHEN_TROUSER);
        limb(x, y, z, cs, sn, 0.09f, -swing, hip, 0.062f, 0.048f,
                Palette.LOHEN_TROUSER);
        /* torse : capsule, plus de boite */
        dynamic.capsule(x, y + hip, z, x, y + hip + torso, z, 0.155f, 0.115f,
                cloth, 0f, 7);
        /* bras */
        limb(x, y + hip + torso * 0.9f, z, cs, sn, -0.22f, -swing * 0.8f,
                height * 0.26f, 0.050f, 0.038f, cloth);
        limb(x, y + hip + torso * 0.9f, z, cs, sn, 0.22f, swing * 0.8f,
                height * 0.26f, 0.050f, 0.038f, cloth);
        /* tete : ellipsoide */
        dynamic.ellipsoid(x, y + hip + torso + head * 0.6f, z, 0.085f,
                head * 0.55f, 0.09f, skin, 0f, 8, 5, -1.5708f, 1.5708f);
    }

    private void limb(float x, float y, float z, float cs, float sn, float side,
                      float swing, float len, float thick, float thick2,
                      int color) {
        float ox = x + side * cs;
        float oz = z - side * sn;
        float ex = ox + swing * cs * len * 0.6f;
        float ez = oz - swing * sn * len * 0.6f;
        float ey = y - len * (1f - Math.abs(swing) * 0.18f);
        dynamic.capsule(ox, y, oz, ex, Math.max(ey, y - len), ez, thick, thick2,
                color, 0f, 6);
    }

    /** Lohen : le gréement de 19 os donne les positions, on ne les reinvente pas. */
    private void drawRig(CharacterRig rig, int coat, int trousers, int skin,
                         float crouchScale) {
        float[] b = rig.bones();
        float k = crouchScale;
        float pelvisY = b[CharacterRig.PELVIS * 3 + 1];
        float[] p = new float[b.length];
        for (int bone = 0; bone < CharacterRig.BONE_COUNT; bone++) {
            p[bone * 3] = b[bone * 3];
            p[bone * 3 + 1] = pelvisY + (b[bone * 3 + 1] - pelvisY) * k;
            p[bone * 3 + 2] = b[bone * 3 + 2];
        }
        int coatDark = shade(coat, 0.8f);
        int boots = Palette.LOHEN_GLOVES;
        int hair = 0xFF3A2C20;
        int pel = CharacterRig.PELVIS * 3;
        /* jambes de pantalon, fuselees */
        capsuleB(p, CharacterRig.THIGH_L, CharacterRig.SHIN_L, 0.075f, 0.058f, trousers);
        capsuleB(p, CharacterRig.SHIN_L, CharacterRig.FOOT_L, 0.058f, 0.047f, trousers);
        capsuleB(p, CharacterRig.THIGH_R, CharacterRig.SHIN_R, 0.075f, 0.058f, trousers);
        capsuleB(p, CharacterRig.SHIN_R, CharacterRig.FOOT_R, 0.058f, 0.047f, trousers);
        sphereB(p, CharacterRig.FOOT_L, 0.055f, 0.045f, 0.10f, boots);
        sphereB(p, CharacterRig.FOOT_R, 0.055f, 0.045f, 0.10f, boots);
        /* pan du manteau : il s'evase du bassin a mi-cuisse */
        dynamic.capsule(p[pel], p[pel + 1], p[pel + 2],
                p[pel], p[pel + 1] - 0.34f * k, p[pel + 2],
                0.145f, 0.20f, coatDark, 0f, 8);
        /* torse : deux segments de manteau, epaules aux hanches */
        capsuleB(p, CharacterRig.PELVIS, CharacterRig.SPINE2, 0.150f, 0.135f, coat);
        capsuleB(p, CharacterRig.SPINE2, CharacterRig.NECK, 0.135f, 0.100f, coat);
        sphereB(p, CharacterRig.SHOULDER_L, 0.065f, 0.060f, 0.065f, coat);
        sphereB(p, CharacterRig.SHOULDER_R, 0.065f, 0.060f, 0.065f, coat);
        /* bras : manche de manteau puis main nue */
        capsuleB(p, CharacterRig.SHOULDER_L, CharacterRig.UPPER_ARM_L, 0.055f, 0.050f, coat);
        capsuleB(p, CharacterRig.UPPER_ARM_L, CharacterRig.FOREARM_L, 0.050f, 0.043f, coat);
        capsuleB(p, CharacterRig.FOREARM_L, CharacterRig.HAND_L, 0.042f, 0.036f, skin);
        capsuleB(p, CharacterRig.SHOULDER_R, CharacterRig.UPPER_ARM_R, 0.055f, 0.050f, coat);
        capsuleB(p, CharacterRig.UPPER_ARM_R, CharacterRig.FOREARM_R, 0.050f, 0.043f, coat);
        capsuleB(p, CharacterRig.FOREARM_R, CharacterRig.HAND_R, 0.042f, 0.036f, skin);
        sphereB(p, CharacterRig.HAND_L, 0.045f, 0.050f, 0.035f, skin);
        sphereB(p, CharacterRig.HAND_R, 0.045f, 0.050f, 0.035f, skin);
        /* cou, tete ronde, calotte de cheveux */
        capsuleB(p, CharacterRig.NECK, CharacterRig.HEAD, 0.050f, 0.045f, skin);
        int hd = CharacterRig.HEAD * 3;
        dynamic.ellipsoid(p[hd], p[hd + 1] + 0.05f, p[hd + 2],
                0.088f, 0.108f, 0.092f, skin, 0f, 8, 5, -1.5708f, 1.5708f);
        dynamic.ellipsoid(p[hd], p[hd + 1] + 0.065f, p[hd + 2],
                0.093f, 0.105f, 0.097f, hair, 0f, 8, 3, 0.28f, 1.5708f);
        /* la sacoche du courrier : elle ne le quitte jamais (10.03) */
        dynamic.box(p[pel] - 0.16f, p[pel + 1] - 0.10f, p[pel + 2] + 0.06f,
                0.13f, 0.15f, 0.07f, 0f, Palette.LOHEN_SATCHEL, 0f, 0f);
        int sp2 = CharacterRig.SPINE2 * 3;
        dynamic.beam(p[sp2] + 0.10f, p[sp2 + 1], p[sp2 + 2],
                p[pel] - 0.16f, p[pel + 1] - 0.02f, p[pel + 2] + 0.06f,
                0.018f, Palette.LOHEN_SATCHEL, 0f);
    }

    private void capsuleB(float[] p, int bone0, int bone1, float r0, float r1,
                          int color) {
        dynamic.capsule(p[bone0 * 3], p[bone0 * 3 + 1], p[bone0 * 3 + 2],
                p[bone1 * 3], p[bone1 * 3 + 1], p[bone1 * 3 + 2],
                r0, r1, color, 0f, 8);
    }

    private void sphereB(float[] p, int bone, float rx, float ry, float rz,
                         int color) {
        dynamic.ellipsoid(p[bone * 3], p[bone * 3 + 1], p[bone * 3 + 2],
                rx, ry, rz, color, 0f, 8, 5, -1.5708f, 1.5708f);
    }

    private static int shade(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int bl = (int) ((argb & 0xFF) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    private void segment(float[] b, int i0, int i1, float thick, int color) {
        dynamic.beam(b[i0], b[i0 + 1], b[i0 + 2], b[i1], b[i1 + 1], b[i1 + 2],
                thick, color, 0f);
    }

    /**
     * L'Echassier : 3,10 m, deux jambes de verre, un balayage (12.05).
     * Il ne court jamais. Il traverse la place en trois enjambees.
     */
    private void drawEchassier(Echassier f, float time) {
        float x = f.x(), y = f.y(), z = f.z();
        int cyan = Palette.CYAN_FIGURE;
        float lean = f.sweepLean();
        dynamic.capsule(x - 0.34f, y, z, x - 0.16f + lean, y + 1.85f, z,
                0.035f, 0.075f, cyan, 0.35f, 7);
        dynamic.capsule(x + 0.34f, y, z, x + 0.16f + lean, y + 1.85f, z,
                0.035f, 0.075f, cyan, 0.35f, 7);
        dynamic.box(x + lean, y + 2.20f, z, 0.30f, 0.42f, 0.18f, f.yaw(), cyan, 0f, 0.45f);
        dynamic.box(x + lean, y + 2.82f, z, 0.13f, 0.16f, 0.13f, f.yaw(), cyan, 0f, 0.55f);
        /* le balayage : un arc de verre, jamais une barre rouge */
        if (f.sweepCount() > 0) {
            float dir = f.sweepDirection();
            float sweep = (float) Math.sin(time * 3.1f) * 1.1f;
            dynamic.beam(x, y + 1.5f, z, x + (float) Math.sin(dir + sweep) * 2.4f,
                    y + 0.4f, z + (float) Math.cos(dir + sweep) * 2.4f, 0.05f,
                    cyan, 0.7f);
        }
    }

    /**
     * Le Mueur : 1,70 m, il saute, il ne marche pas. En meute de trois, il
     * encercle (12.06). La lanterne posee le repousse a 4 m.
     */
    private void drawMueur(Mueur f, float time) {
        float x = f.x(), y = f.y(), z = f.z();
        int cyan = Palette.CYAN_FIGURE;
        float crouch = f.airborne() ? 1.15f : 0.86f;
        dynamic.box(x, y + 0.62f * crouch, z, 0.26f, 0.40f * crouch, 0.20f,
                f.yaw(), cyan, 0f, 0.40f);
        dynamic.box(x, y + 1.18f * crouch, z, 0.12f, 0.14f, 0.12f, f.yaw(),
                cyan, 0f, 0.60f);
        dynamic.capsule(x - 0.18f, y + 0.30f, z, x - 0.26f, y, z, 0.03f, 0.05f,
                cyan, 0.25f, 6);
        dynamic.capsule(x + 0.18f, y + 0.30f, z, x + 0.26f, y, z, 0.03f, 0.05f,
                cyan, 0.25f, 6);
        float hop = f.airborne() ? (float) Math.abs(Math.sin(time * 6f)) * 0.2f : 0f;
        if (hop > 0.01f) {
            dynamic.box(x, y + hop + 0.02f, z, 0.30f, 0.02f, 0.24f, f.yaw(),
                    cyan, 0f, 0.15f);
        }
    }

    /**
     * Le Verrier : Anselme Roux, 2,40 m de verre coule, trois phases, et une
     * fin qui n'est pas un combat (12.16). Il tend le dossier, il montre le
     * Phare, puis il se laisse durcir.
     */
    private void drawVerrier(VerrierBoss f, float time) {
        float x = f.x(), y = f.y(), z = f.z();
        int cyan = Palette.CYAN_FIGURE;
        float phase = f.phase();
        float h = 2.40f - (phase >= 3 ? 0.55f : 0f);
        dynamic.box(x, y + h * 0.45f, z, 0.46f, h * 0.42f, 0.30f, f.yaw(),
                cyan, 0f, 0.30f + phase * 0.05f);
        dynamic.box(x, y + h * 0.95f, z, 0.16f, 0.18f, 0.16f, f.yaw(), cyan,
                0f, 0.5f);
        /* la main de verre, tendue en phase 3 */
        if (phase >= 3) {
            float[] chain = f.handChain();
            if (chain != null && chain.length >= 3) {
                dynamic.beam(x, y + h * 0.7f, z, chain[0], chain[1], chain[2],
                        0.07f, cyan, 0.7f);
            }
        }
        /* les eclats lances */
        for (int i = 0; i < 6; i++) {
            if (!f.shardAlive(i)) {
                continue;
            }
            float[] sp = f.shardPositions();
            if (sp == null || sp.length < (i + 1) * 3) {
                continue;
            }
            dynamic.box(sp[i * 3], sp[i * 3 + 1], sp[i * 3 + 2], 0.09f, 0.14f,
                    0.03f, time * 90f, cyan, 0f, 0.8f);
        }
    }

    /** Le cone du faisceau : transparent, jamais un volume net (09.31). */
    private void beamCone(float x, float y, float z, float dx, float dy, float dz,
                          float length, float spread, int color, float alpha) {
        int seg = 12;
        float[] side = {dz, 0f, -dx};
        float sl = (float) Math.sqrt(side[0] * side[0] + side[2] * side[2]);
        if (sl < 1e-4f) {
            side = new float[]{1f, 0f, 0f};
            sl = 1f;
        }
        side[0] /= sl;
        side[2] /= sl;
        float[] realUp = {
                side[1] * dz - side[2] * dy,
                side[2] * dx - side[0] * dz,
                side[0] * dy - side[1] * dx};
        int base = dynamic.vertexCount();
        for (int i = 0; i <= seg; i++) {
            float t = (float) (i * Math.PI * 2.0 / seg);
            float ct = (float) Math.cos(t);
            float st = (float) Math.sin(t);
            dynamic.vertex(x, y, z, -dx, -dy, -dz, ShaderLib.r(color),
                    ShaderLib.g(color), ShaderLib.b(color), alpha, 0f, 0f);
            float r = length * spread;
            float ex = x + dx * length + (side[0] * ct + realUp[0] * st) * r;
            float ey = y + dy * length + (side[1] * ct + realUp[1] * st) * r;
            float ez = z + dz * length + (side[2] * ct + realUp[2] * st) * r;
            dynamic.vertex(ex, ey, ez, -dx, -dy, -dz, ShaderLib.r(color),
                    ShaderLib.g(color), ShaderLib.b(color), alpha * 0.25f, 0f, 0f);
        }
        for (int i = 0; i < seg; i++) {
            int o = base + i * 2;
            dynamic.tri(o, o + 2, o + 3);
            dynamic.tri(o, o + 3, o + 1);
        }
    }

    /** La sphere qui revele un Echo : 3,5 m/s, 18 m, jamais un dôme opaque. */
    private void revealSphere(float cx, float cy, float cz, float radius,
                              int color, float alpha) {
        int rings = 8;
        int seg = 14;
        int base = dynamic.vertexCount();
        for (int r = 0; r <= rings; r++) {
            float phi = (float) (r * Math.PI / rings);
            float y = (float) Math.cos(phi);
            float rr = (float) Math.sin(phi);
            for (int s = 0; s <= seg; s++) {
                float t = (float) (s * Math.PI * 2.0 / seg);
                float x = (float) (Math.cos(t) * rr);
                float z = (float) (Math.sin(t) * rr);
                dynamic.vertex(cx + x * radius, cy + y * radius, cz + z * radius,
                        x, y, z, ShaderLib.r(color), ShaderLib.g(color),
                        ShaderLib.b(color), alpha * (1f - r / (float) rings * 0.5f),
                        0f, 0.4f);
            }
        }
        int stride = seg + 1;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < seg; s++) {
                int o = base + r * stride + s;
                dynamic.quad(o, o + stride, o + stride + 1, o + 1);
            }
        }
    }

    public void release() {
        level.release();
        dynamic.release();
        sky.release();
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        builtFor = "";
    }
}
