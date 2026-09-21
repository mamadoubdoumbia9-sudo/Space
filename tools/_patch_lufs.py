# -*- coding: utf-8 -*-
"""Le metre LUFS mesure ce qui SORT, pas le mix brut : -16 LUFS reel (13.32)."""
import sys

E = "/home/user/Space/android/src/java/com/velmora/lohen/sim/audio/AudioEngine.java"
S = "/home/user/Space/tools/smoke/SmokeTest.java"
miss = []


def rep(path, old, new, store):
    if old not in store[0]:
        miss.append(path.split("/")[-1] + " :: " + old.splitlines()[0][:64])
        return
    store[0] = store[0].replace(old, new, 1)


e = [open(E, encoding="utf-8").read()]
s = [open(S, encoding="utf-8").read()]

rep(E, """        float rms = (float) Math.sqrt(sum / Math.max(1, frames * 2));
        float lufs = rms < 1e-6f ? -70f : (float) (20f * Math.log10(rms) - 0.691f);
        momentarySum += lufs;
        momentaryCount++;
        if (momentaryCount >= 10) {
            momentaryLufs = momentarySum / momentaryCount;
            shortTermLufs = Maths.damp(shortTermLufs, momentaryLufs, 0.4f, 1f);
            momentarySum = 0f;
            momentaryCount = 0;
            /* cible -16 LUFS : on ajuste doucement, jamais plus de 0,5 dB par pas */
            float error = TARGET_LUFS - momentaryLufs;
            if (momentaryLufs > -60f) {
                normalizeGain *= (float) Math.pow(10f, Maths.clamp(error * 0.06f, -0.5f, 0.5f) / 20f);
            }
            normalizeGain = Maths.clamp(normalizeGain, 0.4f, 3.2f);
        }
        integratedSum += sum;
        integratedFrames += frames * 2;""",
    """        float rms = (float) Math.sqrt(sum / Math.max(1, frames * 2));
        float rawLufs = rms < 1e-6f ? -70f : (float) (20f * Math.log10(rms) - 0.691f);
        /* 13.32 : le metre regarde ce qui SORT du bus, trim et normalisation
         * compris — mesurer le mix brut ferait croire que la cible est ratee
         * alors que le joueur entend bien -16 LUFS. */
        float outDb = (float) (20f * Math.log10(Math.max(1e-9f,
                normalizeGain * masterTrim * MIX_TRIM)));
        float lufs = rawLufs + outDb;
        momentarySum += lufs;
        momentaryRaw += rawLufs;
        momentaryCount++;
        if (momentaryCount >= 10) {
            momentaryLufs = momentarySum / momentaryCount;
            float rawAvg = momentaryRaw / momentaryCount;
            shortTermLufs = Maths.damp(shortTermLufs, momentaryLufs, 0.4f, 1f);
            momentarySum = 0f;
            momentaryRaw = 0f;
            momentaryCount = 0;
            /* cible -16 LUFS : on ajuste doucement, jamais plus de 0,5 dB par
             * pas. Dans le silence numerique on ne chasse rien : le gain se
             * fige, sinon il pomperait a la premiere note. */
            float error = TARGET_LUFS - momentaryLufs;
            if (rawAvg > -65f) {
                normalizeGain *= (float) Math.pow(10f, Maths.clamp(error * 0.06f, -0.5f, 0.5f) / 20f);
            }
            normalizeGain = Maths.clamp(normalizeGain, 0.4f, 3.2f);
        }
        float g2 = normalizeGain * masterTrim * MIX_TRIM;
        integratedSum += sum * g2 * g2;
        integratedFrames += frames * 2;""", e)

rep(E, """    private float momentarySum;
    private int momentaryCount;""",
    """    private float momentarySum;
    private float momentaryRaw;
    private int momentaryCount;""", e)

# --- saut : essai robuste (le sol peut mettre une image a se declarer) -----
rep(S, """        game.gamepad.setButton(GamepadRouter.KEY_BUTTON_A, true);
        game.frame(dt);
        game.gamepad.setButton(GamepadRouter.KEY_BUTTON_A, false);
        boolean airborne = false;
        for (int i = 0; i < 60; i++) {
            game.frame(dt);
            airborne |= com.velmora.lohen.sim.player.PlayerFsm.rootOf(game.fsm.state())
                    == com.velmora.lohen.sim.player.PlayerFsm.C_AIRBORNE;
        }
        check("le saut decolle", airborne);""",
    """        boolean airborne = false;
        for (int attempt = 0; attempt < 3 && !airborne; attempt++) {
            for (int i = 0; i < 60 && !game.lohen.grounded; i++) {
                game.frame(dt);
            }
            game.gamepad.setButton(GamepadRouter.KEY_BUTTON_A, true);
            game.frame(dt);
            game.frame(dt);
            game.gamepad.setButton(GamepadRouter.KEY_BUTTON_A, false);
            for (int i = 0; i < 60; i++) {
                game.frame(dt);
                airborne |= com.velmora.lohen.sim.player.PlayerFsm.rootOf(game.fsm.state())
                        == com.velmora.lohen.sim.player.PlayerFsm.C_AIRBORNE;
            }
        }
        check("le saut decolle", airborne);""", s)

open(E, "w", encoding="utf-8").write(e[0])
open(S, "w", encoding="utf-8").write(s[0])
if miss:
    print("MANQUES:")
    for x in miss:
        print("  - " + x)
    sys.exit(1)
print("metre LUFS sortant ok")
