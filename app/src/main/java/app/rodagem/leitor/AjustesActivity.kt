package app.rodagem.leitor

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Ajustes do semáforo: faixas, limites, comissão por app e a calculadora de custo do carro. */
class AjustesActivity : Activity() {

    private val asfalto = Color.parseColor("#26303A")
    private val fundo = Color.parseColor("#ECEFF2")
    private val tinta = Color.parseColor("#1F262D")
    private val cinza = Color.parseColor("#66717D")
    private val linha = Color.parseColor("#D8DEE4")
    private val sinal = Color.parseColor("#F2B705")
    private val verde = Color.parseColor("#1F8A50")
    private val vermelho = Color.parseColor("#C83E3E")
    private val brasil = Locale("pt", "BR")

    private lateinit var ligado: Switch
    private lateinit var estadoLigado: TextView
    private lateinit var emCima: Button
    private lateinit var embaixo: Button
    private var posicaoEmCima = true

    private val campos = mutableMapOf<String, EditText>()
    private lateinit var resultadoCusto: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = asfalto

        val r = Ajustes.regras(this)
        val c = Ajustes.custo(this)
        posicaoEmCima = Ajustes.emCima(this)

        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(fundo) }

        raiz.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(asfalto)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            addView(TextView(context).apply {
                text = "Semáforo"; setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = "Faixas, limites e custo do carro"; setTextColor(Color.parseColor("#C9D1D8")); textSize = 14f
            })
        })
        raiz.addView(View(this).apply { setBackgroundColor(sinal) }, LinearLayout.LayoutParams(-1, dp(6)))

        val corpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }

        // cartão na tela
        corpo.addView(cartao(
            "Cartão na tela",
            "Quando chega uma oferta, aparece por cima um cartão verde (aceitar), amarelo (avaliar) ou vermelho (recusar). É só um aviso: quem decide e toca é você."
        ).also { k ->
            k.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(4))
                addView(TextView(context).apply {
                    text = "Mostrar o semáforo"; setTextColor(tinta); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(0, -2, 1f))
                estadoLigado = TextView(context).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(8), 0, dp(4), 0) }
                addView(estadoLigado)
                ligado = Switch(context).apply {
                    isChecked = Ajustes.ligado(this@AjustesActivity)
                    val estados = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                    trackTintList = ColorStateList(estados, intArrayOf(verde, vermelho))
                    thumbTintList = ColorStateList(estados, intArrayOf(Color.WHITE, Color.WHITE))
                    setOnCheckedChangeListener { _, _ -> pintarLigado(); salvarSemaforo() }
                }
                addView(ligado)
            })
            pintarLigado()

            k.addView(TextView(this).apply {
                text = "Posição do cartão"; setTextColor(tinta); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(10), 0, 0)
            })
            k.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                emCima = botao("Em cima", false) { posicaoEmCima = true; pintarPosicao(); salvarSemaforo() }
                embaixo = botao("Embaixo", false) { posicaoEmCima = false; pintarPosicao(); salvarSemaforo() }
                addView(emCima, LinearLayout.LayoutParams(0, dp(44), 1f).apply { topMargin = dp(8); rightMargin = dp(5) })
                addView(embaixo, LinearLayout.LayoutParams(0, dp(44), 1f).apply { topMargin = dp(8); leftMargin = dp(5) })
            })
            pintarPosicao()
            k.addView(dica("Embaixo, o cartão pode ficar por cima do botão de aceitar do app. O toque passa direto, mas fica mais difícil de ver."))

            k.addView(botao("Testar cartão", true) { testar() })
            k.addView(dica("Mostra três ofertas de exemplo com os números desta tela. O leitor precisa estar ligado (passo 1 da tela inicial)."))
        })

        // faixas
        corpo.addView(cartao(
            "Faixas",
            "Abaixo do vermelho: vermelho. A partir do verde: verde. No meio: amarelo. A cor final é a pior de todas."
        ).also { k ->
            k.addView(par("Ganho por km (R$/km)", "km_vermelho", r.kmVermelho, "km_verde", r.kmVerde))
            k.addView(par("Ganho por hora (R$/hora)", "hora_vermelho", r.horaVermelho, "hora_verde", r.horaVerde))
            k.addView(par("Nota do passageiro", "nota_vermelho", r.notaVermelho, "nota_verde", r.notaVerde))
            k.addView(dica("O R$/km e o R$/hora contam o caminho até o passageiro mais a corrida. Quando o app não mostra o destino (Maxim às vezes mostra \"0 km\"), o cartão fica amarelo, a não ser que só o caminho até o passageiro já fique abaixo do vermelho (aí é vermelho). A nota só existe quando o app mostra (Easy)."))
        })

        // limites
        corpo.addView(cartao(
            "Limites",
            "Passou de um limite: vermelho. Deixe 0 para não usar."
        ).also { k ->
            k.addView(campo("Valor mínimo da corrida (R$)", "valor_minimo", r.valorMinimo))
            k.addView(campo("Lucro mínimo (R$)", "lucro_minimo", r.lucroMinimo))
            k.addView(campo("Lucro mínimo (% do valor)", "lucro_minimo_pct", r.lucroMinimoPct))
            k.addView(campo("Passageiro a no máximo (km)", "embarque_max_km", r.embarqueMaxKm))
            k.addView(campo("Chegar ao passageiro em no máximo (min)", "embarque_max_min", r.embarqueMaxMin))
            k.addView(campo("Corrida de no máximo (km)", "viagem_max_km", r.viagemMaxKm))
            k.addView(campo("Corrida de no máximo (min)", "viagem_max_min", r.viagemMaxMin))
            k.addView(campo("Velocidade média na cidade (km/h)", "velocidade_media", r.velocidadeMedia))
            k.addView(dica("A velocidade média só serve para estimar o tempo quando o app mostra os km mas não os minutos. No cartão, tempo estimado aparece com \"~\"."))
        })

        // comissão
        corpo.addView(cartao(
            "Comissão do app",
            "Se o valor da oferta ainda vai ter desconto do app, coloque a porcentagem. Se o app já mostra quanto você ganha, deixe 0."
        ).also { k ->
            Ajustes.appsLidos.forEach { app -> k.addView(campo("$app (%)", "comissao_$app", r.comissao[app] ?: 0.0)) }
            k.addView(dica("A Easy mostra o valor que o motorista ganha. Na Maxim, confira no seu extrato se a taxa sai depois."))
        })

        // custo do carro
        corpo.addView(cartao(
            "Custo do carro",
            "Tudo vira um custo por km rodado. O semáforo tira esse custo do valor da corrida para mostrar o lucro."
        ).also { k ->
            k.addView(campo("Combustível (R$ por litro)", "combustivel_litro", c.combustivelLitro))
            k.addView(campo("Consumo (km por litro)", "km_por_litro", c.kmPorLitro))
            k.addView(campo("Km rodados por mês", "km_por_mes", c.kmPorMes))
            k.addView(campo("Parcela ou aluguel do carro (R$ por mês)", "parcela_mes", c.parcelaMes))
            k.addView(campo("Seguro (R$ por mês)", "seguro_mes", c.seguroMes))
            k.addView(campo("IPVA e licenciamento (R$ por ano)", "ipva_ano", c.ipvaAno))
            k.addView(campo("Manutenção: óleo, pneus, revisão (R$ por mês)", "manutencao_mes", c.manutencaoMes))
            k.addView(campo("Desvalorização do carro (R$ por mês)", "depreciacao_mes", c.depreciacaoMes))
            k.addView(campo("Outros: lavagem, celular, internet (R$ por mês)", "outros_mes", c.outrosMes))
            resultadoCusto = TextView(this).apply {
                setTextColor(tinta); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { setColor(fundo); cornerRadius = dp(8).toFloat() }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            }
            k.addView(resultadoCusto)
        })
        mostrarCusto()

        corpo.addView(botao("Salvar", true) { salvar(); aviso("Salvo") })
        corpo.addView(botao("Voltar aos valores de fábrica", false) { padrao() })

        raiz.addView(ScrollView(this).apply { addView(corpo) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(raiz)
    }

    override fun onPause() {
        super.onPause()
        salvar()   // nada do que foi digitado se perde ao sair da tela
    }

    // ---------- ler e salvar ----------

    private fun numero(chave: String): Double =
        campos[chave]?.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    private fun regrasDaTela() = Regras(
        kmVermelho = numero("km_vermelho"),
        kmVerde = numero("km_verde"),
        horaVermelho = numero("hora_vermelho"),
        horaVerde = numero("hora_verde"),
        notaVermelho = numero("nota_vermelho"),
        notaVerde = numero("nota_verde"),
        valorMinimo = numero("valor_minimo"),
        lucroMinimo = numero("lucro_minimo"),
        lucroMinimoPct = numero("lucro_minimo_pct"),
        embarqueMaxKm = numero("embarque_max_km"),
        embarqueMaxMin = numero("embarque_max_min"),
        viagemMaxKm = numero("viagem_max_km"),
        viagemMaxMin = numero("viagem_max_min"),
        velocidadeMedia = numero("velocidade_media"),
        comissao = Ajustes.appsLidos.associateWith { numero("comissao_$it").coerceAtMost(100.0) }
    )

    private fun custoDaTela() = Custo(
        combustivelLitro = numero("combustivel_litro"),
        kmPorLitro = numero("km_por_litro"),
        kmPorMes = numero("km_por_mes"),
        parcelaMes = numero("parcela_mes"),
        seguroMes = numero("seguro_mes"),
        ipvaAno = numero("ipva_ano"),
        manutencaoMes = numero("manutencao_mes"),
        depreciacaoMes = numero("depreciacao_mes"),
        outrosMes = numero("outros_mes")
    )

    private fun salvar() {
        Ajustes.salvarRegras(this, regrasDaTela())
        Ajustes.salvarCusto(this, custoDaTela())
        salvarSemaforo()
    }

    private fun salvarSemaforo() {
        Ajustes.salvarSemaforo(this, ligado.isChecked, posicaoEmCima)
        LeitorService.instancia?.recarregarSemaforo()
    }

    private fun padrao() {
        val r = Regras()
        val c = Custo()
        mapOf(
            "km_vermelho" to r.kmVermelho, "km_verde" to r.kmVerde,
            "hora_vermelho" to r.horaVermelho, "hora_verde" to r.horaVerde,
            "nota_vermelho" to r.notaVermelho, "nota_verde" to r.notaVerde,
            "valor_minimo" to r.valorMinimo, "lucro_minimo" to r.lucroMinimo, "lucro_minimo_pct" to r.lucroMinimoPct,
            "embarque_max_km" to r.embarqueMaxKm, "embarque_max_min" to r.embarqueMaxMin,
            "viagem_max_km" to r.viagemMaxKm, "viagem_max_min" to r.viagemMaxMin,
            "velocidade_media" to r.velocidadeMedia,
            "combustivel_litro" to c.combustivelLitro, "km_por_litro" to c.kmPorLitro, "km_por_mes" to c.kmPorMes,
            "parcela_mes" to c.parcelaMes, "seguro_mes" to c.seguroMes, "ipva_ano" to c.ipvaAno,
            "manutencao_mes" to c.manutencaoMes, "depreciacao_mes" to c.depreciacaoMes, "outros_mes" to c.outrosMes
        ).forEach { (chave, v) -> campos[chave]?.setText(formatar(v)) }
        Ajustes.appsLidos.forEach { campos["comissao_$it"]?.setText(formatar(0.0)) }
        salvar()
        aviso("Valores de fábrica de volta")
    }

    private fun testar() {
        salvar()
        val leitor = LeitorService.instancia
        if (leitor == null) {
            aviso("Ligue o leitor primeiro (passo 1 da tela inicial)")
            return
        }
        leitor.testarSemaforo()
        aviso("Olhe o cartão por cima da tela")
    }

    private fun mostrarCusto() {
        val c = custoDaTela()
        resultadoCusto.text = String.format(
            brasil,
            "Combustível R$\u00A0%.2f/km + gastos fixos R$\u00A0%.2f/km\n= custo do carro R$\u00A0%.2f por km",
            c.combustivelKm(), c.fixosKm(), c.porKm()
        )
    }

    // ---------- peças da tela ----------

    private fun formatar(v: Double): String {
        val t = String.format(brasil, "%.2f", v)
        return if (t.endsWith(",00")) t.dropLast(3) else t
    }

    private fun caixa(chave: String, valor: Double) = EditText(this).apply {
        setText(formatar(valor))
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        keyListener = DigitsKeyListener.getInstance("0123456789,.")
        setSingleLine()
        setTextColor(tinta); textSize = 16f; gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(6).toFloat(); setStroke(dp(1), linha) }
        setSelectAllOnFocus(true)
        if (chave in setOf("combustivel_litro", "km_por_litro", "km_por_mes", "parcela_mes", "seguro_mes",
                "ipva_ano", "manutencao_mes", "depreciacao_mes", "outros_mes")) {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (::resultadoCusto.isInitialized) mostrarCusto() }
            })
        }
        campos[chave] = this
    }

    /** Uma linha: nome à esquerda, número à direita. */
    private fun campo(nome: String, chave: String, valor: Double) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, 0)
        addView(TextView(context).apply { text = nome; setTextColor(tinta); textSize = 14f },
            LinearLayout.LayoutParams(0, -2, 1f))
        addView(caixa(chave, valor), LinearLayout.LayoutParams(dp(96), dp(44)).apply { leftMargin = dp(10) })
    }

    /** Uma faixa: nome em cima, e embaixo "vermelho abaixo de" e "verde a partir de". */
    private fun par(nome: String, chaveVermelho: String, vermelhoV: Double, chaveVerde: String, verdeV: Double) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            addView(TextView(context).apply { text = nome; setTextColor(tinta); textSize = 15f; typeface = Typeface.DEFAULT_BOLD })
            listOf(
                Triple("vermelho abaixo de", vermelho, chaveVermelho to vermelhoV),
                Triple("verde a partir de", verde, chaveVerde to verdeV)
            ).forEach { (rotulo, cor, campo) ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, 0)
                    addView(etiqueta(rotulo, cor), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(caixa(campo.first, campo.second), LinearLayout.LayoutParams(dp(96), dp(44)).apply { leftMargin = dp(10) })
                })
            }
        }

    private fun etiqueta(t: String, cor: Int) = TextView(this).apply {
        text = "●  $t"; setTextColor(cor); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
    }

    private fun pintarLigado() {
        val on = ligado.isChecked
        estadoLigado.text = if (on) "Ligado" else "Desligado"
        estadoLigado.setTextColor(if (on) verde else vermelho)
    }

    private fun pintarPosicao() {
        listOf(emCima to posicaoEmCima, embaixo to !posicaoEmCima).forEach { (b, escolhido) ->
            b.setTextColor(if (escolhido) Color.WHITE else tinta)
            b.background = GradientDrawable().apply { setColor(if (escolhido) asfalto else fundo); cornerRadius = dp(8).toFloat() }
        }
    }

    private fun cartao(titulo: String, texto: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(10).toFloat(); setStroke(dp(1), linha) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        addView(TextView(context).apply { text = titulo; setTextColor(tinta); textSize = 17f; typeface = Typeface.DEFAULT_BOLD })
        addView(TextView(context).apply { text = texto; setTextColor(cinza); textSize = 14f; setPadding(0, dp(6), 0, dp(4)) })
    }

    private fun botao(rotulo: String, principal: Boolean, acao: () -> Unit) = Button(this).apply {
        text = rotulo; isAllCaps = false; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (principal) Color.WHITE else tinta)
        background = GradientDrawable().apply { setColor(if (principal) asfalto else fundo); cornerRadius = dp(8).toFloat() }
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) }
        setOnClickListener { acao() }
    }

    private fun dica(t: String) = TextView(this).apply {
        text = t; setTextColor(cinza); textSize = 13f; setPadding(0, dp(10), 0, 0)
    }

    private fun aviso(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
