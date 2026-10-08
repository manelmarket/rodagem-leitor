package app.rodagem.leitor

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Versão de diagnóstico: só observa as telas dos apps de corrida ligados e registra
 * os textos que aparecem quando há valor, km ou minutos na tela. Dessas telas, o
 * ExtratorCorrida tira as corridas (por enquanto da Maxim e da Easy).
 * Não clica em nada, não aceita e não recusa corridas.
 */
class LeitorService : AccessibilityService() {

    companion object {
        @Volatile var instancia: LeitorService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var monitorados: Set<String> = emptySet()
    private var ultimoTexto = ""
    private var ultimaGravacao = 0L
    private val capturarDepois = Runnable { capturar() }
    private val interesse = Regex("(?i)R\\$|\\bkm\\b|\\bmin\\b|minuto")

    override fun onServiceConnected() {
        instancia = this
        recarregarApps()
    }

    /** Relê quais apps estão ligados no toggle e limita o leitor a eles. */
    fun recarregarApps() {
        monitorados = Apps.ativos(this)
        serviceInfo = serviceInfo.apply {
            // nenhum app ligado: escuta só a si mesmo (ou seja, nada)
            packageNames = if (monitorados.isEmpty()) arrayOf(packageName) else monitorados.toTypedArray()
        }
        val nomes = Apps.instalados(this).filter { it.pacote in monitorados }
        Registro.gravar(this, "APPS LIGADOS",
            if (nomes.isEmpty()) "Nenhum app ligado."
            else nomes.joinToString("\n") { "app monitorado: ${it.nome} (${it.pacote})" })
    }

    override fun onAccessibilityEvent(evento: AccessibilityEvent) {
        val pacote = evento.packageName?.toString() ?: return
        if (pacote !in monitorados) return

        if (evento.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val texto = evento.text.joinToString(" | ").trim()
            if (texto.isNotEmpty()) Registro.gravar(this, "NOTIFICAÇÃO $pacote", "  $texto")
            return
        }
        // espera a tela parar de mudar por um instante antes de ler
        handler.removeCallbacks(capturarDepois)
        handler.postDelayed(capturarDepois, 350)
    }

    private fun capturar() {
        val sb = StringBuilder()
        val janelas: List<AccessibilityWindowInfo> = try { windows } catch (e: Exception) { emptyList() }
        val raizes = if (janelas.isNotEmpty()) janelas.mapNotNull { j -> j.root?.let { j to it } }
                     else listOfNotNull(rootInActiveWindow?.let { null to it })

        for ((janela, raiz) in raizes) {
            val pacote = raiz.packageName?.toString() ?: continue
            if (pacote !in monitorados) continue
            sb.append("-- janela ${tipo(janela)} | pacote $pacote\n")
            despejar(raiz, 0, sb)
        }
        val texto = sb.toString()
        if (!interesse.containsMatchIn(texto)) return
        if (texto == ultimoTexto) return
        // no máximo uma gravação a cada 1,2 s (as ofertas têm contagem regressiva que muda a tela toda hora)
        val agora = SystemClock.elapsedRealtime()
        if (agora - ultimaGravacao < 1200) {
            handler.removeCallbacks(capturarDepois)
            handler.postDelayed(capturarDepois, 1200 - (agora - ultimaGravacao))
            return
        }
        ultimoTexto = texto
        ultimaGravacao = agora
        Registro.gravar(this, "TELA", texto)

        // tira da tela a corrida (oferta, andamento ou fim); um erro aqui não pode derrubar o leitor
        try {
            ExtratorCorrida.analisar(this, texto)
        } catch (e: Exception) {
            Registro.gravar(this, "ERRO NO EXTRATOR", "  ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun tipo(j: AccessibilityWindowInfo?): String = when (j?.type) {
        null -> "ativa"
        AccessibilityWindowInfo.TYPE_APPLICATION -> "app"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "sistema"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "teclado"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "sobreposta"
        else -> "tipo${j.type}"
    } + (j?.let { " camada ${it.layer}" } ?: "")

    private fun despejar(no: AccessibilityNodeInfo, nivel: Int, sb: StringBuilder) {
        if (nivel > 45) return
        val texto = no.text?.toString()?.replace("\n", " ")?.trim()
        val desc = no.contentDescription?.toString()?.replace("\n", " ")?.trim()
        val id = no.viewIdResourceName?.substringAfter(":id/")
        if (!texto.isNullOrEmpty() || !desc.isNullOrEmpty() || id != null) {
            val r = Rect().also { no.getBoundsInScreen(it) }
            sb.append("  ".repeat(nivel))
              .append(no.className?.toString()?.substringAfterLast('.') ?: "?")
            if (id != null) sb.append(" #").append(id)
            if (!texto.isNullOrEmpty()) sb.append(" | texto: ").append(texto)
            if (!desc.isNullOrEmpty() && desc != texto) sb.append(" | desc: ").append(desc)
            if (no.isClickable) sb.append(" | clicável")
            sb.append(" | [").append(r.left).append(',').append(r.top).append(' ')
              .append(r.right).append(',').append(r.bottom).append("]\n")
        }
        for (i in 0 until no.childCount) {
            no.getChild(i)?.let { despejar(it, nivel + 1, sb) }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instancia = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
