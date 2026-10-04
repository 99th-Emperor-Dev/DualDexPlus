package com.enrpau.dualscreendex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import com.enrpau.dualscreendex.data.RomManager

// loads the bundled pixel sprites from assets/sprites/<folder>/<id>[-form].png
object SpriteManager {

    // in release order; older sets only cover their own dex, so we fall forward to the next era first
    private val fullSpriteFolders = listOf("gold", "silver", "crystal", "rs", "emerald", "frlg", "dp", "pt", "hgss", "bw")

    private val index = HashMap<String, Set<String>>()
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    // gen 6+ art is smooth, everything else is pixel art
    private const val MODERN = "gen6"
    private const val GEN1 = "rb"
    private const val GEN5_STYLE = "gen5"

    private class Result(val bitmap: Bitmap, val folder: String)

    /** Large battle sprite for the current game, falling back to other sets so something always shows. */
    fun loadSprite(context: Context, pokemon: Pokemon): Bitmap? = findSprite(context, pokemon)?.bitmap

    private fun findSprite(context: Context, pokemon: Pokemon): Result? {
        val preferred = resolveFolder(context)
        val order = if (preferred == MODERN) {
            listOf(MODERN, "bw", "icons")
        } else {
            val i = fullSpriteFolders.indexOf(preferred)
            val eraFallback = if (i == -1) fullSpriteFolders.reversed()
                else fullSpriteFolders.drop(i + 1) + fullSpriteFolders.take(i).reversed()
            // pixel sets first; anything they lack (newer species, megas, forms) uses the sharp 3d render
            // gen5: black/white-style 2d sprites for everything newer (#650+) and the megas / regional forms
            (listOf(preferred) + eraFallback + GEN5_STYLE + MODERN + "icons").distinct()
        }
        // a mega / regional form's own sprite anywhere beats the base species' sprite
        if (pokemon.variantLabel != null) {
            for (folder in order) load(context, folder, pokemon, formOnly = true)?.let { return Result(it, folder) }
        }
        for (folder in order) load(context, folder, pokemon, formOnly = false)?.let { return Result(it, folder) }
        return null
    }

    /** Small list/team icon: the same game-specific sprite as the card (box icons are only a last resort). */
    private fun findIcon(context: Context, pokemon: Pokemon): Result? = findSprite(context, pokemon)

    /**
     * Binds a pokemon's sprite. Big sprites ([icon] = false) play the idle animation in games that had one
     * (Crystal, Emerald); list icons stay still.
     */
    fun bindSprite(view: ImageView, pokemon: Pokemon?, icon: Boolean = false) {
        view.colorFilter = ThemeManager.spriteFilter()
        (view.drawable as? AnimationDrawable)?.stop()
        (view.drawable as? android.graphics.drawable.AnimatedImageDrawable)?.stop()
        view.setTag(R.id.tag_anim_key, null)
        stopIdle(view)
        // "Animations" off in settings: still sprites only (saves power on weaker handhelds)
        val animate = com.enrpau.dualscreendex.data.GameCatalog.isAnimationsOn(view.context)
        if (pokemon != null && !icon && animate) {
            animationFor(view.context, pokemon)?.let { anim ->
                view.setImageDrawable(anim)
                view.visibility = android.view.View.VISIBLE
                view.post { anim.start() }
                return
            }
        }
        val res = pokemon?.let { if (icon) findIcon(view.context, it) else findSprite(view.context, it) }
            ?.let { r -> if (needsPalette(r.folder)) Result(paletteCopy(r.bitmap), r.folder) else r }
        view.setImageDrawable(res?.let { drawable(view.context, it) })
        view.visibility = if (res != null) android.view.View.VISIBLE else android.view.View.INVISIBLE
        // no idle animation for this sprite: give big sprites a gentle idle motion instead
        if (res != null && !icon && animate) startIdle(view, smooth = res.folder == MODERN)
        view.setTag(R.id.tag_anim_key, null)
        if (pokemon != null && !icon && res != null && animate) loadOnlineAnimation(view, pokemon)
    }

