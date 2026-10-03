package com.enrpau.dualscreendex.data

import android.content.Context

// base stats from assets/dex/base_stats.csv (id,form,hp,atk,def,spa,spd,spe); form is blank or a regional label
object BaseStats {
    data class Stats(val hp: Int, val atk: Int, val def: Int, val spa: Int, val spd: Int, val spe: Int) {
        val total get() = hp + atk + def + spa + spd + spe
    }

    private var table: Map<String, Stats>? = null

    fun get(context: Context, id: Int, variantLabel: String?): Stats? {
        val t = table ?: load(context).also { table = it }
        return t["$id|${variantLabel.orEmpty().lowercase()}"] ?: t["$id|"]
    }

    private fun load(context: Context): Map<String, Stats> = try {
        context.assets.open("dex/base_stats.csv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val c = line.split(',')
                if (c.size < 8) return@mapNotNull null
                val n = c.subList(2, 8).map { it.trim().toIntOrNull() ?: return@mapNotNull null }
                "${c[0].trim()}|${c[1].trim().lowercase()}" to Stats(n[0], n[1], n[2], n[3], n[4], n[5])
            }.toMap()
        }
    } catch (e: Exception) {
        emptyMap()
    }
}
