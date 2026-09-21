/*
 * LOHEN — sim/core/Tuning.java
 *
 * 04.06 [OBL] : tout ce qui est "nombre de design" vit dans des ressources
 * data-driven, jamais en constantes dans le code. Cette classe projette
 * content/tuning/tuning.json (genere depuis le BLOC 08) en champs typés,
 * pour que les boucles chaudes ne fassent aucun acces Map (04.12).
 */
package com.velmora.lohen.sim.core;

import java.util.List;
import java.util.Map;

public final class Tuning {

    /* movement (08.06) */
    public float walkSpeed = 1.4f;
    public float walkSlow = 0.8f;
    public float jogSpeed = 3.2f;
    public float runSpeed = 5.1f;
    public float sprintSpeed = 6.4f;
    public float runLeanDeg = 14f;
    public float stopBrake = 0.45f;
    public float accelGround = 14f;
    public float accelAir = 3.5f;
    public float frictionGround = 11f;
    public float stumbleSpeed = 4f;
    public float gravity = 15.7f;

    /* air (07.08, 08.05) */
    public float jumpVelocity = 5.4f;
    public float coyoteTime = 0.14f;
    public float fallFlailAfter = 3.5f;
    public float landSoftM = 3f;
    public float landMediumM = 7f;
    public float landHardM = 11f;
    public float landHardBreath = 8f;
    public float ledgeGrabWindow = 0.35f;
    public float ledgeCapsuleWiden = 0.20f;
    public float ledgeClimbFast = 0.7f;
    public float ledgeClimbExhausted = 1.3f;
    public float exhaustedBelowPct = 25f;
    public float wallrunMax = 2.1f;
    public float wallrunBreath = 8f;
    public float vaultLow = 0.45f;
    public float airCorrectionDeg = 12f;
    public float platformMagnet = 0.35f;
    public int edgeStopNoJumpMs = 300;
    public float ledgeReachMin = 0.6f;
    public float ledgeReachMax = 2.35f;

    /* grapple (08.08) */
    public float grappleRange = 28f;
    public float grappleProjectileSpeed = 62f;
    public float grapplePullSpeed = 14f;
    public float grapplePullAccel = 0.3f;
    public float pendulumGravityFactor = 1.15f;
    public float releaseVelocityKeep = 0.78f;
    public float grappleCooldown = 0.45f;
    public float grappleBreathShot = 4f;
    public float grappleBreathSwing = 1.5f;
    public float autoAimConeDeg = 40f;
    public float aimWeightAlign = 0.5f;
    public float aimWeightDistance = 0.3f;
    public float aimWeightProgress = 0.2f;
    public int cableSegments = 32;
    public float zipRideSpeed = 11f;
    public float zipBrakeDecel = 6.5f;

    /* breath (08.13) */
    public float breathMax = 100f;
    public float breathMaxEnd = 130f;
    public float breathRegen = 9f;
    public float breathRegenDelay = 1.8f;
    public float breathRegenCombat = 4f;
    public float breathRegenCombatDelay = 3f;
    public float breathSprintPerSec = 6f;
    public float breathSprintAfter = 3f;
    public float breathParryGain = 6f;
    public float breathParryFail = 14f;
    public float breathHitLight = 12f;
    public float breathHitHeavy = 26f;
    public float breathCollapseVulnerable = 3.5f;
    public float breathCollapseRestore = 35f;
    public float breathGlassDrain = 1f;
    public float breathEchoMin = 18f;
    public float breathEchoMax = 40f;

    /* combat (08.14, 07.12, 07.13) */
    public float parryWindow = 0.22f;
    public float parryWindowWide = 0.45f;
    public float parryStagger = 1.4f;
    public float riposteFactor = 3f;
    public int hitstopLightMs = 70;
    public int hitstopHeavyMs = 130;
    public int hitstopParryMs = 160;
    public float shakeAmpDeg = 0.6f;
    public float shakeFreqHz = 22f;
    public float shakeDecay = 0.25f;
    public float dodgeDuration = 0.55f;
    public int dodgeIFrames = 12;
    public float heavyCharge = 1.1f;
    public float blockHoldMax = 2.5f;
    public float combatExit = 1.8f;
    public float combatExitLock = 0.6f;
    public int maxSimultaneous = 4;
    public int attackTokenAbove = 2;
    public float tellEchassier = 0.8f;
    public float tellMueur = 0.5f;
    public int shatterFragments = 47;
    public float strikeLightDamage = 12f;
    public float strikeHeavyDamage = 30f;
    public float figureHealthEchassier = 90f;
    public float figureHealthMueur = 45f;
    public float bossHealth = 620f;
    public float strikeRange = 2.2f;
    public float strikeArcDeg = 110f;