    /** Which downloadable animation set applies right now: 3d models, or 2d sprites when "Use 2D" is on. */
    private fun onlineKind(context: Context): AnimatedSprites.Kind? {
        val catalog = com.enrpau.dualscreendex.data.GameCatalog
        val folder = catalog.currentVersion(context)?.spriteFolder
        val custom = catalog.customSpriteSet(context)
        val modern = if (catalog.isMatchDex(context)) folder == MODERN
            else if (custom != null) custom == MODERN || custom == "bw"
            else RomManager.currentProfile.baseMechanics == com.enrpau.dualscreendex.data.RomProfile.Mechanics.GEN_6_PLUS
        if (!modern || ThemeManager.isLimitedPalette) return null
        val twoD = catalog.isUse2D(context) || custom == "bw" ||
            (catalog.currentVersion(context)?.always2D == true && catalog.isMatchDex(context))
        return if (twoD) AnimatedSprites.Kind.PIXEL_2D else AnimatedSprites.Kind.MODEL_3D
    }

    private fun loadOnlineAnimation(view: ImageView, pokemon: Pokemon) {
        val context = view.context
        val kind = onlineKind(context) ?: return
        val key = "${kind.dir}|${pokemon.id}|${pokemon.variantLabel.orEmpty()}"
        view.setTag(R.id.tag_anim_key, key)
        val path = AnimatedSprites.assetPath(context, kind, pokemon.id, pokemon.variantLabel) ?: return
        val drawable = try {
            if (kind == AnimatedSprites.Kind.MODEL_3D) {
                // smooth 3d animation, decoded frame by frame by the platform
                (android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(context.assets, path))
                    as? android.graphics.drawable.AnimatedImageDrawable)?.apply { repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE }
                    ?.let { anim -> CroppedDrawable(anim, contentBounds(context, path)) }
            } else {
                // pixel animation: our own frames so scaling stays crisp
                val frames = animCache.get(key) ?: decodeGif(context, path.removePrefix("sprites/"), cleanEdges = false)?.also { animCache.put(key, it) }
                frames?.takeIf { it.bitmaps.size > 1 }?.let { fr ->
                    AnimationDrawable().apply {
                        isOneShot = false
                        fr.bitmaps.forEachIndexed { i, b ->
                            addFrame(BitmapDrawable(context.resources, b).apply { setFilterBitmap(false) }, fr.durations[i])
                        }
                    }
                }
            }
        } catch (e: Exception) {
            null
        } ?: return
        stopIdle(view)
        view.setImageDrawable(drawable)
        view.visibility = android.view.View.VISIBLE
        when (drawable) {
            is android.graphics.drawable.AnimatedImageDrawable -> drawable.start()
            is CroppedDrawable -> (drawable.inner as? android.graphics.drawable.AnimatedImageDrawable)?.start()
            is AnimationDrawable -> drawable.start()
        }
    }

    private val boundsCache = HashMap<String, android.graphics.Rect?>()

    /** Where the pokemon actually is in an animation's canvas (first frame, plus room to move). */
    private fun contentBounds(context: Context, path: String): android.graphics.Rect? = boundsCache.getOrPut(path) {
        try {
            val first = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.assets, path)) { d, _, _ ->
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val w = first.width; val h = first.height
            val px = IntArray(w * h); first.getPixels(px, 0, w, 0, 0, w, h)
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) for (x in 0 until w) if (android.graphics.Color.alpha(px[y * w + x]) > 16) {
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
            }
            if (maxX < 0) null else {
                // margin so idle movement isn't clipped; keep it square and on the floor
                val pad = (maxOf(maxX - minX, maxY - minY) * 0.04f).toInt()
                val side = maxOf(maxX - minX, maxY - minY) + pad * 2
                val cx = (minX + maxX) / 2
                val left = (cx - side / 2).coerceIn(0, maxOf(0, w - side))
                val top = (maxY + pad - side).coerceIn(0, maxOf(0, h - side))
                android.graphics.Rect(left, top, minOf(w, left + side), minOf(h, top + side))
            }
        } catch (e: Exception) { null }
    }

    /** Shows only [crop] of [inner] (null = all of it), forwarding animation frames. */
    class CroppedDrawable(val inner: Drawable, private val crop: android.graphics.Rect?) : Drawable(), Drawable.Callback {
        init { inner.callback = this; inner.setBounds(0, 0, inner.intrinsicWidth, inner.intrinsicHeight) }
        override fun getIntrinsicWidth() = crop?.width() ?: inner.intrinsicWidth
        override fun getIntrinsicHeight() = crop?.height() ?: inner.intrinsicHeight
        override fun draw(canvas: android.graphics.Canvas) {
            val c = crop ?: android.graphics.Rect(0, 0, inner.intrinsicWidth, inner.intrinsicHeight)
            val save = canvas.save()
            canvas.clipRect(bounds)
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / c.width().toFloat(), bounds.height() / c.height().toFloat())
            canvas.translate(-c.left.toFloat(), -c.top.toFloat())
            inner.draw(canvas)
            canvas.restoreToCount(save)
        }
        override fun setAlpha(alpha: Int) { inner.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { inner.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        override fun invalidateDrawable(who: Drawable) = invalidateSelf()
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
        override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
    }
    private fun stopIdle(view: ImageView) {
        (view.getTag(R.id.tag_idle_anim) as? android.animation.Animator)?.cancel()
        view.setTag(R.id.tag_idle_anim, null)
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    /** 3d models breathe smoothly; pixel sprites hop between two positions like in the games. */
    private fun startIdle(view: ImageView, smooth: Boolean) {
        val d = view.resources.displayMetrics.density
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (smooth) 1700 else 900
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { v ->
                val f = v.animatedValue as Float
                if (smooth) {
                    view.pivotY = view.height.toFloat()
                    view.pivotX = view.width / 2f
                    view.scaleY = 1f + 0.035f * f
                    view.scaleX = 1f - 0.012f * f
                    view.translationY = -2f * d * f
                } else {
                    view.translationY = if (f > 0.5f) -2f * d else 0f
                }
            }
        }
        view.setTag(R.id.tag_idle_anim, anim)
        anim.start()
    }

    // nearest-neighbour scaling keeps the pixel art crisp; the 3d renders get normal filtering
    private fun drawable(context: Context, res: Result): Drawable =
        BitmapDrawable(context.resources, res.bitmap).apply {
            val smooth = res.folder == MODERN
            setFilterBitmap(smooth)
            setAntiAlias(smooth)
        }

    /** The selected game version picks the set (Yellow, Silver, Emerald...); otherwise it follows the dex generation. */
    private fun resolveFolder(context: Context): String {
        val catalog = com.enrpau.dualscreendex.data.GameCatalog
        val version = catalog.currentVersion(context)
        // a sprite set picked by hand (only offered when the dex isn't matched to a game)
        catalog.customSpriteSet(context)?.let { set ->
            return if (set == MODERN && catalog.isUse2D(context)) "bw" else set
        }
        val folder = if (version != null && catalog.isMatchDex(context)) version.spriteFolder
        else when (RomManager.currentProfile.baseMechanics) {
            com.enrpau.dualscreendex.data.RomProfile.Mechanics.GEN_1 -> if (version?.spriteFolder == "yellow") "yellow" else GEN1
            com.enrpau.dualscreendex.data.RomProfile.Mechanics.GEN_2_TO_5 ->
                version?.spriteFolder?.takeIf { it in fullSpriteFolders } ?: "emerald"
            com.enrpau.dualscreendex.data.RomProfile.Mechanics.GEN_6_PLUS -> MODERN
        }
        // "Use 2D sprites": black/white pixel sprites where they exist
        val twoD = catalog.isUse2D(context) || (version?.always2D == true && catalog.isMatchDex(context))
        return if (folder == MODERN && twoD) "bw" else folder
    }

    // sprites recoloured into the theme's hardware palette, cached per theme
    private val paletteCache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    // real game boy color sprites are already 4-colour GBC art; recolouring them would only distort them
    private val gbcNativeFolders = setOf("gold", "silver", "crystal", "crystal_anim")

    private fun needsPalette(folder: String): Boolean = when {
        !ThemeManager.isLimitedPalette -> false
        ThemeManager.nativeColorSprites -> false
        ThemeManager.currentTheme.gbc15Bit -> folder !in gbcNativeFolders
        else -> true
    }

    private fun paletteCopy(src: Bitmap): Bitmap {
        if (!ThemeManager.isLimitedPalette) return src
        val key = ThemeManager.currentTheme.id + "/" + System.identityHashCode(src)
        return paletteCache.get(key) ?: ThemeManager.quantizeBitmap(src).also { paletteCache.put(key, it) }
    }

    // ---------- idle animations ----------

    private class Frames(val bitmaps: List<Bitmap>, val durations: List<Int>)

    private val animCache = object : LruCache<String, Frames>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Frames) = value.bitmaps.sumOf { it.byteCount }.coerceAtLeast(1)
    }

    private fun animationFor(context: Context, pokemon: Pokemon): AnimationDrawable? {
        if (pokemon.variantLabel != null) return null // the animated sets only have base forms
        val catalog = com.enrpau.dualscreendex.data.GameCatalog
        if (ThemeManager.nativeColorSprites) return null   // yellow's pokemon didn't animate
        // custom dex: the chosen sprite set brings its own idle animations (Crystal, Emerald)
        val folder = if (!catalog.isMatchDex(context)) when (catalog.customSpriteSet(context)) {
            "crystal" -> "crystal_anim"
            "emerald" -> "emerald_anim"
            else -> return null
        } else catalog.currentVersion(context)?.animFolder ?: return null
        val name = "${pokemon.id}.gif"
        if (name !in folderIndex(context, folder)) return null
        val key = "$folder/$name"
        val frames = animCache.get(key) ?: decodeGif(context, key, cleanEdges = folder in opaqueAnimFolders)
            ?.also { animCache.put(key, it) } ?: return null
        if (frames.bitmaps.size < 2) return null
        return AnimationDrawable().apply {
            isOneShot = false
            frames.bitmaps.forEachIndexed { i, b ->
                val frame = if (needsPalette(folder)) paletteCopy(b) else b
                addFrame(BitmapDrawable(context.resources, frame).apply { setFilterBitmap(false) }, frames.durations[i])
            }
        }
    }

    // crystal's animations were ripped on white, like its stills
    private val opaqueAnimFolders = setOf("crystal_anim")

    /** Renders every frame of a GIF, removes the edge background, and crops all frames to one shared box. */
    @Suppress("DEPRECATION")
    private fun decodeGif(context: Context, key: String, cleanEdges: Boolean): Frames? =
        try { decodeGifBytes(context.assets.open("sprites/$key").use { it.readBytes() }, cleanEdges) } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    private fun decodeGifBytes(bytes: ByteArray, cleanEdges: Boolean): Frames? = try {
        val movie = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size)
        if (movie == null || movie.width() <= 0) null else {
            val w = movie.width()
            val h = movie.height()
            val duration = movie.duration().coerceAtLeast(1)
            val step = 40
            val canvasBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(canvasBmp)
            val raw = ArrayList<IntArray>()
            val times = ArrayList<Int>()
            var t = 0
            while (t < duration && raw.size < 150) {
                canvasBmp.eraseColor(0)
                movie.setTime(t)
                movie.draw(canvas, 0f, 0f)
                val px = IntArray(w * h)
                canvasBmp.getPixels(px, 0, w, 0, 0, w, h)
                if (cleanEdges) clearEdgeBackground(px, w, h)
                if (raw.isNotEmpty() && raw.last().contentEquals(px)) times[times.lastIndex] += step
                else { raw += px; times += step }
                t += step
            }
            // one crop box for every frame so the pokemon doesn't jitter
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (px in raw) for (y in 0 until h) for (x in 0 until w) {
                if (android.graphics.Color.alpha(px[y * w + x]) > 16) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
            }
            if (maxX < 0) null else {
                val bw = maxX - minX + 1
                val bh = maxY - minY + 1
                val side = maxOf(bw, bh)
                val bitmaps = raw.map { px ->
                    val full = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                    Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also { out ->
                        android.graphics.Canvas(out).drawBitmap(
                            Bitmap.createBitmap(full, minX, minY, bw, bh),
                            ((side - bw) / 2).toFloat(), (side - bh).toFloat(), null)
                    }
                }
                Frames(bitmaps, times)
            }
        }
    } catch (e: Exception) {
        null
    }
    private fun load(context: Context, folder: String, pokemon: Pokemon, formOnly: Boolean): Bitmap? {
        val files = folderIndex(context, folder)
        val names = candidateNames(pokemon).let { if (formOnly) it.dropLast(1) else it }
        val name = names.firstOrNull { it in files } ?: return null
        val key = "$folder/$name"
        cache.get(key)?.let { return it }
        return try {
            val raw = context.assets.open("sprites/$key").use { BitmapFactory.decodeStream(it) } ?: return null
            val clean = if (folder in opaqueBackgroundFolders) removeEdgeBackground(raw) else raw
            trimTransparent(clean).also { cache.put(key, it) }
        } catch (e: Exception) {
            null
        }
    }
    // the game boy sets were ripped with a solid white background
    private val opaqueBackgroundFolders = setOf("rb", "yellow", "gold", "silver", "crystal")

    /** Makes the background colour transparent, but only where it touches the image edge (keeps white eyes etc). */
    private fun removeEdgeBackground(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        if (android.graphics.Color.alpha(px[0]) == 0) return src
        clearEdgeBackground(px, w, h)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun clearEdgeBackground(px: IntArray, w: Int, h: Int) {
        val bg = px[0]
        if (android.graphics.Color.alpha(bg) == 0) return
        val seen = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        for (x in 0 until w) { stack.add(x); stack.add((h - 1) * w + x) }
        for (y in 0 until h) { stack.add(y * w); stack.add(y * w + w - 1) }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (seen[i] || px[i] != bg) continue
            seen[i] = true
            px[i] = 0
            val x = i % w
            val y = i / w
            if (x > 0) stack.add(i - 1)
            if (x < w - 1) stack.add(i + 1)
            if (y > 0) stack.add(i - w)
            if (y < h - 1) stack.add(i + w)
        }
    }

    /** Crops empty transparent margins so every sprite fills its view and sits centred. */
    private fun trimTransparent(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (android.graphics.Color.alpha(px[y * w + x]) > 16) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return src
        // keep it square so small and large pokemon scale consistently
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val side = maxOf(bw, bh)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).drawBitmap(
            Bitmap.createBitmap(src, minX, minY, bw, bh),
            ((side - bw) / 2).toFloat(), (side - bh).toFloat(), null // centred, standing on the bottom
        )
        return out
    }

    private fun candidateNames(pokemon: Pokemon): List<String> {
        val id = pokemon.id
        val label = pokemon.variantLabel?.lowercase()?.trim()
        val out = ArrayList<String>()
        if (!label.isNullOrEmpty()) {
            val slug = label.replace(" ", "-")
            out += "$id-$slug.png"
            // "Alolan" -> "alola", "Galarian" -> "galar", "Hisuian" -> "hisui"
            out += "$id-${slug.removeSuffix("ian").removeSuffix("an").removeSuffix("n")}.png"
            if ("mega" in slug) out += "$id-mega.png"
        }
        out += "$id.png"
        return out
    }

    @Synchronized
    private fun folderIndex(context: Context, folder: String): Set<String> =
        index.getOrPut(folder) {
            try {
                context.assets.list("sprites/$folder")?.toHashSet() ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }
        }
}
