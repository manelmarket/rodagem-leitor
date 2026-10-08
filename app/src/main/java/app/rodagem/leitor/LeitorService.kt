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
 * Observa as telas dos apps de corrida ligados. Com o que vê:
 * - mostra o semáforo (cartão verde, amarelo ou vermelho) por cima da oferta aberta;
 * - registra os textos das telas que têm valor, km ou minutos (diagnóstico);
 * - monta as corridas com o ExtratorCorrida (por enquanto da Maxim e da Easy).
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
    private var ultimoGravado = ""
    private var ultimaGravacao = 0L
    private var pendente: String? = null
    private val capturarDepois = Runnable { capturar() }
    private val gravarDepois = Runnable { pendente?.let { gravar(it) } }
    private val interesse = Regex("(?i)R\\$|\\bkm\\b|\\bmin\\b|minuto")

    private var semaforo: Semaforo? = null
    private var testeAte = 0L
    /** Enquanto o cartão está na tela, olha de novo a cada segundo se a oferta ainda está lá. */
    private val conferir = object : Runnable {
        override fun run() {
            capturar()
            if (semaforo?.visivel == true) handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        instancia = this
        semaforo = Semaforo(this)
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
        if (texto == ultimoTexto) return
        ultimoTexto = texto

        atualizarSemaforo(texto)

        if (!interesse.containsMatchIn(texto) || texto == ultimoGravado) return
        // no máximo uma gravação a cada 1,2 s (as ofertas têm contagem regressiva que muda a tela toda hora)
        val agora = SystemClock.elapsedRealtime()
        if (agora - ultimaGravacao < 1200) {
            pendente = texto
            handler.removeCallbacks(gravarDepois)
            handler.postDelayed(gravarDepois, 1200 - (agora - ultimaGravacao))
            return
        }
        gravar(texto)
    }

    private fun gravar(texto: String) {
        handler.removeCallbacks(gravarDepois)
        pendente = null
        ultimoGravado = texto
        ultimaGravacao = SystemClock.elapsedRealtime()
        Registro.gravar(this, "TELA", texto)

        // tira da tela a corrida (oferta, andamento ou fim); um erro aqui não pode derrubar o leitor
        try {
            ExtratorCorrida.analisar(this, texto)
        } catch (e: Exception) {
            Registro.gravar(this, "ERRO NO EXTRATOR", "  ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Oferta aberta na tela: mostra o cartão com a cor. Sem oferta: some com ele. */
    private fun atualizarSemaforo(texto: String) {
        val s = semaforo ?: return
        if (SystemClock.elapsedRealtime() < testeAte) return
        val oferta = try {
            if (Ajustes.ligado(this)) ExtratorCorrida.destaque(texto, System.currentTimeMillis()) else null
        } catch (e: Exception) {
            Registro.gravar(this, "ERRO NO SEMÁFORO", "  ${e.javaClass.simpleName}: ${e.message}")
            null
        }
        if (oferta == null) {
            s.esconder()
            handler.removeCallbacks(conferir)
            return
        }
        val antes = s.visivel
        s.mostrar(oferta, Avaliador.avaliar(oferta, Ajustes.regras(this)), Ajustes.emCima(this))
        if (!antes && s.visivel) {
            handler.removeCallbacks(conferir)
            handler.postDelayed(conferir, 1000)
        }
    }

    /** Botão "Testar cartão": mostra três ofertas de exemplo (boa, no meio e ruim) com as regras atuais. */
    fun testarSemaforo() {
        val s = semaforo ?: return
        handler.removeCallbacks(conferir)
        val regras = Ajustes.regras(this)
        val emCima = Ajustes.emCima(this)
        val exemplos = listOf(
            Corrida(aplicativo = "Easy", valor = 18.0, embarqueKm = 1.0, embarqueMinutos = 3,
                distanciaKm = 6.0, tempoMinutos = 14, nota = 4.9),
            Corrida(aplicativo = "Maxim", valor = 11.5, embarqueKm = 1.5, embarqueMinutos = 5,
                distanciaKm = 4.5, tempoMinutos = 15),
            Corrida(aplicativo = "Maxim", valor = 9.0, embarqueKm = 6.5, embarqueMinutos = 18,
                distanciaKm = 5.0, tempoMinutos = 15)
        )
        testeAte = SystemClock.elapsedRealtime() + exemplos.size * 3500L
        exemplos.forEachIndexed { i, c ->
            handler.postDelayed({ s.mostrar(c, Avaliador.avaliar(c, regras), emCima, teste = true) }, i * 3500L)
        }
        handler.postDelayed({
            testeAte = 0L
            s.esconder()
            ultimoTexto = ""   // na próxima tela, volta a valer o que estiver aberto de verdade
        }, exemplos.size * 3500L)
    }

    /** Desligou o semáforo nos ajustes: some com o cartão na hora. */
    fun recarregarSemaforo() {
        if (!Ajustes.ligado(this)) {
            semaforo?.esconder()
            handler.removeCallbacks(conferir)
        }
        ultimoTexto = ""
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
        semaforo?.esconder()
        semaforo = null
        super.onDestroy()
    }
}
