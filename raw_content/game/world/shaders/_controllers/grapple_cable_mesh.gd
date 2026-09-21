extends MeshInstance3D
class_name GrappleCableMesh
## =============================================================================
## grapple_cable_mesh.gd — CONTROLEUR DE `grapple_cable.gdshader` (SHADER 08/10)
## Reference maitre : BLOC 05.24 · S7/D01 · S8/D01 · CI `cable-splice-s8`.
## -----------------------------------------------------------------------------
## LA CATENAIRE A 32 SEGMENTS, RECONSTRUITE CHAQUE FRAME.
##
##   Un cable tendu n'est pas une droite : c'est une chainette. Le joueur ne
##   sait pas ce qu'est une chainette. Il sait qu'une droite parfaite ressemble
##   a un laser et qu'une chainette ressemble a une corde.
##
##   32 segments. Pas 16 (on voit les cassures a 10 m), pas 64 (on ne voit
##   rien de plus et c'est deux fois le cout de rebuild).
##
## [OBL] APRES S7, LE CABLE EST PLUS COURT DE 4 M.
##   Sol le repare avec un epissage a quatre torons. `max_length_m` passe de
##   22.0 a 18.0 et NE REVIENT JAMAIS. Le level design de S8 est concu pour
##   18 m. Job CI `cable-splice-s8`.
##
## [OBL] LA TORSADE EST LE SEUL RETOUR DE TENSION.
##   Pas de jauge, pas de son continu, pas de vibration de manette (il n'y a
##   pas de manette : c'est un APK). `tension` pilote `twist_speed` et
##   `shimmer_gain` dans le shader, rien d'autre.
## =============================================================================

const SEGMENTS := 32
const RIBBON_WIDTH_M := 0.028        ## largeur du ruban, pas du cable
const SAG_COEFF := 0.085             ## creux de la chainette au repos

var max_length_m := 22.0             ## [OBL] 18.0 a partir de S8. Voir _ready.
var splice_position := -1.0          ## [OBL] >= 0 seulement a partir de S8.

var anchor_world := Vector3.ZERO
var attached := false
var tension := 0.0

var _mesh := ImmediateMesh.new()
var _mat: ShaderMaterial


func _ready() -> void:
	mesh = _mesh
	_mat = get_surface_override_material(0) as ShaderMaterial
	cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF

	# L'etat du cable est une propriete du monde, pas une option.
	if GameState.chapter_progress >= GameState.PROGRESS_S8:
		max_length_m = 18.0
		# L'epissage est a 31 % de la longueur cote ancre : la ou il a casse.
		splice_position = 0.31
	_apply_splice()


func _apply_splice() -> void:
	if _mat == null:
		return
	_mat.set_shader_parameter("splice_uv", splice_position)


func _process(_delta: float) -> void:
	if not attached:
		_mesh.clear_surfaces()
		visible = false
		return

	visible = true
	_rebuild()
	if _mat != null:
		_mat.set_shader_parameter("tension", tension)
		_mat.set_shader_parameter("twist_speed", lerpf(0.0, 4.2, tension))


func _rebuild() -> void:
	var a := global_position
	var b := anchor_world
	var span := a.distance_to(b)
	# Plus le cable est tendu, moins il pend. Physiquement faux, visuellement
	# juste : la vraie formule de chainette est invisible a cette echelle.
	var sag := SAG_COEFF * span * (1.0 - tension)

	# Le ruban fait toujours face a la camera : on construit sa largeur dans
	# le plan perpendiculaire a (cable x direction camera).
	var cam := get_viewport().get_camera_3d()
	var to_cam := (cam.global_position - a).normalized() if cam else Vector3.UP

	_mesh.clear_surfaces()
	_mesh.surface_begin(Mesh.PRIMITIVE_TRIANGLE_STRIP)

	for i in SEGMENTS + 1:
		var t := float(i) / float(SEGMENTS)
		var p := a.lerp(b, t)
		# Chainette approximee par une parabole : 4t(1-t) vaut 1 au milieu.
		p.y -= sag * 4.0 * t * (1.0 - t)

		var dir := (b - a).normalized()
		var side := dir.cross(to_cam).normalized() * (RIBBON_WIDTH_M * 0.5)

		# UV.x traverse le ruban (le shader en tire son AA analytique),
		# UV.y court le long du cable (torsade + epissage).
		_mesh.surface_set_uv(Vector2(0.0, t))
		_mesh.surface_add_vertex(to_local(p - side))
		_mesh.surface_set_uv(Vector2(1.0, t))
		_mesh.surface_add_vertex(to_local(p + side))

	_mesh.surface_end()


## Appele par `grapple_system.gd`. Refuse au-dela de la portee courante :
## en S8, le joueur decouvre la limite sans qu'on la lui annonce.
func try_attach(target: Vector3) -> bool:
	if global_position.distance_to(target) > max_length_m:
		return false
	anchor_world = target
	attached = true
	return true