    /* camera (08.23, 05.10, 05.11) */
    public float camDistance = 3.4f;
    public float camTargetHeight = 1.55f;
    public float camShoulderOffset = 0.45f;
    public float camSmoothPos = 0.12f;
    public float camSmoothRot = 0.08f;
    public float camSpringRadius = 0.32f;
    public float camSpringReturn = 0.15f;
    public float camRecenterAfter = 2.5f;
    public float camRecenterSpeed = 45f;
    public float camPitchMin = -62f;
    public float camPitchMax = 58f;
    public float camFovSpeedBonus = 6f;
    public float camFovLerp = 0.8f;
    public float fovExplore = 46f;
    public float fovDialogue = 34f;
    public float fovFall = 62f;
    public float dofFarStart = 45f;

    /* echoes (11.02, 11.03, 05.19) */
    public float echoRitual = 2.4f;
    public float echoRevealSpeed = 3.5f;
    public float echoRevealRadius = 18f;
    public float echoDesaturation = 0.85f;
    public float echoGrain = 0.12f;
    public float echoVignette = 0.55f;
    public int echoHistoryFrames = 6;

    /* monde (09.01, 05.31) */
    public float lighthouseAltitude = 212f;
    public float lighthouseSweepTurns = 0.05f;
    public float lighthouseSweepPeriod = 20f;
    public float beamRange = 900f;
    public float beamExposureEv = 0.4f;
    public float beamExposureSeconds = 1.8f;
    public float streamCellX = 48f;
    public float streamCellY = 32f;
    public float streamCellZ = 48f;
    public float streamBudgetMs = 4f;
    public int streamMaxConcurrent = 2;

    /* carry — V6 PORTER (08.11, 09.14) */
    public float carrySpeedLantern = 0.86f;
    public float carrySpeedChest = 0.62f;
    public float carrySpeedBody = 0.55f;
    public float carrySpeedLever = 0.72f;
    public float carrySpeedDossier = 0.92f;
    public float carrySpeedLetter = 1.0f;
    public boolean carryAllowsGrapple = false;
    public boolean carryAllowsGuard = false;
    public boolean carryAllowsSprint = false;
    public boolean carryAllowsWallrun = false;
    public float carryCameraDistanceFactor = 0.88f;
    public float carryCameraFovDelta = -2f;
    public float carryBreathRegenFactor = 0.8f;
    public float carryPickupSeconds = 0.9f;
    public float carrySetdownSeconds = 0.7f;
    public float carrySwapHandSeconds = 0.45f;
    public float lanternLightRadius = 4f;
    public float mueurRepelRadius = 4f;
    public float darkCrossingM = 8f;
    public int darkCrossingsS5 = 3;
    public float ceilingHeightS5 = 1.4f;

    /* anti-ennui (17.02, 17.03) */
    public float verbIntervalTarget = 8f;
    public float eventIntervalMin = 45f;
    public float eventIntervalMax = 90f;
    public float navigationStallSeconds = 90f;
    public float maxSingleMechanicMinutes = 6f;

    /* dialogue (12.03, 12.04) */
    public int subtitleCharsPerLine = 42;
    public int subtitleMaxLines = 2;
    public float lineMaxSeconds = 4.5f;
    public float pauseMin = 0.25f;
    public float pauseMax = 0.9f;
    public float barkCooldownDefault = 480f;

    private final ContentDb db;

    public Tuning(ContentDb db) {
        this.db = db;
        if (db != null) {
            load();
        }
    }

