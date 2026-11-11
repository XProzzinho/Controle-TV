package com.geanalves.controletv

import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

// Painel ADM do jogo Brumafria: a tela toda é a página assets/index.html (HTML + CSS + JS).
class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(r.url)
        }
        web.setBackgroundColor(0xFF0B1020.toInt())
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A página decide: fecha a janela aberta; se não tiver nenhuma, sai do app.
        web.evaluateJavascript("window.voltar ? window.voltar() : false") { r -> if (r != "true") finish() }
    }
}
