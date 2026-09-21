/*
 * LOHEN — sim/core/Options.java
 *
 * Arborescence complete des options (14.12) + accessibilite (14.08).
 * Implementee en PHASE B, pas en PHASE F : c'est une exigence [OBL].
 * Chaque reglage est serialise dans la sauvegarde et dans un fichier
 * de preferences independant (le joueur ne perd pas ses reglages
 * d'accessibilite en commencant une nouvelle partie).
 */
package com.velmora.lohen.sim.core;

import com.velmora.lohen.sim.math.Maths;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Options {

    /* IMAGE -------------------------------------------------------------- */
    public int quality = 0;              /* 0 Auto · 1 Bas · 2 Moyen · 3 Haut */
    public float renderScale = 1.0f;     /* 50-100 % (02.11) */
    public int targetFps = 60;           /* 30 / 45 / 60 / illimite (0 = illimite) */
    public boolean shadows = true;
    public boolean fog = true;
    public boolean effects = true;
    public boolean grain = true;         /* 05.13 : desactivable */
    public boolean chromatic = true;
    public boolean vignette = true;
    public boolean bloom = true;
    public boolean motionBlur = false;   /* per-object uniquement (05.14) */
    public float brightness = 1.0f;
    public boolean hdr = false;

    /* SON ---------------------------------------------------------------- */
    /* RETOUR JOUEUR : le jeu est livre MUET. Le son existe toujours, il se
     * remonte dans Reglages > Audio. */
    public float volMaster = 0f;
    public float volMusic = 0.8f;
    public float volSfx = 0.9f;
    public float volVo = 1.0f;
    public float volAmb = 0.8f;
    public float volDucking = 1.0f;
    public int outputPreset = 0;         /* 0 auto · 1 casque · 2 haut-parleur */
    public String voLanguage = "fr";
    public boolean subtitles = true;

    /* JEU ---------------------------------------------------------------- */
    public int combatDifficulty = 1;     /* 0 calme · 1 soutenu · 2 brutal */
    public int traversalAssist = 0;      /* 0 normal · 1 genereux · 2 automatique */
    public int prompts = 0;              /* 0 auto · 1 toujours · 2 jamais */
    public int hudMode = 0;              /* 0 complet · 1 minimal · 2 aucun */
    public boolean autoRecenter = true;  /* 08.23 desactivable */
    public float cameraShake = 1.0f;     /* 0-100 % (14.08) */
    public boolean narrationOnly = false;

    /* CONTROLES ---------------------------------------------------------- */
    public float buttonScale = 1.25f;    /* 80-140 % — retour joueur */
    public boolean tapInsteadOfHold = false;
    public float sensitivityX = 1.0f;
    public float sensitivityY = 1.0f;
    public boolean invertX = false;
    public boolean invertY = false;
    public boolean gamepadEnabled = true;
    public float vibration = 1.0f;       /* 0-100 % (02.19) */
    public final Map<Integer, float[]> buttonPositions = new LinkedHashMap<Integer, float[]>();

    /* ACCESSIBILITE (14.08) ---------------------------------------------- */
    public int subtitleSizeSp = 26;      /* 17-30 sp — retour joueur */
    public boolean subtitleOpaqueBg = false;
    public boolean subtitleSpeaker = true;
    public boolean subtitleExtended = false;
    public int colorblindMode = 0;       /* 0 aucun · 1 protan · 2 deutan · 3 tritan */
    public String amberOverride = "";    /* remappable (05.06) */
    public String cyanOverride = "";     /* remappable (05.07) */
    public boolean reducedFlashes = false;
    public boolean wideParryWindow = false;
    public String language = "fr";

    public Options copy() {
        Options o = new Options();
        o.quality = quality;
        o.renderScale = renderScale;
        o.targetFps = targetFps;
        o.shadows = shadows;
        o.fog = fog;
        o.effects = effects;
        o.grain = grain;
        o.chromatic = chromatic;
        o.vignette = vignette;
        o.bloom = bloom;
        o.motionBlur = motionBlur;
        o.brightness = brightness;
        o.hdr = hdr;
        o.volMaster = volMaster;
        o.volMusic = volMusic;
        o.volSfx = volSfx;
        o.volVo = volVo;
        o.volAmb = volAmb;
        o.volDucking = volDucking;
        o.outputPreset = outputPreset;
        o.voLanguage = voLanguage;
        o.subtitles = subtitles;
        o.combatDifficulty = combatDifficulty;
        o.traversalAssist = traversalAssist;
        o.prompts = prompts;
        o.hudMode = hudMode;
        o.autoRecenter = autoRecenter;
        o.cameraShake = cameraShake;
        o.narrationOnly = narrationOnly;
        o.buttonScale = buttonScale;
        o.tapInsteadOfHold = tapInsteadOfHold;
        o.sensitivityX = sensitivityX;
        o.sensitivityY = sensitivityY;
        o.invertX = invertX;
        o.invertY = invertY;
        o.gamepadEnabled = gamepadEnabled;
        o.vibration = vibration;
        o.subtitleSizeSp = subtitleSizeSp;
        o.subtitleOpaqueBg = subtitleOpaqueBg;
        o.subtitleSpeaker = subtitleSpeaker;
        o.subtitleExtended = subtitleExtended;
        o.colorblindMode = colorblindMode;
        o.amberOverride = amberOverride;
        o.cyanOverride = cyanOverride;
        o.reducedFlashes = reducedFlashes;
        o.wideParryWindow = wideParryWindow;
        o.language = language;
        for (Map.Entry<Integer, float[]> e : buttonPositions.entrySet()) {
            o.buttonPositions.put(e.getKey(), e.getValue().clone());
        }
        return o;
    }

    /** Signature courte : detecte un changement sans comparer 50 champs. */
    public String signature() {
        StringBuilder sb = new StringBuilder(96);
        sb.append(quality).append('|').append((int) (renderScale * 100)).append('|')
                .append(targetFps).append('|').append(shadows ? 1 : 0)
                .append(fog ? 1 : 0).append(effects ? 1 : 0).append(grain ? 1 : 0)
                .append(chromatic ? 1 : 0).append(vignette ? 1 : 0).append(bloom ? 1 : 0)
                .append(motionBlur ? 1 : 0).append(hdr ? 1 : 0).append('|')
                .append((int) (volMaster * 100)).append((int) (volMusic * 100))
                .append((int) (volSfx * 100)).append((int) (volVo * 100))
                .append((int) (volAmb * 100)).append(outputPreset).append(voLanguage)
                .append(subtitles ? 1 : 0).append('|')
                .append(combatDifficulty).append(traversalAssist).append(prompts)
                .append(hudMode).append(autoRecenter ? 1 : 0)
                .append((int) (cameraShake * 100)).append(narrationOnly ? 1 : 0).append('|')
                .append((int) (buttonScale * 100)).append(tapInsteadOfHold ? 1 : 0)
                .append((int) (sensitivityX * 100)).append((int) (sensitivityY * 100))
                .append(invertX ? 1 : 0).append(invertY ? 1 : 0)
                .append(gamepadEnabled ? 1 : 0).append((int) (vibration * 100)).append('|')
                .append(subtitleSizeSp).append(subtitleOpaqueBg ? 1 : 0)
                .append(subtitleSpeaker ? 1 : 0).append(subtitleExtended ? 1 : 0)
                .append(colorblindMode).append(amberOverride).append(cyanOverride)
                .append(reducedFlashes ? 1 : 0).append(wideParryWindow ? 1 : 0)
                .append(language);
        return sb.toString();
    }

    /** Serialisation plate "cle=valeur" (fichier options.cfg, XOR+CRC32). */
    public String serialize() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("quality=").append(quality).append('\n');
        sb.append("render_scale=").append(renderScale).append('\n');
        sb.append("target_fps=").append(targetFps).append('\n');
        sb.append("shadows=").append(shadows ? 1 : 0).append('\n');
        sb.append("fog=").append(fog ? 1 : 0).append('\n');
        sb.append("effects=").append(effects ? 1 : 0).append('\n');
        sb.append("grain=").append(grain ? 1 : 0).append('\n');
        sb.append("chromatic=").append(chromatic ? 1 : 0).append('\n');
        sb.append("vignette=").append(vignette ? 1 : 0).append('\n');
        sb.append("bloom=").append(bloom ? 1 : 0).append('\n');
        sb.append("motion_blur=").append(motionBlur ? 1 : 0).append('\n');
        sb.append("brightness=").append(brightness).append('\n');
        sb.append("hdr=").append(hdr ? 1 : 0).append('\n');
        sb.append("vol_master=").append(volMaster).append('\n');
        sb.append("vol_music=").append(volMusic).append('\n');
        sb.append("vol_sfx=").append(volSfx).append('\n');
        sb.append("vol_vo=").append(volVo).append('\n');
        sb.append("vol_amb=").append(volAmb).append('\n');
        sb.append("vol_ducking=").append(volDucking).append('\n');
        sb.append("output_preset=").append(outputPreset).append('\n');
        sb.append("vo_language=").append(voLanguage).append('\n');
        sb.append("subtitles=").append(subtitles ? 1 : 0).append('\n');
        sb.append("combat_difficulty=").append(combatDifficulty).append('\n');
        sb.append("traversal_assist=").append(traversalAssist).append('\n');
        sb.append("prompts=").append(prompts).append('\n');
        sb.append("hud_mode=").append(hudMode).append('\n');
        sb.append("auto_recenter=").append(autoRecenter ? 1 : 0).append('\n');
        sb.append("camera_shake=").append(cameraShake).append('\n');
        sb.append("narration_only=").append(narrationOnly ? 1 : 0).append('\n');
        sb.append("button_scale=").append(buttonScale).append('\n');
        sb.append("tap_mode=").append(tapInsteadOfHold ? 1 : 0).append('\n');
        sb.append("sens_x=").append(sensitivityX).append('\n');
        sb.append("sens_y=").append(sensitivityY).append('\n');
        sb.append("invert_x=").append(invertX ? 1 : 0).append('\n');
        sb.append("invert_y=").append(invertY ? 1 : 0).append('\n');
        sb.append("gamepad=").append(gamepadEnabled ? 1 : 0).append('\n');
        sb.append("vibration=").append(vibration).append('\n');
        sb.append("subtitle_size=").append(subtitleSizeSp).append('\n');
        sb.append("subtitle_bg=").append(subtitleOpaqueBg ? 1 : 0).append('\n');
        sb.append("subtitle_speaker=").append(subtitleSpeaker ? 1 : 0).append('\n');
        sb.append("subtitle_extended=").append(subtitleExtended ? 1 : 0).append('\n');
        sb.append("colorblind=").append(colorblindMode).append('\n');
        sb.append("amber_override=").append(amberOverride).append('\n');
        sb.append("cyan_override=").append(cyanOverride).append('\n');
        sb.append("reduced_flashes=").append(reducedFlashes ? 1 : 0).append('\n');
        sb.append("wide_parry=").append(wideParryWindow ? 1 : 0).append('\n');
        sb.append("language=").append(language).append('\n');
        for (Map.Entry<Integer, float[]> e : buttonPositions.entrySet()) {
            sb.append("btn").append(e.getKey()).append('=').append(e.getValue()[0])
                    .append(',').append(e.getValue()[1]).append('\n');
        }
        return sb.toString();
    }

    public static Options deserialize(String text) {
        Options o = new Options();
        if (text == null) {
            return o;
        }
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String k = line.substring(0, eq).trim();
            String v = line.substring(eq + 1).trim();
            if (k.startsWith("btn")) {
                try {
                    String[] xy = v.split(",");
                    o.buttonPositions.put(Integer.parseInt(k.substring(3)),
                            new float[]{Float.parseFloat(xy[0]), Float.parseFloat(xy[1])});
                } catch (RuntimeException ignored) {
                    /* position invalide : on garde la position par defaut */
                }
                continue;
            }
            try {
                o.apply(k, v);
            } catch (RuntimeException ignored) {
                /* reglage inconnu : ignore sans casser la partie */
            }
        }
        return o;
    }

    private void apply(String k, String v) {
        if ("quality".equals(k)) {
            quality = Integer.parseInt(v);
        } else if ("render_scale".equals(k)) {
            renderScale = Float.parseFloat(v);
        } else if ("target_fps".equals(k)) {
            targetFps = Integer.parseInt(v);
        } else if ("shadows".equals(k)) {
            shadows = "1".equals(v);
        } else if ("fog".equals(k)) {
            fog = "1".equals(v);
        } else if ("effects".equals(k)) {
            effects = "1".equals(v);
        } else if ("grain".equals(k)) {
            grain = "1".equals(v);
        } else if ("chromatic".equals(k)) {
            chromatic = "1".equals(v);
        } else if ("vignette".equals(k)) {
            vignette = "1".equals(v);
        } else if ("bloom".equals(k)) {
            bloom = "1".equals(v);
        } else if ("motion_blur".equals(k)) {
            motionBlur = "1".equals(v);
        } else if ("brightness".equals(k)) {
            brightness = Float.parseFloat(v);
        } else if ("hdr".equals(k)) {
            hdr = "1".equals(v);
        } else if ("vol_master".equals(k)) {
            volMaster = Float.parseFloat(v);
        } else if ("vol_music".equals(k)) {
            volMusic = Float.parseFloat(v);
        } else if ("vol_sfx".equals(k)) {
            volSfx = Float.parseFloat(v);
        } else if ("vol_vo".equals(k)) {
            volVo = Float.parseFloat(v);
        } else if ("vol_amb".equals(k)) {
            volAmb = Float.parseFloat(v);
        } else if ("vol_ducking".equals(k)) {
            volDucking = Float.parseFloat(v);
        } else if ("output_preset".equals(k)) {
            outputPreset = Integer.parseInt(v);
        } else if ("vo_language".equals(k)) {
            voLanguage = v;
        } else if ("subtitles".equals(k)) {
            subtitles = "1".equals(v);
        } else if ("combat_difficulty".equals(k)) {
            combatDifficulty = Integer.parseInt(v);
        } else if ("traversal_assist".equals(k)) {
            traversalAssist = Integer.parseInt(v);
        } else if ("prompts".equals(k)) {
            prompts = Integer.parseInt(v);
        } else if ("hud_mode".equals(k)) {
            hudMode = Integer.parseInt(v);
        } else if ("auto_recenter".equals(k)) {
            autoRecenter = "1".equals(v);
        } else if ("camera_shake".equals(k)) {
            cameraShake = Float.parseFloat(v);
        } else if ("narration_only".equals(k)) {
            narrationOnly = "1".equals(v);
        } else if ("button_scale".equals(k)) {
            buttonScale = Float.parseFloat(v);
        } else if ("tap_mode".equals(k)) {
            tapInsteadOfHold = "1".equals(v);
        } else if ("sens_x".equals(k)) {
            sensitivityX = Float.parseFloat(v);
        } else if ("sens_y".equals(k)) {
            sensitivityY = Float.parseFloat(v);
        } else if ("invert_x".equals(k)) {
            invertX = "1".equals(v);
        } else if ("invert_y".equals(k)) {
            invertY = "1".equals(v);
        } else if ("gamepad".equals(k)) {
            gamepadEnabled = "1".equals(v);
        } else if ("vibration".equals(k)) {
            vibration = Float.parseFloat(v);
        } else if ("subtitle_size".equals(k)) {
            subtitleSizeSp = Integer.parseInt(v);
        } else if ("subtitle_bg".equals(k)) {
            subtitleOpaqueBg = "1".equals(v);
        } else if ("subtitle_speaker".equals(k)) {
            subtitleSpeaker = "1".equals(v);
        } else if ("subtitle_extended".equals(k)) {
            subtitleExtended = "1".equals(v);
        } else if ("colorblind".equals(k)) {
            colorblindMode = Integer.parseInt(v);
        } else if ("amber_override".equals(k)) {
            amberOverride = v;
        } else if ("cyan_override".equals(k)) {
            cyanOverride = v;
        } else if ("reduced_flashes".equals(k)) {
            reducedFlashes = "1".equals(v);
        } else if ("wide_parry".equals(k)) {
            wideParryWindow = "1".equals(v);
        } else if ("language".equals(k)) {
            language = v;
        }
    }

    /** Bornes imposees par la spec (14.08, 14.12). */
    public void clampToSpec() {
        renderScale = Maths.clamp(renderScale, 0.5f, 1.0f);
        buttonScale = Maths.clamp(buttonScale, 0.8f, 1.4f);
        subtitleSizeSp = Maths.clamp(subtitleSizeSp, 17, 30);
        vibration = Maths.clamp01(vibration);
        cameraShake = Maths.clamp01(cameraShake);
        brightness = Maths.clamp(brightness, 0.5f, 1.6f);
        combatDifficulty = Maths.clamp(combatDifficulty, 0, 2);
        traversalAssist = Maths.clamp(traversalAssist, 0, 2);
        hudMode = Maths.clamp(hudMode, 0, 2);
        prompts = Maths.clamp(prompts, 0, 2);
        colorblindMode = Maths.clamp(colorblindMode, 0, 3);
        quality = Maths.clamp(quality, 0, 3);
    }
}
