/*
 * LOHEN — sim/narrative/LetterReader.java
 *
 * BLOC 19 : la sequence finale, beat par beat, sans raccourci.
 *
 * 19.02 LE PALIER (206 m) : porte fermee MAIS PAS verrouillee. Le joueur
 *       doit pousser — maintien de 2,2 s avec resistance progressive.
 *       AUCUNE barre de progression : c'est l'epaule de Lohen qui dit
 *       l'effort. Haptique montante. Le bois grince, c'est le seul son.
 * 19.03 ENTREE : plan de 34 s, camera libre, [OBL] le joueur GARDE le
 *       controle. Ordre de decouverte guide par la lumiere : la lampe
 *       (AMBRE, premiere fois depuis 47 minutes), la couverture pliee,
 *       le mur de feuilles, « 0114 » en marge sur 31 d'entre elles,
 *       les chaussures rangees, le carnet, la feuille pliee en trois.
 * 19.05 LE CARNET (E30) : le joueur tourne les pages, 6 extraits canoniques.
 *       Le carnet ne dit JAMAIS ou il est parti, ni s'il est vivant.
 * 19.06 L'INTERACTION : le prompt est le GLYPHE DE L'ECHO (pas « Prendre »,
 *       pas « Lire »). Lohen enleve ses gants. 31e fois. EXACTEMENT 2,4 s.
 * 19.07 IL N'Y A PAS D'ECHO. Rien. 3,5 s de silence. Puis il ouvre les yeux,
 *       regarde ce qu'il a dans la main : du papier. Il le deplie
 *       (unfold_letter, 4,1 s, 3 plis, mains qui tremblent 2 mm / 7 Hz
 *       active seulement sur la troisieme seconde).
 * 19.08 LA LECTURE : plan rapproche par-dessus l'epaule droite, legerement
 *       en plongee. Texte LISIBLE dans l'ecriture d'Esteban. La voix
 *       d'Esteban lit, SECHE, sans reverb (11.06 paie ici). Le joueur fait
 *       defiler a son rythme, la voix suit. [OBL] S'il s'arrete, la voix
 *       s'arrete. Trois coupes breves (1,2 s) sur le visage de Lohen :
 *       il ne pleure pas aux deux premieres ; a la troisieme si, et la
 *       camera est deja en train de partir.
 * 19.09 M18 (4:10) : 0:00-1:10 rien que le vent et la lampe ; 1:10-2:05 une
 *       note de violoncelle qui ne resout pas ; 2:05-3:00 le piano, trois
 *       notes ; 3:00 UNE CLOCHE et LES CINQ NOTES COMPLETES LA-DO-MI-RE-LA,
 *       [OBL] synchronisees a la ligne « Tu as les mains sales. » ;
 *       3:00-4:10 tout ensemble tres doux + voix sans paroles sur les 40
 *       dernieres secondes ; 4:10 coupure nette, le vent revient.
 * 19.10 APRES : immobile 6 s SANS controle, puis le controle revient sans
 *       transition ni indication. Une seule interaction : la sacoche.
 *       fold_and_store 5,2 s — unfold_letter a l'envers, image par image.
 * 19.11 LA DESCENTE : 90 s jouables en silence. Il fait jour.
 * 19.12 LE DERNIER PLAN : Sol dort assis contre la porte. La veste de
 *       relayeur posee sur Sol. 24 s de recul. M19. Cartons.
 *       Puis, apres 4 s de noir, en bas a droite, tres petit, dans
 *       l'ecriture d'Esteban : « Il reste six lettres. »
 * 19.13 GENERIQUE immediat, non skippable 60 s, M20 avec paroles.
 * 19.14 APRES : le menu a change, onglet « LETTRES — 1 / 7 », lettre
 *       relisible integralement a tout moment.
 */
package com.velmora.lohen.sim.narrative;

