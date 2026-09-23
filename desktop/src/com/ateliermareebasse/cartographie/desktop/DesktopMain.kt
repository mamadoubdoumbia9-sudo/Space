package com.ateliermareebasse.cartographie.desktop

import com.ateliermareebasse.cartographie.core.engine.Game
import com.ateliermareebasse.cartographie.core.platform.Input
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Lanceur bureau.
 *   java -cp … DesktopMainKt            → fenêtre jouable (souris = doigt, Échap = retour)
 *   … --tests                            → suite de tests de contenu et de moteur (code de sortie ≠ 0 si échec)
 *   … --tour build/out/shots            → parcours automatique + captures d'écran
 */
fun main(args: Array<String>) {
    val root = File(System.getProperty("assets.dir") ?: "app/src/main/assets")
    val data = File(System.getProperty("data.dir") ?: "build/desktop_data")
    when {
        args.contains("--tests") -> { val t = ContentTests(root); t.only = args.getOrNull(args.indexOf("--tests") + 1)?.takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }; val ok = t.runAll(); System.exit(if (ok) 0 else 1) }
        args.contains("--tour") -> { val out = File(args.getOrNull(args.indexOf("--tour") + 1) ?: "build/out/shots"); Tour(root, out).run(); System.exit(0) }
        else -> SwingUtilities.invokeLater { Window(root, data).show() }
    }
}

class Window(root: File, data: File) {
    private val platform = DesktopPlatform(root, data)
    private val audio = DesktopAudio()
    private val game = Game(platform, audio)
    private var painter = DesktopPainter(platform, 1280, 720)
    private val panel = object : JPanel() {
        override fun paintComponent(g: Graphics) { super.paintComponent(g); g.drawImage(painter.image, 0, 0, null) }
    }
    private var last = System.nanoTime()
    fun show() {
        platform.textInputProvider = { title, initial -> javax.swing.JOptionPane.showInputDialog(panel, title, initial) }
        game.painter = painter
        game.start()
        val frame = JFrame("La Cartographie des Absents — Chapitre 1")
        panel.preferredSize = Dimension(1280, 720)
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.contentPane.add(panel); frame.pack(); frame.setLocationRelativeTo(null); frame.isVisible = true
        panel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { game.input(Input.Down(e.x.toFloat(), e.y.toFloat())) }
            override fun mouseReleased(e: MouseEvent) { game.input(Input.Up(e.x.toFloat(), e.y.toFloat())) }
        })
        panel.addMouseMotionListener(object : MouseAdapter() { override fun mouseDragged(e: MouseEvent) { game.input(Input.Move(e.x.toFloat(), e.y.toFloat())) } })
        frame.addKeyListener(object : KeyAdapter() { override fun keyPressed(e: KeyEvent) { if (e.keyCode == KeyEvent.VK_ESCAPE) game.input(Input.Back) } })
        Timer(16) {
            if (panel.width > 0 && (panel.width != painter.w || panel.height != painter.h)) { painter.resize(panel.width, panel.height); game.painter = painter }
            val now = System.nanoTime(); val dt = ((now - last) / 1e9).toFloat(); last = now
            game.update(dt); painter.begin(); game.render(); panel.repaint()
            if (platform.quitRequested) System.exit(0)
        }.start()
    }
}

/** Parcours automatique : joue une partie scriptée et capture les écrans clés. */
class Tour(val root: File, val out: File) {
    fun run() {
        out.mkdirs()
        val platform = DesktopPlatform(root, File("build/tour_data").also { it.deleteRecursively() })
        val audio = DesktopAudio()
        val game = Game(platform, audio)
        val painter = DesktopPainter(platform, 1280, 720)
        game.painter = painter
        game.start()
        fun frames(n: Int) { repeat(n) { game.update(1f / 30f); painter.begin(); game.render() } }
        fun shot(name: String) { painter.begin(); game.render(); ImageIO.write(painter.image, "png", File(out, "$name.png")); println("  📷 $name") }
        fun tap(x: Float, y: Float) { game.input(Input.Down(x, y)); frames(2); game.input(Input.Up(x, y)); frames(6) }
        // chargement
        var guard = 0
        while (!game.contentReady && guard++ < 600) frames(1)
        frames(60); shot("01_titre")
        game.push(com.ateliermareebasse.cartographie.core.screens.SettingsScreen(game)); frames(10); shot("02_options"); game.pop()
        game.newGame(); frames(30); shot("03_cinematique_reve")
        // passe la cinématique
        game.input(Input.Back); frames(30)
        shot("04_cabinet")
        // ouvre le dialogue d'Ombeline si présent
        frames(20); shot("05_dialogue")
        var k = 0
        while (game.top() is com.ateliermareebasse.cartographie.core.screens.DialogueScreen && k++ < 80) {
            val d = game.top() as com.ateliermareebasse.cartographie.core.screens.DialogueScreen
            val c = d.firstChoiceCenter()
            if (c != null) tap(c.first, c.second) else tap(640f, 650f)
        }
        frames(60)  // laisse la cinématique / le voyage se terminer
        if (game.top() is com.ateliermareebasse.cartographie.core.screens.CinematicScreen) { game.input(Input.Back); frames(30) }
        frames(10); shot("06_tableau")
        game.openCarnet(null); frames(10); shot("07_carnet"); game.pop()
        game.openCarnet("sacoche"); frames(10); shot("08_sacoche"); game.pop()
        game.push(com.ateliermareebasse.cartographie.core.screens.MapScreen(game)); frames(10); shot("09_carte"); game.pop()
        game.state.tutosSeen.add("puzzle")  // les captures montrent les plateaux, pas le tutoriel (déjà vu)
        for (id in listOf("E01", "E02", "E03", "E04", "E05", "E06", "E07", "E08", "E09", "E10", "E11", "E12", "E13", "E14", "E15", "E16", "S08")) {
            game.openPuzzle(id); frames(20); shot("10_enigme_$id")
            while (game.top() is com.ateliermareebasse.cartographie.core.puzzles.PuzzleScreen) game.pop()
        }
        game.state.zone = "z17"; game.push(com.ateliermareebasse.cartographie.core.screens.LetterScreen(game)); frames(30); shot("11_lettre"); game.screens.remove(game.top())
        game.push(com.ateliermareebasse.cartographie.core.screens.PauseScreen(game)); frames(10); shot("12_pause"); game.pop()
        game.push(com.ateliermareebasse.cartographie.core.screens.SaveLoadScreen(game, true)); frames(10); shot("13_sauvegardes"); game.pop()
        game.content.echoes["E-05"]?.let { game.push(com.ateliermareebasse.cartographie.core.screens.EchoScreen(game, it, false)); frames(40); shot("14_echo"); game.pop() }
        game.push(com.ateliermareebasse.cartographie.core.screens.EpilogueScreen(game)); frames(10); shot("15_epilogue"); game.pop()
        game.push(com.ateliermareebasse.cartographie.core.screens.CreditsScreen(game)); frames(60); shot("16_credits"); game.pop()
        // format portrait
        painter.resize(720, 1280); game.painter = painter; frames(5); shot("17_portrait_monde")
        game.openCarnet(null); frames(10); shot("18_portrait_carnet"); game.pop()
        println("Tour terminé : ${out.listFiles()?.size} captures dans $out")
    }
}
