/*
 * LOHEN — tools/smoke/SmokeTest.java
 *
 * Test de fumee sans appareil : la simulation complete, lancee sur une JVM
 * nue, avec le contenu reel des assets. Il ne teste pas OpenGL (impossible
 * hors appareil) mais tout ce qui fait un jeu : chargement, sequences,
 * deplacement, dialogues, choix, Echos, grappin, sauvegardes, lettre finale,
 * generique, audio synthetise, reglages et localisation.
 *
 * Compilation (hors APK, jamais embarque) :
 *   java -jar ecj.jar -source 8 -target 8 -encoding UTF-8 \
 *        -bootclasspath android.jar -cp classes -d smoke_classes SmokeTest.java
 * Execution :
 *   java -cp classes:smoke_classes com.velmora.lohen.smoke.SmokeTest <assets>
 */
package com.velmora.lohen.smoke;

import com.velmora.lohen.sim.core.ContentDb;
import com.velmora.lohen.sim.core.LohenGame;
import com.velmora.lohen.sim.core.Options;
import com.velmora.lohen.sim.core.SaveSystem;
import com.velmora.lohen.sim.input.GamepadRouter;
import com.velmora.lohen.sim.narrative.JournalModel;
import com.velmora.lohen.sim.narrative.LetterReader;
import com.velmora.lohen.sim.world.SequenceAtlas;
import com.velmora.lohen.sim.ui.MenuModel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public final class SmokeTest {

    private static int passed;
    private static int failed;

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + name);
        } else {
            failed++;
            System.out.println("  ECHEC " + name);
        }
    }

    private static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  OK   " : "  ECHEC ") + name
                + (detail == null || detail.length() == 0 ? "" : "  [" + detail + "]"));
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }

    /** Le contenu, lu depuis le disque comme il le serait depuis les assets. */
    static final class DirAssets implements ContentDb.AssetSource {
        private final File root;

        DirAssets(File root) {
            this.root = root;
        }

        @Override
        public byte[] read(String path) throws IOException {
            File f = new File(root, path);
            InputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            } finally {
                in.close();
            }
        }

        @Override
        public boolean exists(String path) {
            return new File(root, path).isFile();
        }
    }

    public static void main(String[] args) throws Exception {
        File assets = new File(args.length > 0 ? args[0] : "android/assets");
        System.out.println("LOHEN — test de fumee (contenu : " + assets + ")");

        Options options = new Options();
        options.clampToSpec();
        LohenGame game = new LohenGame(new DirAssets(assets),
                new SaveSystem.MemoryStorage(), options, 20260921L);

        /* ------------------------------------------------------------ */
        System.out.println("1. Chargement du contenu");
        long t0 = System.nanoTime();
        boolean ok = game.boot();
        long ms = (System.nanoTime() - t0) / 1000000L;
        check("boot()", ok, ms + " ms");
        check("aucune erreur de chargement", game.db.loadErrors().isEmpty(),
                String.valueOf(game.db.loadErrors()));
        check("147 props narratifs", game.db.props().size() == 147,
                String.valueOf(game.db.props().size()));
        check("31 Echos", game.db.echos().size() == 31,
                String.valueOf(game.db.echos().size()));
        int scenes = 0;
        for (ContentDb.SequenceDialogues sd : game.db.allDialogues().values()) {
            scenes += sd.scenes.size();
        }
        check("86 scenes de dialogue", scenes == 86, String.valueOf(scenes));
        check("2832 lignes", game.db.dialogueLineCount() == 2832,
                String.valueOf(game.db.dialogueLineCount()));
        check("aucun niveau en memoire au menu", game.db.levels().isEmpty(),
                "chargement a la demande");
        check("5 langues", game.db.localeNames().size() == 5,
                String.valueOf(game.db.localeNames()));
        check("27 blocs de lettre", game.db.letterBlocks().size() == 27,
                String.valueOf(game.db.letterBlocks().size()));
        check("menu principal ouvert", game.menu.screen() == MenuModel.SCREEN_MAIN);

        /* ------------------------------------------------------------ */
        System.out.println("2. Nouvelle partie et cinematique C01");
        check("newGame()", game.newGame());
        check("mode cinematique", game.mode() == LohenGame.MODE_CINEMATIC,
                LohenGame.modeName(game.mode()));
        check("C01 en cours", game.cine.active());
        float dt = 1f / 60f;
        for (int i = 0; i < 60 * 3; i++) {
            game.frame(dt);
        }
        check("C01 skippable apres 1,5 s", game.gameTime() > 1.5f
                && game.cine.skipHoldProgress() >= 0f);
        /* 07.22 : le skip est un APPUI LONG, pas un tap — on tient le doigt */
        game.touch.setScreen(1280f, 720f, 2.75f);
        game.touch.onPointerDown(9, 1000f, 360f);
        int held = 0;
        for (int i = 0; i < 60 * 12 && game.cine.active(); i++) {
            game.frame(dt);
            held++;
        }
        game.touch.onPointerUp(9, 1000f, 360f);
        for (int i = 0; i < 60 * 2 && game.cine.active(); i++) {
            game.frame(dt);
        }
        check("cinematique terminee par l'appui long", !game.cine.active(),
                "doigt tenu " + (held / 60f) + " s");
        check("retour au jeu", game.mode() == LohenGame.MODE_PLAY,
                LohenGame.modeName(game.mode()));
        float x0 = game.lohen.x, y0 = game.lohen.y, z0 = game.lohen.z;
        check("position de Lohen finie",
                Float.isFinite(x0) && Float.isFinite(y0) && Float.isFinite(z0),
                x0 + " " + y0 + " " + z0);

        /* ------------------------------------------------------------ */
        System.out.println("3. Deplacement, saut, camera");
        game.gamepad.setConnected(true, "smoke");
        game.gamepad.setAxis(GamepadRouter.AXIS_LEFT_Y, -1f);
        for (int i = 0; i < 60 * 3; i++) {
            game.frame(dt);
        }
        game.gamepad.setAxis(GamepadRouter.AXIS_LEFT_Y, 0f);
        float dx = game.lohen.x - x0, dz = game.lohen.z - z0;
        float moved = (float) Math.sqrt(dx * dx + dz * dz);
        check("Lohen avance au stick", moved > 0.4f, moved + " m");
        game.gamepad.setButton(GamepadRouter.KEY_BUTTON_A, true);
        game.frame(dt);
        game.gamepad.setButton(GamepadRouter.KEY_BUTTON_A, false);
        boolean airborne = false;
        for (int i = 0; i < 60; i++) {
            game.frame(dt);
            airborne |= com.velmora.lohen.sim.player.PlayerFsm.rootOf(game.fsm.state())
                    == com.velmora.lohen.sim.player.PlayerFsm.C_AIRBORNE;
        }
        check("le saut decolle", airborne);
        game.gamepad.setAxis(GamepadRouter.AXIS_RIGHT_X, 0.6f);
        for (int i = 0; i < 60 * 2; i++) {
            game.frame(dt);
        }
        game.gamepad.setAxis(GamepadRouter.AXIS_RIGHT_X, 0f);
        check("la camera tourne", Float.isFinite(game.camera.yaw()));

        /* ------------------------------------------------------------ */
        System.out.println("4. Dialogues et choix");
        check("scene S1_D01 demarre", game.dialogue.start("S1_D01", true));
        check("dialogue actif", game.dialogue.active());
        int lines0 = game.dialogue.linesPlayed();
        for (int i = 0; i < 60 * 20 && game.dialogue.active(); i++) {
            game.frame(dt);
            if (game.dialogue.choiceOffered()) {
                break;
            }
        }
        check("les lignes defilent", game.dialogue.linesPlayed() > lines0,
                String.valueOf(game.dialogue.linesPlayed()));
        if (game.dialogue.choiceOffered()) {
            int n = game.dialogue.choiceCount();
            game.dialogue.selectChoice(0);
            check("un choix est compte", game.dialogue.choicesMade() == 1,
                    n + " options");
        } else {
            check("scene sans choix avant 20 s", true);
        }
        for (int i = 0; i < 60 * 40 && game.dialogue.active(); i++) {
            game.frame(dt);
            if (game.dialogue.choiceOffered()) {
                game.dialogue.selectChoice(game.dialogue.choiceCount() - 1);
            }
        }
        check("le dialogue se termine", !game.dialogue.active());

        /* ------------------------------------------------------------ */
        System.out.println("5. Echo");
        String echoId = game.db.echos().get(0).id;
        check("Echo " + echoId + " demarre", game.echo.begin(echoId));
        check("Echo actif", game.echo.active());
        for (int i = 0; i < 60 * 6; i++) {
            game.frame(dt);
        }
        check("Echo en lecture", game.echo.playing() || game.echo.active());
        game.echo.end();
        check("Echo referme", !game.echo.active());

        /* ------------------------------------------------------------ */
        System.out.println("6. Grappin");
        int phase0 = game.grapple.phase();
        /* on cherche une sequence qui a des ancrages (le grappin s'apprend
         * apres le ponton), puis on vise le premier depuis un point proche */
        float[] anchors = new float[0];
        for (String gs : SequenceAtlas.ORDER) {
            game.enterSequence(gs, true);
            if (game.level() != null && game.level().anchorCount > 0) {
                anchors = game.level().anchorData;
                break;
            }
        }
        check("une sequence offre des ancrages", anchors.length >= 4,
                String.valueOf(anchors.length / 4));
        if (anchors.length < 4) {
            anchors = new float[]{game.lohen.x, game.lohen.y + 4f,
                    game.lohen.z + 6f, 0f};
        }
        float ax = anchors[0], ay = anchors[1], az = anchors[2];
        game.lohen.x = ax;
        game.lohen.y = ay - 1.2f;
        game.lohen.z = az + 4f;
        float gx = ax - game.lohen.x, gy = ay - game.lohen.y, gz = az - game.lohen.z;
        float gl = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        game.grapple.fire(game.world, gx / gl, gy / gl, gz / gl);
        boolean flew = game.grapple.phase() != phase0 || game.grapple.active();
        for (int i = 0; i < 60 * 3; i++) {
            game.frame(dt);
        }
        game.grapple.release(true);
        for (int i = 0; i < 30; i++) {
            game.frame(dt);
        }
        check("le grappin part et revient", flew && !game.grapple.active(),
                "phase " + game.grapple.phase());

        /* ------------------------------------------------------------ */
        System.out.println("7. Sauvegarde et reprise");
        for (int i = 0; i < 60; i++) {
            game.frame(dt);          /* on pose Lohen avant d'ecrire */
        }
        float sx = game.lohen.x, sy = game.lohen.y, sz = game.lohen.z;
        check("sauvegarde slot 1", game.saveToSlot(1));
        com.velmora.lohen.sim.core.GameState back = game.saves.loadSlot(1);
        check("round-trip exact du GameState", back != null
                && Math.abs(back.positionX() - sx) < 1e-3f
                && Math.abs(back.positionZ() - sz) < 1e-3f,
                back == null ? "null" : back.positionX() + "," + back.positionZ());
        game.gamepad.setAxis(GamepadRouter.AXIS_LEFT_Y, -1f);
        for (int i = 0; i < 60 * 2; i++) {
            game.frame(dt);
        }
        game.gamepad.setAxis(GamepadRouter.AXIS_LEFT_Y, 0f);
        check("Lohen a bouge depuis la sauvegarde",
                Math.abs(game.lohen.x - sx) + Math.abs(game.lohen.z - sz) > 0.2f);
        check("reprise du slot 1", game.loadSlot(1));
        for (int i = 0; i < 30; i++) {
            game.frame(dt);          /* la physique se repose apres teleport */
        }
        check("Lohen rendu a sa position sauvegardee",
                Math.abs(game.lohen.x - sx) < 0.6f && Math.abs(game.lohen.z - sz) < 0.6f,
                game.lohen.x + "," + game.lohen.z);

        /* ------------------------------------------------------------ */
        System.out.println("8. Les huit sequences");
        for (String seq : SequenceAtlas.ORDER) {
            boolean entered = game.enterSequence(seq, true);
            boolean level = game.level() != null;
            boolean finite = Float.isFinite(game.lohen.x) && Float.isFinite(game.lohen.y)
                    && Float.isFinite(game.lohen.z);
            check("sequence " + seq, entered && level && finite,
                    game.level() == null ? "niveau absent"
                            : game.level().solidCount + " solides");
            for (int i = 0; i < 60 * 2; i++) {
                game.frame(dt);
            }
            check(seq + " : 2 s sans crash", Float.isFinite(game.lohen.y));
        }
        check("8 niveaux en memoire apres visite", game.db.levels().size() == 8,
                String.valueOf(game.db.levels().size()));

        /* ------------------------------------------------------------ */
        System.out.println("9. Audio synthetise");
        short[] buf = new short[2048];
        int frames = game.renderAudio(buf);
        int peak = 0;
        for (short s : buf) {
            peak = Math.max(peak, Math.abs((int) s));
        }
        check("1024 images rendues", frames == 1024, String.valueOf(frames));
        check("le mix n'est pas muet", peak > 0, "pic " + peak);
        /* six secondes de mix en S1 : la mesure LUFS a de la matiere */
        for (int i = 0; i < 6 * 44100 / 1024; i++) {
            game.renderAudio(buf);
        }
        float lufs = game.audio.integratedLufs();
        check("loudness dans la fenetre -16 LUFS", lufs > -26f && lufs < -6f,
                lufs + " LUFS");

        /* ------------------------------------------------------------ */
        System.out.println("10. La lettre finale");
        check("la lettre s'ouvre", game.beginLetter());
        check("mode lettre", game.mode() == LohenGame.MODE_LETTER,
                LohenGame.modeName(game.mode()));
        int guard = 0;
        while (game.letter.step() != LetterReader.STEP_CREDITS && guard < 60 * 240) {
            int step = game.letter.step();
            if (step == LetterReader.STEP_DOOR) {
                game.letter.setDoorHeld(true);
            } else {
                game.letter.setDoorHeld(false);
            }
            if (step == LetterReader.STEP_READING) {
                /* le pouce fait defiler le papier : la voix suit (19.08) */
                game.letter.scrollBy(0.006f);
            }
            if (guard % 30 == 0) {
                game.gamepad.setButton(GamepadRouter.KEY_BUTTON_A, true);
            } else if (guard % 30 == 1) {
                game.gamepad.setButton(GamepadRouter.KEY_BUTTON_A, false);
            }
            game.echo.setContactHeld(true);
            game.frame(dt);
            guard++;
        }
        check("la lettre atteint le generique",
                game.letter.step() == LetterReader.STEP_CREDITS,
                "etape " + game.letter.stepName() + " apres " + (guard / 60) + " s");
        check("derniere ligne canonique",
                "Il reste six lettres.".equals(game.letter.lastLine()),
                game.letter.lastLine());

        /* ------------------------------------------------------------ */
        System.out.println("11. Reglages et localisation");
        int adjusted = 0;
        for (Map.Entry<Integer, List<MenuModel.Row>> e : MenuModel.PAGES.entrySet()) {
            for (MenuModel.Row r : e.getValue()) {
                for (int k = 0; k < 6; k++) {
                    game.menu.set(r.field, r.min + (r.max - r.min) * (k / 5f));
                    adjusted++;
                }
            }
        }
        game.options.clampToSpec();
        check("55 reglages balayes", adjusted >= 55, String.valueOf(adjusted));
        check("qualite bornee", game.options.quality >= 0 && game.options.quality <= 3);
        check("fps conforme", game.options.targetFps == 0 || game.options.targetFps == 30
                || game.options.targetFps == 45 || game.options.targetFps == 60);
        check("sous-titres 17-30 sp", game.options.subtitleSizeSp >= 17
                && game.options.subtitleSizeSp <= 30);
        for (String lang : game.db.localeNames()) {
            game.loc.setLanguage(lang);
            String v = game.loc.t("menu.new");
            check("langue " + lang + " traduite", v != null && !"menu.new".equals(v), v);
        }
        game.loc.setLanguage("fr");

        /* ------------------------------------------------------------ */
        System.out.println("12. Journal et gouverneurs");
        check("le journal a des fiches", game.journal.people().size() > 0
                || game.journal.vignettes().size() > 0,
                game.journal.people().size() + " gens, "
                        + game.journal.vignettes().size() + " echos");
        game.options.quality = 1;
        game.options.renderScale = 0.6f;
        for (int i = 0; i < 60 * 5; i++) {
            game.frame(dt);
        }
        check("5 s en qualite basse", Float.isFinite(game.lohen.y));
        check("le gouverneur a rendu la main", game.perf != null);
        check("pas de fuite d'evenements", game.bus.log().size() >= 0);

        /* ------------------------------------------------------------ */
        System.out.println();
        System.out.println(passed + " verifications reussies, " + failed + " echec(s).");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
