package com.enrpau.dualscreendex

import android.content.Context

/**
 * Animated sprites for the switch-era games, bundled with the app (no internet needed):
 * the games' animated 3D models, or black/white-style animated 2D sprites when "Use 2D sprites" is on.
 * Files live in assets/sprites/ani and assets/sprites/gen5ani, named like "charizard-megax.gif".
 */
object AnimatedSprites {

    enum class Kind(val dir: String) {
        MODEL_3D("ani"),       // animated 3d models from the games
        PIXEL_2D("gen5ani")    // black/white-style animated pixel sprites
    }

    private var names: Map<String, String>? = null
    private val index = HashMap<String, Set<String>>()

    /** The sprite file name for a pokemon / form ("charizard-megax"), or null if there is none. */
    @Synchronized
    fun spriteName(context: Context, id: Int, form: String?): String? {
        val map = names ?: try {
            context.assets.open("dex/showdown_names.csv").bufferedReader().useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    val p = line.split(',')
                    if (p.size < 3) null else "${p[0]}|${p[1]}" to p[2]
                }.toMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }.also { names = it }
        return map["$id|${form.orEmpty()}"]
    }

    /** Asset path of the animation for this pokemon / form, or null when the app doesn't have one. */
    @Synchronized
    fun assetPath(context: Context, kind: Kind, id: Int, form: String?): String? {
        val name = spriteName(context, id, form) ?: return null
        val files = index.getOrPut(kind.dir) {
            try { context.assets.list("sprites/${kind.dir}")?.toHashSet() ?: emptySet() } catch (e: Exception) { emptySet() }
        }
        if ("$name.gif" in files) return "sprites/${kind.dir}/$name.gif"
        // forms that look the same as the species (Let's Go partners) use the species' animation
        if (form in LOOKALIKE_FORMS) return assetPath(context, kind, id, null)
        return null
    }

    private val LOOKALIKE_FORMS = setOf("Partner")
}
