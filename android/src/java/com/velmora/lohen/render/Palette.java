/*
 * LOHEN — render/Palette.java
 *
 * Les couleurs du chapitre, telles que donnees par BLOC 05. Ce ne sont pas des
 * choix artistiques libres : ce sont des DONNEES.
 *
 * 05.06 [ART] L'AMBRE (#FFA33C) est RESERVE. Il n'apparait que sur Esteban :
 *        sa peau dans l'Echo, la tranche de ses lettres, le reflet de sa
 *        montre, la lumiere de sa chambre. JAMAIS sur un objet ramassable,
 *        JAMAIS sur un PNJ vivant, JAMAIS dans le HUD.
 * 05.07 [ART] Le CYAN (#6BF2D8) obeit a la regle miroir : il n'appartient
 *        qu'aux Figures (l'Echassier, le Mueur, le Verrier) et a la resonance
 *        hostile du verre. Jamais sur un etre vivant.
 * 05.01 [OBL] Aucun contour, aucune surbrillance, aucune fleche, aucun jaune
 *        autre que l'ambre reserve. Les objets interactifs se signalent par un
 *        contraste d'humidite.
 */
package com.velmora.lohen.render;

import com.velmora.lohen.sim.math.Geom;

public final class Palette {

    private Palette() {
    }

    /* ---------------- fonds et brume (05.24) ---------------- */
    public static final int NOIR_VERRE = 0xFF05070C;   /* la Maree en ombre */
    public static final int BLEU_REFLET = 0xFF0C1420;  /* bleu-noir des reflets */
    public static final int BLEU_BRUME = 0xFF16263A;   /* fond de brume */

    /* ---------------- matieres ---------------- */
    /* RETOUR JOUEUR « c'est toujours gris » : matieres plus chaudes,
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
    public static final int TAPIS = 0xFF83443A;

    /* ---------------- lumieres (13.xx) ---------------- */
    public static final int LANTERNE_2700K = 0xFFFFD6A0;
    public static final int SOLEIL_5200K = 0xFFFFF0D2;
    public static final int CIEL_7800K = 0xFFC8E4FF;

    /* ---------------- reserves (05.06 / 05.07) ---------------- */
    public static final int AMBRE_ESTEBAN = 0xFFFFA33C;
    public static final int AMBRE_SATURE = 0xFFFF7A18;
    public static final int CYAN_FIGURE = 0xFF6BF2D8;

    /* ---------------- interface (14.02) ---------------- */
    public static final int BLANC_CASSE = 0xFFEDE6DA;  /* jamais un blanc pur */
    public static final int SOUFFLE_BAS = 0xFFA8442F;   /* rouge sourd */
    public static final int ECRAN_NUIT = 0xF2070B11;    /* voiles de menu */
    public static final int ENCRE = 0xFF12161C;         /* papier du journal */
    public static final int PAPIER = 0xFFE4DCCC;        /* feuille de la lettre */

    /** Couleur d'albedo d'un materiau du niveau (05.20 a 05.26). */
    public static int material(int mat) {
        switch (mat) {
            case Geom.MAT_WOOD: return 0xFF6B4C33;
            case Geom.MAT_WOOD_WET: return BOIS_GOUDRON;
            case Geom.MAT_STONE: return PIERRE_SECHE;
            case Geom.MAT_STONE_WET: return PIERRE_HUMIDE;
            case Geom.MAT_GRAVEL: return GRAVIER;
            case Geom.MAT_GLASS: return NOIR_VERRE;
            case Geom.MAT_METAL: return METAL;
            case Geom.MAT_CARPET: return TAPIS;
            case Geom.MAT_WATER: return BLEU_REFLET;
            default: return PIERRE_HUMIDE;
        }
    }

