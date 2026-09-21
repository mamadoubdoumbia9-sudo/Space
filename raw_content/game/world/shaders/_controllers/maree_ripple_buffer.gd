extends Node
class_name MareeRippleBuffer
## =============================================================================
## maree_ripple_buffer.gd — CONTROLEUR DE `maree_glass.gdshader` (SHADER 01/10)
## Reference maitre : BLOC 05.17 · CI `ripple-persistent`.
## -----------------------------------------------------------------------------
## LE BUFFER DE RIDES FIGEES.
##
##   Quand Lohen touche le verre de la Maree, une ride se grave. Elle ne part
##   jamais. Pas pendant la scene, pas pendant le chapitre, pas de la session.
##
##   [OBL] AUCUNE DECROISSANCE. Il n'y a pas de `decay`, pas de `fade`, pas de
##   timer d'effacement dans ce fichier. Si quelqu'un en ajoute un, le job CI
##   `ripple-persistent` echoue : il rejoue 400 contacts scriptes, capture le
##   buffer a t+0 s et t+900 s, et compare la somme des luminances. Tolerance
##   zero a la baisse.
##
##   La raison narrative n'est jamais dite dans le jeu : le verre n'est plus
##   liquide. Ce qui s'y inscrit reste. C'est la seule chose de Velmora qui
##   garde la trace de Lohen, et Lohen ne s'en apercoit pas.
##
## IMPLEMENTATION
##   Un ViewportTexture 256x256 R8 par zone de verre (128 m). Un quad avec un
##   materiau d'accumulation ecrit un point gaussien a chaque contact, en
##   `BLEND_MODE_ADD`, sur un viewport en `CLEAR_MODE_NEVER`.
##   Cout : 1 draw call par contact, zero par frame au repos.
## =============================================================================

const BUFFER_RES := 256
const ZONE_SIZE_M := 128.0

## Rayon d'une ride en metres monde. Un contact de main, pas une explosion.
const RIPPLE_RADIUS_M := 0.42
## Amplitude d'un contact. Plusieurs contacts au meme endroit se cumulent et
## saturent naturellement a 1.0 dans le shader : le verre ne se creuse pas
## indefiniment.
const RIPPLE_STRENGTH := 0.55

var _viewports: Dictionary = {}      # zone_id:int -> SubViewport
var _stamp_quads: Dictionary = {}    # zone_id:int -> MeshInstance2D
var _zone_origins: Dictionary = {}   # zone_id:int -> Vector2 (origine XZ monde)

var _stamp_shader := preload("res://game/world/shaders/_controllers/ripple_stamp.gdshader")


func register_zone(zone_id: int, origin_xz: Vector2) -> ViewportTexture:
	## Appele par `zone_loader.gd` au chargement d'une zone contenant du verre.
	if _viewports.has(zone_id):
		return _viewports[zone_id].get_texture()

	var vp := SubViewport.new()
	vp.size = Vector2i(BUFFER_RES, BUFFER_RES)
	vp.disable_3d = true
	vp.transparent_bg = false
	vp.render_target_update_mode = SubViewport.UPDATE_DISABLED
	# [OBL] NEVER. C'est cette ligne qui rend les rides permanentes.
	vp.render_target_clear_mode = SubViewport.CLEAR_MODE_NEVER
	add_child(vp)

	var quad := MeshInstance2D.new()
	quad.mesh = QuadMesh.new()
	var mat := ShaderMaterial.new()
	mat.shader = _stamp_shader
	mat.blend_mode = CanvasItemMaterial.BLEND_MODE_ADD
	quad.material = mat
	vp.add_child(quad)

	_viewports[zone_id] = vp
	_stamp_quads[zone_id] = quad
	_zone_origins[zone_id] = origin_xz

	# Un clear unique a l'ouverture, puis plus jamais.
	vp.render_target_clear_mode = SubViewport.CLEAR_MODE_ONCE
	vp.render_target_update_mode = SubViewport.UPDATE_ONCE
	await RenderingServer.frame_post_draw
	vp.render_target_clear_mode = SubViewport.CLEAR_MODE_NEVER

	return vp.get_texture()


func stamp(zone_id: int, world_xz: Vector2, strength: float = RIPPLE_STRENGTH) -> void:
	## Grave une ride. Appele par `glass_contact_area.gd` sur `body_entered`,
	## et par `anim_event_glass_touch` (voir ANIM_MANIFEST_744, A-0412).
	if not _viewports.has(zone_id):
		push_warning("MareeRippleBuffer: zone %d non enregistree" % zone_id)
		return

	var origin: Vector2 = _zone_origins[zone_id]
	var uv := (world_xz - origin) / ZONE_SIZE_M
	if uv.x < 0.0 or uv.x > 1.0 or uv.y < 0.0 or uv.y > 1.0:
		return

	var mat: ShaderMaterial = _stamp_quads[zone_id].material
	mat.set_shader_parameter("stamp_uv", uv)
	mat.set_shader_parameter("stamp_radius", RIPPLE_RADIUS_M / ZONE_SIZE_M)
	mat.set_shader_parameter("stamp_strength", strength)

	# UPDATE_ONCE + CLEAR_MODE_NEVER = accumulation pure.
	_viewports[zone_id].render_target_update_mode = SubViewport.UPDATE_ONCE


func get_texture(zone_id: int) -> ViewportTexture:
	if not _viewports.has(zone_id):
		return null
	return _viewports[zone_id].get_texture()


## --- DIAGNOSTIC CI -----------------------------------------------------------
## Utilise par `tools/amber_guard.py --scan-shaders` et par le job
## `ripple-persistent`. N'est jamais appele en jeu.
func debug_total_luminance(zone_id: int) -> float:
	if not _viewports.has(zone_id):
		return -1.0
	var img: Image = _viewports[zone_id].get_texture().get_image()
	var total := 0.0
	for y in range(0, BUFFER_RES, 4):
		for x in range(0, BUFFER_RES, 4):
			total += img.get_pixel(x, y).r
	return total
