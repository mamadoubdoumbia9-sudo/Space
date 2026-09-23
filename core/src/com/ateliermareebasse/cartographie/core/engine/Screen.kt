package com.ateliermareebasse.cartographie.core.engine

import com.ateliermareebasse.cartographie.core.platform.Input

/** Écran de la pile : les écrans non opaques laissent voir celui du dessous (superpositions). */
abstract class Screen(val game: Game) {
    open val opaque = true
    /** L'écran met en pause le monde (marée, barks) ? */
    open val pausesWorld = true
    var time = 0f
    open fun onEnter() {}
    open fun onExit() {}
    open fun onResume() {}
    open fun update(dt: Float) { time += dt }
    abstract fun render()
    /** Retourne true si l'événement est consommé. */
    open fun onInput(e: Input): Boolean = false
    /** Bouton retour : true si géré (sinon le jeu ferme l'écran). */
    open fun onBack(): Boolean = false
    open fun onPause() {}
}
