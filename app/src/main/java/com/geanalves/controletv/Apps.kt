package com.geanalves.controletv

import android.content.Context

/** Um atalho de app da TV. [pacotes] tem os nomes do app na TCL e na Fire TV. */
data class AppTv(val nome: String, val cor: Int, val pacotes: List<String>)

object Apps {
    /** Apps conhecidos. Só aparecem os que estiverem instalados na TV. */
    val conhecidos = listOf(
        AppTv("Netflix", 0xFFE50914.toInt(), listOf("com.netflix.ninja")),
        AppTv("YouTube", 0xFFFF0000.toInt(), listOf("com.google.android.youtube.tv", "com.amazon.firetv.youtube", "com.google.android.youtube.tvunplugged")),
        AppTv("Prime Video", 0xFF1A98FF.toInt(), listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod", "com.amazon.firebat")),
        AppTv("Globoplay", 0xFFF2380F.toInt(), listOf("com.globo.globotv")),
        AppTv("Disney+", 0xFF0E2A6B.toInt(), listOf("com.disney.disneyplus")),
        AppTv("Max", 0xFF002BE7.toInt(), listOf("com.wbd.stream", "com.hbo.hbonow")),
        AppTv("Spotify", 0xFF1DB954.toInt(), listOf("com.spotify.tv.android")),
        AppTv("Pluto TV", 0xFF2D2D2D.toInt(), listOf("tv.pluto.android")),
        AppTv("Paramount+", 0xFF0064FF.toInt(), listOf("com.cbs.ott", "com.cbs.ca")),
        AppTv("Twitch", 0xFF9146FF.toInt(), listOf("tv.twitch.android.app", "tv.twitch.android.viewer")),
        AppTv("Downloader", 0xFFF57C00.toInt(), listOf("com.esaba.downloader")),
        AppTv("Play Store", 0xFF01875F.toInt(), listOf("com.android.vending")),
        AppTv("Início TV", 0xFF1C1C1E.toInt(), listOf("com.geanalves.tvhome")),
    )

    private val coresExtras = intArrayOf(
        0xFF2B3A67.toInt(), 0xFF3D2C5E.toInt(), 0xFF1F4E5F.toInt(),
        0xFF5A2F3F.toInt(), 0xFF2F4F3A.toInt(), 0xFF4A3B24.toInt(),
    )

    /** Nome bonito a partir do pacote: "com.exemplo.meuapp" vira "Meuapp". */
    fun nomeDoPacote(pkg: String): String {
        val partes = pkg.split('.').filter { it !in setOf("com", "br", "tv", "android", "app", "org", "net", "ott") }
        return (partes.lastOrNull() ?: pkg).replaceFirstChar { it.uppercase() }
    }

    fun extra(pkg: String, nome: String) =
        AppTv(nome, coresExtras[(pkg.hashCode() and 0x7fffffff) % coresExtras.size], listOf(pkg))
}

/** Preferências salvas no celular. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("controle", Context.MODE_PRIVATE)

    var ip: String?
        get() = sp.getString("ip", null)
        set(v) = sp.edit().putString("ip", v).apply()

    /** PIN da Administração (guardado embaralhado, nunca o número). */
    var pin: String?
        get() = sp.getString("pin", null)
        set(v) = sp.edit().putString("pin", v).apply()

    var escuro: Boolean
        get() = sp.getBoolean("escuro", false)
        set(v) = sp.edit().putBoolean("escuro", v).apply()

    /** Apps extras adicionados pelo botão "+" (pacote -> nome). */
    var extras: Map<String, String>
        get() = sp.getStringSet("extras", emptySet())!!.mapNotNull {
            val i = it.indexOf('|')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        set(v) = sp.edit().putStringSet("extras", v.map { "${it.key}|${it.value}" }.toSet()).apply()

    /** Apps conhecidos que o usuário escondeu (segurando o botão). */
    var escondidos: Set<String>
        get() = sp.getStringSet("escondidos", emptySet())!!
        set(v) = sp.edit().putStringSet("escondidos", v).apply()
}
