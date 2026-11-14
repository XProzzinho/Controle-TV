package com.geanalves.controletv

import android.app.Activity
import android.app.AlertDialog
import android.webkit.JsResult
import android.webkit.WebChromeClient
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
        // Sem isso o WebView ignora confirm()/alert(): confirm() volta sempre "false" e botões como Resetar conta não fazem nada.
        web.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(v: WebView, url: String?, msg: String?, r: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(msg)
                    .setPositiveButton("Confirmar") { _, _ -> r.confirm() }
                    .setNegativeButton("Cancelar") { _, _ -> r.cancel() }
                    .setOnCancelListener { r.cancel() }.show()
                return true
            }
            override fun onJsAlert(v: WebView, url: String?, msg: String?, r: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(msg)
                    .setPositiveButton("OK") { _, _ -> r.confirm() }
                    .setOnCancelListener { r.confirm() }.show()
                return true
            }
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
