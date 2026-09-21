extends Node
class_name EchoHistoryBuffer
## =============================================================================
## echo_history_buffer.gd — CONTROLEUR DE `echo_reveal.gdshader` (SHADER 03/10)
## Reference maitre : BLOC 05.19 · CI `ritual-duration` · CI `no-echo-on-letter`.
## -----------------------------------------------------------------------------
## LES SIX FRAMES DE RETARD.
##
##   Le rituel d'Echo dure 2,44 s. Exactement. 31 fois dans le chapitre.
##   Pendant la revelation, l'image du present traine derriere celle du passe :
##   on conserve 6 frames de retard dans un anneau de textures, et
##   `echo_reveal.gdshader` melange la frame courante avec la frame N-6.
##
##   Ce n'est pas un motion blur. Un motion blur suit le mouvement. Ceci
##   retarde le monde. La difference est invisible en capture d'ecran et
##   evidente en jeu, ce qui est exactement le but.
##
## [OBL] DUREE FIGEE — 2.44 s. CI `ritual-duration`.
##   Pas de variante longue pour les "moments importants". Pas de raccourci
##   quand le joueur a deja compris. Le rituel est identique aux 31
##   occurrences parce que Lohen le fait toujours de la meme facon.
##
## [OBL] EN S8, LE RITUEL NE PRODUIT RIEN. CI `no-echo-on-letter`.
##   Lohen le lance devant la lettre. Les 2,44 s s'ecoulent. `reveal` reste a
##   0.0 pendant toute la duree. Aucun son de decouverte, aucun fondu, rien.
##   Puis l'anim de sortie joue normalement.
##   C'est le seul echec du systeme, et il n'est jamais commente.
## =============================================================================

const RITUAL_DURATION := 2.44          ## [OBL] NE PAS MODIFIER. CI.
const HISTORY_FRAMES := 6
const BUFFER_SCALE := 0.5              ## demi-resolution : Adreno, 05.16

signal ritual_started
signal ritual_finished(produced_echo: bool)

var _ring: Array[SubViewport] = []
var _write_index := 0
var _capturing := false

var _reveal := 0.0
var _elapsed := 0.0
var _active := false
var _current_echo_id := ""

@onready var _post_mat: ShaderMaterial = get_node("../EchoPostProcess").material


func _ready() -> void:
	var res := Vector2i(get_viewport().size) * BUFFER_SCALE
	for i in HISTORY_FRAMES:
		var vp := SubViewport.new()
		vp.size = res
		vp.disable_3d = true
		vp.render_target_update_mode = SubViewport.UPDATE_DISABLED
		add_child(vp)
		_ring.append(vp)
	set_process(false)


## Appele par `EchoSystem.trigger(echo_id)`.
## `echo_id` vide ou inconnu => le rituel se joue et ne produit rien.
func start_ritual(echo_id: String) -> void:
	_current_echo_id = echo_id
	_elapsed = 0.0
	_active = true
	_capturing = true
	set_process(true)
	ritual_started.emit()


func _process(delta: float) -> void:
	_elapsed += delta

	# Anneau : on ecrit la frame courante, on lit la plus ancienne.
	_write_index = (_write_index + 1) % HISTORY_FRAMES
	if _capturing:
		_ring[_write_index].render_target_update_mode = SubViewport.UPDATE_ONCE
	var read_index := (_write_index + 1) % HISTORY_FRAMES
	_post_mat.set_shader_parameter("history_tex", _ring[read_index].get_texture())

	# Courbe du rituel. Montee 0.62 s, plateau, descente 0.71 s.
	# Ces trois nombres sont les memes pour les 31 echos.
	var t := _elapsed / RITUAL_DURATION
	var curve := smoothstep(0.0, 0.254, t) * (1.0 - smoothstep(0.709, 1.0, t))

	# [OBL] S8 : le rituel s'execute, le reveal reste plat.
	var produces := _echo_exists(_current_echo_id)
	_reveal = curve if produces else 0.0

	_post_mat.set_shader_parameter("reveal", _reveal)

	if _elapsed >= RITUAL_DURATION:
		_active = false
		_capturing = false
		set_process(false)
		_post_mat.set_shader_parameter("reveal", 0.0)
		ritual_finished.emit(produces)


func _echo_exists(echo_id: String) -> bool:
	if echo_id.is_empty():
		return false
	# La table des 31 echos vit dans `EchoRegistry` (autoload, 04.03).
	# En S8 devant la lettre, `EchoSystem` appelle start_ritual("") :
	# volontairement, explicitement, avec un commentaire qui dit pourquoi.
	return EchoRegistry.has(echo_id)
