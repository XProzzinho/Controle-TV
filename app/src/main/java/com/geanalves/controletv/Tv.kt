package com.geanalves.controletv

import android.content.Context
import android.os.Handler
import android.os.Looper
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.text.Normalizer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Conversa com a TV pelo Wi-Fi usando a depuração ADB (porta 5555).
 * Funciona na TCL (Android TV) e na Fire TV Stick, sem instalar nada na TV.
 */
class Tv(private val ctx: Context) {

    enum class Estado { DESLIGADO, CONECTANDO, CONECTADO, ERRO }

    private val fila = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var dadb: Dadb? = null

    var ip: String? = null
        private set
    var estado = Estado.DESLIGADO
        private set

    /** Avisado sempre que o estado da conexão muda. */
    var aoMudar: ((Estado, String?) -> Unit)? = null

    private val chave: AdbKeyPair by lazy {
        val priv = File(ctx.filesDir, "adbkey")
        val pub = File(ctx.filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        AdbKeyPair.read(priv, pub)
    }

    private fun mudar(e: Estado, msg: String? = null) {
        estado = e
        main.post { aoMudar?.invoke(e, msg) }
    }

    fun conectar(novoIp: String) {
        ip = novoIp.trim()
        fila.execute {
            fechar()
            mudar(Estado.CONECTANDO)
            try {
                val d = Dadb.create(ip!!, PORTA, chave)
                // Na primeira vez a TV pergunta "Permitir depuração?": aceite lá
                d.shell("echo ok")
                dadb = d
                mudar(Estado.CONECTADO)
            } catch (t: Throwable) {
                fechar()
                mudar(Estado.ERRO, "Não consegui falar com a TV ($ip)")
            }
        }
    }

    private fun fechar() {
        try {
            dadb?.close()
        } catch (_: Throwable) {
        }
        dadb = null
    }

    /** Roda um comando na TV. Se a conexão caiu, tenta reconectar uma vez. */
    private fun rodar(cmd: String, resposta: ((String?) -> Unit)? = null) {
        fila.execute {
            var saida: String? = null
            for (tentativa in 0..1) {
                try {
                    val d = dadb ?: run {
                        val alvo = ip ?: return@execute
                        mudar(Estado.CONECTANDO)
                        Dadb.create(alvo, PORTA, chave).also {
                            it.shell("echo ok")
                            dadb = it
                            mudar(Estado.CONECTADO)
                        }
                    }
                    saida = d.shell(cmd).allOutput
                    break
                } catch (t: Throwable) {
                    fechar()
                    if (tentativa == 1) mudar(Estado.ERRO, "A TV não respondeu")
                }
            }
            if (resposta != null) {
                val s = saida
                main.post { resposta(s) }
            }
        }
    }

    fun tecla(codigo: Int) = rodar("input keyevent $codigo")

    /**
     * Brilho da tela pelo Início TV (véu escuro por cima de tudo).
     * [extra] = "--ei nivel 30" ou "--ei passo -10" ou "" (só consultar).
     * Devolve o nível novo (5 a 100) ou null se o Início TV não estiver na TV.
     */
    fun brilho(extra: String, cb: (Int?) -> Unit) {
        rodar(
            "appops set com.geanalves.tvhome SYSTEM_ALERT_WINDOW allow 2>/dev/null; " +
                "am broadcast -n com.geanalves.tvhome/.BrilhoReceiver $extra",
        ) { out ->
            cb(out?.let { Regex("data=\"(\\d+)\"").find(it)?.groupValues?.get(1)?.toIntOrNull() })
        }
    }

    /** Pega a conexão aberta ou conecta de novo (roda dentro da fila). */
    private fun conexao(): Dadb {
        dadb?.let { return it }
        val alvo = ip ?: throw IllegalStateException("Nenhuma TV escolhida")
        mudar(Estado.CONECTANDO)
        val d = Dadb.create(alvo, PORTA, chave)
        d.shell("echo ok")
        dadb = d
        mudar(Estado.CONECTADO)
        return d
    }

    /** Manda um APK do celular para a TV e instala (sem precisar aceitar nada na TV). */
    fun instalar(apk: File, fim: (Boolean, String) -> Unit) {
        fila.execute {
            var ok = false
            var msg = ""
            for (tentativa in 0..1) {
                try {
                    conexao().install(apk)
                    ok = true
                    msg = "Instalado na TV!"
                    break
                } catch (t: Throwable) {
                    msg = t.message ?: t.toString()
                    // Conexão caiu: tenta de novo uma vez; erro do instalador: para
                    if (t is java.io.IOException) fechar() else break
                }
            }
            main.post { fim(ok, msg) }
        }
    }

    /** Comando livre (terminal da Administração). Devolve o que a TV respondeu. */
    fun comando(cmd: String, cb: (String?) -> Unit) = rodar(cmd, cb)

    /** Digita na TV. O comando da TV não aceita acentos, então eles são tirados. */
    fun digitar(texto: String) {
        val semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\x20-\\x7E]"), "")
        if (semAcento.isEmpty()) return
        val escapado = semAcento.replace(" ", "%s").replace("'", "'\\''")
        rodar("input text '$escapado'")
    }