    public void load() {
        Map<String, Object> m = db.tuningGroup("movement");
        walkSpeed = num(m, "walk_speed", walkSpeed);
        walkSlow = num(m, "walk_slow", walkSlow);
        jogSpeed = num(m, "jog_speed", jogSpeed);
        runSpeed = num(m, "run_speed", runSpeed);
        sprintSpeed = num(m, "sprint_speed", sprintSpeed);
        runLeanDeg = num(m, "run_lean_deg", runLeanDeg);
        stopBrake = num(m, "stop_brake_s", stopBrake);
        accelGround = num(m, "accel_ground", accelGround);
        accelAir = num(m, "accel_air", accelAir);
        frictionGround = num(m, "friction_ground", frictionGround);
        stumbleSpeed = num(m, "stumble_speed_threshold", stumbleSpeed);
        gravity = num(m, "gravity", gravity);

        m = db.tuningGroup("air");
        jumpVelocity = num(m, "jump_velocity", jumpVelocity);
        coyoteTime = num(m, "coyote_time_s", coyoteTime);
        fallFlailAfter = num(m, "fall_flail_after_s", fallFlailAfter);
        landSoftM = num(m, "land_soft_m", landSoftM);
        landMediumM = num(m, "land_medium_m", landMediumM);
        landHardM = num(m, "land_hard_m", landHardM);
        landHardBreath = num(m, "land_hard_breath_cost", landHardBreath);
        ledgeGrabWindow = num(m, "ledge_grab_window_s", ledgeGrabWindow);
        ledgeCapsuleWiden = num(m, "ledge_capsule_widen_m", ledgeCapsuleWiden);
        ledgeClimbFast = num(m, "ledge_climb_fast_s", ledgeClimbFast);
        ledgeClimbExhausted = num(m, "ledge_climb_exhausted_s", ledgeClimbExhausted);
        exhaustedBelowPct = num(m, "exhausted_below_breath_pct", exhaustedBelowPct);
        wallrunMax = num(m, "wallrun_max_s", wallrunMax);
        wallrunBreath = num(m, "wallrun_breath_per_s", wallrunBreath);
        vaultLow = num(m, "vault_low_s", vaultLow);
        airCorrectionDeg = num(m, "air_correction_deg_s", airCorrectionDeg);
        platformMagnet = num(m, "platform_magnet_m", platformMagnet);
        edgeStopNoJumpMs = (int) num(m, "edge_stop_no_jump_ms", edgeStopNoJumpMs);

        m = db.tuningGroup("grapple");
        grappleRange = num(m, "range_m", grappleRange);
        grappleProjectileSpeed = num(m, "projectile_speed", grappleProjectileSpeed);
        grapplePullSpeed = num(m, "pull_speed", grapplePullSpeed);
        grapplePullAccel = num(m, "pull_accel_s", grapplePullAccel);
        pendulumGravityFactor = num(m, "pendulum_gravity_factor", pendulumGravityFactor);
        releaseVelocityKeep = num(m, "release_velocity_keep", releaseVelocityKeep);
        grappleCooldown = num(m, "cooldown_s", grappleCooldown);
        grappleBreathShot = num(m, "breath_per_shot", grappleBreathShot);
        grappleBreathSwing = num(m, "breath_per_swing_s", grappleBreathSwing);
        autoAimConeDeg = num(m, "auto_aim_cone_deg", autoAimConeDeg);
        cableSegments = (int) num(m, "cable_segments", cableSegments);
        zipRideSpeed = num(m, "zip_ride_speed", zipRideSpeed);
        zipBrakeDecel = num(m, "zip_brake_decel", zipBrakeDecel);
        Map<String, Object> w = MiniJson.child(m, "auto_aim_weights");
        if (w != null) {
            aimWeightAlign = num(w, "camera_align", aimWeightAlign);
            aimWeightDistance = num(w, "distance", aimWeightDistance);
            aimWeightProgress = num(w, "progress", aimWeightProgress);
        }

        m = db.tuningGroup("breath");
        breathMax = num(m, "max", breathMax);
        breathMaxEnd = num(m, "max_end_of_chapter", breathMaxEnd);
        breathRegen = num(m, "regen_per_s", breathRegen);
        breathRegenDelay = num(m, "regen_delay_s", breathRegenDelay);
        breathRegenCombat = num(m, "regen_combat_per_s", breathRegenCombat);
        breathRegenCombatDelay = num(m, "regen_combat_delay_s", breathRegenCombatDelay);
        breathSprintPerSec = num(m, "cost_sprint_per_s_after_3s", breathSprintPerSec);
        breathParryGain = num(m, "gain_parry_success", breathParryGain);
        breathParryFail = num(m, "cost_parry_fail", breathParryFail);
        breathHitLight = num(m, "cost_hit_light_taken", breathHitLight);
        breathHitHeavy = num(m, "cost_hit_heavy_taken", breathHitHeavy);
        breathCollapseVulnerable = num(m, "collapse_vulnerable_s", breathCollapseVulnerable);
        breathCollapseRestore = num(m, "collapse_restore", breathCollapseRestore);
        breathGlassDrain = num(m, "glass_passive_drain_per_s", breathGlassDrain);
        breathEchoMin = num(m, "cost_echo_min", breathEchoMin);
        breathEchoMax = num(m, "cost_echo_max", breathEchoMax);

        m = db.tuningGroup("combat");
        parryWindow = num(m, "parry_window_s", parryWindow);
        parryWindowWide = num(m, "parry_window_wide_s", parryWindowWide);
        parryStagger = num(m, "parry_stagger_s", parryStagger);
        riposteFactor = num(m, "riposte_damage_factor", riposteFactor);
        hitstopLightMs = (int) num(m, "hitstop_light_ms", hitstopLightMs);
        hitstopHeavyMs = (int) num(m, "hitstop_heavy_ms", hitstopHeavyMs);
        hitstopParryMs = (int) num(m, "hitstop_parry_ms", hitstopParryMs);
        shakeAmpDeg = num(m, "shake_amp_deg", shakeAmpDeg);
        shakeFreqHz = num(m, "shake_freq_hz", shakeFreqHz);
        shakeDecay = num(m, "shake_decay_s", shakeDecay);
        dodgeDuration = num(m, "dodge_s", dodgeDuration);
        dodgeIFrames = (int) num(m, "dodge_iframes", dodgeIFrames);
        heavyCharge = num(m, "heavy_charge_s", heavyCharge);
        blockHoldMax = num(m, "block_hold_max_s", blockHoldMax);
        combatExit = num(m, "combat_exit_s", combatExit);
        combatExitLock = num(m, "combat_exit_lock_s", combatExitLock);
        maxSimultaneous = (int) num(m, "max_simultaneous", maxSimultaneous);
        attackTokenAbove = (int) num(m, "attack_token_above", attackTokenAbove);
        tellEchassier = num(m, "tell_echassier_s", tellEchassier);
        tellMueur = num(m, "tell_mueur_s", tellMueur);
        shatterFragments = (int) num(m, "shatter_fragments", shatterFragments);

        m = db.tuningGroup("camera");
        camDistance = num(m, "distance_m", camDistance);
        camTargetHeight = num(m, "target_height_m", camTargetHeight);
        camShoulderOffset = num(m, "shoulder_offset_m", camShoulderOffset);
        camSmoothPos = num(m, "smooth_pos_s", camSmoothPos);
        camSmoothRot = num(m, "smooth_rot_s", camSmoothRot);
        camSpringRadius = num(m, "spring_arm_radius_m", camSpringRadius);
        camSpringReturn = num(m, "spring_arm_return_s", camSpringReturn);
        camRecenterAfter = num(m, "recenter_after_s", camRecenterAfter);
        camRecenterSpeed = num(m, "recenter_speed_deg_s", camRecenterSpeed);
        camPitchMin = num(m, "pitch_clamp_0", camPitchMin);
        camPitchMax = num(m, "pitch_clamp_1", camPitchMax);
        camFovSpeedBonus = num(m, "fov_speed_bonus_deg", camFovSpeedBonus);
        camFovLerp = num(m, "fov_lerp_s", camFovLerp);
        fovExplore = num(m, "fov_explore_deg", fovExplore);
        fovDialogue = num(m, "fov_dialogue_deg", fovDialogue);
        fovFall = num(m, "fov_fall_deg", fovFall);
        dofFarStart = num(m, "dof_far_start_m", dofFarStart);
        List<Object> clamp = MiniJson.childList(db.tuningGroup("camera"), "pitch_clamp");
        if (clamp != null && clamp.size() >= 2) {
            camPitchMin = ((Number) clamp.get(0)).floatValue();
            camPitchMax = ((Number) clamp.get(1)).floatValue();
        }

        m = db.tuningGroup("echo_system");
        echoRitual = num(m, "ritual_s", echoRitual);
        echoRevealSpeed = num(m, "reveal_speed_m_s", echoRevealSpeed);
        echoRevealRadius = num(m, "reveal_radius_m", echoRevealRadius);
        echoDesaturation = num(m, "desaturation", echoDesaturation);
        echoGrain = num(m, "grain", echoGrain);
        echoVignette = num(m, "vignette", echoVignette);
        echoHistoryFrames = (int) num(m, "history_frames", echoHistoryFrames);

        m = db.tuningGroup("world");
        lighthouseAltitude = num(m, "lighthouse_altitude_m", lighthouseAltitude);
        lighthouseSweepTurns = num(m, "sweep_turns_per_s", lighthouseSweepTurns);
        lighthouseSweepPeriod = num(m, "sweep_period_s", lighthouseSweepPeriod);
        beamRange = num(m, "beam_range_m", beamRange);
        beamExposureEv = num(m, "beam_exposure_ev", beamExposureEv);
        beamExposureSeconds = num(m, "beam_exposure_s", beamExposureSeconds);
        streamCellX = num(m, "stream_cell_m_0", streamCellX);
        streamBudgetMs = num(m, "stream_budget_ms", streamBudgetMs);
        streamMaxConcurrent = (int) num(m, "stream_max_concurrent", streamMaxConcurrent);
        List<Object> cell = MiniJson.childList(m, "stream_cell_m");
        if (cell != null && cell.size() >= 3) {
            streamCellX = ((Number) cell.get(0)).floatValue();
            streamCellY = ((Number) cell.get(1)).floatValue();
            streamCellZ = ((Number) cell.get(2)).floatValue();
        }

        m = db.tuningGroup("carry");
        carrySpeedLantern = num(m, "speed_factor_lantern", carrySpeedLantern);
        carrySpeedChest = num(m, "speed_factor_chest", carrySpeedChest);
        carrySpeedBody = num(m, "speed_factor_body", carrySpeedBody);
        carrySpeedLever = num(m, "speed_factor_lever", carrySpeedLever);
        carrySpeedDossier = num(m, "speed_factor_dossier", carrySpeedDossier);
        carrySpeedLetter = num(m, "speed_factor_letter", carrySpeedLetter);
        carryAllowsGrapple = num(m, "grapple_allowed", carryAllowsGrapple ? 1f : 0f) > 0.5f;
        carryAllowsGuard = num(m, "guard_allowed", carryAllowsGuard ? 1f : 0f) > 0.5f;
        carryAllowsSprint = num(m, "sprint_allowed", carryAllowsSprint ? 1f : 0f) > 0.5f;
        carryAllowsWallrun = num(m, "wallrun_allowed", carryAllowsWallrun ? 1f : 0f) > 0.5f;
        carryCameraDistanceFactor = num(m, "camera_distance_factor", carryCameraDistanceFactor);
        carryCameraFovDelta = num(m, "camera_fov_delta", carryCameraFovDelta);
        carryBreathRegenFactor = num(m, "breath_regen_factor", carryBreathRegenFactor);
        carryPickupSeconds = num(m, "pickup_seconds", carryPickupSeconds);
        carrySetdownSeconds = num(m, "setdown_seconds", carrySetdownSeconds);
        carrySwapHandSeconds = num(m, "swap_hand_seconds", carrySwapHandSeconds);
        lanternLightRadius = num(m, "lantern_light_radius_m", lanternLightRadius);
        mueurRepelRadius = num(m, "mueur_repelled_at_m", mueurRepelRadius);
        darkCrossingM = num(m, "dark_crossing_m", darkCrossingM);
        darkCrossingsS5 = (int) num(m, "dark_crossings_s5", darkCrossingsS5);
        ceilingHeightS5 = num(m, "ceiling_height_s5_m", ceilingHeightS5);

        m = db.tuningGroup("input");
        /* rien de critique ici : le routeur lit Options directement */
    }

    private static float num(Map<String, Object> m, String key, float dflt) {
        return MiniJson.num(m, key, dflt);
    }

    public ContentDb db() {
        return db;
    }

    /** Signature utilisee par les tests : un changement de tuning doit etre trace. */
    public String signature() {
        return walkSpeed + "|" + jumpVelocity + "|" + grappleRange + "|" + breathMax
                + "|" + parryWindow + "|" + camDistance + "|" + echoRitual
                + "|" + lighthouseAltitude;
    }
}
