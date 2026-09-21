/*
 * LOHEN — sim/ai/AttackTokenScheduler.java
 *
 * 08.14 : quatre Figures maximum simultanement, DEUX jetons d'attaque
 * au-dela de ce nombre. Sans cette regle, le joueur est submerge et le
 * combat devient illisible — c'est un choix de design, pas une optimisation.
 */
package com.velmora.lohen.sim.ai;

import com.velmora.lohen.sim.core.Tuning;

import java.util.ArrayList;
import java.util.List;

public final class AttackTokenScheduler {

    private final Tuning tuning;
    private final List<Figure> active = new ArrayList<Figure>(8);
    private final List<Figure> tokens = new ArrayList<Figure>(4);
    private int granted, denied, released;
    private float fairnessTimer;
    private int fairnessIndex;

    public AttackTokenScheduler(Tuning tuning) {
        this.tuning = tuning;
    }

    public void register(Figure f) {
        if (!active.contains(f)) {
            active.add(f);
        }
    }

    public void unregister(Figure f) {
        active.remove(f);
        tokens.remove(f);
    }

    public void clear() {
        active.clear();
        tokens.clear();
    }

    /** Nombre de Figures actuellement en engagement. */
    public int engagedCount() {
        int n = 0;
        for (int i = 0; i < active.size(); i++) {
            Figure f = active.get(i);
            if (f.alive() && !f.peaceful()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Demande un jeton d'attaque. Refuse si la limite est atteinte.
     * Regle : max(4) Figures simultanées ; au-dela, seuls 2 jetons circulent.
     */
    public boolean request(Figure f) {
        if (f == null || !f.alive() || f.peaceful()) {
            return false;
        }
        if (tokens.contains(f)) {
            return true;
        }
        int limit = tuning.maxSimultaneous;
        int engaged = engagedCount();
        if (engaged > limit) {
            limit = tuning.attackTokenAbove;
        }
        /* nettoie les jetons des Figures mortes ou staggered */
        for (int i = tokens.size() - 1; i >= 0; i--) {
            Figure t = tokens.get(i);
            if (!t.alive() || t.staggering() || t.peaceful()) {
                tokens.remove(i);
            }
        }
        if (tokens.size() >= limit) {
            denied++;
            return false;
        }
        tokens.add(f);
        granted++;
        return true;
    }

    public void release(Figure f) {
        if (tokens.remove(f)) {
            released++;
        }
    }

    /** Rotation equitable : aucune Figure ne monopolise le jeton (08.14). */
    public void update(float dt) {
        fairnessTimer += dt;
        if (fairnessTimer < 4f || tokens.isEmpty() || active.size() <= tokens.size()) {
            return;
        }
        fairnessTimer = 0f;
        for (int i = 0; i < active.size(); i++) {
            fairnessIndex = (fairnessIndex + 1) % active.size();
            Figure f = active.get(fairnessIndex);
            if (f.alive() && !f.peaceful() && !f.staggering() && !tokens.contains(f)) {
                Figure oldest = tokens.get(0);
                if (oldest.stateTime() > 3f) {
                    tokens.remove(0);
                    tokens.add(f);
                    break;
                }
            }
        }
    }

    public int granted() {
        return granted;
    }

    public int denied() {
        return denied;
    }

    public int released() {
        return released;
    }

    public int tokenCount() {
        return tokens.size();
    }

    public boolean hasToken(Figure f) {
        return tokens.contains(f);
    }
}
