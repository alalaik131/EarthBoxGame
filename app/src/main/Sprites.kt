package com.earthbox.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

object Sprites {
    val map = HashMap<String, Bitmap>()
    var logo: Bitmap? = null

    fun load(ctx: Context) {
        if (map.isNotEmpty()) return
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val names = ctx.assets.list("sprites") ?: emptyArray()
        for (f in names) {
            if (!f.endsWith(".png")) continue
            ctx.assets.open("sprites/$f").use { s ->
                BitmapFactory.decodeStream(s, null, opts)?.let { map[f.removeSuffix(".png")] = it }
            }
        }
        ctx.assets.open("ui/logo.png").use { logo = BitmapFactory.decodeStream(it, null, opts) }
    }

    operator fun get(n: String): Bitmap? = map[n]
}
