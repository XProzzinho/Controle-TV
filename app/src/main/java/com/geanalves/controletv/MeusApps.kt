package com.geanalves.controletv

import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ajustes sem código nos seus apps (nome e ícone), pelo GitHub:
 * o controle manda a imagem para ajustes/icone.png e dispara o "Ajustes do app",
 * que troca nome/ícone, salva e gera o APK novo.
 */
object MeusApps {
    private val main = Handler(Looper.getMainLooper())

    private fun pedir(token: String, metodo: String, caminho: String, corpo: JSONObject? = null): Pair<Int, String> {
        val c = URL("https://api.github.com/repos/$caminho").openConnection() as HttpURLConnection
        c.requestMethod = metodo
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (corpo != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(corpo.toString().toByteArray()) }
        }
        val code = c.responseCode
        val txt = try {
            (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        } catch (_: Throwable) {
            ""
        }
        c.disconnect()
        return code to txt
    }

    /**
     * [icone] = PNG já reduzido (ou null para não mexer no ícone).
     * [fim] recebe null se deu certo, ou a mensagem de erro.
     */
    fun aplicar(token: String, repo: String, nome: String, icone: ByteArray?, fim: (String?) -> Unit) {
        Thread {
            val erro = try {
                var falha: String? = null
                if (icone != null) {
                    // Precisa do "sha" se a imagem já existe (troca em vez de criar)
                    val (c0, t0) = pedir(token, "GET", "$repo/contents/ajustes/icone.png")
                    val sha = if (c0 == 200) JSONObject(t0).optString("sha") else null
                    val corpo = JSONObject()
                        .put("message", "Ícone novo pelo Controle TV [skip ci]")
                        .put("content", Base64.encodeToString(icone, Base64.NO_WRAP))
                    if (!sha.isNullOrBlank()) corpo.put("sha", sha)
                    val (c1, _) = pedir(token, "PUT", "$repo/contents/ajustes/icone.png", corpo)
                    if (c1 !in 200..201) falha = explicar(c1, "mandar o ícone")
                }
                if (falha == null) {
                    val corpo = JSONObject()
                        .put("ref", "main")
                        .put("inputs", JSONObject().put("nome", nome).put("icone", if (icone != null) "sim" else "nao"))
                    val (c2, _) = pedir(token, "POST", "$repo/actions/workflows/ajustes.yml/dispatches", corpo)
                    if (c2 != 204) falha = explicar(c2, "começar os ajustes")
                }
                falha
            } catch (_: Throwable) {
                "Sem internet ou o GitHub não respondeu"
            }
            main.post { fim(erro) }
        }.start()
    }

    private fun explicar(code: Int, oque: String) = when (code) {
        401 -> "A chave do GitHub não vale (confira ou crie outra)"
        403 -> "A chave não tem permissão para $oque (precisa de Contents e Actions: leitura e escrita)"
        404 -> "Não achei o repositório ou o ajuste nele (a chave tem acesso a esse repositório?)"
        422 -> "O GitHub recusou ($oque)"
        else -> "Não consegui $oque (erro $code)"
    }
}