    /**
     * 05.01 : un objet interactif ne recoit AUCUN contour. Il recoit un
     * contraste d'humidite — legerement plus sombre et plus froid que son
     * voisin, comme une pierre qui vient d'etre touchee.
     */
    public static int interactiveTint(int base) {
        int r = (base >> 16) & 0xFF;
        int g = (base >> 8) & 0xFF;
        int b = base & 0xFF;
        r = (int) (r * 0.82f);
        g = (int) (g * 0.86f);
        b = (int) Math.min(255, b * 1.06f + 6f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Les etats d'ame du monde (05.08) : la saturation de chaque sequence.
     * S6 monte a 0,70 pendant l'Echo de la salle de bal — le pic absolu du
     * chapitre, et il retombe a 0,42 quand la salle redevient vide.
     */
    public static float saturationFor(String seq) {
        if (seq == null) {
            return 0.35f;
        }
        if ("S1".equals(seq)) return 0.62f;
        if ("S2".equals(seq)) return 0.58f;
        if ("S3".equals(seq)) return 0.75f;
        if ("S4".equals(seq)) return 0.65f;
        if ("S5".equals(seq)) return 0.52f;
        if ("S6".equals(seq)) return 0.72f;
        if ("S7".equals(seq)) return 0.55f;
        if ("S8".equals(seq)) return 0.80f;
        return 0.62f;
    }

    /** Couleur de brouillard de la sequence (05.24). */
    public static int fogFor(String seq) {
        if ("S5".equals(seq)) return 0xFF1E2836;      /* conduits : penombre bleue */
        if ("S6".equals(seq)) return 0xFF202C3A;      /* salle scellee */
        if ("S7".equals(seq)) return 0xFF3A4C60;      /* pluie battante */
        if ("S8".equals(seq)) return 0xFF7A8CA2;      /* aube : la brume monte */
        if ("S3".equals(seq)) return 0xFF4A5A6E;      /* marche : brume tiede */
        return 0xFF40536A;
    }

    /** Densite de brouillard par sequence (05.24, 09.51). */
    public static float fogDensityFor(String seq) {
        if ("S5".equals(seq)) return 0.040f;
        if ("S7".equals(seq)) return 0.022f;
        if ("S2".equals(seq)) return 0.013f;
        if ("S8".equals(seq)) return 0.006f;
        return 0.010f;
    }

    /** Visibilite en metres, pour la regle du Phare visible de partout. */
    public static float fogVisibilityFor(String seq) {
        return seq != null && "S8".equals(seq) ? 180f : 70f;
    }

    /**
     * Couleur d'un PNJ vivant : jamais l'ambre, jamais le cyan (05.06/05.07).
     * Les 34 habitants du marche portent des teintes de laine et de suie.
     */
    public static int npcTint(String archetype, int seed) {
        /* le marche suspendu : des laines TEINTES (retour joueur) */
        int[] wools = {0xFF8C4A3A, 0xFF4A5E7A, 0xFF7A6A3A, 0xFF4E6B4A,
                0xFF6A4A6E, 0xFF9A7A4A, 0xFF3F5A5E, 0xFF8A5A2E};
        int base = wools[Math.abs(seed) % wools.length];
        if ("enfant".equals(archetype)) {
            return 0xFF6E5A48;
        }
        if ("laveuse".equals(archetype)) {
            return 0xFF57514A;
        }
        if ("forgeron".equals(archetype)) {
            return 0xFF3E3229;
        }
        if ("tallec".equals(archetype)) {
            return 0xFF2F3A42;      /* le capitaine : bleu de travail */
        }
        if ("mireille".equals(archetype)) {
            return 0xFF4A4038;
        }
        if ("sol".equals(archetype)) {
            return 0xFF5C4A3A;      /* Sol : trop grande veste brune */
        }
        return base;
    }

    /** Les Figures portent le cyan, et rien d'autre (05.07). */
    public static int figureTint(String typeName) {
        return CYAN_FIGURE;
    }

    /** Esteban, et Esteban seul, porte l'ambre (05.06). */
    public static int estebanTint() {
        return AMBRE_ESTEBAN;
    }

    /** Lohen : manteau de courrier, laine grise verdatre, jamais saturé. */
    public static final int LOHEN_COAT = 0xFF414A44;
    public static final int LOHEN_TROUSER = 0xFF2E3330;
    public static final int LOHEN_SKIN = 0xFF9C7A5E;
    public static final int LOHEN_GLOVES = 0xFF2A2723;
    public static final int LOHEN_SATCHEL = 0xFF5A4432;
}
