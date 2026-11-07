package com.geanalves.controletv

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Os três apps e onde ficam as versões novas no GitHub. */
object Atualizacoes {
    data class App(val nome: String, val repo: String, val pkg: String, val apk: String, val naTv: Boolean)
    data class Nova(val app: App, val atual: Int?, val nova: Int, val link: String)

    val APPS = listOf(
        App("Início TV", "XProzzinho/inicio-tv.", "com.geanalves.tvhome", "inicio-tv.apk", naTv = true),
        App("TV Online", "XProzzinho/TV-online", "com.geanalves.tvonline", "tv-online.apk", naTv = true),
        App("Controle TV", "XProzzinho/Controle-TV", "com.geanalves.controletv", "controle-tv.apk", naTv = false),
    )

    private val main = Handler(Looper.getMainLooper())

    /** Número da última versão publicada (v1.57 → 57) e o link do APK. */
    fun ultima(app: App, cb: (Pair<Int, String>?) -> Unit) {
        Thread {
            val r = try {
                val c = URL("https://api.github.com/repos/${app.repo}/releases/latest").openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 10000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val js = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                c.disconnect()
                val n = js.optString("tag_name").substringAfterLast('.').toIntOrNull()
                val assets = js.optJSONArray("assets")
                var link: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name") == app.apk) link = a.optString("browser_download_url")
                    }
                }
                if (n != null && link != null) n to link else null
            } catch (_: Throwable) {
                null
            }
            main.post { cb(r) }
        }.start()
    }

    /** Versão deste app no celular. */
    fun versaoAqui(ctx: Context): Int = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
    } catch (_: Throwable) {
        0
    }

    fun arquivo(ctx: Context): File = File(ctx.cacheDir, "controle-novo.apk")
}

/** Entrega o APK novo do próprio controle para o instalador do celular. */
class ApkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = Atualizacoes.arquivo(context ?: return null)
        if (uri.lastPathSegment != f.name || !f.exists()) return null
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "application/vnd.android.package-archive"
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