    /** Abre um app da TV pelo nome do pacote. */
    fun abrir(pkg: String) {
        rodar(
            "out=\$(monkey -p $pkg -c android.intent.category.LEANBACK_LAUNCHER 1 2>&1); " +
                "case \"\$out\" in *aborted*|*No\\ activities*) monkey -p $pkg -c android.intent.category.LAUNCHER 1 ;; esac",
        )
    }

    /** Abre o primeiro app da lista que estiver instalado na TV (nomes diferentes na TCL e na Fire TV). */
    fun abrirPrimeiro(pkgs: List<String>) {
        if (pkgs.size == 1) return abrir(pkgs[0])
        val lista = pkgs.joinToString(" ")
        rodar(
            "for p in $lista; do if pm path \$p >/dev/null 2>&1; then " +
                "out=\$(monkey -p \$p -c android.intent.category.LEANBACK_LAUNCHER 1 2>&1); " +
                "case \"\$out\" in *aborted*|*No\\ activities*) monkey -p \$p -c android.intent.category.LAUNCHER 1 ;; esac; " +
                "break; fi; done",
        )
    }

    /** Lista dos pacotes instalados na TV. */
    fun pacotes(cb: (Set<String>?) -> Unit) {
        rodar("pm list packages") { out ->
            cb(
                out?.lineSequence()
                    ?.map { it.trim().removePrefix("package:") }
                    ?.filter { it.isNotBlank() }
                    ?.toSet(),
            )
        }
    }

    /** Apps que o usuário instalou na TV (sem os do sistema). */
    fun pacotesInstalados(cb: (List<String>?) -> Unit) {
        rodar("pm list packages -3") { out ->
            cb(
                out?.lineSequence()
                    ?.map { it.trim().removePrefix("package:") }
                    ?.filter { it.isNotBlank() }
                    ?.sorted()
                    ?.toList(),
            )
        }
    }

    companion object {
        const val PORTA = 5555

        /** Última versão do Início TV no GitHub. */
        const val LINK_INICIO = "https://github.com/XProzzinho/inicio-tv./releases/latest/download/inicio-tv.apk"

        /** Baixa um arquivo pela internet do celular, avisando o progresso em MB. */
        fun baixar(link: String, destino: File, progresso: (String) -> Unit, fim: (File?, String?) -> Unit) {
            val main = Handler(Looper.getMainLooper())
            Thread {
                try {
                    var url = java.net.URL(link)
                    var con: java.net.HttpURLConnection
                    var pulos = 0
                    while (true) {
                        con = url.openConnection() as java.net.HttpURLConnection
                        con.instanceFollowRedirects = false
                        con.connectTimeout = 15_000
                        con.readTimeout = 30_000
                        val code = con.responseCode
                        if (code in 300..399 && pulos < 8) {
                            url = java.net.URL(url, con.getHeaderField("Location"))
                            con.disconnect()
                            pulos++
                            continue
                        }
                        if (code !in 200..299) throw java.io.IOException("O link respondeu erro $code")
                        break
                    }
                    val total = con.contentLengthLong
                    var lido = 0L
                    var ultimo = 0L
                    con.inputStream.use { ent ->
                        destino.outputStream().use { sai ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = ent.read(buf)
                                if (n < 0) break
                                sai.write(buf, 0, n)
                                lido += n
                                if (lido - ultimo >= 2 * 1024 * 1024) {
                                    ultimo = lido
                                    val mb = "%.1f".format(lido / 1048576f)
                                    val tot = if (total > 0) " de %.1f MB".format(total / 1048576f) else " MB"
                                    main.post { progresso("Baixando… $mb$tot") }
                                }
                            }
                        }
                    }
                    main.post { fim(destino, null) }
                } catch (t: Throwable) {
                    main.post { fim(null, t.message ?: t.toString()) }
                }
            }.start()
        }

        /** Uma conexão só para o app todo (não cai ao trocar claro/escuro). */
        @Volatile
        private var unica: Tv? = null

        fun de(ctx: Context): Tv = unica ?: synchronized(this) {
            unica ?: Tv(ctx.applicationContext).also { unica = it }
        }

        // Teclas do controle (códigos do Android)
        const val CIMA = 19
        const val BAIXO = 20
        const val ESQUERDA = 21
        const val DIREITA = 22
        const val OK = 23
        const val VOLTAR = 4
        const val INICIO = 3
        const val MENU = 82
        const val PLAY_PAUSE = 85
        const val VOL_MAIS = 24
        const val VOL_MENOS = 25
        const val MUDO = 164
        const val LIGAR = 26
        const val APAGAR = 67

        /**
         * Procura TVs na mesma rede Wi-Fi: testa a porta 5555 em todos os
         * endereços da rede (ex.: 192.168.0.1 a 192.168.0.254).
         */
        fun procurar(meuIp: String, achou: (String) -> Unit, fim: () -> Unit) {
            val base = meuIp.substringBeforeLast('.')
            val pool = Executors.newFixedThreadPool(48)
            val main = Handler(Looper.getMainLooper())
            for (i in 1..254) {
                val alvo = "$base.$i"
                if (alvo == meuIp) continue
                pool.execute {
                    try {
                        Socket().use { it.connect(InetSocketAddress(alvo, PORTA), 400) }
                        main.post { achou(alvo) }
                    } catch (_: Throwable) {
                    }
                }
            }
            pool.shutdown()
            Thread {
                pool.awaitTermination(30, TimeUnit.SECONDS)
                main.post { fim() }
            }.start()
        }
    }
}
