extends Node3D
class_name LighthouseHandoff
## =============================================================================
## lighthouse_handoff.gd — CONTROLEUR DE `skybox_velmora.gdshader` (SHADER 09/10)
## Reference maitre : BLOC 05.25 · CI `lighthouse-seam` · CI `lighthouse-dark`
##                    · 21.08 (interdits absolus).
## -----------------------------------------------------------------------------
## LE RACCORD INVISIBLE A 400 M.
##
##   Le Phare existe deux fois : peint dans le skybox, et en geometrie reelle.
##   Entre 420 m et 380 m, les deux se croisent. Le joueur ne doit jamais voir
##   ni double, ni trou.
##
##   Le fondu n'est pas lineaire : la silhouette skybox tient jusqu'a 400 m
##   puis s'efface vite, pendant que la geometrie monte lentement depuis
##   420 m. A 400 m exactement, geometrie = 0.5 et skybox = 0.5, et les deux
##   images sont assez proches pour que la somme soit correcte.
##
## [OBL] IL NE S'ALLUME PAS. 21.08, interdit absolu.
##   Ce script n'ecrit JAMAIS `lighthouse_lamp`. La seule ligne qui le
##   mentionne est `assert`. Job CI `lighthouse-dark` echoue si la valeur est
##   non nulle a n'importe quelle frame de n'importe quelle scene.
## =============================================================================

const HANDOFF_CENTER_M := 400.0
const HANDOFF_HALF_WIDTH_M := 20.0

@export var geometry_root: Node3D        ## le Phare reel, 212 m, 1038 marches
@export var sky_material: ShaderMaterial

var _player: Node3D


func _ready() -> void:
	_player = get_tree().get_first_node_in_group("player")
	assert(sky_material.get_shader_parameter("lighthouse_lamp") == 0.0,
		"21.08 : le Phare ne s'allume pas dans le chapitre 1.")


func _process(_delta: float) -> void:
	if _player == null or sky_material == null:
		return

	# Distance horizontale seulement : sinon, monter sur un toit ferait
	# clignoter le raccord.
	var a := Vector2(_player.global_position.x, _player.global_position.z)
	var b := Vector2(global_position.x, global_position.z)
	var d := a.distance_to(b)

	# 1.0 = skybox seule (loin), 0.0 = geometrie seule (proche).
	var t := clampf(
		(d - (HANDOFF_CENTER_M - HANDOFF_HALF_WIDTH_M)) / (HANDOFF_HALF_WIDTH_M * 2.0),
		0.0, 1.0)
	var sky_w := smoothstep(0.0, 1.0, t)

	sky_material.set_shader_parameter("lighthouse_fade", sky_w)

	if geometry_root != null:
		geometry_root.visible = sky_w < 1.0
		# La geometrie prend le relais par la brume, pas par l'opacite :
		# un fondu alpha sur 212 m de tour se verrait.
		for c in geometry_root.find_children("*", "MeshInstance3D"):
			var m := (c as MeshInstance3D).get_surface_override_material(0)
			if m is ShaderMaterial:
				m.set_shader_parameter("haze_blend", sky_w)
