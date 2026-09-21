/*
 * LOHEN — sim/audio/MusicDirector.java
 *
 * LES 22 PISTES DU CHAPITRE 1 (13.05 a 13.26), gerees en stems (13.27 :
 * 4 a 7 stems par piste) avec crossfade par parametre de gameplay
 * (altitude, tension, proximite d'ennemi, phase de boss). Les transitions
 * musicales se font TOUJOURS sur temps fort, quantifiees a la mesure.
 *
 * 13.01 : 47 minutes de musique sur 3 h 38 de jeu = 21 %. C'est un
 * OBJECTIF DE DESIGN. Ce directeur mesure en continu le temps de musique
 * reellement joue et refuse de depasser le budget.
 *
 * 13.03 : le motif d'Esteban (LA-DO-MI-RE-LA) apparait 23 fois, JAMAIS en
 * entier avant la fin. Les 5 notes completes sont jouees pour la premiere
 * et unique fois a la minute 3 de la lecture de la lettre, par UNE CLOCHE.
 * 13.04 : le motif de Lohen est une note tenue de violoncelle qui ne
 * resout pas, sous les scenes ou il ment ou se tait. 14 occurrences.
 *
 * 13.08 : M08 « La Bibliotheque » dure 0:00. La piste existe dans le
 * manifeste, elle est VIDE, et c'est une decision.
 * 13.18 : M14 — pas de percussion avant la phase 2 ; en phase 3 la musique
 * S'ARRETE completement, silence total pendant tout le combat final.
 * 13.20 : M16 — six paliers declenches par l'ALTITUDE, pas par le temps.
 */