import com.velmora.lohen.sim.core.ContentDb;
import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.GameState;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.MiniJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LetterReader {

    /* etapes */
    public static final int STEP_INACTIVE = 0;
    public static final int STEP_DOOR = 1;
    public static final int STEP_ENTRY = 2;
    public static final int STEP_ROOM_FREE = 3;
    public static final int STEP_NOTEBOOK = 4;
    public static final int STEP_LETTER_PROMPT = 5;
    public static final int STEP_RITUAL = 6;
    public static final int STEP_NO_ECHO = 7;
    public static final int STEP_UNFOLD = 8;
    public static final int STEP_READING = 9;
    public static final int STEP_AFTER = 10;
    public static final int STEP_CONTROL_RETURN = 11;
    public static final int STEP_FOLD_STORE = 12;
    public static final int STEP_DESCENT = 13;
    public static final int STEP_FINAL_SHOT = 14;
    public static final int STEP_CREDITS = 15;
    public static final int STEP_DONE = 16;

    /* constantes canoniques */
    public static final float DOOR_HOLD = 2.2f;
    public static final float ENTRY_PLAN = 34f;
    public static final float NOTEBOOK_DURATION = 150f;      /* E30 : 2:30 */
    public static final int NOTEBOOK_EXTRACTS = 6;
    public static final float RITUAL_DURATION = 2.4f;        /* 19.06 : exactement */
    public static final float NO_ECHO_SILENCE = 3.5f;        /* 19.07 */
    public static final float UNFOLD_DURATION = 4.1f;
    public static final float TREMBLE_MM = 2f;
    public static final float TREMBLE_HZ = 7f;
    public static final float TREMBLE_FROM_SECOND = 3f;
    public static final int CAMERA_CUTS = 3;
    public static final float CAMERA_CUT_DURATION = 1.2f;
    public static final float AFTER_IMMOBILE = 6f;           /* 19.10 */
    public static final float FOLD_STORE_DURATION = 5.2f;
    public static final float DESCENT_DURATION = 90f;        /* 19.11 */
    public static final float FINAL_SHOT_PULLBACK = 24f;     /* 19.12 */
    public static final float FINAL_BLACK_BEFORE_LINE = 4f;
    public static final String LAST_LINE = "Il reste six lettres.";
    public static final float CREDITS_LOCK = 60f;            /* 19.13 */
    public static final float MUSIC_DURATION = 250f;         /* M18 : 4:10 */
    public static final float BELL_AT = 180f;                /* 3:00 */
    public static final String BELL_SYNC_LINE = "Tu as les mains sales.";
    public static final float VOICELESS_TAIL = 40f;          /* 40 dernieres secondes */
    public static final int PAPERS_ON_WALL = 314;
    public static final int PAPERS_WITH_0114 = 31;
    public static final float ROOM_ALTITUDE = 206f;
    public static final float CLIMB_TOTAL = 204f;            /* 19.01 */
    public static final float DAWN_EV_PER_MINUTE = 0.6f / 9f;/* 0 -> 0,6 EV sur 9 min */

    private final ContentDb db;
    private final GameState state;
    private final EventBus bus;

    private int step = STEP_INACTIVE;
    private float stepTime;
    private float totalTime;
    private float doorProgress;
    private boolean doorHeld;
    private float entryTime;
    private float dawnEv;

    /* carnet */
    private int notebookPage;
    private final List<String> notebookExtracts = new ArrayList<String>(NOTEBOOK_EXTRACTS);
    private float notebookTime;

    /* lettre */
    private final List<String> blocks = new ArrayList<String>(32);
    private final List<String> stagings = new ArrayList<String>(32);
    private int visibleBlocks;
    private float scroll;                 /* 0..1 progression de lecture */
    private float scrollVelocity;
    private boolean playerScrolling;
    private float voiceTime;
    private float musicTime;
    private boolean bellFired;
    private int cameraCut;
    private float cameraCutTime;
    private boolean thirdCutCried;
    private int bellBlock = -1;
    private final float[] anchors = new float[32];
    private boolean readingDone;
    private float readingRealTime;

    /* apres */
    private boolean controlLocked = true;
    private boolean folded;
    private float creditsTime;
    private boolean creditsSkippable;
    private boolean chapterFinished;
    private int promptGlyph = 1;          /* 1 = glyphe de l'Echo (19.06) */

    public LetterReader(ContentDb db, GameState state, EventBus bus) {
        this.db = db;
        this.state = state;
        this.bus = bus;
    }

    /** Charge le texte canonique (BLOC 20 — ne jamais reecrire). */
    @SuppressWarnings("unchecked")
    public boolean loadLetter() {
        blocks.clear();
        stagings.clear();
        notebookExtracts.clear();
        if (db == null) {
            return false;
        }
        Map<String, Object> root = db.json("content/letter/lettre_esteban.json");
        if (root == null) {
            return false;
        }
        List<Object> list = MiniJson.childList(root, "blocks");
        if (list != null) {
            for (Object o : list) {
                if (o instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    blocks.add(MiniJson.str(m, "text", ""));
                    stagings.add(MiniJson.str(m, "staging", ""));
                } else {
                    blocks.add(String.valueOf(o));
                    stagings.add("");
                }
            }
        }
        /* 19.05 : les 6 extraits canoniques du carnet */
        List<Object> notes = MiniJson.childList(root, "notebook_extracts");
        if (notes != null) {
            for (Object o : notes) {
                notebookExtracts.add(String.valueOf(o));
            }
        }
        if (notebookExtracts.isEmpty()) {
            notebookExtracts.add("Jour 4. La lentille tourne toute seule. Je ne l'explique pas, je l'entretiens. C'est deja un travail.");
            notebookExtracts.add("Jour 61. J'ai compte : de la chambre, on voit onze toits de la rue des Lavandieres. Le notre est le quatrieme en partant de la grue. Il a perdu des tuiles.");
            notebookExtracts.add("Jour 140. Je descends jusqu'au palier des cloches deux fois par semaine. Elles sont fausses. Je les accorde. Personne ne les entendra. Ce n'est pas une raison.");
            notebookExtracts.add("Jour 300. Aujourd'hui j'ai ecrit son matricule au lieu de son prenom. Je crois que c'est parce que le matricule, lui, existe dans un registre quelque part.");
            notebookExtracts.add("Jour 612. Si quelqu'un monte un jour, qu'il sache : la lampe n'est pas un signal. C'est juste que je n'aime pas le noir.");
            notebookExtracts.add("Jour 1049. Je n'ai plus de papier.");
        }
        /* ancre musicale : la ligne de la cloche (19.09) */
        bellBlock = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).contains(BELL_SYNC_LINE)) {
                bellBlock = i;
                break;
            }
        }
        if (bellBlock < 0) {
            bellBlock = blocks.size() / 2;
        }
        buildAnchors();
        return !blocks.isEmpty();
    }

    /**
     * La musique suit la LECTURE, pas l'horloge : les 180 premieres secondes
     * de M18 couvrent les blocs 0..bellBlock, les 70 suivantes couvrent
     * bellBlock..fin. Ainsi la cloche tombe EXACTEMENT sur la ligne.
     */
    private void buildAnchors() {
        if (blocks.isEmpty()) {
            return;
        }
        int last = blocks.size() - 1;
        int charsBefore = 0;
        int charsAfter = 0;
        for (int i = 0; i < blocks.size(); i++) {
            int len = Math.max(1, blocks.get(i).length());
            if (i <= bellBlock) {
                charsBefore += len;
            }
            if (i >= bellBlock) {
                charsAfter += len;
            }
        }
        int accBefore = 0;
        int accAfter = 0;
        for (int i = 0; i < blocks.size(); i++) {
            int len = Math.max(1, blocks.get(i).length());
            if (i <= bellBlock) {
                anchors[i] = BELL_AT * (charsBefore == 0 ? 0f : accBefore / (float) charsBefore);
                accBefore += len;
            } else {
                float t = charsAfter == 0 ? 0f : accAfter / (float) charsAfter;
                anchors[i] = BELL_AT + (MUSIC_DURATION - BELL_AT) * t;
                accAfter += len;
            }
        }
        anchors[last] = MUSIC_DURATION;
        anchors[bellBlock] = BELL_AT;
    }

    public float musicTimeForBlock(int i) {
        return i >= 0 && i < anchors.length ? anchors[i] : 0f;
    }

    /* ------------------------------------------------------------------ */
    /* Demarrage                                                           */
    /* ------------------------------------------------------------------ */

    public boolean begin() {
        if (step != STEP_INACTIVE && step != STEP_DONE) {
            return false;
        }
        if (!loadLetter()) {
            return false;
        }
        step = STEP_DOOR;
        stepTime = 0f;
        totalTime = 0f;
        doorProgress = 0f;
        entryTime = 0f;
        dawnEv = 0f;
        notebookPage = 0;
        notebookTime = 0f;
        visibleBlocks = 0;
        scroll = 0f;
        scrollVelocity = 0f;
        playerScrolling = false;
        voiceTime = 0f;
        musicTime = 0f;
        bellFired = false;
        cameraCut = 0;
        cameraCutTime = 0f;
        thirdCutCried = false;
        readingDone = false;
        readingRealTime = 0f;
        controlLocked = true;
        folded = false;
        creditsTime = 0f;
        creditsSkippable = false;
        chapterFinished = false;
        bus.emit(EventBus.LETTER_STARTED);
        bus.emit(EventBus.DOOR_PUSH, 0f, DOOR_HOLD);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    public void update(float dt) {
        if (step == STEP_INACTIVE || step == STEP_DONE) {
            return;
        }
        stepTime += dt;
        totalTime += dt;
        /* 19.04 : l'aube monte de 0 a 0,6 EV sur 9 minutes, pendant toute la scene */
        dawnEv = Math.min(0.6f, dawnEv + DAWN_EV_PER_MINUTE * dt);
        bus.emit(EventBus.LETTER_DAWN, dawnEv);

        switch (step) {
            case STEP_DOOR:
                updateDoor(dt);
                break;
            case STEP_ENTRY:
                updateEntry(dt);
                break;
            case STEP_ROOM_FREE:
                /* le joueur garde le controle (19.03) : rien n'est force */
                if (stepTime > 4f) {
                    bus.emit(EventBus.LETTER_PROMPT, promptGlyph);
                }
                break;
            case STEP_NOTEBOOK:
                updateNotebook(dt);
                break;
            case STEP_LETTER_PROMPT:
                break;
            case STEP_RITUAL:
                updateRitual(dt);
                break;
            case STEP_NO_ECHO:
                updateNoEcho(dt);
                break;
            case STEP_UNFOLD:
                updateUnfold(dt);
                break;
            case STEP_READING:
                updateReading(dt);
                break;
            case STEP_AFTER:
                updateAfter(dt);
                break;
            case STEP_CONTROL_RETURN:
                if (stepTime > 0.1f) {
                    setStep(STEP_ROOM_FREE);
                    controlLocked = false;
                }
                break;
            case STEP_FOLD_STORE:
                updateFold(dt);
                break;
            case STEP_DESCENT:
                updateDescent(dt);
                break;
            case STEP_FINAL_SHOT:
                updateFinalShot(dt);
                break;
            case STEP_CREDITS:
                updateCredits(dt);
                break;
            default:
                break;
        }
    }

    private void setStep(int s) {
        step = s;
        stepTime = 0f;
        bus.emit(EventBus.LETTER_STEP, s);
    }

    /** 19.02 : maintien 2,2 s, resistance progressive, haptique montante. */
    private void updateDoor(float dt) {
        if (!doorHeld) {
            /* la porte se referme : resistance progressive, pas de barre */
            doorProgress = Math.max(0f, doorProgress - dt * 0.55f);
        } else {
            float resistance = 0.55f + 0.45f * (1f - doorProgress);
            doorProgress += dt / (DOOR_HOLD * resistance);
            bus.emit(EventBus.DOOR_PUSH, Maths.clamp01(doorProgress), DOOR_HOLD);
            /* haptique montante : hap_door_push (08.31, 19.02) */
            if ((int) (doorProgress * 5f) != (int) ((doorProgress - dt / DOOR_HOLD) * 5f)) {
                bus.emit(EventBus.HAPTIC, "hap_door_push", doorProgress);
            }
        }
        if (doorProgress >= 1f) {
            bus.emit(EventBus.SFX, "door_wood_creak", 1f);
            setStep(STEP_ENTRY);
            controlLocked = false;      /* [OBL] le joueur garde le controle */
            bus.emit(EventBus.CONTROL_RESTORED, "S8_chambre");
        }
    }

    public void setDoorHeld(boolean held) {
        doorHeld = held;
    }

    /** 19.03 : 34 s, camera libre, le joueur garde le controle. */
    private void updateEntry(float dt) {
        entryTime += dt;
        /* la lumiere guide le regard : ordre de decouverte (19.03) */
        if (entryTime > 2f && entryTime - dt <= 2f) {
            bus.emit(EventBus.LETTER_DISCOVERY, 1, "lampe_ambre");
        }
        if (entryTime > 6f && entryTime - dt <= 6f) {
            bus.emit(EventBus.LETTER_DISCOVERY, 2, "couverture_pliee");
        }
        if (entryTime > 11f && entryTime - dt <= 11f) {
            bus.emit(EventBus.LETTER_DISCOVERY, 3, "mur_de_feuilles");
        }
        if (entryTime > 17f && entryTime - dt <= 17f) {
            bus.emit(EventBus.LETTER_DISCOVERY, 4, "0114_en_marge");
        }
        if (entryTime > 22f && entryTime - dt <= 22f) {
            bus.emit(EventBus.LETTER_DISCOVERY, 5, "chaussures_rangees");
        }
        if (entryTime > 26f && entryTime - dt <= 26f) {
            bus.emit(EventBus.LETTER_DISCOVERY, 6, "carnet_ouvert");
        }
        if (entryTime > 30f && entryTime - dt <= 30f) {
            bus.emit(EventBus.LETTER_DISCOVERY, 7, "feuille_pliee_en_trois");
        }
        if (entryTime >= ENTRY_PLAN) {
            setStep(STEP_ROOM_FREE);
        }
    }

    /** 19.05 : le carnet — le joueur tourne les pages. */
    public void openNotebook() {
        if (step != STEP_ROOM_FREE) {
            return;
        }
        notebookPage = 0;
        notebookTime = 0f;
        setStep(STEP_NOTEBOOK);
        bus.emit(EventBus.LETTER_NOTEBOOK_PAGE, 0, notebookExtracts.get(0));
        bus.emit(EventBus.ECHO_STARTED, "E30", 'A', NOTEBOOK_DURATION, 18f);
    }

    public void turnPage() {
        if (step != STEP_NOTEBOOK) {
            return;
        }
        notebookPage++;
        if (notebookPage >= notebookExtracts.size()) {
            closeNotebook();
            return;
        }
        bus.emit(EventBus.LETTER_NOTEBOOK_PAGE, notebookPage, notebookExtracts.get(notebookPage));
        bus.emit(EventBus.SFX, "page_turn", 0.8f);
    }

    public void closeNotebook() {
        notebookPage = notebookExtracts.size();
        bus.emit(EventBus.ECHO_FINISHED, "E30", 1);
        setStep(STEP_ROOM_FREE);
    }

    private void updateNotebook(float dt) {
        notebookTime += dt;
    }

    /** 19.06 : le prompt est le glyphe de l'Echo. Il enleve ses gants. */
    public void interactWithLetter() {
        if (step != STEP_ROOM_FREE && step != STEP_LETTER_PROMPT) {
            return;
        }
        setStep(STEP_RITUAL);
        controlLocked = true;
        bus.emit(EventBus.ECHO_RITUAL_BEGIN, "E31_LETTRE", RITUAL_DURATION);
        bus.emit(EventBus.HAPTIC, "hap_echo_start", 1f);
        if (state != null) {
            state.incrementRitual();      /* la 31e fois */
        }
    }

    private void updateRitual(float dt) {
        float p = Maths.clamp01(stepTime / RITUAL_DURATION);
        bus.emit(EventBus.ECHO_PHASE, p < 0.21f ? 1 : p < 0.67f ? 2 : p < 0.84f ? 3 : 4);
        bus.emit(EventBus.LETTER_RITUAL, p, RITUAL_DURATION);
        if (p < 0.21f) {
            bus.emit(EventBus.GESTURE, "L4");
        } else if (p < 0.67f) {
            bus.emit(EventBus.GESTURE, "L1");
        }
        /* 19.06 [OBL] : exactement le meme temps que les 30 autres */
        if (stepTime >= RITUAL_DURATION) {
            setStep(STEP_NO_ECHO);
            bus.emit(EventBus.LETTER_NO_ECHO, NO_ECHO_SILENCE);
        }
    }

    /** 19.07 : il n'y a pas d'Echo. Rien ne se passe. */
    private void updateNoEcho(float dt) {
        bus.emit(EventBus.ECHO_VISUAL, 0, 0f, 0f, 0f, 0f, 0f, 0);
        if (stepTime >= NO_ECHO_SILENCE) {
            setStep(STEP_UNFOLD);
            bus.emit(EventBus.LETTER_UNFOLD, UNFOLD_DURATION);
            bus.emit(EventBus.SFX, "paper_unfold", 1f);
        }
    }

    private void updateUnfold(float dt) {
        float p = Maths.clamp01(stepTime / UNFOLD_DURATION);
        /* tremblement 2 mm / 7 Hz, active seulement sur la 3e seconde */
        float tremble = stepTime >= TREMBLE_FROM_SECOND
                ? TREMBLE_MM * (float) Math.sin(stepTime * TREMBLE_HZ * Maths.TWO_PI) : 0f;
        int fold = p < 0.33f ? 1 : p < 0.66f ? 2 : 3;
        bus.emit(EventBus.LETTER_UNFOLD_PROGRESS, p, fold, tremble);
        if (stepTime >= UNFOLD_DURATION) {
            setStep(STEP_READING);
            controlLocked = false;      /* le joueur fait defiler a son rythme */
            bus.emit(EventBus.MUSIC_CUE, "M18", 2f);
            visibleBlocks = 1;
            bus.emit(EventBus.LETTER_LINE, 0, blocks.get(0), stagings.get(0));
        }
    }

    /** Le joueur fait defiler. S'il s'arrete, la voix s'arrete (19.08). */
    public void scrollBy(float amount) {
        if (step != STEP_READING) {
            return;
        }
        playerScrolling = amount != 0f;
        scrollVelocity = amount;
        if (amount == 0f) {
            return;
        }
        float before = scroll;
        scroll = Maths.clamp01(scroll + amount);
        bus.emit(EventBus.LETTER_SCROLL, scroll, amount);
        /* revelation par blocs : la voix suit le defilement */
        int target = 1 + (int) (scroll * (blocks.size() - 1));
        while (visibleBlocks < target && visibleBlocks < blocks.size()) {
            bus.emit(EventBus.LETTER_LINE, visibleBlocks, blocks.get(visibleBlocks),
                    stagings.get(visibleBlocks));
            bus.emit(EventBus.LETTER_VOICE, visibleBlocks, blocks.get(visibleBlocks));
            /* cloche : exactement sur « Tu as les mains sales. » (19.09) */
            if (visibleBlocks == bellBlock && !bellFired) {
                bellFired = true;
                musicTime = BELL_AT;
                bus.emit(EventBus.LETTER_BELL, BELL_AT);
                bus.emit(EventBus.MUSIC_CUE, "M18_BELL_FULL_MOTIF", 0f);
            }
            visibleBlocks++;
        }
        if (scroll >= 1f && !readingDone) {
            readingDone = true;
            finishReading();
        }
        if (before != scroll) {
            musicTime = musicTimeForProgress(scroll);
            bus.emit(EventBus.MUSIC_CUE, "M18_POSITION", musicTime);
        }
    }

    /** Position musicale interpolée depuis la progression de lecture. */
    public float musicTimeForProgress(float p) {
        if (blocks.isEmpty()) {
            return 0f;
        }
        float f = Maths.clamp01(p) * (blocks.size() - 1);
        int i = (int) Math.floor(f);
        int j = Math.min(blocks.size() - 1, i + 1);
        return Maths.lerp(anchors[i], anchors[j], f - i);
    }

    private void updateReading(float dt) {
        readingRealTime += dt;
        if (!playerScrolling) {
            scrollVelocity = 0f;
            /* la voix et la musique s'arretent avec le joueur (19.08) */
            bus.emit(EventBus.LETTER_VOICE_HOLD, 1);
        } else {
            bus.emit(EventBus.LETTER_VOICE_HOLD, 0);
        }
        /* trois coupes breves de 1,2 s sur le visage de Lohen (19.08) */
        float[] cutAt = {0.28f, 0.58f, 0.88f};
        if (cameraCut < CAMERA_CUTS && scroll >= cutAt[cameraCut]) {
            cameraCut++;
            cameraCutTime = CAMERA_CUT_DURATION;
            boolean cries = cameraCut == CAMERA_CUTS;
            thirdCutCried = cries;
            bus.emit(EventBus.LETTER_CAMERA_CUT, cameraCut, CAMERA_CUT_DURATION, cries);
        }
        if (cameraCutTime > 0f) {
            cameraCutTime -= dt;
            if (cameraCutTime <= 0f && cameraCut == CAMERA_CUTS) {
                /* a la troisieme, la camera est DEJA en train de partir */
                bus.emit(EventBus.LETTER_CAMERA_LEAVING);
            }
        }
        /* voix sans paroles sur les 40 dernieres secondes (19.09) */
        if (musicTime >= MUSIC_DURATION - VOICELESS_TAIL && musicTime < MUSIC_DURATION) {
            bus.emit(EventBus.MUSIC_CUE, "M18_VOICELESS", MUSIC_DURATION - musicTime);
        }
    }

    private void finishReading() {
        /* 4:10 coupure nette. Silence. Le vent revient. */
        bus.emit(EventBus.MUSIC_CUE, "M18_CUT", 0f);
        bus.emit(EventBus.SFX, "wind_return", 1f);
        bus.emit(EventBus.LETTER_ENDED);
        if (state != null) {
            state.markLetterDelivered("LETTRE_ESTEBAN_0114");
            state.addJournalNote("letter:esteban:read");
            state.setFlag("chapter1_complete");
        }
        setStep(STEP_AFTER);
        controlLocked = true;      /* 6 s immobile, sans controle (19.10) */
    }

    private void updateAfter(float dt) {
        if (stepTime >= AFTER_IMMOBILE) {
            /* le controle revient SANS transition, sans indication */
            setStep(STEP_CONTROL_RETURN);
            controlLocked = false;
            bus.emit(EventBus.CONTROL_RESTORED, "S8_apres_lecture");
        }
    }

    /** 19.10 : une seule interaction dans toute la piece — la sacoche. */
    public void storeLetter() {
        if (controlLocked || folded) {
            return;
        }
        folded = true;
        setStep(STEP_FOLD_STORE);
        controlLocked = true;
        bus.emit(EventBus.LETTER_FOLD_STORE, FOLD_STORE_DURATION);
        bus.emit(EventBus.SFX, "paper_fold", 1f);
        bus.emit(EventBus.HAPTIC, "hap_letter_open", 1f);
    }

    private void updateFold(float dt) {
        float p = Maths.clamp01(stepTime / FOLD_STORE_DURATION);
        /* fold_and_store reprend unfold_letter A L'ENVERS, image par image */
        bus.emit(EventBus.LETTER_FOLD_PROGRESS, 1f - p, p);
        if (stepTime >= FOLD_STORE_DURATION) {
            setStep(STEP_DESCENT);
            controlLocked = false;
            bus.emit(EventBus.DESCENT_BEGIN, DESCENT_DURATION);
            bus.emit(EventBus.MUSIC_CUE, "SILENCE", 1f);
            bus.emit(EventBus.FOG_CLEARED);
        }
    }

    public void beginDescent() {
        if (step == STEP_CONTROL_RETURN || step == STEP_ROOM_FREE) {
            setStep(STEP_DESCENT);
            controlLocked = false;
            bus.emit(EventBus.DESCENT_BEGIN, DESCENT_DURATION);
        }
    }

    /** 19.11 : 90 s jouables en silence. Le joueur peut regarder. */
    private void updateDescent(float dt) {
        bus.emit(EventBus.LETTER_DESCENT_PROGRESS, Maths.clamp01(stepTime / DESCENT_DURATION));
        if (stepTime >= DESCENT_DURATION) {
            setStep(STEP_FINAL_SHOT);
            bus.emit(EventBus.FINAL_SHOT, FINAL_SHOT_PULLBACK);
            bus.emit(EventBus.MUSIC_CUE, "M19", 3f);
        }
    }

    /** 19.12 : Sol dort contre la porte. La veste. 24 s de recul. */
    private void updateFinalShot(float dt) {
        float p = Maths.clamp01(stepTime / FINAL_SHOT_PULLBACK);
        bus.emit(EventBus.FINAL_SHOT_PROGRESS, p);
        if (stepTime > 3f && stepTime - dt <= 3f) {
            bus.emit(EventBus.FINAL_BEAT, 1, "sol_dort_assis");
        }
        if (stepTime > 8f && stepTime - dt <= 8f) {
            bus.emit(EventBus.FINAL_BEAT, 2, "veste_relayeur_0114");
        }
        if (stepTime > 13f && stepTime - dt <= 13f) {
            bus.emit(EventBus.FINAL_BEAT, 3, "sol_ne_se_reveille_pas");
        }
        if (stepTime > 16f && stepTime - dt <= 16f) {
            bus.emit(EventBus.FINAL_BEAT, 4, "lohen_sassoit_regarde_le_soleil");
        }
        if (stepTime >= FINAL_SHOT_PULLBACK) {
            bus.emit(EventBus.FINAL_BEAT, 5, "miroir_noir_reflete_un_ciel_bleu");
            bus.emit(EventBus.CHAPTER_CARD, "CHAPITRE 1 — LA VILLE QUI RETIENT SON SOUFFLE");
            chapterFinished = true;
            if (state != null) {
                state.setFlag("ending_reached");
            }
            bus.emit(EventBus.ENDING_REACHED, "lettre");
            setStep(STEP_CREDITS);
            creditsSkippable = false;
            bus.emit(EventBus.CREDITS_BEGIN, CREDITS_LOCK);
        }
    }

    /** 19.13 : generique immediat, non skippable 60 s, M20 avec paroles. */
    private void updateCredits(float dt) {
        creditsTime += dt;
        if (!creditsSkippable && creditsTime >= CREDITS_LOCK) {
            creditsSkippable = true;
            bus.emit(EventBus.CREDITS_SKIPPABLE);
        }
        bus.emit(EventBus.CREDITS_PROGRESS, creditsTime, creditsSkippable);
    }

    public void skipCredits() {
        if (!creditsSkippable) {
            return;      /* [OBL] pas skippable pendant les 60 premieres secondes */
        }
        finishCredits();
    }

    public void finishCredits() {
        bus.emit(EventBus.CREDITS_END, creditsTime);
        /* 19.14 : le menu a change, onglet LETTRES — 1/7, lettre relisible */
        bus.emit(EventBus.MENU_CHANGED, "letters_tab", 1, 7);
        bus.emit(EventBus.CHAPTER_COMPLETE);
        if (state != null) {
            state.setFlag("credits_seen");
            state.setFlag("letters_tab_unlocked");
        }
        setStep(STEP_DONE);
        controlLocked = false;
    }

    /** 19.14 : la lettre est relisible integralement, a tout moment. */
    public boolean openForRereading() {
        if (!chapterFinished) {
            return false;
        }
        step = STEP_READING;
        stepTime = 0f;
        scroll = 0f;
        visibleBlocks = 1;
        bellFired = false;
        cameraCut = 0;
        readingDone = false;
        musicTime = 0f;
        controlLocked = false;
        bus.emit(EventBus.LETTER_REREAD);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public int step() {
        return step;
    }

    public String stepName() {
        switch (step) {
            case STEP_DOOR:
                return "palier_206";
            case STEP_ENTRY:
                return "entree_chambre";
            case STEP_ROOM_FREE:
                return "chambre_libre";
            case STEP_NOTEBOOK:
                return "carnet_E30";
            case STEP_LETTER_PROMPT:
                return "prompt_glyphe";
            case STEP_RITUAL:
                return "rituel_31e";
            case STEP_NO_ECHO:
                return "pas_d_echo";
            case STEP_UNFOLD:
                return "depliage";
            case STEP_READING:
                return "lecture";
            case STEP_AFTER:
                return "immobile_6s";
            case STEP_CONTROL_RETURN:
                return "retour_controle";
            case STEP_FOLD_STORE:
                return "sacoche";
            case STEP_DESCENT:
                return "descente";
            case STEP_FINAL_SHOT:
                return "dernier_plan";
            case STEP_CREDITS:
                return "generique";
            case STEP_DONE:
                return "termine";
            default:
                return "inactif";
        }
    }

    public float stepTime() {
        return stepTime;
    }

    public float totalTime() {
        return totalTime;
    }

    public float doorProgress() {
        return doorProgress;
    }

    public float dawnEv() {
        return dawnEv;
    }

    public int blockCount() {
        return blocks.size();
    }

    public String block(int i) {
        return i >= 0 && i < blocks.size() ? blocks.get(i) : "";
    }

    public String staging(int i) {
        return i >= 0 && i < stagings.size() ? stagings.get(i) : "";
    }

    public int visibleBlocks() {
        return visibleBlocks;
    }

    public float scroll() {
        return scroll;
    }

    public float musicTime() {
        return musicTime;
    }

    public boolean bellFired() {
        return bellFired;
    }

    public int bellBlock() {
        return bellBlock;
    }

    public int cameraCut() {
        return cameraCut;
    }

    public boolean thirdCutCried() {
        return thirdCutCried;
    }

    public float readingRealTime() {
        return readingRealTime;
    }

    public boolean controlLocked() {
        return controlLocked;
    }

    public boolean chapterFinished() {
        return chapterFinished;
    }

    public boolean creditsSkippable() {
        return creditsSkippable;
    }

    public float creditsTime() {
        return creditsTime;
    }

    public int promptGlyph() {
        return promptGlyph;   /* toujours le glyphe de l'Echo, jamais « Prendre » */
    }

    public int notebookPage() {
        return notebookPage;
    }

    public int notebookExtractCount() {
        return notebookExtracts.size();
    }

    public String lastLine() {
        return LAST_LINE;
    }

    public boolean active() {
        return step != STEP_INACTIVE && step != STEP_DONE;
    }

    public void reset() {
        step = STEP_INACTIVE;
        stepTime = 0f;
        controlLocked = false;
        chapterFinished = false;
    }
}
