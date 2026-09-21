# -*- coding: utf-8 -*-
"""Patch confort (retour joueur) : jeu muet par defaut, textes agrandis,
boutons tactiles elargis, matieres et laine des habitants en couleur."""
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


# ---------------------------------------------------------------- Options
op = load("/sim/core/Options.java")
rep(op, """    public float volMaster = 0.85f;""",
    """    /* RETOUR JOUEUR : le jeu est livre MUET. Le son existe toujours, il se
     * remonte dans Reglages > Audio. */
    public float volMaster = 0f;""")
rep(op, """    public float buttonScale = 1.0f;     /* 80-140 % */""",
    """    public float buttonScale = 1.25f;    /* 80-140 % — retour joueur */""")
rep(op, """    public int subtitleSizeSp = 21;      /* 17-30 sp */""",
    """    public int subtitleSizeSp = 26;      /* 17-30 sp — retour joueur */""")

# ---------------------------------------------------------------- HUD : tailles
hud = load("/render/HudRenderer.java")
rep(hud, """    public float dp(float value) {
        return value * density;
    }

    /** sp vers pixels : 17 sp minimum pour les sous-titres (14.10). */
    public int sp(float value) {
        return Math.round(value * density);
    }""",
    """    /* RETOUR JOUEUR « textes tres petits voire illisibles » : tout le HUD
     * est mis a l'echelle — texte x1,45, gabarits x1,12. */
    public static final float TEXT_SCALE = 1.45f;
    public static final float LAYOUT_SCALE = 1.12f;

    public float dp(float value) {
        return value * density * LAYOUT_SCALE;
    }

    /** sp vers pixels : 17 sp minimum pour les sous-titres (14.10). */
    public int sp(float value) {
        return Math.round(value * density * TEXT_SCALE);
    }""")

# ---------------------------------------------------------------- boutons tactiles
tir = load("/sim/input/TouchInputRouter.java")
rep(tir, """        float rA = 72f * dpScale * scale * 0.5f;
        float rB = 64f * dpScale * scale * 0.5f;
        float rC = 64f * dpScale * scale * 0.5f;
        float rD = 64f * dpScale * scale * 0.5f;""",
    """        /* retour joueur « bugs interactifs » : cibles elargies */
        float rA = 96f * dpScale * scale * 0.5f;
        float rB = 84f * dpScale * scale * 0.5f;
        float rC = 84f * dpScale * scale * 0.5f;
        float rD = 84f * dpScale * scale * 0.5f;""")

# ---------------------------------------------------------------- Palette
pa = load("/render/Palette.java")
rep(pa, """    public static final int PIERRE_HUMIDE = 0xFF4A423A;
    public static final int PIERRE_SECHE = 0xFF6E6254;
    public static final int PLATRE = 0xFF8E7F6C;
    public static final int CALCAIRE = 0xFFB9A88E;
    public static final int BOIS_GOUDRON = 0xFF3B2A21;
    public static final int BRIQUE = 0xFF7A3F2C;
    public static final int TERRE_CUITE = 0xFFA8552F;
    public static final int METAL = 0xFF4E565E;
    public static final int GRAVIER = 0xFF5A5046;
    public static final int TAPIS = 0xFF5A3B32;""",
    """    /* RETOUR JOUEUR « c'est toujours gris » : matieres plus chaudes,
     * plus claires, plus saturees — la ville est de brique, de platre et
     * de terre cuite, pas de beton gris. */
    public static final int PIERRE_HUMIDE = 0xFF5E5548;
    public static final int PIERRE_SECHE = 0xFF8C7A60;
    public static final int PLATRE = 0xFFA9977C;
    public static final int CALCAIRE = 0xFFCFBB95;
    public static final int BOIS_GOUDRON = 0xFF4E382A;
    public static final int BRIQUE = 0xFF9E4E33;
    public static final int TERRE_CUITE = 0xFFC46C36;
    public static final int METAL = 0xFF6E7A86;
    public static final int GRAVIER = 0xFF7A6C5C;
    public static final int TAPIS = 0xFF83443A;""")
rep(pa, """            case Geom.MAT_WOOD: return 0xFF4A382B;""",
    """            case Geom.MAT_WOOD: return 0xFF6B4C33;""")
rep(pa, """        int[] wools = {0xFF4E4438, 0xFF5A4A3C, 0xFF413A34, 0xFF6A5A48,
                0xFF3A3F45, 0xFF55463A, 0xFF484038, 0xFF605044};""",
    """        /* le marche suspendu : des laines TEINTES (retour joueur) */
        int[] wools = {0xFF8C4A3A, 0xFF4A5E7A, 0xFF7A6A3A, 0xFF4E6B4A,
                0xFF6A4A6E, 0xFF9A7A4A, 0xFF3F5A5E, 0xFF8A5A2E};""")

# ---------------------------------------------------------------- SmokeTest
sm = load(["/home/user/Space/tools/smoke/SmokeTest.java"][0] and "/home/user/Space/tools/smoke/SmokeTest.java")
rep(sm, """        System.out.println("9. Audio synthetise");
        short[] buf = new short[2048];
        int frames = game.renderAudio(buf);
        int peak = 0;
        for (short s : buf) {
            peak = Math.max(peak, Math.abs((int) s));
        }
        check("1024 images rendues", frames == 1024, String.valueOf(frames));
        check("le mix n'est pas muet", peak > 0, "pic " + peak);""",
    """        System.out.println("9. Audio synthetise");
        short[] buf = new short[2048];
        /* RETOUR JOUEUR : le jeu est livre MUET — on le verifie d'abord */
        int mutePeak = 0;
        for (int i = 0; i < 8; i++) {
            game.frame(dt);
            game.renderAudio(buf);
            for (short s : buf) {
                mutePeak = Math.max(mutePeak, Math.abs((int) s));
            }
        }
        check("muet par defaut (retour joueur)", mutePeak == 0, "pic " + mutePeak);
        /* puis on remonte le son comme le ferait le joueur dans Reglages */
        game.options.volMaster = 0.85f;
        game.audio.applyOptions();
        int frames = game.renderAudio(buf);
        int peak = 0;
        for (int i = 0; i < 12; i++) {
            game.frame(dt);
            frames = game.renderAudio(buf);
            for (short s : buf) {
                peak = Math.max(peak, Math.abs((int) s));
            }
        }
        check("1024 images rendues", frames == 1024, String.valueOf(frames));
        check("le mix n'est pas muet une fois le son active", peak > 0,
                "pic " + peak);""")

for p, store in (("/sim/core/Options.java", op), ("/render/HudRenderer.java", hud),
                 ("/sim/input/TouchInputRouter.java", tir), ("/render/Palette.java", pa)):
    open(ROOT + p, "w", encoding="utf-8").write(store[0])
open("/home/user/Space/tools/smoke/SmokeTest.java", "w", encoding="utf-8").write(sm[0])

if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("patch confort ok")
