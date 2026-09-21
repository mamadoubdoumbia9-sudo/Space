extends Node
class_name StoneWetZone
## =============================================================================
## stone_wet_zone.gd — CONTROLEUR DE `stone_wet.gdshader` (SHADER 04/10)
## Reference maitre : BLOC 05.20 · 09.21 · CI `no-outline` · CI `waterline-12-30`.
## -----------------------------------------------------------------------------
## LA LIGNE D'EAU QUI N'EXISTE PLUS.
##
##   La pierre de Velmora est humide jusqu'a 12 m d'altitude, seche au-dessus
##   de 30 m, et fait une transition entre les deux. Cette ligne est le niveau
##   de l'eau D'AVANT.
##
##   [OBL] CE N'EST JAMAIS EXPLIQUE. Aucun dialogue, aucun document, aucun
##   Echo n'y fait reference. Un joueur attentif comprend qu'il y a eu un
##   avant ou la ville etait sous l'eau jusqu'au premier etage. Il n'aura
##   jamais confirmation. Job CI `waterline-12-30` verifie les deux seuils
##   dans tous les materiaux de pierre du chapitre.
##
## [OBL] L'INTERACTION EST UN CONTRASTE D'HUMIDITE, JAMAIS UN CONTOUR (09.21).
##   Quand un objet est interactif, sa pierre devient legerement PLUS SECHE
##   que son entourage — delta clampe a 0.22, jamais plus. Pas de rim light,
##   pas d'outline, pas de pulsation, pas de particule.
##   Job CI `no-outline` : rend 120 objets interactifs et verifie qu'aucun
##   pixel de bord ne depasse de plus de 0.22 le voisinage.
## =============================================================================

const WET_LINE_LOW_M := 12.0         ## [OBL] CI `waterline-12-30`
const WET_LINE_HIGH_M := 30.0        ## [OBL] CI `waterline-12-30`
const INTERACT_DELTA_MAX := 0.22     ## [OBL] CI `no-outline`

@export var materials: Array[ShaderMaterial] = []

var _maree_level_y := 0.0


func _ready() -> void:
	MareeState.level_changed.connect(_on_maree_level_changed)
	_push_constants()


func _push_constants() -> void:
	for m in materials:
		if m == null:
			continue
		m.set_shader_parameter("wet_line_low", WET_LINE_LOW_M)
		m.set_shader_parameter("wet_line_high", WET_LINE_HIGH_M)
		m.set_shader_parameter("interact_delta_max", INTERACT_DELTA_MAX)


func _on_maree_level_changed(level_y: float) -> void:
	## Le verre de la Maree et la pierre partagent la meme altitude de
	## reference. Si les deux divergent d'un centimetre, l'oeil le voit :
	## la pierre semble flotter.
	_maree_level_y = level_y
	for m in materials:
		if m != null:
			m.set_shader_parameter("maree_level_y", level_y)


func set_interactive(m: ShaderMaterial, on: bool) -> void:
	## Appele par `InteractionSystem`. La valeur est clampee ici ET dans le
	## shader : deux verrous, parce que c'est la regle la plus facile a
	## casser par inadvertance et la plus destructrice pour le langage visuel.
	if m == null:
		return
	m.set_shader_parameter("interact_dry", clampf(1.0 if on else 0.0, 0.0, 1.0))