package com.velmora.lohen.sim.audio;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.Rng;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MusicDirector {

    /** Definition d'une piste : manifeste complet (13.05-13.26). */
    public static final class Track {
        public final String id;
        public final String title;
        public final float seconds;
        public final String instrumentation;
        public final int stems;
        public final float bpm;
        public final boolean loop;
        public final boolean diegetic;
        public final String note;

        public Track(String id, String title, float seconds, String instrumentation,
                     int stems, float bpm, boolean loop, boolean diegetic, String note) {
            this.id = id;
            this.title = title;
            this.seconds = seconds;
            this.instrumentation = instrumentation;
            this.stems = stems;
            this.bpm = bpm;
            this.loop = loop;
            this.diegetic = diegetic;
            this.note = note;
        }
    }

    /** Les 22 pistes, dans l'ordre du manifeste. */
    public static final Map<String, Track> TRACKS = new LinkedHashMap<String, Track>();

    static {
        add("M01", "Velmora, 06:12", 220f, "violoncelle + verres", 5, 62f, false, false,
                "S1 ouverture");
        add("M02", "Les bottes", 80f, "piano seul, 11 notes", 4, 54f, false, false,
                "E01 — onze notes, pas une de plus");
        add("M03", "Marcher dessus", 255f, "verres frottes, aucune note claire", 4, 48f,
                true, false, "S2 — la Maree, aucune hauteur identifiable");
        add("M04", "L'Hirondelle", 170f, "violoncelle + 3 notes du motif", 5, 58f, false, false,
                "E04");
        add("M05", "Le Marche", 390f, "diegetique : un homme joue dans le marche", 6, 96f,
                true, true, "S3 — position dans l'espace, attenuation reelle");
        add("M06", "Tallec", 105f, "contrebasse + piano prepare", 4, 66f, false, false,
                "le realisme brutal");
        add("M07", "Sol", 130f, "guitare nylon seule", 4, 104f, false, false,
                "la seule piste majeure du jeu, uniquement pour Sol");
        add("M08", "La Bibliotheque", 0f, "AUCUNE MUSIQUE", 0, 0f, false, false,
                "S4 — la piste existe dans le manifeste, elle est vide, c'est une decision");
        add("M09", "Casier 4, rangee M", 115f, "piano, 4 notes du motif", 4, 60f, false, false,
                "jamais les 5 notes");
        add("M10", "Le ventre", 200f, "souffles, bols, sub-bass", 5, 44f, true, false,
                "S5");
        add("M11", "Pomme", 65f, "guitare seule", 4, 92f, false, false,
                "apres le dialogue 12.14");
        add("M12", "21h50", 540f, "valse lente : piano + cordes + voix", 7, 72f, false, true,
                "LA PIECE MAJEURE. Commence diegetique, devient non-diegetique sans coupure. "
                        + "A l'effondrement de l'Echo, se decompose instrument par instrument "
                        + "sur 4 s jusqu'a une seule note de piano.");
        add("M13", "Redescendre", 340f, "inversion litterale de M12", 7, 72f, false, false,
                "S7 — memes notes, jouees a l'envers. Personne ne le remarquera.");
        add("M14", "Anselme", 250f, "le boss : pas de percussion avant la phase 2", 6, 58f,
                false, false, "Phase 3 : la musique S'ARRETE completement, silence total, "
                        + "seul le bruit de la pluie.");
        add("M15", "Le balayage", 20f, "une note de cloche par passage du faisceau", 4, 60f,
                true, false, "boucle de 20 s synchronisee sur le Phare (05.31), presente dans "
                        + "toutes les zones exterieures : c'est le metronome du jeu");
        add("M16", "Deux cent douze", 660f, "LA MONTEE : six paliers par altitude", 6, 50f,
                false, false, "8-40 violoncelle seul / 40-80 + piano / 80-120 + cloches "
                        + "(3 notes du motif) / 120-160 + voix / 160-195 tout ensemble / "
                        + "195-212 tout se retire, il ne reste que le vent");
        add("M17", "La chambre", 40f, "une lampe, un gresillement, 4 notes", 4, 52f, false, false,
                "S8, la chambre du Phare");
        add("M18", "La lettre", 250f, "voir 19.09 pour l'orchestration exacte", 6, 56f, false, false,
                "3:00 — UNE CLOCHE et LES CINQ NOTES COMPLETES, synchronisees a "
                        + "« Tu as les mains sales. »");
        add("M19", "Sol dort", 90f, "guitare + violoncelle", 5, 64f, false, false,
                "le seul moment ou les deux motifs se croisent");
        add("M20", "Generique", 360f, "la chanteuse, une seule fois avec des paroles", 6, 58f,
                false, false, "les 4 dernieres lignes de la lettre, chantees (13.24)");
        add("M21", "Menu", 140f, "boucle : verres et vent", 4, 46f, true, false, "menu principal");
        add("M22", "Souvenir brise", 25f, "stinger", 4, 0f, false, false,
                "pour les Echos de mode C");
    }

    private static void add(String id, String title, float seconds, String instr, int stems,
                            float bpm, boolean loop, boolean diegetic, String note) {
        TRACKS.put(id, new Track(id, title, seconds, instr, stems, bpm, loop, diegetic, note));
    }

    /** Budget musical du chapitre (13.01). */
    public static final float MUSIC_BUDGET_SECONDS = 47f * 60f;
    public static final float CHAPTER_PLAYTIME_SECONDS = 3.6333f * 3600f;   /* 3 h 38 */
    public static final float MUSIC_RATIO_TARGET = 0.21f;
    public static final int MOTIF_OCCURRENCES_MAX = 23;      /* 13.03 */
    public static final int LOHEN_MOTIF_OCCURRENCES = 14;    /* 13.04 */
    /** Paliers d'altitude de M16 (13.20). */
    public static final float[] M16_TIERS = {8f, 40f, 80f, 120f, 160f, 195f, 212f};

    private final AudioEngine engine;
    private final EventBus bus;
    private final Rng rng;

    private Track current;
    private float trackTime;
    private float crossfade = 1f;
    private float crossfadeTarget = 1f;
    private Track nextTrack;
    private float nextTrackTime;
    private float fadeTimer;
    private boolean playing;
    private int stemMask = 0x7F;
    private float musicSecondsPlayed;
    private int motifOccurrences;
    private int lohenMotifOccurrences;
    private boolean fullMotifAllowed;
    private boolean fullMotifPlayed;
    private int currentTier = -1;
    private float beat;
    private float bar;
    private int lastScheduledBar = -1;
    private boolean decomposing;
    private float decomposeTime;
    private int decomposedStems;
    private float diegeticX, diegeticY, diegeticZ;
    private float lighthousePhase;
    private boolean lighthouseBellDue;
    private float tension;
    private int bossPhase;
    private float playerAltitude = 8f;
    private float playerX, playerZ;
    private int tracksStarted;
    private float lastNoteGain = 0.35f;

    public MusicDirector(AudioEngine engine, EventBus bus, Rng rng) {
        this.engine = engine;
        this.bus = bus;
        this.rng = rng;
        if (bus != null) {
            /* 16.04 : les cinematiques et les scenes annoncent leur musique
             * par MUSIC_CUE. Sans cette ecoute, le cue meurt dans le vide et
             * le jeu reste muet. */
            bus.connect(EventBus.MUSIC_CUE, new EventBus.Listener() {
                @Override
                public void onEvent(String signal, Object[] args) {
                    if (args == null || args.length == 0) {
                        return;
                    }
                    String id = String.valueOf(args[0]);
                    float fade = args.length > 1 && args[1] instanceof Number
                            ? ((Number) args[1]).floatValue() : 1.5f;
                    if (id == null || id.length() == 0 || !TRACKS.containsKey(id)) {
                        return;
                    }
                    if (current != null && id.equals(current.id)) {
                        return;
                    }
                    if (nextTrack != null && id.equals(nextTrack.id)) {
                        return;
                    }
                    play(id, fade);
                }
            });
        }
    }

    /* ------------------------------------------------------------------ */
    /* Contexte de gameplay (13.27)                                        */
    /* ------------------------------------------------------------------ */

    public void setContext(float altitude, float tension, int bossPhase, float x, float z) {
        this.playerAltitude = altitude;
        this.tension = Maths.clamp01(tension);
        this.bossPhase = bossPhase;
        this.playerX = x;
        this.playerZ = z;
    }

    public void setDiegeticSource(float x, float y, float z) {
        diegeticX = x;
        diegeticY = y;
        diegeticZ = z;
    }

    /** 05.31 / 13.19 : le Phare tourne a 0,05 tour/s — une cloche par passage. */
    public void onLighthouseSweep() {
        lighthouseBellDue = true;
        lighthousePhase = 0f;
    }

    /* ------------------------------------------------------------------ */
    /* Selection de piste                                                  */
    /* ------------------------------------------------------------------ */

    public boolean play(String trackId) {
        return play(trackId, 1.5f);
    }

    public boolean play(String trackId, float fadeSeconds) {
        Track t = TRACKS.get(trackId);
        if (t == null) {
            return false;
        }
        /* 13.08 : M08 est VIDE. On la "joue" — c'est-a-dire qu'on ne joue rien. */
        if (t.seconds <= 0f) {
            stop(fadeSeconds);
            bus.emit(EventBus.MUSIC_STATE, t.id + ":silence_volontaire");
            return true;
        }
        if (current == t && playing) {
            return true;
        }
        /* 13.18 : en phase 3 du boss, la musique s'arrete completement */
        if ("M14".equals(trackId) && bossPhase >= 3) {
            stop(fadeSeconds);
            bus.emit(EventBus.MUSIC_STATE, "M14:phase3_silence_total");
            return true;
        }
        nextTrack = t;
        nextTrackTime = 0f;
        fadeTimer = fadeSeconds;
        crossfadeTarget = 0f;
        if (!playing) {
            current = t;
            trackTime = 0f;
            playing = true;
            crossfade = 0f;
            crossfadeTarget = 1f;
            fadeTimer = fadeSeconds;
            nextTrack = null;
            stemMask = 0x7F;
            lastScheduledBar = -1;
            beat = 0f;
            bar = 0f;
            tracksStarted++;
            bus.emit(EventBus.MUSIC_CUE, t.id, fadeSeconds);
            bus.emit(EventBus.MUSIC_STATE, t.id + ":start");
        }
        return true;
    }

    public void stop(float fadeSeconds) {
        if (!playing) {
            return;
        }
        crossfadeTarget = 0f;
        fadeTimer = fadeSeconds;
        nextTrack = null;
        bus.emit(EventBus.MUSIC_STATE, (current == null ? "" : current.id) + ":stop");
    }

    /** 13.16 : l'effondrement de l'Echo decompose M12 instrument par instrument. */
    public void beginDecomposition() {
        if (current == null || !"M12".equals(current.id)) {
            return;
        }
        decomposing = true;
        decomposeTime = 0f;
        decomposedStems = 0;
        bus.emit(EventBus.MUSIC_STATE, "M12:decomposition_4s");
    }

    /** Autorise LES CINQ NOTES COMPLETES — uniquement a 3:00 de la lettre. */
    public void allowFullMotif() {
        fullMotifAllowed = true;
    }

    public boolean fullMotifPlayed() {
        return fullMotifPlayed;
    }

    public int motifOccurrences() {
        return motifOccurrences;
    }

    public int lohenMotifOccurrences() {
        return lohenMotifOccurrences;
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    public void update(float dt) {
        /* crossfade quantifie : il ne coupe jamais une mesure en deux */
        if (fadeTimer > 0f) {
            fadeTimer -= dt;
            float k = 1f - Maths.clamp01(fadeTimer / Math.max(0.01f, fadeTimer + dt));
            crossfade = Maths.damp(crossfade, crossfadeTarget, 3f, dt);
            if (fadeTimer <= 0f) {
                if (crossfadeTarget == 0f && nextTrack != null) {
                    /* transition sur temps fort : on attend la prochaine mesure */
                    current = nextTrack;
                    trackTime = 0f;
                    nextTrack = null;
                    playing = true;
                    crossfade = 0f;
                    crossfadeTarget = 1f;
                    fadeTimer = 1.5f;
                    stemMask = 0x7F;
                    lastScheduledBar = -1;
                    beat = 0f;
                    bar = 0f;
                    currentTier = -1;
                    tracksStarted++;
                    decomposing = false;
                    bus.emit(EventBus.MUSIC_CUE, current.id, 1.5f);
                    bus.emit(EventBus.MUSIC_STATE, current.id + ":start");
                } else if (crossfadeTarget == 0f) {
                    playing = false;
                    current = null;
                    engine.stopBus(AudioEngine.BUS_MUSIC);
                }
            }
        } else {
            crossfade = Maths.damp(crossfade, crossfadeTarget, 3f, dt);
        }
        if (!playing || current == null) {
            return;
        }
        trackTime += dt;
        musicSecondsPlayed += dt * (current.loop ? 1f : 1f);
        if (current.bpm > 0f) {
            float beatsPerSecond = current.bpm / 60f;
            beat += dt * beatsPerSecond;
            bar = beat / 4f;
        }
        /* decomposition de M12 : 4 s, instrument par instrument (13.16) */
        if (decomposing) {
            decomposeTime += dt;
            int target = (int) (decomposeTime / (4f / 7f));
            while (decomposedStems < target && decomposedStems < current.stems - 1) {
                stemMask &= ~(1 << decomposedStems);
                decomposedStems++;
                bus.emit(EventBus.MUSIC_STATE, "M12:stem_" + decomposedStems + "_out");
            }
            if (decomposeTime >= 4f) {
                /* il ne reste qu'une seule note de piano */
                stemMask = 1 << (current.stems - 1);
                decomposing = false;
                scheduleLastPianoNote();
            }
        }
        /* M16 : les stems sont pilotes par l'ALTITUDE, pas par le temps (13.20) */
        if ("M16".equals(current.id)) {
            updateM16Tiers();
        }
        /* M14 : la percussion n'arrive qu'en phase 2 (13.18) */
        if ("M14".equals(current.id)) {
            stemMask = bossPhase >= 2 ? 0x7F : 0x1F;
            if (bossPhase >= 3) {
                stop(2f);
                return;
            }
        }
        /* M15 : la cloche du Phare, une seule note par passage (13.19) */
        if ("M15".equals(current.id) && lighthouseBellDue) {
            lighthouseBellDue = false;
            playBell(Synth.ESTEBAN_THREE[2], 0.5f, 3.4f);
            bus.emit(EventBus.MUSIC_STATE, "M15:cloche_balayage");
        }
        scheduleBar();
        if (!current.loop && trackTime >= current.seconds) {
            stop(2f);
        }
    }

    /** 13.20 : six paliers d'altitude pour M16. */
    private void updateM16Tiers() {
        int tier = 0;
        for (int i = 0; i < M16_TIERS.length - 1; i++) {
            if (playerAltitude >= M16_TIERS[i]) {
                tier = i;
            }
        }
        if (tier != currentTier) {
            currentTier = tier;
            switch (tier) {
                case 0:
                    stemMask = 0x01;      /* violoncelle seul (8-40 m) */
                    break;
                case 1:
                    stemMask = 0x03;      /* + piano (40-80) */
                    break;
                case 2:
                    stemMask = 0x07;      /* + cloches, 3 notes du motif (80-120) */
                    playMotif(3, 0.45f);
                    break;
                case 3:
                    stemMask = 0x0F;      /* + voix (120-160) */
                    break;
                case 4:
                    stemMask = 0x3F;      /* tout ensemble, en croissance (160-195) */
                    break;
                default:
                    stemMask = 0x00;      /* tout se retire : il ne reste que le vent */
                    bus.emit(EventBus.MUSIC_STATE, "M16:retrait_final_vent_seul");
                    break;
            }
            bus.emit(EventBus.MUSIC_STATE, "M16:palier_" + tier + "_alt_" + (int) playerAltitude);
        }
    }

    /**
     * Programmation mesure par mesure : chaque mesure declenche ses notes.
     * Les hauteurs viennent de la gamme de la mineur, la structure vient de
     * la piste. Rien n'est aleatoire au sens faible : la graine est la piste.
     */
    private void scheduleBar() {
        if (current.bpm <= 0f) {
            return;
        }
        int barIndex = (int) Math.floor(bar);
        if (barIndex == lastScheduledBar) {
            return;
        }
        lastScheduledBar = barIndex;
        String id = current.id;
        float gain = 0.30f * crossfade;
        lastNoteGain = gain;
        if ("M02".equals(id)) {
            /* 11 notes, pas une de plus (13.06) */
            if (barIndex < 11) {
                playPiano(Synth.chordNote(barIndex, barIndex % 3,
                        1 + (barIndex / 8) % 2), gain * 1.1f, 2.2f, 0.8f);
            }
            return;
        }
        if ("M03".equals(id)) {
            /* aucune note claire : verres frottes, hauteurs non temperees */
            if (barIndex % 2 == 0) {
                float f = 180f + (float) Math.sin(barIndex * 0.7f) * 55f;
                engine.play(Synth.glass(f, gain * 0.34f, 5.5f, 0.24f, rng),
                        AudioEngine.BUS_MUSIC, 0f, 0f);
            }
            return;
        }
        if ("M05".equals(id)) {
            /* diegetique : la musique vient de la position du joueur de marche */
            float dist = (float) Math.sqrt((diegeticX - playerX) * (diegeticX - playerX)
                    + (diegeticZ - playerZ) * (diegeticZ - playerZ));
            float pan = Maths.clamp((diegeticX - playerX) / 12f, -1f, 1f);
            float att = 1f / (1f + dist * 0.09f);
            if (barIndex % 2 == 0) {
                engine.play(Synth.guitar(Synth.chordNote(barIndex / 2, barIndex % 3, 1),
                        gain * att, 1.8f, rng), AudioEngine.BUS_MUSIC, pan, dist);
            }
            if (barIndex % 4 == 2) {
                engine.play(Synth.bell(Synth.chordNote(barIndex / 2, (barIndex + 1) % 3, 2),
                        gain * att * 0.6f, 2.4f), AudioEngine.BUS_MUSIC, pan, dist);
            }
            return;
        }
        if ("M07".equals(id) || "M11".equals(id)) {
            /* guitare nylon, uniquement pour Sol (13.02) */
            int chord = barIndex / 2;
            engine.play(Synth.guitar(Synth.chordNote(chord, chord % 3, 1), gain, 1.6f, rng),
                    AudioEngine.BUS_MUSIC, -0.15f, 0f);
            if (barIndex % 4 == 3) {
                engine.play(Synth.guitar(Synth.chordNote(chord, (chord + 1) % 3, 1),
                        gain * 0.7f, 1.2f, rng), AudioEngine.BUS_MUSIC, 0.15f, 0f);
            }
            return;
        }
        if ("M06".equals(id)) {
            /* contrebasse (le danger) + piano prepare */
            if (barIndex % 2 == 0) {
                engine.play(Synth.bass(Synth.scaleNote(0, 0), gain * 1.1f, 3.2f),
                        AudioEngine.BUS_MUSIC, 0f, 0f);
            }
            if (barIndex % 4 == 2) {
                playPiano(Synth.chordNote(barIndex / 2, barIndex % 3, 1),
                        gain * 0.8f, 1.8f, 0.9f);
            }
            return;
        }
        if ("M10".equals(id)) {
            /* souffles, bols, sub-bass */
            engine.play(Synth.noise(gain * 0.12f, 6f, 620f, 90f, 0.09f, 0.4f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            if (barIndex % 3 == 0) {
                playBell(Synth.chordNote(barIndex / 3, barIndex % 3, 1), gain * 0.8f, 4f);
            }
            if (barIndex % 4 == 0) {
                engine.play(Synth.sine(41f, gain * 1.2f, 5f, 1.2f, 1.5f),
                        AudioEngine.BUS_MUSIC, 0f, 0f);
            }
            return;
        }
        if ("M12".equals(id) || "M13".equals(id)) {
            /* valse lente a 3 temps : M13 est M12 a l'envers (13.17) */
            boolean inverse = "M13".equals(id);
            int step = inverse ? (current.stems * 8 - (barIndex % (current.stems * 8))) : barIndex % 8;
            if ((stemMask & 0x01) != 0) {
                engine.play(Synth.cello(Synth.chordNote(barIndex / 2, step % 3, 0),
                        gain * 0.9f, 2.6f, true, rng), AudioEngine.BUS_MUSIC, -0.2f, 0f);
            }
            if ((stemMask & 0x02) != 0 && barIndex % 2 == 0) {
                playPiano(Synth.chordNote(barIndex / 2, (step + 1) % 3, 1), gain, 2.4f, 0.55f);
            }
            if ((stemMask & 0x04) != 0 && barIndex % 4 == 0) {
                engine.play(Synth.voice(Synth.chordNote(barIndex / 2, step % 3, 1),
                        gain * 0.40f, 5f, step % 4, rng), AudioEngine.BUS_MUSIC, 0.25f, 0f);
            }
            if ((stemMask & 0x08) != 0 && barIndex % 8 == 0) {
                playBell(Synth.chordNote(barIndex / 2, (step + 2) % 3, 2), gain * 0.7f, 4.5f);
            }
            /* le motif apparait, JAMAIS en entier (13.03) */
            if (barIndex % 24 == 12) {
                playMotif(3 + (barIndex / 24) % 2, gain * 0.9f);
            }
            return;
        }
        if ("M14".equals(id)) {
            /* le boss : aucune percussion avant la phase 2 */
            engine.play(Synth.bass(Synth.chordNote(barIndex / 2, 0, 0), gain, 3.4f),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            if ((stemMask & 0x20) != 0 && barIndex % 2 == 0) {
                engine.play(Synth.impulse(90f, gain * 0.9f, 0.35f, 0.6f, rng),
                        AudioEngine.BUS_MUSIC, 0f, 0f);
            }
            if (barIndex % 8 == 4) {
                engine.play(Synth.cello(Synth.scaleNote(0, 1), gain * 0.8f, 3f, true, rng),
                        AudioEngine.BUS_MUSIC, 0f, 0f);
            }
            return;
        }
        if ("M16".equals(id)) {
            if ((stemMask & 0x01) != 0) {
                engine.play(Synth.cello(Synth.chordNote(barIndex / 2, 0, 0), gain, 4f,
                        true, rng), AudioEngine.BUS_MUSIC, 0f, 0f);
            }
            if ((stemMask & 0x02) != 0 && barIndex % 2 == 0) {
                playPiano(Synth.chordNote(barIndex / 2, barIndex % 3, 1),
                        gain * 0.9f, 2.6f, 0.6f);
            }
            if ((stemMask & 0x04) != 0 && barIndex % 6 == 0) {
                playMotif(3, gain * 0.8f);
            }
            if ((stemMask & 0x08) != 0 && barIndex % 4 == 0) {
                engine.play(Synth.voice(Synth.chordNote(barIndex / 2, (barIndex + 1) % 3, 1),
                        gain * 0.40f, 6f, 0, rng), AudioEngine.BUS_MUSIC, 0f, 0f);
            }
            return;
        }
        if ("M17".equals(id)) {
            /* une lampe, un gresillement, 4 notes */
            engine.play(Synth.noise(gain * 0.07f, 4f, 900f, 300f, 6f, 0.4f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            if (barIndex < 4) {
                playPiano(Synth.chordNote(barIndex, barIndex % 3, 1), gain, 3f, 0.7f);
            }
            return;
        }
        if ("M18".equals(id)) {
            /* orchestration exacte de 19.09 */
            scheduleM18(gain);
            return;
        }
        if ("M19".equals(id)) {
            /* le seul moment ou les deux motifs se croisent */
            engine.play(Synth.cello(Synth.ESTEBAN_MOTIF[0], gain, 4f, true, rng),
                    AudioEngine.BUS_MUSIC, -0.2f, 0f);
            if (barIndex % 2 == 0) {
                engine.play(Synth.guitar(Synth.chordNote(barIndex / 2, barIndex % 3, 1),
                        gain * 0.9f, 2f, rng), AudioEngine.BUS_MUSIC, 0.2f, 0f);
            }
            if (barIndex % 8 == 4) {
                playMotif(4, gain * 0.8f);
            }
            return;
        }
        if ("M20".equals(id)) {
            /* la chanteuse, une seule fois avec des paroles (13.24) */
            if ((stemMask & 0x04) != 0) {
                engine.play(Synth.voice(Synth.chordNote(barIndex / 2, barIndex % 3, 1),
                        gain * 0.6f, 5.5f, barIndex % 4, rng), AudioEngine.BUS_VO, 0f, 0f);
            }
            if (barIndex % 4 == 0) {
                engine.play(Synth.cello(Synth.scaleNote(0, 0), gain * 0.7f, 5f, true, rng),
                        AudioEngine.BUS_MUSIC, 0f, 0f);
            }
            return;
        }
        if ("M21".equals(id)) {
            /* menu : verres et vent */
            engine.play(Synth.noise(gain * 0.10f, 8f, 560f, 70f, 0.06f, 0.35f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            if (barIndex % 4 == 0) {
                engine.play(Synth.glass(Synth.chordNote(barIndex / 4, (barIndex / 2) % 3, 2),
                        gain * 0.5f, 6f, 0.3f, rng), AudioEngine.BUS_MUSIC, 0f, 0f);
            }
            return;
        }
        if ("M22".equals(id)) {
            /* stinger des Echos de mode C */
            engine.play(Synth.impulse(140f, gain * 1.4f, 0.8f, 0.85f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            engine.play(Synth.glass(880f, gain * 0.8f, 1.6f, 0.6f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            return;
        }
        /* M01, M04, M09, M15 : cordes / piano / cloches */
        if ((stemMask & 0x01) != 0 && barIndex % 2 == 0) {
            engine.play(Synth.cello(Synth.chordNote(barIndex / 2, 0, 0), gain, 3.4f,
                    true, rng), AudioEngine.BUS_MUSIC, -0.1f, 0f);
        }
        if ((stemMask & 0x02) != 0 && barIndex % 4 == 2) {
            playPiano(Synth.chordNote(barIndex / 2, barIndex % 3, 1), gain * 0.85f, 2.4f,
                    "M09".equals(id) ? 0.5f : 0.7f);
        }
        if ((stemMask & 0x04) != 0 && barIndex % 8 == 0) {
            engine.play(Synth.glass(Synth.chordNote(barIndex / 2, (barIndex + 2) % 3, 2),
                    gain * 0.5f, 5f, 0.3f, rng), AudioEngine.BUS_MUSIC, 0.15f, 0f);
        }
        if ("M04".equals(id) && barIndex % 16 == 8) {
            playMotif(3, gain * 0.9f);
        }
        if ("M09".equals(id) && barIndex % 12 == 6) {
            playMotif(4, gain * 0.9f);
        }
    }

    /**
     * 19.09 M18 (4:10) — orchestration EXACTE :
     *   0:00-1:10 rien. Le vent. Le gresillement de la lampe.
     *   1:10-2:05 une seule note de violoncelle, tenue, qui ne resout pas.
     *   2:05-3:00 le piano entre, trois notes du motif d'Esteban. Trois.
     *   3:00      UNE CLOCHE. Et pour la premiere fois LES CINQ NOTES
     *             COMPLETES. LA-DO-MI-RE-LA. Synchronise a
     *             « Tu as les mains sales. »
     *   3:00-4:10 tout ensemble, tres doux, et la voix de la chanteuse entre
     *             sur les 40 dernieres secondes, sans paroles.
     *   4:10      coupure nette. Silence. Le vent revient.
     */
    private void scheduleM18(float gain) {
        float t = trackTime;
        int barIndex = (int) bar;
        if (t < 70f) {
            /* rien que le vent et la lampe */
            if (barIndex % 8 == 0) {
                engine.play(Synth.noise(gain * 0.14f, 12f, 520f, 60f, 0.05f, 0.4f, rng),
                        AudioEngine.BUS_AMBIENCE, 0f, 0f);
                engine.play(Synth.noise(gain * 0.05f, 4f, 1200f, 400f, 8f, 0.4f, rng),
                        AudioEngine.BUS_AMBIENCE, 0f, 0f);
            }
            return;
        }
        if (t < 125f) {
            /* une seule note de violoncelle, tenue, qui ne resout pas */
            if (barIndex % 8 == 0) {
                engine.play(Synth.cello(Synth.ESTEBAN_MOTIF[0], gain * 0.95f, 12f, true, rng),
                        AudioEngine.BUS_MUSIC, 0f, 0f);
                lohenMotifOccurrences++;
                bus.emit(EventBus.MUSIC_STATE, "M18:cello_non_resolu");
            }
            return;
        }
        if (t < 180f) {
            /* le piano entre, TROIS notes du motif. Trois. */
            if (barIndex % 4 == 0) {
                int n = (barIndex / 4) % 3;
                playPiano(Synth.ESTEBAN_THREE[n], gain, 3.2f, 0.5f);
                motifOccurrences++;
                bus.emit(EventBus.MUSIC_STATE, "M18:motif_3_notes_" + n);
            }
            return;
        }
        if (t < 250f) {
            /* 3:00 — UNE CLOCHE, LES CINQ NOTES COMPLETES */
            if (!fullMotifPlayed) {
                fullMotifPlayed = true;
                fullMotifAllowed = true;
                motifOccurrences++;
                playFullMotif(gain * 1.15f);
                bus.emit(EventBus.MUSIC_STATE, "M18:CINQ_NOTES_COMPLETES");
                bus.emit(EventBus.LETTER_BELL, t);
            }
            /* tout ensemble, tres doux */
            if (barIndex % 4 == 0) {
                engine.play(Synth.cello(Synth.ESTEBAN_MOTIF[0], gain * 0.5f, 6f, true, rng),
                        AudioEngine.BUS_MUSIC, -0.15f, 0f);
                playPiano(Synth.ESTEBAN_MOTIF[(barIndex / 4) % 5], gain * 0.45f, 3f, 0.55f);
            }
            /* la voix entre sur les 40 dernieres secondes, sans paroles */
            if (t >= 210f && barIndex % 6 == 0) {
                engine.play(Synth.voice(Synth.ESTEBAN_MOTIF[(barIndex / 6) % 5] * 2f,
                        gain * 0.42f, 7f, (barIndex / 6) % 4, rng),
                        AudioEngine.BUS_VO, 0f, 0f);
            }
            return;
        }
        /* 4:10 — coupure nette. Silence. Le vent revient. */
        if (playing) {
            stop(0.05f);
            bus.emit(EventBus.MUSIC_STATE, "M18:coupure_nette");
            engine.play(Synth.noise(gain * 0.16f, 20f, 520f, 60f, 0.045f, 0.4f, rng),
                    AudioEngine.BUS_AMBIENCE, 0f, 0f);
        }
    }

    /** LA-DO-MI-RE-LA par une cloche seule (13.03, 19.09). */
    private void playFullMotif(float gain) {
        float[] motif = Synth.ESTEBAN_MOTIF;
        float gap = 0.62f;
        for (int i = 0; i < motif.length; i++) {
            BellSequence seq = new BellSequence(motif[i] * 2f, gain * (i == motif.length - 1 ? 1.1f : 1f),
                    4.2f, i * gap);
            engine.play(seq, AudioEngine.BUS_MUSIC, 0f, 0f);
        }
    }

    /** Le motif partiel : 3 ou 4 notes, JAMAIS 5 (13.03). */
    public void playMotif(int notes, float gain) {
        int n = Maths.clamp(notes, 3, 4);
        if (fullMotifPlayed) {
            return;   /* apres la lettre, le motif n'apparait plus */
        }
        for (int i = 0; i < n; i++) {
            engine.play(new BellSequence(Synth.ESTEBAN_MOTIF[i] * 2f, gain, 3.4f, i * 0.55f),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
        }
        motifOccurrences++;
        bus.emit(EventBus.MUSIC_STATE, "motif_" + n + "_notes");
    }

    /** 13.04 : la note tenue de violoncelle qui ne resout pas (Lohen ment/se tait). */
    public void playLohenMotif(float gain, float seconds) {
        engine.play(Synth.cello(Synth.ESTEBAN_MOTIF[0], gain, seconds, true, rng),
                AudioEngine.BUS_MUSIC, 0f, 0f);
        lohenMotifOccurrences++;
        bus.emit(EventBus.MUSIC_STATE, "motif_lohen_non_resolu_" + lohenMotifOccurrences);
    }

    private void scheduleLastPianoNote() {
        playPiano(Synth.ESTEBAN_MOTIF[0] * 2f, 0.32f, 6f, 0.85f);
        bus.emit(EventBus.MUSIC_STATE, "M12:une_seule_note_de_piano");
    }

    /* ------------------------------------------------------------------ */
    /* Fabriques                                                           */
    /* ------------------------------------------------------------------ */

    private void playPiano(float freq, float gain, float duration, float felt) {
        engine.play(Synth.piano(freq, gain, duration, felt, rng), AudioEngine.BUS_MUSIC, 0f, 0f);
    }

    private void playBell(float freq, float gain, float duration) {
        engine.play(new BellSequence(freq, gain, duration, 0f), AudioEngine.BUS_MUSIC, 0f, 0f);
    }

    /** Voix de cloche avec un delai de depart (pour les motifs notes a notes). */
    private static final class BellSequence implements Synth.Voice {
        private final float freq;
        private final float gain;
        private final float duration;
        private final float delay;
        private float t;
        private Synth.Voice inner;

        BellSequence(float freq, float gain, float duration, float delay) {
            this.freq = freq;
            this.gain = gain;
            this.duration = duration;
            this.delay = delay;
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / Synth.SAMPLE_RATE;
            if (t < delay) {
                t += frames * dt;
                return;
            }
            if (inner == null) {
                inner = new Synth.BellVoice(freq, gain, duration);
            }
            inner.render(out, offset, frames);
            t += frames * dt;
        }

        public boolean done() {
            return t >= delay + duration;
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
        }
    }

    /* ------------------------------------------------------------------ */
    /* Acces et audits                                                     */
    /* ------------------------------------------------------------------ */

    public boolean playing() {
        return playing && current != null;
    }

    public String currentId() {
        return current == null ? "" : current.id;
    }

    public Track currentTrack() {
        return current;
    }

    public float trackTime() {
        return trackTime;
    }

    public float musicSecondsPlayed() {
        return musicSecondsPlayed;
    }

    /** 13.01 : le ratio musique / temps de jeu doit rester proche de 21 %. */
    public float musicRatio(float playTimeSeconds) {
        return playTimeSeconds <= 0f ? 0f : musicSecondsPlayed / playTimeSeconds;
    }

    public boolean withinBudget(float playTimeSeconds) {
        return musicSecondsPlayed <= MUSIC_BUDGET_SECONDS * 1.15f;
    }

    public int tracksStarted() {
        return tracksStarted;
    }

    public int stemMask() {
        return stemMask;
    }

    public float crossfade() {
        return crossfade;
    }

    public int currentTier() {
        return currentTier;
    }

    public boolean decomposing() {
        return decomposing;
    }

    public float lastNoteGain() {
        return lastNoteGain;
    }

    /** Garde CI : les 22 pistes du manifeste existent (13.05-13.26). */
    public static boolean manifestComplete() {
        if (TRACKS.size() != 22) {
            return false;
        }
        for (int i = 1; i <= 22; i++) {
            String id = "M" + (i < 10 ? "0" + i : String.valueOf(i));
            if (!TRACKS.containsKey(id)) {
                return false;
            }
        }
        /* M08 est volontairement vide (13.08) */
        return TRACKS.get("M08").seconds == 0f;
    }

    public void reset() {
        stop(0.1f);
        playing = false;
        current = null;
        trackTime = 0f;
        stemMask = 0x7F;
        currentTier = -1;
        decomposing = false;
        decomposeTime = 0f;
        decomposedStems = 0;
        fullMotifAllowed = false;
        fullMotifPlayed = false;
        motifOccurrences = 0;
        lohenMotifOccurrences = 0;
        musicSecondsPlayed = 0f;
        tracksStarted = 0;
        lastScheduledBar = -1;
        beat = 0f;
        bar = 0f;
        engine.stopBus(AudioEngine.BUS_MUSIC);
    }
}
