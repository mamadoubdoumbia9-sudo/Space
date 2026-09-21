extends Node3D
class_name FogZoneDriver
## =============================================================================
## fog_zone_driver.gd — CONTROLEUR DE `fog_volumetric_cheap.gdshader` (06/10)
## Reference maitre : BLOC 05.22 · 09.17 · CI `fog-plane-s8` · CI `fog-not-a-wall`.
## -----------------------------------------------------------------------------
## LES 12 SLICES, ET L'ALTITUDE DE 150 M.
##
##   Un MultiMesh de 12 quads alignes camera. Les slices basses sont plus
##   denses que les hautes : le tableau `SLICE_ALPHA` est la courbe qui fait
##   passer douze plans plats pour un volume.
##
## [OBL] A 150 M, LA BRUME PASSE SOUS LE JOUEUR (09.17).
##   Dans S8, en montant le Phare, `fog_plane_y` reste fixe a 150.0 pendant
##   que le joueur monte. Au moment ou il franchit cette altitude, Velmora
##   disparait sous une nappe blanche. Il ne reste que le ciel et le Phare.
##   Ce n'est pas une cinematique : c'est de l'arithmetique, et c'est pour ca
##   que ca fonctionne.
##   Job CI `fog-plane-s8` : la courbe doit valoir exactement 150.0 en S8.
##
## [OBL] LA BRUME N'EST JAMAIS UN MUR (CI `fog-not-a-wall`).
##   Interdiction de monter `density` pour masquer un pop-in, une limite de
##   niveau ou un chargement. Si quelque chose doit etre cache, on corrige le
##   LOD ou le level design.
## =============================================================================

const SLICE_COUNT := 12
const SLICE_SPACING_M := 4.0
## Courbe de densite par slice, du bas vers le haut. Somme ~ 4.0 : au-dela,
## l'overdraw coute plus que le resultat n'apporte.
const SLICE_ALPHA := [
	0.78, 0.72, 0.64, 0.55, 0.46, 0.38,
	0.31, 0.25, 0.19, 0.14, 0.10, 0.06,
]

@export var fog_plane_y := 18.0
@export var density := 0.62
@export var multimesh_instance: MultiMeshInstance3D

var _mat: ShaderMaterial


func _ready() -> void:
	assert(SLICE_ALPHA.size() == SLICE_COUNT)
	_mat = multimesh_instance.material_override as ShaderMaterial
	_build_slices()
	_push()


func _build_slices() -> void:
	var mm := multimesh_instance.multimesh
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.use_colors = true
	mm.instance_count = SLICE_COUNT
	for i in SLICE_COUNT:
		var y := float(i) * SLICE_SPACING_M
		mm.set_instance_transform(i, Transform3D(Basis(), Vector3(0.0, y, 0.0)))
		# L'opacite de la slice voyage dans COLOR.a, lue par le vertex shader.
		mm.set_instance_color(i, Color(1.0, 1.0, 1.0, SLICE_ALPHA[i]))


func _push() -> void:
	if _mat == null:
		return
	_mat.set_shader_parameter("fog_plane_y", fog_plane_y)
	_mat.set_shader_parameter("density", density)


func set_plane(y: float) -> void:
	fog_plane_y = y
	_push()
