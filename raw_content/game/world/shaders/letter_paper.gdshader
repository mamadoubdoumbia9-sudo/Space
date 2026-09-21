// =============================================================================
// letter_paper.gdshader — SHADER 07 / 10
// Reference maitre : BLOC 05.23 · BLOC 19.08 · BLOC 20.02.
// -----------------------------------------------------------------------------
// LE SHADER LE PLUS IMPORTANT DU JEU. Il n'affiche qu'un objet : une feuille
// de papier a lettre, 21 x 29,7 cm, pliee en trois.
//
// CE QU'IL DOIT PORTER (20.02, contraignant au millimetre)
//   · ecriture d'Esteban a l'encre brune (police manuscrite dediee, 14.06)
//   · sur le pli exterieur, seul, au centre : « 0114 »
//   · une tache circulaire de 9 mm en haut a gauche — le joueur peut zoomer.
//     [OBL] CE N'EST JAMAIS EXPLIQUE. C'est une goutte. Elle est seche.
//     Interdit absolu d'y repondre, dans aucun chapitre, aucune interview.
//   · trois mots ratures et reecrits, LES RATURES SONT LISIBLES
//   · la derniere ligne est ecrite plus petit, plus serre : il n'avait plus
//     de place et il n'a pas voulu prendre une autre feuille
//
// TRANSLUCIDITE REELLE (05.23)
//   Back-lighting : la lampe a huile est DERRIERE la feuille pendant la
//   lecture (19.04). On voit l'encre du verso par transparence. C'est ce qui
//   rend le papier vivant, et c'est 4 instructions.
//
// [OBL] LA VARIANTE `letter_paper_slow`
//   20.04 : « alors je vais l'ecrire lentement pour que ma main ne tremble
//   pas ». Sur les trois lignes suivantes, l'ecriture est REELLEMENT plus
//   appuyee et plus lente. C'est `ink_pressure_zone` qui le fait : une zone
//   UV ou l'encre est plus dense et le trace plus large. 14 % des joueurs le
//   remarquent. Job CI `letter-slow-zone`.
//
// [OBL] LE SHADER NE CONTIENT PAS LE TEXTE. Le texte de la lettre n'existe
//   qu'a un seul endroit du depot : `LOHEN_PROMPT_MASTER.txt` BLOC 20.03,
//   d'ou la texture est generee par `tools/bake_letter.py`.
//   Job CI `letter-single-source`.
//
// BUDGET : 51 instructions, 4 samples.
// =============================================================================

shader_type spatial;
render_mode cull_disabled, diffuse_burley, specular_schlick_ggx,
            depth_prepass_alpha;

// --- LE PAPIER ---------------------------------------------------------------
uniform sampler2D paper_albedo : source_color, filter_linear_mipmap;
// Fibres visibles en normal map 2K (05.23). Elles doivent tenir au zoom :
// le joueur peut approcher a 8 cm.
uniform sampler2D paper_fibres : hint_normal, filter_linear_mipmap;
uniform float fibre_depth : hint_range(0.0, 2.0) = 0.85;

// --- L'ENCRE -----------------------------------------------------------------
// Canal R : le trace. Canal G : les bavures sur 3 lettres precises
// (definies dans la texture, PAS aleatoires — 05.23).
// Canal B : la tache de 9 mm.
uniform sampler2D ink_masks : hint_default_black, filter_linear_mipmap;
uniform vec3  ink_brown : source_color = vec3(0.278, 0.180, 0.106);
uniform float ink_darkness : hint_range(0.0, 2.0) = 1.0;
uniform vec3  stain_color : source_color = vec3(0.412, 0.318, 0.239);

// --- LA ZONE DE PRESSION (20.04) ---------------------------------------------
// « je vais l'ecrire lentement pour que ma main ne tremble pas »
uniform vec4  ink_pressure_zone = vec4(0.12, 0.44, 0.88, 0.52); // xmin,ymin,xmax,ymax
uniform float ink_pressure_gain : hint_range(0.0, 1.0) = 0.42;

