package app.rodagem.leitor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/**
 * O cartão do semáforo, por cima da oferta. Só mostra: não recebe toque nenhum
 * (o toque passa direto para o app de corrida) e nunca aceita nem recusa nada.
 * Usa a janela própria do serviço de Acessibilidade, então não precisa de outra permissão.
 */
class Semaforo(private val servico: AccessibilityService) {

    companion object {
        // as mesmas cores do Rodagem
        const val VERDE = 0xFF1F8A50.toInt()
        const val AMARELO = 0xFFF2B705.toInt()
        const val VERMELHO = 0xFFC83E3E.toInt()
        const val TINTA = 0xFF1F262D.toInt()

        fun corDe(c: Cor) = when (c) {
            Cor.VERDE -> VERDE
            Cor.AMARELO -> AMARELO
            Cor.VERMELHO -> VERMELHO
        }

        fun textoSobre(c: Cor) = if (c == Cor.AMARELO) TINTA else Color.WHITE

        fun rotulo(c: Cor) = when (c) {
            Cor.VERDE -> "ACEITAR"
            Cor.AMARELO -> "AVALIAR"
            Cor.VERMELHO -> "RECUSAR"
        }

        private val brasil = Locale("pt", "BR")

        /** Espaço que não quebra: "R$ 14,23" nunca se parte no fim da linha. */
        private const val JUNTO = '\u00A0'

        private fun junto(s: String) = s.replace(' ', JUNTO)

        fun reais(v: Double) = String.format(brasil, "R\$${JUNTO}%.2f", v)

        fun km(v: Double) = String.format(brasil, "%.1f${JUNTO}km", v)

        /** Os motivos da cor, separados por " · " (cada motivo inteiro na mesma linha, se couber). */
        fun motivos(a: Avaliacao) = a.motivos.joinToString("  ·  ") { junto(it) }

        /** "R$ 2,10/km · R$ 38/h · lucro R$ 7,80". Sem o km da corrida: "até R$ 3,86/km" (no máximo). */
        fun numeros(a: Avaliacao): String {
            val partes = mutableListOf<String>()
            partes += a.porKm?.let { "${reais(it)}/km" } ?: a.porKmMax?.let { "até ${reais(it)}/km" } ?: "R$ ?/km"
            partes += a.porHora?.let { "R$ ${it.roundToInt()}/h" } ?: a.porHoraMax?.let { "até R$ ${it.roundToInt()}/h" } ?: "R$ ?/h"
            a.lucro?.let { partes += "lucro ${reais(it)}" } ?: a.lucroMax?.let { partes += "lucro até ${reais(it)}" }
            return partes.joinToString("  ·  ") { junto(it) }
        }

        /** "7,0 km (1,0 até o passageiro) · ~17 min · nota 4,9" */
        fun trajeto(a: Avaliacao, c: Corrida): String {
            val partes = mutableListOf<String>()
            if (a.kmConhecido) {
                partes += km(a.kmTotal) + if (c.embarqueKm > 0) " (${km(c.embarqueKm)} até o passageiro)" else ""
            } else if (c.embarqueKm > 0) {
                partes += "${km(c.embarqueKm)} até o passageiro"
            }
            if (a.minutos > 0) partes += (if (a.minutosEstimados) "~" else "") + "${a.minutos.roundToInt()} min"
            if (a.nota > 0) partes += String.format(brasil, "nota %.1f", a.nota)
            return partes.joinToString("  ·  ") { junto(it) }
        }
    }

    private val wm = servico.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var vista: View? = null
    private var mostrando = ""

    val visivel get() = vista != null

    private fun dp(v: Int) = (v * servico.resources.displayMetrics.density).roundToInt()

    /** Mostra (ou troca) o cartão. Se for a mesma oferta com o mesmo resultado, não faz nada. */
    fun mostrar(c: Corrida, a: Avaliacao, emCima: Boolean, teste: Boolean = false) {
        val chave = "$emCima|$teste|${c.copy(dataHora = 0, atualizado = 0)}|$a"
        if (chave == mostrando && vista != null) return
        esconder()

        val cartao = cartao(c, a, teste)
        val moldura = FrameLayout(servico).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            addView(cartao)
        }

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (emCima) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
            y = if (emCima) barraDeStatus() else dp(4)
            title = "Semáforo Rodagem"
        }

        try {
            wm.addView(moldura, p)
            vista = moldura
            mostrando = chave
        } catch (e: Exception) {
            vista = null
            mostrando = ""
            Registro.gravar(servico, "ERRO NO SEMÁFORO", "  ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun esconder() {
        vista?.let { v ->
            try { wm.removeViewImmediate(v) } catch (e: Exception) { }
        }
        vista = null
        mostrando = ""
    }

    private fun barraDeStatus(): Int {
        val id = servico.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) servico.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun cartao(c: Corrida, a: Avaliacao, teste: Boolean): View {
        val fundo = corDe(a.cor)
        val letra = textoSobre(a.cor)
        val suave = if (a.cor == Cor.AMARELO) Color.parseColor("#3A3320") else Color.parseColor("#E8F0EC")

        return LinearLayout(servico).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(fundo)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), Color.argb(70, 0, 0, 0))
            }
            elevation = dp(6).toFloat()

            // ACEITAR / AVALIAR / RECUSAR e o app
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = "●  " + rotulo(a.cor)
                    setTextColor(letra); textSize = 20f; typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(context).apply {
                    text = c.aplicativo + if (teste) " (teste)" else ""
                    setTextColor(letra); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                })
            })

            // valor (já sem a comissão do app, se tiver)
            val bruto = if (c.valor > 0) c.valor else c.valorCobrado
            addView(TextView(context).apply {
                text = reais(a.valor) + if (bruto - a.valor > 0.004) "  (de ${reais(bruto)}, sem a comissão)" else ""
                setTextColor(letra); textSize = 24f; typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(2), 0, 0)
            })

            addView(TextView(context).apply {
                text = numeros(a)
                setTextColor(letra); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(2), 0, 0)
            })

            val t = trajeto(a, c)
            if (t.isNotEmpty()) addView(TextView(context).apply {
                text = t
                setTextColor(suave); textSize = 14f
                setPadding(0, dp(2), 0, 0)
            })

            if (a.motivos.isNotEmpty()) addView(TextView(context).apply {
                text = motivos(a)
                setTextColor(letra); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(4), 0, 0)
            })
        }
    }
}
