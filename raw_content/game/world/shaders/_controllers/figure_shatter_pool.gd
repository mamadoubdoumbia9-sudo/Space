extends Node3D
class_name FigureShatterPool
## =============================================================================
## figure_shatter_pool.gd — CONTROLEUR DE `figure_body.gdshader` (SHADER 02/10)
##                          et de `figure_core.gdshader`.
## Reference maitre : BLOC 05.18 · CI `no-parry-ui` · CI `fragments-47`.
## -----------------------------------------------------------------------------
## LES 47 FRAGMENTS.
##
##   Une Figure qui meurt n'explose pas en particules : elle se casse en 47
##   morceaux de verre preinstancies, tous distincts, tous rigides.
##   47 parce qu'a 40 on voit que c'est peu et qu'a 60 le framerate d'un
##   Adreno 640 tombe sous 30 quand deux Figures meurent ensemble.
##
##   [OBL] Les 47 sont PREINSTANCIES au chargement de zone. Aucune
##   instanciation pendant un combat. Job CI `fragments-47` compte les
##   `instantiate()` pendant un combat scripte : doit valoir 0.
##
## [OBL] LE COEUR QUI REMONTE EST LE SEUL TELL.
##   `figure_core.gdshader` affiche un mesh interne a 0.82 d'echelle, en
##   `blend_add` / `depth_draw_never`. Quand la Figure entre dans sa fenetre
##   de riposte, le coeur MONTE dans la poitrine. C'est tout.
##   Aucun contour, aucun flash, aucune icone, aucun ralenti, aucun son
##   dedie. Job CI `no-parry-ui` : inspecte l'arbre CanvasLayer pendant les
##   fenetres de riposte, doit etre vide.
##   Le joueur apprend ce tell par la mort. C'est le contrat.
## =============================================================================

const FRAGMENT_COUNT := 47            ## [OBL] NE PAS ARRONDIR. CI.
const CORE_SCALE := 0.82
const CORE_RISE_M := 0.19             ## la montee du coeur, en metres
const PARRY_WINDOW_S := 0.31

@export var fragment_scene: PackedScene
@export var body_mesh: MeshInstance3D
@export var core_mesh: MeshInstance3D

var _pool: Array[RigidBody3D] = []
var _free: Array[int] = []
var _core_mat: ShaderMaterial
var _body_mat: ShaderMaterial
var _core_base_y := 0.0
var _parry_t := 0.0


func _ready() -> void:
	_core_mat = core_mesh.get_surface_override_material(0) as ShaderMaterial
	_body_mat = body_mesh.get_surface_override_material(0) as ShaderMaterial
	_core_base_y = core_mesh.position.y
	core_mesh.scale = Vector3.ONE * CORE_SCALE

	for i in FRAGMENT_COUNT:
		var f := fragment_scene.instantiate() as RigidBody3D
		f.visible = false
		f.freeze = true
		f.process_mode = Node.PROCESS_MODE_DISABLED
		add_child(f)
		_pool.append(f)
		_free.append(i)


func _process(delta: float) -> void:
	# La fenetre de riposte : le coeur monte, puis redescend. Rien d'autre
	# ne change dans l'image.
	_parry_t = maxf(_parry_t - delta / PARRY_WINDOW_S, 0.0)
	var rise := sin(_parry_t * PI)          # 0 -> 1 -> 0
	core_mesh.position.y = _core_base_y + rise * CORE_RISE_M
	if _core_mat != null:
		# Le coeur devient un peu plus dense, pas plus lumineux : un flash
		# serait une UI deguisee.
		_core_mat.set_shader_parameter("core_density", 0.55 + rise * 0.28)


func open_parry_window() -> void:
	## Appele par l'AnimationPlayer de la Figure, sur le call-track de
	## l'attaque (voir ANIM_MANIFEST_744, familles A-0201 a A-0248).
	_parry_t = 1.0


func shatter(impact_world: Vector3, force: float) -> void:
	## 100 % de dommage. Les 47 partent d'un coup.
	body_mesh.visible = false
	core_mesh.visible = false
	for i in _pool.size():
		var f := _pool[i]
		f.global_position = body_mesh.global_position + _fragment_offset(i)
		f.visible = true
		f.freeze = false
		f.process_mode = Node.PROCESS_MODE_INHERIT
		var dir := (f.global_position - impact_world).normalized()
		f.linear_velocity = dir * force * _fragment_mass_factor(i)
		f.angular_velocity = Vector3(
			randf_range(-6.0, 6.0), randf_range(-6.0, 6.0), randf_range(-6.0, 6.0))


func _fragment_offset(i: int) -> Vector3:
	# Position de repos de chaque fragment dans la silhouette. Table figee :
	# deux Figures qui meurent de la meme facon se cassent pareil, et c'est
	# volontaire — le verre a un grain, pas une humeur.
	var a := float(i) * 2.399963          # angle d'or
	var h := float(i) / float(FRAGMENT_COUNT) * 1.72
	var r := 0.11 + 0.17 * sin(float(i) * 1.13)
	return Vector3(cos(a) * r, h, sin(a) * r)


func _fragment_mass_factor(i: int) -> float:
	# Les gros morceaux partent moins loin. 7 gros, 40 eclats.
	return 0.42 if i < 7 else 1.0