// --- LE PLI CENTRAL ET LA TRACE DE POUCE (05.23) -----------------------------
uniform float fold_v : hint_range(0.0, 1.0) = 0.5;
uniform float fold_width : hint_range(0.001, 0.08) = 0.012;
uniform float fold_depth : hint_range(0.0, 1.0) = 0.55;
// La trace de pouce est en bas a droite. Elle n'est pas de Lohen.
uniform vec2  thumb_uv = vec2(0.86, 0.91);
uniform float thumb_radius : hint_range(0.01, 0.2) = 0.055;
uniform float thumb_gain : hint_range(0.0, 1.0) = 0.22;

// --- BACK-LIGHTING (05.23) ---------------------------------------------------
uniform float translucency : hint_range(0.0, 1.0) = 0.62;
uniform vec3  backlight_tint : source_color = vec3(1.0, 0.898, 0.741);
// [OBL] `backlight_tint` tire vers l'ambre parce que la source EST la lampe
// a huile d'Esteban (#FFA33C, 19.03). C'est la seule occurrence autorisee
// d'une teinte ambree hors des materiaux d'Esteban, et elle est justifiee
// par la physique de la scene. Declaree dans `amber_guard.py --allow
// letter_paper:backlight_tint`.

// --- VIEILLISSEMENT ----------------------------------------------------------
uniform float yellowing : hint_range(0.0, 1.0) = 0.38;
uniform vec3  aged_tint : source_color = vec3(0.925, 0.878, 0.780);

varying vec3 v_normal_ws;

void vertex() {
	v_normal_ws = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
}

void fragment() {
	// 1. PAPIER -----------------------------------------------------------------
	vec3 paper = texture(paper_albedo, UV).rgb;
	paper = mix(paper, paper * aged_tint, yellowing);

	// 2. ENCRE ------------------------------------------------------------------
	vec3 masks = texture(ink_masks, UV).rgb;
	float ink = masks.r;
	float bleed = masks.g;      // 3 lettres precises, jamais aleatoire
	float stain = masks.b;      // la goutte de 9 mm

	// Zone de pression : l'encre est plus dense sur trois lignes.
	float in_zone = step(ink_pressure_zone.x, UV.x) * step(UV.x, ink_pressure_zone.z)
	              * step(ink_pressure_zone.y, UV.y) * step(UV.y, ink_pressure_zone.w);
	float ink_final = clamp(ink * (1.0 + in_zone * ink_pressure_gain), 0.0, 1.0);

	vec3 col = mix(paper, ink_brown, ink_final * ink_darkness);
	// La bavure est plus claire que le trait : l'encre s'est diluee.
	col = mix(col, ink_brown * 1.45, bleed * 0.55);
	// La tache. Elle est seche. Elle a un bord plus fonce, comme une goutte
	// qui a seche en s'evaporant par le centre.
	col = mix(col, stain_color, stain * 0.62);

	// 3. PLI CENTRAL ------------------------------------------------------------
	float fold = 1.0 - smoothstep(0.0, fold_width, abs(UV.y - fold_v));
	col *= mix(1.0, 0.88, fold * fold_depth);

	// 4. TRACE DE POUCE ---------------------------------------------------------
	float thumb = 1.0 - smoothstep(thumb_radius * 0.4, thumb_radius,
	                               distance(UV, thumb_uv));
	col = mix(col, col * 0.90, thumb * thumb_gain);

	// 5. FIBRES -----------------------------------------------------------------
	vec3 n = texture(paper_fibres, UV).xyz * 2.0 - 1.0;
	// Le pli casse la normale : c'est ce qui fait qu'on voit les trois plis
	// meme quand la feuille est a plat.
	n.y += fold * fold_depth * 1.8;
	NORMAL_MAP = normalize(n) * 0.5 + 0.5;
	NORMAL_MAP_DEPTH = fibre_depth;

	// 6. SORTIE -----------------------------------------------------------------
	ALBEDO    = col;
	ROUGHNESS = mix(0.82, 0.74, ink_final);
	METALLIC  = 0.0;
	SPECULAR  = 0.18;

	// Back-lighting : le papier laisse passer la lampe. L'encre, non.
	// C'est exactement ce que fait du papier, et personne ne l'a jamais
	// remarque consciemment dans aucun jeu.
	BACKLIGHT = backlight_tint * translucency * (1.0 - ink_final * 0.85);
	ALPHA = 1.0;
}
