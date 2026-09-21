# -*- coding: utf-8 -*-
"""La musique existe vraiment : cues ecoutes, themes de sequence, triggers un coup."""
import sys

M = "/home/user/Space/android/src/java/com/velmora/lohen/sim/audio/MusicDirector.java"
G = "/home/user/Space/android/src/java/com/velmora/lohen/sim/core/LohenGame.java"
miss = []


def rep(path, old, new, store):
    if old not in store[0]:
        miss.append(path.split("/")[-1] + " :: " + old.splitlines()[0][:64])
        return
    store[0] = store[0].replace(old, new, 1)


m = [open(M, encoding="utf-8").read()]
g = [open(G, encoding="utf-8").read()]

# --- 1. le directeur ecoute ses cues (et ne reboucle pas sur les siens) ----
rep(M, """    public MusicDirector(AudioEngine engine, EventBus bus, Rng rng) {""",
    """    public MusicDirector(AudioEngine engine, EventBus bus, Rng rng) {
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

    private MusicDirector self() {
        return this;
    }

    public MusicDirector(AudioEngine engine, EventBus bus, Rng rng, boolean unused) {""", m)

# --- 2. triggers : une cinematique skippee ne se rejoue pas en boucle ------
rep(G, """        world.build(level);
        sequence = seq;
        state.setSequence(seq);
        npcs.setSequence(seq);
        npcs.loadFromLevel(level);
        clearFigures();
        for (int i = 0; i < triggerFired.length; i++) {
            triggerFired[i] = false;
        }""",
    """        world.build(level);
        /* revenir dans la meme sequence (reprise d'une sauvegarde, etat final
         * d'une cinematique) ne doit pas rearmer les triggers deja consommes :
         * sinon le joueur qui saute C01 se le reprendrait en boucle, et une
         * reprise declencherait a nouveau checkpoint et cinematiques. */
        boolean sameSequence = seq.equals(sequence);
        sequence = seq;
        state.setSequence(seq);
        npcs.setSequence(seq);
        npcs.loadFromLevel(level);
        clearFigures();
        if (!sameSequence || fromStart) {
            for (int i = 0; i < triggerFired.length; i++) {
                triggerFired[i] = false;
            }
        }""", g)

# --- 3. themes de sequence, lettre, generique, menu ------------------------
rep(G, """        streamer.update(0f, lohen.x, lohen.y, lohen.z);
        state.setPosition(lohen.x, lohen.y, lohen.z);
        state.setAltitude(lohen.y);
        mode = MODE_PLAY;
        bus.emit(EventBus.SEQUENCE_CHANGED, seq, level.totalPrimitives());""",
    """        streamer.update(0f, lohen.x, lohen.y, lohen.z);
        state.setPosition(lohen.x, lohen.y, lohen.z);
        state.setAltitude(lohen.y);
        mode = MODE_PLAY;
        music.play(musicForSequence(seq), 2.5f);
        bus.emit(EventBus.SEQUENCE_CHANGED, seq, level.totalPrimitives());""", g)

rep(G, """    /** Le verbe actuellement propose : le HUD en tire un glyphe (14.07). */""",
    """    /**
     * Le theme de fond d'une sequence (BLOC 16). M08 est un silence voulu :
     * la bibliotheque n'a pas de musique, c'est ecrit dans le dossier.
     */
    public static String musicForSequence(String seq) {
        if ("S2".equals(seq)) {
            return "M03";
        }
        if ("S3".equals(seq)) {
            return "M05";
        }
        if ("S4".equals(seq)) {
            return "M04";
        }
        if ("S5".equals(seq)) {
            return "M09";
        }
        if ("S6".equals(seq)) {
            return "M12";
        }
        if ("S7".equals(seq)) {
            return "M13";
        }
        if ("S8".equals(seq)) {
            return "M16";
        }
        return "M01";
    }

    /** Le verbe actuellement propose : le HUD en tire un glyphe (14.07). */""", g)

rep(G, """        mode = MODE_MENU;
        menu.openMain();
        stage(listener, "pret", 1f);""",
    """        mode = MODE_MENU;
        menu.openMain();
        music.play("M21", 2f);          /* 16.09 : le menu a sa boucle */
        stage(listener, "pret", 1f);""", g)

rep(G, """        } else if (EventBus.LETTER_STEP.equals(signal)) {
            if (args != null && args.length > 0 && "STEP_CREDITS".equals(String.valueOf(args[0]))) {
                mode = MODE_CREDITS;
            }
        }""",
    """        } else if (EventBus.LETTER_STEP.equals(signal)) {
            if (args != null && args.length > 0 && "STEP_CREDITS".equals(String.valueOf(args[0]))) {
                mode = MODE_CREDITS;
                music.play("M20", 3f);      /* 16.08 : la chanteuse, une fois */
            }
        } else if (EventBus.LETTER_STARTED.equals(signal)) {
            music.play("M18", 2f);          /* 19.09 : l'orchestration de la lettre */
        } else if (EventBus.CINEMATIC_ENDED.equals(signal)) {
            /* la musique de la sequence reprend, sans coupure seche */
            if (mode == MODE_PLAY) {
                music.play(musicForSequence(sequence), 1.5f);
            }
        } else if (EventBus.COMBAT_ENTERED.equals(signal)) {
            if ("S7".equals(sequence)) {
                music.play("M14", 1f);      /* 12.09 : Anselme, pas de percussio avant P2 */
            }
        }""", g)

open(M, "w", encoding="utf-8").write(m[0])
open(G, "w", encoding="utf-8").write(g[0])
if miss:
    print("MANQUES:")
    for x in miss:
        print("  - " + x)
    sys.exit(1)
print("musique cablee")
