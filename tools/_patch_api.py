#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Corrections d'API pour la couche Android (rendu + orchestrateur)."""
import os, sys

SIM = "/home/user/Space/android/src/java/com/velmora/lohen/sim"
APP = "/home/user/Space/android/src/java/com/velmora/lohen"


def patch(path, old, new, count=1, required=True):
    with open(path, encoding="utf-8") as f:
        s = f.read()
    if old not in s:
        if required:
            print("MISS  %s :: %s" % (os.path.basename(path), old.splitlines()[0][:60]))
            return False
        return True
    s = s.replace(old, new, count)
    with open(path, "w", encoding="utf-8") as f:
        f.write(s)
    return True


# ---------------------------------------------------------------- MenuModel
patch(os.path.join(SIM, "ui/MenuModel.java"),
      "    static final class ReadWrite {",
      """    /**
     * Valeur numerique d'un champ d'Options, exposee au rendu pour tracer un
     * curseur (14.12). Le rendu n'a pas le droit de reflechir sur Options :
     * la table ReadWrite est la seule passerelle.
     */
    public static float valueOf(Options o, String field) {
        return ReadWrite.number(o, field);
    }

    /** Valeur minimale d'un champ, pour normaliser un curseur. */
    public static float minOf(String field) {
        List<Row> rows = allRows();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.field.equals(field)) {
                return r.min;
            }
        }
        return 0f;
    }

    /** Valeur maximale d'un champ, pour normaliser un curseur. */
    public static float maxOf(String field) {
        List<Row> rows = allRows();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.field.equals(field)) {
                return r.max;
            }
        }
        return 1f;
    }

    private static List<Row> allRows() {
        List<Row> all = new ArrayList<Row>(64);
        for (List<Row> page : PAGES.values()) {
            all.addAll(page);
        }
        return all;
    }

    static final class ReadWrite {""")

# ------------------------------------------------------------- Localization
patch(os.path.join(SIM, "core/Localization.java"),
      "    public String t(String key) {",
      """    /**
     * Traduction avec repli explicite : si la cle est absente de la table,
     * c'est le texte de repli qui s'affiche, jamais la cle brute. L'interface
     * ne doit jamais laisser voir une cle (14.10).
     */
    public String text(String key, String fallback) {
        Map<String, String> table = db.locale(language);
        String value = table.get(key);
        if (value == null && !"fr".equals(language)) {
            value = db.locale("fr").get(key);
        }
        return value == null ? (fallback == null ? key : fallback) : value;
    }

    public String t(String key) {""")

# --------------------------------------------------------------- LohenGame
patch(os.path.join(SIM, "core/LohenGame.java"),
      "    public float secondsSinceEvent() {",
      """    /** Le verbe actuellement propose : le HUD en tire un glyphe (14.07). */
    public String interactVerb() {
        return interactVerb;
    }

    /** La cible du verbe : un Echo, une scene, un objet, une sequence. */
    public String interactTarget() {
        return interactTarget;
    }

    /** Un slot ou un autosave existe : le « Continuer » du menu en depend. */
    public boolean hasSave() {
        for (int i = 0; i < SaveSystem.SLOTS; i++) {
            if (saves.hasSlot(i)) {
                return true;
            }
        }
        return saves.loadLatestAutosave() != null;
    }

    /** Description d'un emplacement pour l'ecran des sauvegardes (14.05). */
    public String describeSlot(int slot) {
        java.util.Map<String, Object> d = saves.describeSlot(slot);
        if (d == null || d.isEmpty()) {
            return "\\u2014";
        }
        StringBuilder sb = new StringBuilder(96);
        Object seq = d.get("sequence");
        Object time = d.get("play_time");
        Object node = d.get("quest_node");
        if (seq != null) {
            sb.append(seq);
        }
        if (time instanceof Number) {
            int minutes = (int) (((Number) time).floatValue() / 60f);
            sb.append(" \\u00b7 ").append(minutes).append(" min");
        }
        if (node != null) {
            sb.append(" \\u00b7 ").append(node);
        }
        return sb.length() == 0 ? "\\u2014" : sb.toString();
    }

    public float secondsSinceEvent() {""")

# ------------------------------------------------------------- MeshBuilder
patch(os.path.join(APP, "gl/MeshBuilder.java"),
      """    public void ramp(float x, float y, float z, float w, float h, float d,
                     float yawDeg, int steps, int argb) {""",
      """    public void ramp(float x, float y, float z, float w, float h, float d,
                     float yawDeg, float steps, int argb) {""")

# ----------------------------------------------------------------- UiBatch
patch(os.path.join(APP, "gl/UiBatch.java"),
      """    /**
     * Un triangle : les fleches sont interdites (14.03), mais les arcs du""",
      """    /** Un quad dont les quatre coins sont libres : anneaux, arcs, filets. */
    public void quadCorners(float x0, float y0, float x1, float y1,
                            float x2, float y2, float x3, float y3,
                            float u, float v, int argb) {
        if (verts + 4 > capacity) {
            return;
        }
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float a = ShaderLib.a(argb);
        put(x0, y0, u, v, r, g, b, a);
        put(x1, y1, u, v, r, g, b, a);
        put(x2, y2, u, v, r, g, b, a);
        put(x3, y3, u, v, r, g, b, a);
    }

    /**
     * Un triangle : les fleches sont interdites (14.03), mais les arcs du""")

# --------------------------------------------------------------- TextAtlas
patch(os.path.join(APP, "gl/TextAtlas.java"),
      """    private int penX = PAD;
    private int penY = PAD;""",
      """    /**
     * Le bloc blanc opaque en (0,0) : il sert de texture d'un pixel pour tous
     * les aplats, arcs et filets du HUD. Un seul atlas, un seul draw call.
     */
    public static final float WHITE_UV = 4f / (float) SIZE;
    public static final int WHITE_BLOCK = 8;

    private int penX = WHITE_BLOCK + PAD * 2;
    private int penY = PAD;""")

patch(os.path.join(APP, "gl/TextAtlas.java"),
      """        bitmap.eraseColor(0);
        canvas = new Canvas(bitmap);""",
      """        bitmap.eraseColor(0);
        canvas = new Canvas(bitmap);
        canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
        android.graphics.Paint white = new android.graphics.Paint();
        white.setColor(0xFFFFFFFF);
        canvas.drawRect(0, 0, WHITE_BLOCK, WHITE_BLOCK, white);""")

print("patch script termine")
