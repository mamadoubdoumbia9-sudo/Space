# -*- coding: utf-8 -*-
"""Le chargement annonce ses etapes reelles (02.13 : il ne ment jamais)."""
import sys

P = "/home/user/Space/android/src/java/com/velmora/lohen/sim/core/LohenGame.java"
s = open(P, encoding="utf-8").read()
old = """    public boolean boot() {
        bootOk = db.loadAll();
        tuning.load();
        audio.applyOptions();
        hud.applyOptions();
        loc.setLanguage(loc.language());
        quests.begin();
        letter.loadLetter();
        mode = MODE_MENU;
        menu.openMain();
        bus.emit(EventBus.LOADING_ENDED, bootOk ? 1f : 0f);
        return bootOk;
    }"""
new = """    /**
     * Ecoute du demarrage : chaque etape correspond a un travail reel, donc la
     * barre de progression ne ment jamais (02.13).
     */
    public interface BootListener {
        void stage(String name, float progress);
    }

    public boolean boot() {
        return boot(null);
    }

    public boolean boot(BootListener listener) {
        stage(listener, "contenu", 0.04f);
        bootOk = db.loadAll();
        if (!bootOk) {
            stage(listener, "erreur", 1f);
            bus.emit(EventBus.LOADING_ENDED, 0f);
            return false;
        }
        stage(listener, "reglages", 0.58f);
        tuning.load();
        stage(listener, "audio", 0.68f);
        audio.applyOptions();
        hud.applyOptions();
        loc.setLanguage(loc.language());
        stage(listener, "journal", 0.78f);
        quests.begin();
        stage(listener, "lettre", 0.88f);
        letter.loadLetter();
        mode = MODE_MENU;
        menu.openMain();
        stage(listener, "pret", 1f);
        bus.emit(EventBus.LOADING_ENDED, 1f);
        return bootOk;
    }

    private static void stage(BootListener l, String name, float p) {
        if (l != null) {
            l.stage(name, p);
        }
    }"""
if old not in s:
    print("MANQUE: boot()")
    sys.exit(1)
s = s.replace(old, new, 1)
open(P, "w", encoding="utf-8").write(s)
print("etapes de boot ok")
