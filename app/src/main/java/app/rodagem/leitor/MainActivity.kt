package app.rodagem.leitor

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider

class MainActivity : Activity() {

    // mesmas cores do Rodagem
    private val asfalto = Color.parseColor("#26303A")
    private val fundo = Color.parseColor("#ECEFF2")
    private val tinta = Color.parseColor("#1F262D")
    private val cinza = Color.parseColor("#66717D")
    private val linha = Color.parseColor("#D8DEE4")
    private val sinal = Color.parseColor("#F2B705")
    private val verde = Color.parseColor("#1F8A50")
    private val vermelho = Color.parseColor("#C83E3E")

    private lateinit var statusLeitor: TextView
    private lateinit var statusBateria: TextView
    private lateinit var listaCorrida: LinearLayout
    private lateinit var listaOutros: LinearLayout
    private lateinit var botaoOutros: Button
    private var mostrandoOutros = false
    private lateinit var textoRegistro: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = asfalto

        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(fundo) }

        // topo
        raiz.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(asfalto)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            addView(TextView(context).apply {
                text = "Rodagem Leitor"; setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = "Versão de diagnóstico ${versao()}"; setTextColor(Color.parseColor("#C9D1D8")); textSize = 14f
            })
        })
        raiz.addView(View(this).apply { setBackgroundColor(sinal) }, LinearLayout.LayoutParams(-1, dp(6)))

        val corpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }

        // 1. ligar o leitor
        corpo.addView(cartao(
            "1. Ligar o leitor",
            "Abra Acessibilidade, entre em \"Rodagem Leitor\" (pode estar em \"Apps instalados\" ou \"Serviços baixados\") e ligue.",
        ).also { c ->
            statusLeitor = selo(); c.addView(statusLeitor, 1)
            c.addView(botao("Abrir Acessibilidade", true) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            c.addView(dica("Se aparecer \"Configuração restrita\": toque no botão abaixo, depois nos 3 pontinhos ⋮ no canto de cima e em \"Permitir configurações restritas\". Volte e ligue de novo."))
            c.addView(botao("Abrir informações do app", false) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
        })

        // 2. bateria
        corpo.addView(cartao(
            "2. Não deixar o Android desligar",
            "Sem isso, o celular pode desligar o leitor para economizar bateria.",
        ).also { c ->
            statusBateria = selo(); c.addView(statusBateria, 1)
            c.addView(botao("Liberar uso de bateria", false) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            })
        })

        // 3. apps que o leitor lê
        corpo.addView(cartao(
            "3. Apps que o leitor lê",
            "Ligue só os apps de corrida que você usa. Os desligados ficam totalmente de fora.",
        ).also { c ->
            listaCorrida = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(listaCorrida)
            botaoOutros = botao("Mostrar outros apps instalados", false) {
                mostrandoOutros = !mostrandoOutros
                atualizar()
            }
            c.addView(botaoOutros)
            listaOutros = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(listaOutros)
        })

        // 4. coletar e enviar
        corpo.addView(cartao(
            "4. Coletar e enviar",
            "Fique online no Uber, na 99 e no inDrive e deixe aparecer algumas ofertas de corrida (não precisa aceitar). Quanto mais ofertas, melhor: o ideal são umas 10 de cada app. Depois toque em Enviar registro e mande o arquivo para a Claude.",
        ).also { c ->
            textoRegistro = TextView(this).apply { setTextColor(tinta); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(4), 0, dp(4)) }
            c.addView(textoRegistro)
            c.addView(botao("Enviar registro", true) { enviar() })
            c.addView(botao("Limpar registro", false) {
                Registro.limpar(this); atualizar(); aviso("Registro apagado")
            })
            c.addView(dica("O registro inclui os endereços que aparecem nas ofertas. Ele fica só neste aparelho até você enviar."))
        })

        raiz.addView(ScrollView(this).apply { addView(corpo) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(raiz)
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun atualizar() {
        val ligado = leitorLigado()
        pintarSelo(statusLeitor, ligado, "Leitor ligado", "Leitor desligado")
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        pintarSelo(statusBateria, pm.isIgnoringBatteryOptimizations(packageName), "Liberado", "Ainda não liberado")

        val apps = Apps.instalados(this)
        val ativos = Apps.ativos(this)
        listaCorrida.removeAllViews(); listaOutros.removeAllViews()
        val corrida = apps.filter { it.corrida }
        if (corrida.isEmpty()) listaCorrida.addView(dica("Nenhum app de corrida conhecido foi encontrado. Use \"Mostrar outros apps\" para ligar o seu."))
        corrida.forEach { listaCorrida.addView(linhaApp(it, it.pacote in ativos)) }
        botaoOutros.text = if (mostrandoOutros) "Esconder outros apps" else "Mostrar outros apps instalados"
        listaOutros.visibility = if (mostrandoOutros) View.VISIBLE else View.GONE
        if (mostrandoOutros) apps.filter { !it.corrida }.forEach { listaOutros.addView(linhaApp(it, it.pacote in ativos)) }

        val total = Registro.total(this)
        textoRegistro.text = if (total == 0) "Nada registrado ainda."
                             else "$total registros. Último: ${Registro.ultimo(this)}"
    }

    private fun leitorLigado(): Boolean {
        val alvo = ComponentName(this, LeitorService::class.java).flattenToString()
        val lista = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val partes = TextUtils.SimpleStringSplitter(':').apply { setString(lista) }
        while (partes.hasNext()) if (partes.next().equals(alvo, ignoreCase = true)) return true
        return false
    }

    private fun enviar() {
        val f = Registro.arquivo(this)
        if (!f.exists() || f.length() == 0L) { aviso("Ainda não há nada para enviar"); return }
        val uri = FileProvider.getUriForFile(this, "app.rodagem.leitor.arquivos", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Registro do Rodagem Leitor")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Enviar registro"))
    }

    // ---------- peças da tela ----------
    private fun cartao(titulo: String, texto: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(10).toFloat(); setStroke(dp(1), linha) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        addView(TextView(context).apply { text = titulo; setTextColor(tinta); textSize = 17f; typeface = Typeface.DEFAULT_BOLD })
        addView(TextView(context).apply { text = texto; setTextColor(cinza); textSize = 14f; setPadding(0, dp(6), 0, dp(4)) })
    }

    /** Uma linha da lista: nome do app e toggle verde (ligado) ou vermelho (desligado). */
    private fun linhaApp(app: Apps.App, ligado: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = app.nome; setTextColor(tinta); textSize = 15f; typeface = Typeface.DEFAULT_BOLD })
            addView(TextView(context).apply { text = app.pacote; setTextColor(cinza); textSize = 12f })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val estado = TextView(context).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(8), 0, dp(4), 0) }
        fun pintar(on: Boolean) { estado.text = if (on) "Ligado" else "Desligado"; estado.setTextColor(if (on) verde else vermelho) }
        pintar(ligado)
        addView(estado)
        addView(Switch(context).apply {
            isChecked = ligado
            val estados = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            trackTintList = ColorStateList(estados, intArrayOf(verde, vermelho))
            thumbTintList = ColorStateList(estados, intArrayOf(Color.WHITE, Color.WHITE))
            setOnCheckedChangeListener { _, on ->
                Apps.definir(this@MainActivity, app.pacote, on)
                LeitorService.instancia?.recarregarApps()
                pintar(on)
                aviso(if (on) "${app.nome} ligado" else "${app.nome} desligado")
            }
        })
    }

    private fun selo() = TextView(this).apply {
        setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(5), dp(12), dp(5))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8); bottomMargin = dp(4) }
    }

    private fun pintarSelo(t: TextView, ok: Boolean, sim: String, nao: String) {
        t.text = if (ok) "●  $sim" else "●  $nao"
        t.background = GradientDrawable().apply { setColor(if (ok) verde else vermelho); cornerRadius = dp(99).toFloat() }
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
        text = t; setTextColor(cinza); textSize = 13f; setPadding(0, dp(10), 0, 0); gravity = Gravity.START
    }

    private fun aviso(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun versao() = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
}
