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
        col.addView(direcional(), LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(espaco(h = 24))
        col.addView(linhaNavegacao(), LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(espaco(h = 16))
        col.addView(linhaVolume(), LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(titulo("Digitar na TV"))
        col.addView(teclado())
        col.addView(cabecalhoApps())
        appsGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(appsGrid)
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

    /** Círculo grande com as setas e o OK no meio. */
    private fun direcional(): View {
        val lado = (resources.displayMetrics.widthPixels - dp(40)).coerceAtMost(dp(300))
        val pad = FrameLayout(this).apply {
            background = oval(Cores.janela)
            elevation = dpf(4)
            layoutParams = LinearLayout.LayoutParams(lado, lado)
        }
        fun seta(icone: Int, tecla: Int, g: Int) {
            val b = botao(icone, tamanho = 76, tamIcone = 36, repetir = true) { tv.tecla(tecla) }
            val lp = FrameLayout.LayoutParams(dp(76), dp(76), g)
            lp.setMargins(dp(10), dp(10), dp(10), dp(10))
            pad.addView(b, lp)
        }
        seta(R.drawable.ic_up, Tv.CIMA, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        seta(R.drawable.ic_down, Tv.BAIXO, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        seta(R.drawable.ic_left, Tv.ESQUERDA, Gravity.START or Gravity.CENTER_VERTICAL)
        seta(R.drawable.ic_right, Tv.DIREITA, Gravity.END or Gravity.CENTER_VERTICAL)
        val ok = botao(null, texto = "OK", tamanho = 116, fundo = Cores.campo) { tv.tecla(Tv.OK) }
        pad.addView(ok, FrameLayout.LayoutParams(dp(116), dp(116), Gravity.CENTER))
        return pad
    }

    /** Voltar, Início, Menu e Play/Pause, com o nome embaixo. */
    private fun linhaNavegacao(): View {
        val p = pilula()
        fun item(icone: Int, nome: String, tecla: Int) {
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(2), dp(4), dp(4))
            }
            c.addView(botao(icone, tamanho = 54) { tv.tecla(tecla) })
            c.addView(
                TextView(this).apply {
                    text = nome
                    textSize = 11f
                    setTextColor(Cores.cinza)
                    gravity = Gravity.CENTER
                },
            )
            p.addView(c)
        }
        item(R.drawable.ic_back, "Voltar", Tv.VOLTAR)
        item(R.drawable.ic_home, "Início", Tv.INICIO)
        item(R.drawable.ic_menu, "Menu", Tv.MENU)
        item(R.drawable.ic_play, "Play", Tv.PLAY_PAUSE)
        return p
    }

    /** Volume: menos, mudo e mais (segurar repete). */
    private fun linhaVolume(): View {
        val p = pilula()
        p.addView(botao(R.drawable.ic_vol_down, tamanho = 60, repetir = true) { tv.tecla(Tv.VOL_MENOS) })
        p.addView(
            TextView(this).apply {
                text = "Volume"
                textSize = 15f
                setTextColor(Cores.cinza)
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
            },
        )
        p.addView(botao(R.drawable.ic_mute, tamanho = 60, fundo = Cores.campo) { tv.tecla(Tv.MUDO) })
        p.addView(espaco(w = 14))
        p.addView(botao(R.drawable.ic_vol_up, tamanho = 60, repetir = true) { tv.tecla(Tv.VOL_MAIS) })
        return p
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
    private fun blocoApp(app: AppTv): View {
        val bg = rounded(app.cor, 18)
        val t = TextView(this).apply {
            text = app.nome
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = bg
            elevation = dpf(2)
            setPadding(dp(6), 0, dp(6), 0)
            maxLines = 2
        }
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

    // ---------- conexão ----------

    private fun mostrarEstado(e: Tv.Estado, msg: String?) {
        if (!::statusText.isInitialized) return
        when (e) {
            Tv.Estado.CONECTADO -> {
                statusDot.setColor(Cores.verde)
                statusText.text = "Conectado · ${tv.ip}"
                tv.pacotes { p ->
                    if (p != null) {
                        instalados = p
                        montarApps()
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

    // ---------- modo escuro ----------

    /** Lua = deixa tudo escuro; sol = volta para o claro. */
    private fun trocarModo() {
        prefs.escuro = !prefs.escuro
        Cores.escuro = prefs.escuro
        recreate()
    }
}
