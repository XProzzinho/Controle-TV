package com.geanalves.controletv

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.method.ScrollingMovementMethod
import java.net.Inet4Address

/** Controle remoto da TV pelo celular, com as cores do Início TV. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var tv: Tv
    private lateinit var statusDot: GradientDrawable
    private lateinit var statusText: TextView
    private lateinit var appsGrid: LinearLayout
    private var instalados: Set<String>? = null

    private val handler = Handler(Looper.getMainLooper())

    // ---------- ciclo de vida ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Cores.escuro = prefs.escuro
        tv = Tv.de(this)
        tv.aoMudar = { e, msg -> mostrarEstado(e, msg) }
        pintarBarras()
        setContentView(montar())
        montarApps()
        val ip = prefs.ip
        when {
            tv.estado == Tv.Estado.CONECTADO || tv.estado == Tv.Estado.CONECTANDO -> mostrarEstado(tv.estado, null)
            ip != null -> tv.conectar(ip)
            else -> {
                mostrarEstado(Tv.Estado.DESLIGADO, null)
                handler.postDelayed({ escolherTv() }, 400)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val ip = prefs.ip
        if (ip != null && ::tv.isInitialized && tv.estado == Tv.Estado.ERRO) tv.conectar(ip)
    }

    @Suppress("DEPRECATION")
    private fun pintarBarras() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Cores.fundoTopo
        window.navigationBarColor = Cores.fundoBase
        var flags = window.decorView.systemUiVisibility
        flags = if (Cores.escuro) {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        } else {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    // ---------- ajudas ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Int): Float = v * resources.displayMetrics.density

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dpf(radius)
            setColor(color)
        }

    private fun oval(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun vibrar(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    /**
     * Botão que fica preto quando apertado (igual ao selecionado na TV).
     * Com [repetir], segurar o dedo repete a tecla (bom para setas e volume).
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun botao(
        icone: Int?,
        texto: String? = null,
        tamanho: Int = 56,
        fundo: Int = Color.TRANSPARENT,
        corIcone: Int = Cores.tinta,
        tamIcone: Int = 24,
        repetir: Boolean = false,
        aoTocar: () -> Unit,
    ): FrameLayout {
        val bg = oval(fundo)
        val img = icone?.let {
            ImageView(this).apply {
                setImageResource(it)
                setColorFilter(corIcone)
            }
        }
        val txt = texto?.let {
            TextView(this).apply {
                text = it
                textSize = 18f
                setTextColor(corIcone)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
        }
        val b = FrameLayout(this).apply { background = bg }
        img?.let { b.addView(it, FrameLayout.LayoutParams(dp(tamIcone), dp(tamIcone), Gravity.CENTER)) }
        txt?.let { b.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)) }
        b.layoutParams = LinearLayout.LayoutParams(dp(tamanho), dp(tamanho))

        fun apertado(sim: Boolean) {
            bg.setColor(if (sim) Cores.tinta else fundo)
            img?.setColorFilter(if (sim) Cores.papel else corIcone)
            txt?.setTextColor(if (sim) Cores.papel else corIcone)
            b.animate().scaleX(if (sim) 0.92f else 1f).scaleY(if (sim) 0.92f else 1f).setDuration(90).start()
        }

        val repete = object : Runnable {
            override fun run() {
                vibrar(b)
                aoTocar()
                handler.postDelayed(this, 140)
            }
        }
        b.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    apertado(true)
                    vibrar(v)
                    aoTocar()
                    if (repetir) handler.postDelayed(repete, 450)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    apertado(false)
                    handler.removeCallbacks(repete)
                }
            }
            true
        }
        return b
    }

    /** Barra arredondada branca (igual à barrinha do Wi-Fi da TV). */
    private fun pilula(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Cores.janela, 40)
            elevation = dpf(3)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

    private fun titulo(s: String): TextView =
        TextView(this).apply {
            text = s
            textSize = 20f
            setTextColor(Cores.tinta)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), dp(28), 0, dp(12))
        }

    private fun espaco(w: Int = 0, h: Int = 0) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(w), dp(h)) }

    // ---------- tela ----------

    private fun montar(): View {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Cores.fundoTopo, Cores.fundoBase),
            )
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(32))
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(col)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        col.addView(topo())
        col.addView(espaco(h = 28))
        // O círculo precisa do tamanho certo, senão o OK cobre as setas
        val lado = (resources.displayMetrics.widthPixels - dp(40)).coerceAtMost(dp(300))
        col.addView(direcional(), LinearLayout.LayoutParams(lado, lado).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(espaco(h = 24))
        col.addView(linhaNavegacao(), LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(espaco(h = 16))
        col.addView(linhaVolume(), LinearLayout.LayoutParams(-1, -2))
        col.addView(espaco(h = 16))
        col.addView(linhaBrilho(), LinearLayout.LayoutParams(-1, -2))
        col.addView(titulo("Digitar na TV"))
        col.addView(teclado())
        col.addView(cabecalhoLembretes())
        lembretesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(lembretesBox)
        col.addView(cabecalhoApps())
        appsGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(appsGrid)
        // Administração: comandos para modificar a TV (igual ao Bugjaeger)
        col.addView(espaco(h = 24))
        col.addView(botaoAdmin())
        return root
    }

    /** Título, situação da conexão e a barrinha com TV, lua e desligar. */
    private fun topo(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(
            TextView(this).apply {
                text = "Controle TV"
                textSize = 30f
                setTextColor(Cores.tinta)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            },
        )
        val st = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(4), 0, 0)
            setOnClickListener { escolherTv() }
        }
        statusDot = oval(Cores.cinza)
        st.addView(View(this).apply { background = statusDot }, LinearLayout.LayoutParams(dp(9), dp(9)))
        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Cores.cinza)
            setPadding(dp(8), 0, 0, 0)
            maxLines = 2
        }
        st.addView(statusText)
        left.addView(st)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))

        val group = pilula()
        group.addView(botao(R.drawable.ic_tv, tamanho = 44, tamIcone = 22) { escolherTv() })
        group.addView(espaco(w = 2))
        group.addView(botao(if (Cores.escuro) R.drawable.ic_claro else R.drawable.ic_lua, tamanho = 44, tamIcone = 22) { trocarModo() })
        group.addView(espaco(w = 2))
        group.addView(botao(R.drawable.ic_power, tamanho = 44, tamIcone = 22, corIcone = Cores.vermelho) { tv.tecla(Tv.LIGAR) })
        row.addView(group)
        return row
    }

    /** Anel fino com as setas e o OK grande no meio (prévia 4). */
    private fun direcional(): View {
        val lado = (resources.displayMetrics.widthPixels - dp(40)).coerceAtMost(dp(300))
        val anel = dp(62)
        val pad = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(anel, Cores.janela)
            }
            layoutParams = LinearLayout.LayoutParams(lado, lado)
        }
        fun seta(icone: Int, tecla: Int, g: Int) {
            val b = botao(icone, tamanho = anel, tamIcone = 30, repetir = true) { tv.tecla(tecla) }
            pad.addView(b, FrameLayout.LayoutParams(anel, anel, g))
        }
        seta(R.drawable.ic_up, Tv.CIMA, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        seta(R.drawable.ic_down, Tv.BAIXO, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        seta(R.drawable.ic_left, Tv.ESQUERDA, Gravity.START or Gravity.CENTER_VERTICAL)
        seta(R.drawable.ic_right, Tv.DIREITA, Gravity.END or Gravity.CENTER_VERTICAL)
        // OK grande e cheio: preto no claro, branco no escuro
        val okLado = lado - anel * 2 - dp(18)
        val ok = botao(null, texto = "OK", tamanho = okLado, fundo = Cores.tinta, corIcone = Cores.papel) { tv.tecla(Tv.OK) }
        (ok.getChildAt(0) as? TextView)?.textSize = 24f
        pad.addView(ok, FrameLayout.LayoutParams(okLado, okLado, Gravity.CENTER))
        return pad
    }

    /** Voltar, Início, Menu, Play e Mudo: ícones soltos, sem barra. */
    private fun linhaNavegacao(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for ((icone, tecla) in listOf(
            R.drawable.ic_back to Tv.VOLTAR,
            R.drawable.ic_home to Tv.INICIO,
            R.drawable.ic_menu to Tv.MENU,
            R.drawable.ic_play to Tv.PLAY_PAUSE,
            R.drawable.ic_mute to Tv.MUDO,
        )) {
            row.addView(botao(icone, tamanho = 56, tamIcone = 26) { tv.tecla(tecla) }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { leftMargin = dp(4); rightMargin = dp(4) })
        }
        return row
    }

    /** Trilha de arrastar (volume e brilho): mostra o nível e muda ao soltar o dedo. */
    @SuppressLint("ViewConstructor")
    private inner class Trilha(val aoSoltar: (Float) -> Unit) : View(this@MainActivity) {
        var valor = -1f
            set(v) {
                field = v
                invalidate()
            }
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: android.graphics.Canvas) {
            val h = dpf(6)
            val y = height / 2f
            val r = dpf(9)
            val x0 = r
            val x1 = width - r
            p.color = Cores.campo
            c.drawRoundRect(x0, y - h / 2, x1, y + h / 2, h, h, p)
            if (valor < 0f) return
            val x = x0 + (x1 - x0) * valor.coerceIn(0f, 1f)
            p.color = Cores.tinta
            c.drawRoundRect(x0, y - h / 2, x, y + h / 2, h, h, p)
            c.drawCircle(x, y, r, p)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            val r = dpf(9)
            val f = ((e.x - r) / (width - 2 * r)).coerceIn(0f, 1f)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    valor = f
                }
                MotionEvent.ACTION_MOVE -> valor = f
                MotionEvent.ACTION_UP -> {
                    valor = f
                    vibrar(this)
                    aoSoltar(f)
                }
            }
            return true
        }
    }

    private lateinit var brilhoText: TextView
    private lateinit var trilhaBrilho: Trilha
    private lateinit var volumeText: TextView
    private lateinit var trilhaVolume: Trilha
    private var volumeMax = 15

    private fun mostrarBrilho(n: Int?) {
        if (!::brilhoText.isInitialized) return
        if (n == null) {
            toast("Para o brilho, instale o Início TV nessa TV")
            brilhoText.text = "Brilho"
        } else {
            brilhoText.text = "Brilho $n%"
            trilhaBrilho.valor = (n - 5) / 95f
        }
    }

    private fun mostrarVolume(v: Pair<Int, Int>?) {
        if (!::volumeText.isInitialized) return
        if (v == null) {
            volumeText.text = "Volume"
            return
        }
        volumeMax = v.second.coerceAtLeast(1)
        volumeText.text = "Volume ${v.first}"
        trilhaVolume.valor = v.first / volumeMax.toFloat()
    }

    /** Barra branca: − , nome + trilha, + e um botão especial redondo. */
    private fun barraNivel(
        menos: Int,
        mais: Int,
        especial: Int,
        rotulo: TextView,
        trilha: Trilha,
        aoMenos: () -> Unit,
        aoMais: () -> Unit,
        aoEspecial: () -> Unit,
    ): View {
        val p = pilula().apply { setPadding(dp(6), dp(6), dp(8), dp(6)) }
        p.addView(botao(menos, tamanho = 52, tamIcone = 22, repetir = true) { aoMenos() })
        val meio = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        rotulo.apply {
            textSize = 12f
            setTextColor(Cores.cinza)
            gravity = Gravity.CENTER
        }
        meio.addView(rotulo, LinearLayout.LayoutParams(-1, -2))
        meio.addView(trilha, LinearLayout.LayoutParams(-1, dp(26)))
        p.addView(meio, LinearLayout.LayoutParams(0, -2, 1f))
        p.addView(botao(mais, tamanho = 52, tamIcone = 24, repetir = true) { aoMais() })
        p.addView(espaco(w = 6))
        p.addView(botao(especial, tamanho = 48, tamIcone = 22, fundo = Cores.campo) { aoEspecial() })
        return p
    }

    private fun linhaVolume(): View {
        volumeText = TextView(this).apply { text = "Volume" }
        trilhaVolume = Trilha { f -> tv.volume(Math.round(f * volumeMax)) { mostrarVolume(it) } }
        return barraNivel(
            R.drawable.ic_vol_down, R.drawable.ic_vol_up, R.drawable.ic_mute, volumeText, trilhaVolume,
            aoMenos = { tv.tecla(Tv.VOL_MENOS); atualizarVolumeDepois() },
            aoMais = { tv.tecla(Tv.VOL_MAIS); atualizarVolumeDepois() },
            aoEspecial = { tv.tecla(Tv.MUDO) },
        )
    }

    private val lerVolume = Runnable { tv.volume(null) { mostrarVolume(it) } }

    private fun atualizarVolumeDepois() {
        handler.removeCallbacks(lerVolume)
        handler.postDelayed(lerVolume, 600)
    }

    /** Brilho: − , trilha, + e a lua (dormir: bem baixinho). Tocar no texto volta ao normal. */
    private fun linhaBrilho(): View {
        brilhoText = TextView(this).apply {
            text = "Brilho"
            setOnClickListener {
                vibrar(this)
                tv.brilho("--ei nivel 100") { mostrarBrilho(it) }
            }
        }
        trilhaBrilho = Trilha { f -> tv.brilho("--ei nivel ${5 + Math.round(f * 95)}") { mostrarBrilho(it) } }
        return barraNivel(
            R.drawable.ic_claro, R.drawable.ic_claro, R.drawable.ic_lua, brilhoText, trilhaBrilho,
            aoMenos = { tv.brilho("--ei passo -10") { mostrarBrilho(it) } },
            aoMais = { tv.brilho("--ei passo 10") { mostrarBrilho(it) } },
            aoEspecial = { tv.brilho("--ei nivel 10") { mostrarBrilho(it) } },
        )
    }

    /** Campo para digitar no celular e mandar o texto para a TV. */
    private fun teclado(): View {
        val p = pilula().apply { setPadding(dp(16), dp(6), dp(6), dp(6)) }
        val campo = EditText(this).apply {
            hint = "Ex.: nome do filme"
            textSize = 16f
            setTextColor(Cores.tinta)
            setHintTextColor(Cores.cinza)
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        fun enviar() {
            val t = campo.text.toString()
            if (t.isBlank()) return
            tv.digitar(t)
            campo.setText("")
        }
        campo.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) {
                enviar()
                true
            } else {
                false
            }
        }
        p.addView(campo, LinearLayout.LayoutParams(0, -2, 1f))
        p.addView(botao(R.drawable.ic_backspace, tamanho = 48, tamIcone = 22, repetir = true) { tv.tecla(Tv.APAGAR) })
        p.addView(espaco(w = 4))
        p.addView(botao(R.drawable.ic_send, tamanho = 48, tamIcone = 20, fundo = Cores.tinta, corIcone = Cores.papel) { enviar() })
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(p)
        box.addView(
            TextView(this).apply {
                text = "Dica: acentos não vão para a TV (ação → acao)."
                textSize = 12f
                setTextColor(Cores.cinza)
                setPadding(dp(8), dp(8), 0, 0)
            },
        )
        return box
    }

    // ---------- lembretes ----------

    private lateinit var lembretesBox: LinearLayout
    private var nomesPerfis = listOf("Perfil 1", "Perfil 2", "Perfil 3")

    private fun cabecalhoLembretes(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        row.addView(titulo("Lembretes"), LinearLayout.LayoutParams(0, -2, 1f))
        val add = botao(R.drawable.ic_add, tamanho = 40, tamIcone = 22, fundo = Cores.janela) { novoLembrete() }
        add.elevation = dpf(2)
        row.addView(add, LinearLayout.LayoutParams(dp(40), dp(40)).apply { bottomMargin = dp(8) })
        return row
    }

    private fun quando(data: String): String {
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val c = java.util.Calendar.getInstance()
        val hoje = iso.format(c.time)
        c.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val amanha = iso.format(c.time)
        return when (data) {
            hoje -> "Hoje"
            amanha -> "Amanhã"
            else -> try {
                java.text.SimpleDateFormat("EEE, d 'de' MMM", java.util.Locale("pt", "BR")).format(iso.parse(data)!!)
            } catch (_: Throwable) {
                data
            }
        }
    }

    /** Busca na TV os nomes dos perfis e os lembretes que estão valendo. */
    private fun carregarLembretes() {
        if (!::lembretesBox.isInitialized) return
        tv.comando("am broadcast -n com.geanalves.tvhome/.ConfigReceiver --ez lembretes true") { out ->
            val b64 = out?.let { Regex("data=\"([A-Za-z0-9+/=]+)\"").find(it)?.groupValues?.get(1) }
            lembretesBox.removeAllViews()
            if (b64 == null) {
                lembretesBox.addView(textoJanela("Para os lembretes, a TV precisa do Início TV atualizado.", 13f))
                return@comando
            }
            val linhas = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT)).lines()
            nomesPerfis = linhas.firstOrNull()?.split('|')?.takeIf { it.size == 3 } ?: nomesPerfis
            var algum = false
            for (l in linhas.drop(1)) {
                val p = l.split('|', limit = 4)
                if (p.size < 4) continue
                algum = true
                val id = p[0]
                val nome = nomesPerfis.getOrElse(p[1].toIntOrNull() ?: 0) { "" }
                lembretesBox.addView(
                    opcao(p[3], "$nome · ${quando(p[2])} · toque para apagar") {
                        confirmar("Apagar o lembrete \"${p[3]}\"?") {
                            tv.comando("am broadcast -n com.geanalves.tvhome/.ConfigReceiver --es apagar_lembrete $id") {
                                carregarLembretes()
                            }
                        }
                    },
                )
            }
            if (!algum) lembretesBox.addView(textoJanela("Nenhum lembrete. Toque no + para criar.", 13f))
        }
    }

    /** Novo lembrete: texto, perfil e até que dia aparece. */
    private fun novoLembrete() {
        if (tv.estado != Tv.Estado.CONECTADO) {
            toast("Conecte na TV primeiro")
            return
        }
        val (d, box) = janela()
        box.addView(textoJanela("Novo lembrete", 22f, Cores.tinta, negrito = true))
        val campo = EditText(this).apply {
            hint = "Ex.: Pagar a conta de luz"
            textSize = 16f
            setTextColor(Cores.tinta)
            setHintTextColor(Cores.cinza)
            background = rounded(Cores.campo, 16)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(60))
        }
        box.addView(campo, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // Escolhas em pílulas: a escolhida fica preta
        fun grupo(titulo: String, opcoes: List<String>, inicial: Int, mudou: (Int) -> Unit): List<TextView> {
            box.addView(textoJanela(titulo, 13f).apply { setPadding(0, dp(14), 0, dp(6)) })
            val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val chips = ArrayList<TextView>()
            fun pintar(sel: Int) {
                chips.forEachIndexed { i, c ->
                    c.background = rounded(if (i == sel) Cores.tinta else Cores.campo, 16)
                    c.setTextColor(if (i == sel) Cores.papel else Cores.tinta)
                }
            }
            opcoes.forEachIndexed { i, o ->
                val c = TextView(this).apply {
                    text = o
                    textSize = 13f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(8), dp(10), dp(8), dp(10))
                    setOnClickListener {
                        vibrar(this)
                        pintar(i)
                        mudou(i)
                    }
                }
                chips += c
                linha.addView(c, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(6) })
            }
            pintar(inicial)
            box.addView(linha, LinearLayout.LayoutParams(-1, -2))
            return chips
        }

        var perfil = 0
        grupo("Para quem", nomesPerfis.map { it.substringBefore(' ') }, 0) { perfil = it }

        val iso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        var data = iso.format(cal.time)
        val chipsData = grupo("Até quando aparece", listOf("Hoje", "Amanhã", "Escolher dia"), 0) { }
        chipsData[0].setOnClickListener {
            data = iso.format(java.util.Calendar.getInstance().time)
            chipsData.forEachIndexed { i, c -> c.background = rounded(if (i == 0) Cores.tinta else Cores.campo, 16); c.setTextColor(if (i == 0) Cores.papel else Cores.tinta) }
        }
        chipsData[1].setOnClickListener {
            val c = java.util.Calendar.getInstance()
            c.add(java.util.Calendar.DAY_OF_YEAR, 1)
            data = iso.format(c.time)
            chipsData.forEachIndexed { i, ch -> ch.background = rounded(if (i == 1) Cores.tinta else Cores.campo, 16); ch.setTextColor(if (i == 1) Cores.papel else Cores.tinta) }
        }
        chipsData[2].setOnClickListener {
            val c = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(this, { _, y, m, dia ->
                val esc = java.util.Calendar.getInstance()
                esc.set(y, m, dia)
                data = iso.format(esc.time)
                chipsData[2].text = quando(data)
                chipsData.forEachIndexed { i, ch -> ch.background = rounded(if (i == 2) Cores.tinta else Cores.campo, 16); ch.setTextColor(if (i == 2) Cores.papel else Cores.tinta) }
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
            }.show()
        }

        val erro = textoJanela("", 13f, Cores.vermelho)
        box.addView(erro)
        box.addView(
            opcao("Salvar na TV", escuro = true) {
                val t = campo.text.toString().trim()
                if (t.isEmpty()) {
                    erro.text = "Escreva o lembrete primeiro."
                    return@opcao
                }
                val seguro = t.replace("'", "'\\''")
                tv.comando("am broadcast -n com.geanalves.tvhome/.ConfigReceiver --es lembrete '$seguro' --ei perfil $perfil --es data $data") { out ->
                    if (out?.contains("data=\"ok\"") == true) {
                        toast("Lembrete salvo na TV")
                        d.dismiss()
                        carregarLembretes()
                    } else {
                        erro.text = "A TV não recebeu. Ela tem o Início TV atualizado?"
                    }
                }
            },
        )
        box.addView(opcao("Cancelar") { d.dismiss() })
        d.show()
        campo.requestFocus()
    }

    private fun cabecalhoApps(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        row.addView(titulo("Apps"), LinearLayout.LayoutParams(0, -2, 1f))
        val add = botao(R.drawable.ic_add, tamanho = 40, tamIcone = 22, fundo = Cores.janela) { adicionarApp() }
        add.elevation = dpf(2)
        row.addView(add, LinearLayout.LayoutParams(dp(40), dp(40)).apply { bottomMargin = dp(8) })
        return row
    }

    /** Grade de atalhos: só os apps que estão na TV (quando já sabe quais são). */
    private fun montarApps() {
        appsGrid.removeAllViews()
        val inst = instalados
        val escondidos = prefs.escondidos
        val lista = ArrayList<AppTv>()
        for (a in Apps.conhecidos) {
            if (a.nome in escondidos) continue
            if (inst == null) {
                lista += a
            } else {
                val achados = a.pacotes.filter { it in inst }
                if (achados.isNotEmpty()) lista += a.copy(pacotes = achados)
            }
        }
        for ((pkg, nome) in prefs.extras) lista += Apps.extra(pkg, nome)

        var linha: LinearLayout? = null
        lista.forEachIndexed { i, app ->
            if (i % 3 == 0) {
                linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                appsGrid.addView(linha, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
            val lp = LinearLayout.LayoutParams(0, dp(64), 1f)
            if (i % 3 != 2) lp.rightMargin = dp(10)
            linha!!.addView(blocoApp(app), lp)
        }
        // Completa a última linha para os blocos ficarem do mesmo tamanho
        val sobra = (3 - lista.size % 3) % 3
        repeat(sobra) { k ->
            val lp = LinearLayout.LayoutParams(0, dp(64), 1f)
            if (k < sobra - 1) lp.rightMargin = dp(10)
            linha?.addView(View(this), lp)
        }
        if (lista.isEmpty()) {
            appsGrid.addView(
                TextView(this).apply {
                    text = "Toque no + para adicionar apps da TV"
                    textSize = 14f
                    setTextColor(Cores.cinza)
                },
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun pastaIcones() = java.io.File(filesDir, "icones")

    /** Bloco do app: com o ícone de verdade (copiado da TV) ou colorido com o nome. */
    private fun blocoApp(app: AppTv): View {
        val icones = prefs.icones
        val pkg = app.pacotes.firstOrNull { it in icones && java.io.File(pastaIcones(), "$it.png").exists() }
        val bmp = pkg?.let { android.graphics.BitmapFactory.decodeFile(java.io.File(pastaIcones(), "$it.png").absolutePath) }
        val t: View = when {
            bmp != null && icones[pkg] == "b" -> FrameLayout(this).apply {
                // Banner largo da TV, igual à barra de apps do Início TV
                background = rounded(0xFF1C1C1E.toInt(), 18)
                clipToOutline = true
                elevation = dpf(2)
                addView(
                    ImageView(this@MainActivity).apply {
                        setImageBitmap(bmp)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    },
                    FrameLayout.LayoutParams(-1, -1),
                )
            }
            bmp != null -> LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(Cores.janela, 18)
                elevation = dpf(2)
                addView(
                    ImageView(this@MainActivity).apply { setImageBitmap(bmp) },
                    LinearLayout.LayoutParams(dp(30), dp(30)),
                )
                addView(
                    TextView(this@MainActivity).apply {
                        text = app.nome
                        textSize = 11f
                        setTextColor(Cores.tinta)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(dp(4), dp(3), dp(4), 0)
                    },
                )
            }
            else -> TextView(this).apply {
                text = app.nome
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = rounded(app.cor, 18)
                elevation = dpf(2)
                setPadding(dp(6), 0, dp(6), 0)
                maxLines = 2
            }
        }
        t.isClickable = true
        t.isLongClickable = true
        t.setOnClickListener {
            vibrar(t)
            tv.abrirPrimeiro(app.pacotes)
        }
        t.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }
            false
        }
        t.setOnLongClickListener {
            vibrar(t)
            confirmar("Tirar ${app.nome} da lista?") {
                val extras = prefs.extras
                val pkg = app.pacotes.first()
                if (pkg in extras) prefs.extras = extras - pkg else prefs.escondidos = prefs.escondidos + app.nome
                montarApps()
            }
            true
        }
        return t
    }

    /** Copia da TV os ícones que ainda não estão no celular. */
    private fun buscarIcones() {
        val inst = instalados ?: return
        val ja = prefs.icones.keys.filter { java.io.File(pastaIcones(), "$it.png").exists() }.toSet()
        val faltam = (Apps.conhecidos.flatMap { it.pacotes } + prefs.extras.keys)
            .filter { it in inst && it !in ja }
            .distinct()
        if (faltam.isEmpty()) return
        tv.icones(faltam, pastaIcones()) { mapa ->
            if (!mapa.isNullOrEmpty()) {
                prefs.icones = prefs.icones + mapa
                montarApps()
            }
        }
    }

    // ---------- conexão ----------

    private fun mostrarEstado(e: Tv.Estado, msg: String?) {
        if (!::statusText.isInitialized) return
        when (e) {
            Tv.Estado.CONECTADO -> {
                statusDot.setColor(Cores.verde)
                statusText.text = "Conectado · ${tv.ip}"
                tv.brilho("") { n -> if (n != null) mostrarBrilho(n) }
                tv.volume(null) { mostrarVolume(it) }
                carregarLembretes()
                tv.pacotes { p ->
                    if (p != null) {
                        instalados = p
                        montarApps()
                        buscarIcones()
                    }
                }
            }
            Tv.Estado.CONECTANDO -> {
                statusDot.setColor(0xFFFF9F0A.toInt())
                statusText.text = "Conectando… se a TV perguntar, aceite"
            }
            Tv.Estado.ERRO -> {
                statusDot.setColor(Cores.vermelho)
                statusText.text = (msg ?: "Sem conexão") + " · toque aqui"
            }
            Tv.Estado.DESLIGADO -> {
                statusDot.setColor(Cores.cinza)
                statusText.text = "Nenhuma TV · toque aqui"
            }
        }
    }

    private fun meuIp(): String? {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    /** Janela branca arredondada, igual às da TV. */
    private fun janela(): Pair<Dialog, LinearLayout> {
        val d = Dialog(this)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Cores.janela, 28)
            setPadding(dp(24), dp(22), dp(24), dp(22))
        }
        val scroll = ScrollView(this)
        scroll.addView(box)
        d.setContentView(scroll)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        return d to box
    }

    private fun textoJanela(s: String, tam: Float = 14f, cor: Int = Cores.cinza, negrito: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = tam
            setTextColor(cor)
            if (negrito) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun opcao(s: String, sub: String? = null, escuro: Boolean = false, aoTocar: () -> Unit): View {
        val bg = rounded(if (escuro) Cores.tinta else Cores.campo, 16)
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            setOnClickListener {
                vibrar(this)
                aoTocar()
            }
        }
        c.addView(
            TextView(this).apply {
                text = s
                textSize = 16f
                setTextColor(if (escuro) Cores.papel else Cores.tinta)
                typeface = Typeface.DEFAULT_BOLD
                gravity = if (escuro) Gravity.CENTER else Gravity.START
            },
        )
        if (sub != null) {
            c.addView(
                TextView(this).apply {
                    text = sub
                    textSize = 12f
                    setTextColor(Cores.cinza)
                },
            )
        }
        c.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        return c
    }

    private fun escolherTv() {
        val (d, box) = janela()
        box.addView(textoJanela("Escolher TV", 22f, Cores.tinta, negrito = true))
        val busca = textoJanela("Procurando TVs na sua rede Wi-Fi…")
        box.addView(busca)
        val lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(lista)

        val campoBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(Cores.campo, 16)
            setPadding(dp(16), dp(2), dp(16), dp(2))
        }
        val campo = EditText(this).apply {
            hint = "Ou digite o IP (ex.: 192.168.0.15)"
            setText(prefs.ip ?: "")
            textSize = 16f
            setTextColor(Cores.tinta)
            setHintTextColor(Cores.cinza)
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_PHONE
        }
        campoBox.addView(campo, LinearLayout.LayoutParams(-1, -2))
        box.addView(campoBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        fun usar(ip: String) {
            if (ip.isBlank()) return
            prefs.ip = ip.trim()
            tv.conectar(ip)
            d.dismiss()
        }
        box.addView(opcao("Conectar", escuro = true) { usar(campo.text.toString()) })
        box.addView(
            textoJanela(
                "Antes, ligue a depuração na TV:\n" +
                    "• Fire TV: Configurações › Minha Fire TV › Opções do desenvolvedor › Depuração ADB.\n" +
                    "• TCL: Configurações › Preferências do dispositivo › Opções do desenvolvedor › Depuração USB.\n" +
                    "Na primeira vez a TV pergunta se pode: marque \"Sempre permitir\" e aceite.\n" +
                    "O IP da TV fica em Configurações › Rede.",
                12f,
            ).apply { setPadding(0, dp(14), 0, 0) },
        )
        d.show()

        val meu = meuIp()
        if (meu == null) {
            busca.text = "Ligue o Wi-Fi do celular na mesma rede da TV."
            return
        }
        var achou = 0
        Tv.procurar(
            meu,
            achou = { ip ->
                achou++
                lista.addView(opcao(ip, "TV encontrada na rede") { usar(ip) })
            },
            fim = {
                busca.text = if (achou == 0) {
                    "Não achei nenhuma TV. Confira se a depuração está ligada e se o celular está no mesmo Wi-Fi."
                } else {
                    "Toque na sua TV:"
                }
            },
        )
    }

    // ---------- apps ----------

    private fun adicionarApp() {
        if (tv.estado != Tv.Estado.CONECTADO) {
            toast("Conecte na TV primeiro")
            escolherTv()
            return
        }
        val (d, box) = janela()
        box.addView(textoJanela("Adicionar app", 22f, Cores.tinta, negrito = true))
        val aviso = textoJanela("Buscando os apps da TV…")
        box.addView(aviso)
        d.show()
        val jaTem = prefs.extras.keys + Apps.conhecidos.flatMap { it.pacotes }
        tv.pacotesInstalados { pkgs ->
            if (pkgs == null) {
                aviso.text = "A TV não respondeu."
                return@pacotesInstalados
            }
            val novos = pkgs.filter { it !in jaTem }
            aviso.text = if (novos.isEmpty()) "Todos os apps da TV já estão na lista." else "Toque no app para colocar no controle:"
            for (pkg in novos) {
                box.addView(
                    opcao(Apps.nomeDoPacote(pkg), pkg) {
                        prefs.extras = prefs.extras + (pkg to Apps.nomeDoPacote(pkg))
                        montarApps()
                        buscarIcones()
                        d.dismiss()
                    },
                )
            }
        }
        // Apps escondidos podem voltar
        val escondidos = prefs.escondidos
        if (escondidos.isNotEmpty()) {
            box.addView(
                opcao("Mostrar de novo: ${escondidos.joinToString()}") {
                    prefs.escondidos = emptySet()
                    montarApps()
                    d.dismiss()
                },
            )
        }
    }

    private fun confirmar(pergunta: String, sim: () -> Unit) {
        val (d, box) = janela()
        box.addView(textoJanela(pergunta, 18f, Cores.tinta, negrito = true))
        box.addView(
            opcao("Sim", escuro = true) {
                sim()
                d.dismiss()
            },
        )
        box.addView(opcao("Cancelar") { d.dismiss() })
        d.show()
    }

    // ---------- administração ----------

    private fun botaoAdmin(): View {
        val p = pilula().apply {
            setPadding(dp(18), dp(10), dp(10), dp(10))
            isClickable = true
            setOnClickListener {
                vibrar(this)
                abrirAdmin()
            }
        }
        val txt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        txt.addView(
            TextView(this).apply {
                text = "Administração"
                textSize = 17f
                setTextColor(Cores.tinta)
                typeface = Typeface.DEFAULT_BOLD
            },
        )
        txt.addView(
            TextView(this).apply {
                text = "Comandos para modificar a TV"
                textSize = 12f
                setTextColor(Cores.cinza)
            },
        )
        p.addView(txt, LinearLayout.LayoutParams(0, -2, 1f))
        p.addView(
            FrameLayout(this).apply {
                background = oval(Cores.tinta)
                addView(
                    ImageView(this@MainActivity).apply {
                        setImageResource(R.drawable.ic_right)
                        setColorFilter(Cores.papel)
                    },
                    FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER),
                )
            },
            LinearLayout.LayoutParams(dp(40), dp(40)),
        )
        return p
    }

    private lateinit var saidaAdmin: TextView
    private var scrollAdmin: ScrollView? = null

    /** Mostra o comando e a resposta da TV na caixa preta do terminal. */
    private fun rodarAdmin(cmd: String, depois: ((String?) -> Unit)? = null) {
        if (tv.estado != Tv.Estado.CONECTADO && tv.ip == null) {
            toast("Conecte na TV primeiro")
            return
        }
        saidaAdmin.append("\n$ $cmd\n")
        tv.comando(cmd) { out ->
            val r = out?.trimEnd()
            saidaAdmin.append(if (r == null) "(a TV não respondeu)\n" else if (r.isEmpty()) "(feito)\n" else "$r\n")
            saidaAdmin.post {
                val layout = saidaAdmin.layout ?: return@post
                val y = layout.getLineTop(saidaAdmin.lineCount) - saidaAdmin.height
                if (y > 0) saidaAdmin.scrollTo(0, y)
            }
            depois?.invoke(out)
        }
    }

    // ---------- PIN da Administração ----------

    private fun hashPin(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(("controletv:" + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun campoPin(dica: String): EditText =
        EditText(this).apply {
            hint = dica
            textSize = 22f
            setTextColor(Cores.tinta)
            setHintTextColor(Cores.cinza)
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            background = rounded(Cores.campo, 16)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }

    /** Primeira vez: cria o PIN (digita duas vezes). Depois: pede o PIN para entrar. */
    private fun pedirPin(liberado: () -> Unit) {
        val salvo = prefs.pin
        if (salvo == null) {
            novoPin(liberado)
            return
        }
        val (d, box) = janela()
        box.addView(textoJanela("Administração", 22f, Cores.tinta, negrito = true))
        box.addView(textoJanela("Digite o seu PIN para entrar."))
        val campo = campoPin("PIN")
        box.addView(campo)
        val erro = textoJanela("", 13f, Cores.vermelho)
        box.addView(erro)
        fun conferir() {
            if (hashPin(campo.text.toString()) == salvo) {
                d.dismiss()
                liberado()
            } else {
                erro.text = "PIN errado. Tente de novo."
                campo.setText("")
            }
        }
        campo.imeOptions = EditorInfo.IME_ACTION_DONE
        campo.setOnEditorActionListener { _, _, _ ->
            conferir()
            true
        }
        box.addView(opcao("Entrar", escuro = true) { conferir() })
        box.addView(opcao("Cancelar") { d.dismiss() })
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
        campo.requestFocus()
    }

    /** Novo PIN: digita duas vezes e confirma. */
    private fun novoPin(pronto: () -> Unit) {
        val (d, box) = janela()
        box.addView(textoJanela("Novo PIN", 22f, Cores.tinta, negrito = true))
        box.addView(textoJanela("Crie um PIN de 4 a 8 números. Só quem souber o PIN entra na Administração."))
        val c1 = campoPin("Novo PIN")
        val c2 = campoPin("Confirme o PIN")
        box.addView(c1)
        box.addView(c2)
        val erro = textoJanela("", 13f, Cores.vermelho)
        box.addView(erro)
        fun salvar() {
            val a = c1.text.toString()
            val b = c2.text.toString()
            when {
                a.length < 4 -> erro.text = "O PIN precisa ter pelo menos 4 números."
                a != b -> {
                    erro.text = "Os dois PIN não são iguais. Digite de novo."
                    c2.setText("")
                }
                else -> {
                    prefs.pin = hashPin(a)
                    toast("PIN salvo")
                    d.dismiss()
                    pronto()
                }
            }
        }
        c2.imeOptions = EditorInfo.IME_ACTION_DONE
        c2.setOnEditorActionListener { _, _, _ ->
            salvar()
            true
        }
        box.addView(opcao("Confirmar", escuro = true) { salvar() })
        box.addView(opcao("Cancelar") { d.dismiss() })
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
        c1.requestFocus()
    }

    private fun abrirAdmin() {
        pedirPin { abrirAdminLiberado() }
    }

    private fun abrirAdminLiberado() {
        if (tv.ip == null) {
            toast("Conecte na TV primeiro")
            escolherTv()
            return
        }
        val d = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar)
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Cores.fundoTopo, Cores.fundoBase),
            )
        }
        scrollAdmin = scroll
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(32))
        }
        scroll.addView(col)
        d.setContentView(scroll)

        // Topo: voltar + título
        val topo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val voltar = botao(R.drawable.ic_back, tamanho = 44, tamIcone = 22, fundo = Cores.janela) { d.dismiss() }
        voltar.elevation = dpf(2)
        topo.addView(voltar)
        topo.addView(
            TextView(this).apply {
                text = "Administração"
                textSize = 26f
                setTextColor(Cores.tinta)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(dp(14), 0, 0, 0)
            },
        )
        col.addView(topo)
        col.addView(
            textoJanela("Conectado em ${tv.ip}. Desativar e remover apps dá para desfazer aqui mesmo.", 13f)
                .apply { setPadding(dp(4), dp(10), 0, 0) },
        )

        // Terminal
        col.addView(titulo("Terminal"))
        val term = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF1C1C1E.toInt(), 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        saidaAdmin = TextView(this).apply {
            text = "Pronto. Digite um comando (ex.: pm list packages) ou use os botões abaixo."
            textSize = 12f
            setTextColor(0xFFE5E5EA.toInt())
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
            isVerticalScrollBarEnabled = true
        }
        term.addView(saidaAdmin, LinearLayout.LayoutParams(-1, dp(220)))
        val linhaCmd = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF2C2C2E.toInt(), 14)
            setPadding(dp(12), 0, dp(4), 0)
        }
        val campo = EditText(this).apply {
            hint = "adb shell …"
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8E8E93.toInt())
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        fun enviar() {
            val c = campo.text.toString().trim().removePrefix("adb shell ").trim()
            if (c.isEmpty()) return
            rodarAdmin(c)
            campo.setText("")
        }
        campo.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) {
                enviar()
                true
            } else {
                false
            }
        }
        linhaCmd.addView(campo, LinearLayout.LayoutParams(0, -2, 1f))
        linhaCmd.addView(botao(R.drawable.ic_send, tamanho = 44, tamIcone = 20, corIcone = Color.WHITE) { enviar() })
        term.addView(linhaCmd, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        col.addView(term)

        // Instalar APK na TV pelo celular
        col.addView(titulo("Instalar na TV"))
        col.addView(opcao("Atualizar Início TV", "Baixa a última versão do GitHub e instala na TV") {
            instalarLink(Tv.LINK_INICIO)
        })
        val linkBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Cores.campo, 16)
            setPadding(dp(14), dp(2), dp(4), dp(2))
        }
        val linkCampo = EditText(this).apply {
            hint = "Cole aqui o link do APK"
            textSize = 14f
            setTextColor(Cores.tinta)
            setHintTextColor(Cores.cinza)
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        linkBox.addView(linkCampo, LinearLayout.LayoutParams(0, -2, 1f))
        linkBox.addView(botao(R.drawable.ic_send, tamanho = 44, tamIcone = 20, fundo = Cores.tinta, corIcone = Cores.papel) {
            val l = linkCampo.text.toString().trim()
            if (l.startsWith("http")) instalarLink(l) else toast("Cole um link que começa com http")
        })
        col.addView(linkBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(opcao("Escolher APK do celular", "Um arquivo .apk que já está baixado no celular") {
            val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            try {
                startActivityForResult(i, PEDIR_APK)
            } catch (_: Throwable) {
                toast("Não achei o gerenciador de arquivos")
            }
        })

        // Apps da TV
        col.addView(titulo("Apps da TV"))
        col.addView(opcao("Gerenciar apps", "Desativar, reativar, remover ou abrir qualquer app") { gerenciarApps(false) })
        col.addView(opcao("Apps desativados", "Ver e reativar o que foi desligado") { gerenciarApps(true) })
        col.addView(opcao("Restaurar app removido", "Trazer de volta um app que você removeu") { restaurarApps() })

        // Comandos prontos
        col.addView(titulo("Comandos prontos"))
        col.addView(opcao("Fixar Início TV", "Tela inicial principal, mesmo depois de reiniciar") {
            confirmar("Fixar o Início TV como tela inicial?") {
                rodarAdmin(
                    "cmd package set-home-activity com.geanalves.tvhome/.MainActivity; " +
                        "pm disable-user --user 0 com.google.android.tvlauncher; " +
                        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1",
                )
            }
        })
        col.addView(opcao("Voltar tela inicial original", "Religa o launcher da TV") {
            rodarAdmin("pm enable com.google.android.tvlauncher")
        })
        col.addView(opcao("Desligar Google Assistente", "Para de abrir o Assistente no botão do controle") {
            confirmar("Desligar o Google Assistente?") { rodarAdmin("pm disable-user --user 0 com.google.android.katniss") }
        })
        col.addView(opcao("Religar Google Assistente") { rodarAdmin("pm enable com.google.android.katniss") })
        col.addView(opcao("Animações rápidas", "A TV fica mais esperta (0.5x)") {
            rodarAdmin("settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5")
        })
        col.addView(opcao("Animações normais", "Volta ao padrão (1x)") {
            rodarAdmin("settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1")
        })
        col.addView(opcao("Desligar protetor de tela da TV", "Fica só a tela de descanso do Início TV") {
            rodarAdmin(
                "settings put secure screensaver_enabled 0; " +
                    "settings put secure screensaver_activate_on_sleep 0; " +
                    "settings put secure screensaver_activate_on_dock 0; " +
                    "pm disable-user --user 0 com.google.android.backdrop 2>&1; " +
                    "echo Protetor da TV: \$(settings get secure screensaver_enabled)",
            )
        })
        col.addView(opcao("Religar protetor de tela da TV", "Volta o que veio de fábrica") {
            rodarAdmin(
                "pm enable com.google.android.backdrop 2>&1; " +
                    "settings put secure screensaver_enabled 1; " +
                    "settings put secure screensaver_activate_on_sleep 1",
            )
        })
        col.addView(opcao("Liberar memória", "Fecha os apps abertos em segundo plano") { rodarAdmin("am kill-all") })
        col.addView(opcao("Informações da TV", "Modelo, Android, memória e espaço") {
            rodarAdmin("echo Modelo: \$(getprop ro.product.manufacturer) \$(getprop ro.product.model); echo Android: \$(getprop ro.build.version.release); grep -E 'MemTotal|MemAvailable' /proc/meminfo; df -h /data | tail -1")
        })
        col.addView(opcao("Ver tela inicial atual") { rodarAdmin("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1") })
        col.addView(opcao("Reiniciar a TV", "Desliga e liga de novo") {
            confirmar("Reiniciar a TV agora?") { rodarAdmin("reboot") }
        })

        // Chave do TMDB: os cartazes de filmes e séries do Início TV
        col.addView(titulo("Cartazes de filmes (TMDB)"))
        col.addView(
            textoJanela(
                "Para aparecer os cartazes no Início TV, crie uma chave grátis:\n" +
                    "1. Toque em \"Abrir site do TMDB\" e crie uma conta.\n" +
                    "2. Em Configurações › API, peça uma chave (uso pessoal).\n" +
                    "3. Copie a \"Chave da API\" ou o \"Token de leitura\" e cole aqui.",
                13f,
            ).apply { setPadding(dp(4), 0, 0, dp(4)) },
        )
        col.addView(opcao("Abrir site do TMDB", "themoviedb.org › Configurações › API") {
            try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.themoviedb.org/settings/api")))
            } catch (_: Throwable) {
                toast("Não achei o navegador")
            }
        })
        val chaveBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Cores.campo, 16)
            setPadding(dp(14), dp(2), dp(4), dp(2))
        }
        val chaveCampo = EditText(this).apply {
            hint = "Cole a chave do TMDB"
            textSize = 14f
            setTextColor(Cores.tinta)
            setHintTextColor(Cores.cinza)
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        chaveBox.addView(chaveCampo, LinearLayout.LayoutParams(0, -2, 1f))
        chaveBox.addView(botao(R.drawable.ic_send, tamanho = 44, tamIcone = 20, fundo = Cores.tinta, corIcone = Cores.papel) {
            val k = chaveCampo.text.toString().trim()
            if (!Regex("[A-Za-z0-9._-]{20,400}").matches(k)) {
                toast("Essa chave não parece certa. Copie de novo do site.")
            } else {
                rodarAdmin("am broadcast -n com.geanalves.tvhome/.ConfigReceiver --es tmdb '$k'") { out ->
                    if (out?.contains("salva") == true) {
                        toast("Chave enviada! Os cartazes vão aparecer na TV")
                        chaveCampo.setText("")
                    } else {
                        toast("A TV não recebeu. Ela tem o Início TV atualizado?")
                    }
                }
            }
        })
        col.addView(chaveBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(opcao("Tirar a chave da TV", "Os cartazes somem e a fileira mostra os apps") {
            confirmar("Tirar a chave do TMDB da TV?") {
                rodarAdmin("am broadcast -n com.geanalves.tvhome/.ConfigReceiver --es tmdb ''")
            }
        })

        // TV ao vivo (IPTV): o link da lista M3U vai para o Início TV
        col.addView(titulo("TV ao vivo (IPTV)"))
        col.addView(
            textoJanela(
                "Cole o link da sua lista de canais (M3U ou M3U8). Na TV, abra \"● TV ao vivo\" ao lado das abas.",
                13f,
            ).apply { setPadding(dp(4), 0, 0, dp(4)) },
        )
        val iptvBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Cores.campo, 16)
            setPadding(dp(14), dp(2), dp(4), dp(2))
        }
        val iptvCampo = EditText(this).apply {
            hint = "https://…/lista.m3u"
            textSize = 14f
            setTextColor(Cores.tinta)
            setHintTextColor(Cores.cinza)
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        fun mandarLista(link: String) {
            val seguro = link.replace("'", "'\\''")
            rodarAdmin("am broadcast -n com.geanalves.tvhome/.ConfigReceiver --es iptv '$seguro'") { out ->
                if (out?.contains("data=\"ok\"") == true) {
                    toast(if (link.isBlank()) "Lista tirada da TV" else "Lista enviada! Abra TV ao vivo na TV")
                    iptvCampo.setText("")
                } else {
                    toast("A TV não recebeu. Ela tem o Início TV atualizado?")
                }
            }
        }
        iptvBox.addView(iptvCampo, LinearLayout.LayoutParams(0, -2, 1f))
        iptvBox.addView(botao(R.drawable.ic_send, tamanho = 44, tamIcone = 20, fundo = Cores.tinta, corIcone = Cores.papel) {
            val l = iptvCampo.text.toString().trim()
            if (l.startsWith("http")) mandarLista(l) else toast("Cole um link que começa com http")
        })
        col.addView(iptvBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(opcao("Canais abertos do Brasil", "Lista grátis e pública (iptv-org)") {
            mandarLista("https://iptv-org.github.io/iptv/countries/br.m3u")
        })
        col.addView(opcao("Tirar a lista da TV") {
            confirmar("Tirar a lista de canais da TV?") { mandarLista("") }
        })

        col.addView(titulo("Segurança"))
        col.addView(opcao("Trocar PIN", "Crie um PIN novo para a Administração") { novoPin {} })

        d.show()
    }

    /** Lista de apps da TV (todos ou só os desativados) para mexer em cada um. */
    private fun gerenciarApps(soDesativados: Boolean) {
        val (d, box) = janela()
        box.addView(textoJanela(if (soDesativados) "Apps desativados" else "Gerenciar apps", 22f, Cores.tinta, negrito = true))
        val aviso = textoJanela("Buscando os apps da TV…")
        box.addView(aviso)
        d.show()
        tv.comando("echo D; pm list packages -d; echo T; pm list packages -3; echo A; pm list packages") { out ->
            if (out == null) {
                aviso.text = "A TV não respondeu."
                return@comando
            }
            val des = HashSet<String>()
            val terceiros = HashSet<String>()
            val todos = ArrayList<String>()
            var parte = ""
            for (l in out.lines()) {
                val t = l.trim()
                if (t == "D" || t == "T" || t == "A") {
                    parte = t
                    continue
                }
                val pkg = t.removePrefix("package:")
                if (pkg.isBlank() || !t.startsWith("package:")) continue
                when (parte) {
                    "D" -> des += pkg
                    "T" -> terceiros += pkg
                    "A" -> todos += pkg
                }
            }
            val lista = (if (soDesativados) des.toList() else todos)
                .sortedWith(compareBy<String>({ it !in terceiros }, { it }))
            aviso.text = when {
                lista.isEmpty() && soDesativados -> "Nenhum app desativado."
                soDesativados -> "Toque para reativar ou remover:"
                else -> "${lista.size} apps. Primeiro os que você instalou, depois os do sistema. Toque em um:"
            }
            for (pkg in lista) {
                val sub = buildString {
                    append(pkg)
                    if (pkg in des) append(" · desativado")
                    if (pkg !in terceiros) append(" · sistema")
                }
                box.addView(opcao(Apps.nomeDoPacote(pkg), sub) { acoesApp(pkg, pkg in des) { d.dismiss() } })
            }
        }
    }

    private fun acoesApp(pkg: String, desativado: Boolean, fechar: () -> Unit) {
        val (d, box) = janela()
        box.addView(textoJanela(Apps.nomeDoPacote(pkg), 22f, Cores.tinta, negrito = true))
        box.addView(textoJanela(pkg, 12f))
        fun acao(nome: String, sub: String, cmd: String, perguntar: String? = null) {
            box.addView(
                opcao(nome, sub) {
                    val vai = {
                        rodarAdmin(cmd)
                        d.dismiss()
                        fechar()
                    }
                    if (perguntar != null) confirmar(perguntar) { vai() } else vai()
                },
            )
        }
        acao("Abrir na TV", "Abre o app agora", "monkey -p $pkg -c android.intent.category.LEANBACK_LAUNCHER 1 || monkey -p $pkg 1")
        if (desativado) {
            acao("Reativar", "Liga o app de novo", "pm enable $pkg")
        } else {
            acao("Desativar", "Some da TV, mas dá para reativar", "pm disable-user --user 0 $pkg", "Desativar ${Apps.nomeDoPacote(pkg)}?")
        }
        acao("Remover", "Tira da TV (dá para restaurar depois)", "pm uninstall -k --user 0 $pkg", "Remover ${Apps.nomeDoPacote(pkg)}?")
        acao("Limpar dados", "Apaga login e configurações do app", "pm clear $pkg", "Apagar os dados de ${Apps.nomeDoPacote(pkg)}?")
        acao("Forçar parada", "Fecha o app", "am force-stop $pkg")
        box.addView(opcao("Cancelar") { d.dismiss() })
        d.show()
    }

    /** Apps removidos com "Remover" continuam guardados na TV e podem voltar. */
    private fun restaurarApps() {
        val (d, box) = janela()
        box.addView(textoJanela("Restaurar app removido", 22f, Cores.tinta, negrito = true))
        val aviso = textoJanela("Buscando…")
        box.addView(aviso)
        d.show()
        tv.comando("echo U; pm list packages -u; echo A; pm list packages") { out ->
            if (out == null) {
                aviso.text = "A TV não respondeu."
                return@comando
            }
            val u = HashSet<String>()
            val a = HashSet<String>()
            var parte = ""
            for (l in out.lines()) {
                val t = l.trim()
                if (t == "U" || t == "A") {
                    parte = t
                    continue
                }
                if (!t.startsWith("package:")) continue
                if (parte == "U") u += t.removePrefix("package:") else a += t.removePrefix("package:")
            }
            val removidos = (u - a).sorted()
            aviso.text = if (removidos.isEmpty()) "Nenhum app removido." else "Toque para trazer de volta:"
            for (pkg in removidos) {
                box.addView(
                    opcao(Apps.nomeDoPacote(pkg), pkg) {
                        rodarAdmin("cmd package install-existing $pkg")
                        d.dismiss()
                    },
                )
            }
        }
    }

    // ---------- instalar APK ----------

    private var instalando = false

    private fun instalarLink(link: String) {
        if (instalando) {
            toast("Já tem uma instalação em andamento")
            return
        }
        instalando = true
        saidaAdmin.append("\n» Baixando $link\n")
        val destino = java.io.File(cacheDir, "para-tv.apk")
        Tv.baixar(
            link,
            destino,
            progresso = { saidaAdmin.append("$it\n") },
            fim = { arq, erro ->
                if (arq == null) {
                    instalando = false
                    saidaAdmin.append("Não consegui baixar: $erro\n")
                } else {
                    mandarParaTv(arq)
                }
            },
        )
    }

    private fun mandarParaTv(apk: java.io.File) {
        instalando = true
        saidaAdmin.append("Enviando para a TV e instalando (%.1f MB)…\n".format(apk.length() / 1048576f))
        tv.instalar(apk) { ok, msg ->
            instalando = false
            saidaAdmin.append(if (ok) "✔ $msg\n" else "✖ Não instalou: $msg\n")
            toast(if (ok) "Instalado na TV!" else "Não instalou na TV")
            apk.delete()
        }
    }

    @Deprecated("Usado só para escolher o APK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PEDIR_APK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (!::saidaAdmin.isInitialized) return
        if (instalando) {
            toast("Já tem uma instalação em andamento")
            return
        }
        instalando = true
        saidaAdmin.append("\n» Preparando o APK do celular…\n")
        Thread {
            val destino = java.io.File(cacheDir, "para-tv.apk")
            val ok = try {
                contentResolver.openInputStream(uri)!!.use { e -> destino.outputStream().use { e.copyTo(it) } }
                true
            } catch (_: Throwable) {
                false
            }
            runOnUiThread {
                if (ok) {
                    mandarParaTv(destino)
                } else {
                    instalando = false
                    saidaAdmin.append("Não consegui ler o arquivo.\n")
                }
            }
        }.start()
    }

    // ---------- modo escuro ----------

    /** Lua = deixa tudo escuro; sol = volta para o claro. */
    private fun trocarModo() {
        prefs.escuro = !prefs.escuro
        Cores.escuro = prefs.escuro
        recreate()
    }

    companion object {
        private const val PEDIR_APK = 42
    }
}
