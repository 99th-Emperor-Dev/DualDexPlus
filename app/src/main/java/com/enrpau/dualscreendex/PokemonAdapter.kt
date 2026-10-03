package com.enrpau.dualscreendex

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import com.enrpau.dualscreendex.data.RomProfile
import com.google.android.material.card.MaterialCardView

class PokemonAdapter(
    private var fullList: List<Pokemon>,
    private val onClick: (Pokemon) -> Unit
) : RecyclerView.Adapter<PokemonAdapter.PokemonViewHolder>() {

    private var filteredList = fullList.toMutableList()

    /** Called with how often each sprite colour appears across the rows currently on screen (GBC palette budget). */
    var onSpriteColorsChanged: ((Map<Int, Int>) -> Unit)? = null
    private val rowColors = HashMap<PokemonViewHolder, Map<Int, Int>>()
    private val colorCache = android.util.LruCache<Int, Map<Int, Int>>(256)

    private fun spriteColors(holder: PokemonViewHolder): Map<Int, Int> {
        val bmp = (holder.ivIcon.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return emptyMap()
        val id = System.identityHashCode(bmp)
        colorCache.get(id)?.let { return it }
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val counts = HashMap<Int, Int>()
        for (p in px) if (android.graphics.Color.alpha(p) >= 128) counts[p or 0xFF000000.toInt()] = (counts[p or 0xFF000000.toInt()] ?: 0) + 1
        return counts.also { colorCache.put(id, it) }
    }

    private fun publishColors() {
        val cb = onSpriteColorsChanged ?: return
        val total = HashMap<Int, Int>()
        rowColors.values.forEach { m -> m.forEach { (k, v) -> total[k] = (total[k] ?: 0) + v } }
        cb(total)
    }

    override fun onViewRecycled(holder: PokemonViewHolder) {
        super.onViewRecycled(holder)
        if (rowColors.remove(holder) != null) publishColors()
    }
    private var currentMechanics: RomProfile.Mechanics = RomProfile.Mechanics.GEN_6_PLUS
    private var currentTheme: AppTheme = ThemeManager.currentTheme

    inner class PokemonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val tvName: TextView = itemView.findViewById(R.id.tvRowName)
        val tvId: TextView = itemView.findViewById(R.id.tvRowId)
        val typeContainer: LinearLayout = itemView.findViewById(R.id.rowTypesContainer)
        val ivIcon: android.widget.ImageView = itemView.findViewById(R.id.ivRowIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PokemonViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pokemon_row, parent, false)
        return PokemonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PokemonViewHolder, position: Int) {
        val pokemon = filteredList[position]

        val cleanName = pokemon.name.replaceFirstChar { it.uppercase() }

        val displayName = if (pokemon.variantLabel != null) {
            "$cleanName (${pokemon.variantLabel})"
        } else {
            cleanName
        }

        holder.tvName.text = displayName
        holder.tvId.text = String.format("#%03d", pokemon.dexNumber ?: pokemon.id)
        SpriteManager.bindSprite(holder.ivIcon, pokemon, icon = true)

        holder.tvName.setTextColor(currentTheme.listTextColor)
        holder.tvId.setTextColor(ThemeManager.ui(ColorUtils.setAlphaComponent(currentTheme.listTextColor, 110)))

        // card surface: retro theme stays flat/transparent to keep its pixel-grid look,
        // every other theme gets a soft elevated surface that sits just off the page background
        val inner = holder.card.getChildAt(0)
        if (currentTheme.isRetroScreen) {
            // each row is a little game window
            holder.card.setCardBackgroundColor(Color.TRANSPARENT)
            holder.card.radius = 0f
            holder.card.strokeWidth = 0
            inner.background = ThemeManager.boxDrawable(holder.itemView.context, theme = currentTheme)
            holder.tvId.setTextColor(currentTheme.labelTextColor)
        } else {
            inner.background = null
            holder.card.setCardBackgroundColor(currentTheme.gridBackgroundColor)
            holder.card.radius = currentTheme.cardCornerRadius
            holder.card.strokeWidth = 1
            holder.card.strokeColor = ColorUtils.setAlphaComponent(currentTheme.listTextColor, 18)
        }

        val (t1, t2) = GenerationHelper.getGenSpecificTypes(pokemon, currentMechanics)

        holder.typeContainer.removeAllViews()
        addMiniBadge(holder.typeContainer, t1)
        if (t2 != PokemonType.UNKNOWN) {
            addMiniBadge(holder.typeContainer, t2)
        }

        ThemeManager.applyFont(holder.itemView, currentTheme)
        if (onSpriteColorsChanged != null && currentTheme.gbc15Bit) {
            rowColors[holder] = spriteColors(holder)
            publishColors()
        }
        holder.itemView.setOnClickListener { onClick(pokemon) }
    }

    override fun getItemCount() = filteredList.size

    fun updateList(newList: List<Pokemon>) {
        filteredList.clear()
        filteredList = ArrayList(newList)
        fullList = ArrayList(newList)
        notifyDataSetChanged()
    }

    fun updateSettings(mechanics: RomProfile.Mechanics, theme: AppTheme) {
        this.currentMechanics = mechanics
        this.currentTheme = theme
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            fullList.toMutableList()
        } else {
            fullList.filter { it.name.contains(query, ignoreCase = true) }.toMutableList()
        }
        notifyDataSetChanged()
    }

    private fun addMiniBadge(container: LinearLayout, type: PokemonType) {
        val tv = TextView(container.context)
        tv.text = type.displayName.take(3).uppercase()
        tv.textSize = 10f
        tv.letterSpacing = 0.04f
        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        tv.setTextColor(ThemeManager.badgeText(currentTheme))
        tv.setPadding(16, 7, 16, 7)

        tv.background = ThemeManager.shape(container.context, ThemeManager.badgeBg(type.colorHex, theme = currentTheme), 8f)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(6, 0, 0, 0)
        container.addView(tv, params)
    }
}