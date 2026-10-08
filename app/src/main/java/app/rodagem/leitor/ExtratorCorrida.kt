package app.rodagem.leitor

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** Onde as corridas ficam guardadas (no app: BancoCorridas). */
interface Deposito {

    /** Corridas do app lidas a partir de [desde], da mais nova para a mais antiga. */
    fun recentes(aplicativo: String, desde: Long): List<Corrida>

    fun inserir(c: Corrida): Long

    fun atualizar(c: Corrida)
}

/**
 * Transforma as telas lidas em corridas. Cada app tem as suas regras, pelos IDs reais
 * dos elementos da tela, mapeados nos registros enviados. Só lê: nunca toca em nada.
 *
 * Mapeados: Maxim (Taxsee Driver) e Easy (EasyMob Motorista).
 * Ainda não: Uber e 99 (faltam telas de oferta deles nos registros).
 */
object ExtratorCorrida {

    /** O que uma tela diz sobre uma corrida. */
    data class Leitura(
        val corrida: Corrida,
        /** false: só atualiza uma corrida que já existe (histórico, aviso de fim). */
        val podeCriar: Boolean = true,
        /** Aviso sem endereço nem número: vale para a última corrida em andamento do app. */
        val ultimaAtiva: Boolean = false,
        /** Histórico: só vale para corridas lidas até esta hora. */
        val ateData: Long = 0L,
        /** Oferta aberta na tela, esperando o motorista decidir: é ela que vai para o semáforo. */
        val destaque: Boolean = false
    )

    private const val MAXIM = "com.taxsee.driver"
    private const val EASY = "br.com.easymob.taxi.drivermachine"

    private const val HORA = 60 * 60 * 1000L
    private const val JANELA = 3 * HORA   // até quanto tempo depois uma tela ainda conta como a mesma corrida

    private var ultimaAssinatura = ""


    // ---------- entrada pelo app ----------

    /** Chamado pelo LeitorService a cada tela nova. */
    @Synchronized
    fun analisar(ctx: Context, dump: String) {
        processar(BancoCorridas.de(ctx), dump, System.currentTimeMillis())
    }

    /** Apaga as corridas e lê de novo todas as telas guardadas no registro. Devolve quantas corridas ficaram. */
    @Synchronized
    fun reler(ctx: Context): Int {
        val banco = BancoCorridas.de(ctx)
        banco.apagarTudo()
        ultimaAssinatura = ""
        val arquivo = Registro.arquivo(ctx)
        if (arquivo.exists()) {
            for ((hora, dump) in telasDoRegistro(arquivo.readText())) processar(banco, dump, hora)
        }
        ultimaAssinatura = ""
        return banco.total()
    }

    /** Lê uma tela e grava o que ela disser sobre corridas. [agora] é a hora da tela. */
    fun processar(dep: Deposito, dump: String, agora: Long) {
        val leituras = extrair(dump, agora)
        if (leituras.isEmpty()) return
        // as ofertas têm contagem regressiva: a tela muda, mas a corrida lida é a mesma
        val assinatura = leituras.joinToString("\n") {
            it.copy(corrida = it.corrida.copy(dataHora = 0, atualizado = 0)).toString()
        }
        if (assinatura == ultimaAssinatura) return
        ultimaAssinatura = assinatura
        leituras.forEach { aplicar(dep, it, agora) }
    }

    /** Separa o registro em telas, cada uma com a sua hora. */
    fun telasDoRegistro(registro: String): List<Pair<Long, String>> {
        val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
        val telas = mutableListOf<Pair<Long, String>>()
        var hora = -1L
        val corpo = StringBuilder()

        fun fechar() {
            if (hora >= 0) telas.add(hora to corpo.toString())
            hora = -1L
            corpo.setLength(0)
        }

        for (l in registro.lineSequence()) {
            if (l.startsWith("===== ")) {
                fechar()
                val partes = l.removePrefix("===== ").split(" | ", limit = 2)
                if (partes.size == 2 && partes[1].trim() == "TELA") {
                    hora = try {
                        formato.parse(partes[0].trim())?.time ?: -1L
                    } catch (e: Exception) {
                        -1L
                    }
                }
                continue
            }
            if (hora >= 0) corpo.append(l).append('\n')
        }
        fechar()
        return telas
    }


    // ---------- leitura das telas ----------

    /** A oferta aberta na tela agora (para o semáforo), ou null se não houver nenhuma. */
    fun destaque(dump: String, agora: Long): Corrida? =
        extrair(dump, agora).firstOrNull { it.destaque }?.corrida

    fun extrair(dump: String, agora: Long): List<Leitura> {
        val leituras = mutableListOf<Leitura>()
        for (janela in Tela.ler(dump).janelas) {
            when (janela.pacote) {
                MAXIM -> leituras += maxim(janela, agora)
                EASY -> leituras += easy(janela, agora)
            }
        }
        return leituras
    }

    /**
     * Maxim (Taxsee Driver).
     * - Oferta: "Pedido atribuído!" (Aceitar/Recusar) ou pedido aberto da lista (botão "Solicitar").
     * - Em andamento: título "A caminho (R$ saldo)" e botão "Cheguei no local".
     *   Atenção: o R$ do título é o saldo do motorista, não o preço; o preço é o tv_price.
     * - Listas: cartões de pedido no mapa e nas categorias; em "Meus pedidos" vêm
     *   com "Pedido concluído / cancelado / data", que fecha a corrida.
     */
    private fun maxim(j: Tela.Janela, agora: Long): List<Leitura> {
        val app = "Maxim"
        val emAndamento = j.tem("active_order_nav_host") || j.tem("btn_main_action")
        val oferta = j.tem("tvAssignTitle") || j.tem("request_order")

        if (emAndamento || oferta) {
            val enderecos = j.textos("tv_main_address").map { endereco(it) }.filter { it.isNotEmpty() }
            val valor = dinheiro(j.texto("tv_price"))
            if (enderecos.isEmpty() || valor <= 0) return emptyList()

            var km = 0.0
            var min = 0
            var embarqueKm = 0.0
            var embarqueMin = 0
            for ((titulo, legenda) in paresGeo(j)) {
                val ateOPassageiro = legenda.contains("embarque", true) || legenda.contains("até o endereço", true)
                if (ateOPassageiro) {
                    if (km(titulo) > 0) embarqueKm = km(titulo)
                    if (minutos(titulo) > 0) embarqueMin = minutos(titulo)
                } else {
                    if (km(titulo) > 0) km = km(titulo)
                    if (minutos(titulo) > 0) min = minutos(titulo)
                }
            }

            val comentario = j.busca("v_comments_container").firstOrNull()?.texto("tv_text") ?: ""

            return listOf(
                Leitura(
                    Corrida(
                        aplicativo = app,
                        valor = valor,
                        pagamento = pagamento(j.texto("tv_first_method")),
                        origem = enderecos.first(),
                        destino = if (enderecos.size > 1) enderecos.last() else "",
                        distanciaKm = km,
                        tempoMinutos = min,
                        embarqueKm = embarqueKm,
                        embarqueMinutos = embarqueMin,
                        cliente = cliente(comentario),
                        categoria = j.texto("tv_tariff"),
                        status = if (emAndamento) statusMaxim(j.texto("title")) else Status.OFERTA,
                        dataHora = agora,
                        atualizado = agora
                    ),
                    destaque = !emAndamento
                )
            )
        }

        val leituras = mutableListOf<Leitura>()
        for (cartao in j.nos.filter { it.id == "order_card" || it.id == "cardContent" }) {
            val valor = dinheiro(cartao.texto("tv_price"))
            val origem = endereco(cartao.texto("tv_title_start_address"))
            if (valor <= 0 || origem.isEmpty()) continue
            val base = Corrida(
                aplicativo = app,
                valor = valor,
                origem = origem,
                destino = endereco(cartao.texto("tv_title_final_address")),
                // "Maxim Brazil, Econômica"
                categoria = cartao.texto("tv_organization").substringAfter(",", "").trim(),
                dataHora = agora,
                atualizado = agora
            )
            val fim = cartao.texto("tv_complete_date")
            if (fim.isEmpty()) {
                leituras += Leitura(base)
                continue
            }
            val quando = dataMaxim(fim) ?: continue
            val status = when {
                fim.contains("cancel", true) -> Status.CANCELADA
                fim.contains("conclu", true) -> Status.CONCLUIDA
                else -> continue
            }
            leituras += Leitura(base.copy(status = status), podeCriar = false, ateData = quando + 60_000)
        }
        return leituras
    }

    /** Pares (valor, legenda) do quadro de distâncias: "4.4 km." / "Até o endereço", "0 km." / "Distância da rota"... */
    private fun paresGeo(j: Tela.Janela): List<Pair<String, String>> {
        val quadro = j.busca("v_geo_info_inner_container").firstOrNull() ?: return emptyList()
        val pares = mutableListOf<Pair<String, String>>()
        var titulo: String? = null
        for (n in quadro.todos()) {
            if (n.id == "tv_title") {
                titulo = n.texto
            } else if (n.id == "tv_subtitle" && titulo != null) {
                pares += titulo to n.texto
                titulo = null
            }
        }
        return pares
    }

    private fun statusMaxim(titulo: String): String {
        val t = titulo.substringBefore("(").trim().lowercase()
        return when {
            // confirmado no registro: "A caminho" (indo buscar o passageiro)
            t.startsWith("a caminho") && !t.contains("destino") -> Status.ACEITA
            // ainda não apareceram nos registros; ficam aqui para quando aparecerem
            t.contains("aguard") || t.contains("no local") -> Status.NO_LOCAL
            t.contains("viagem") || t.contains("em curso") || t.contains("a bordo") || t.contains("destino") -> Status.EM_CORRIDA
            else -> Status.ACEITA
        }
    }

    private val meses = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
    )
    private val reDataMaxim = Regex("""(\d{1,2})\s+(\p{L}+)\s+(\d{4}),?\s+(\d{1,2}):(\d{2})""")

    /** "Pedido concluído / 6 outubro 2026, 17:27" */
    private fun dataMaxim(s: String): Long? {
        val m = reDataMaxim.find(s) ?: return null
        val mes = meses.indexOf(m.groupValues[2].lowercase())
        if (mes < 0) return null
        return Calendar.getInstance().apply {
            clear()
            set(m.groupValues[3].toInt(), mes, m.groupValues[1].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt())
        }.timeInMillis
    }

    /**
     * Easy (EasyMob Motorista).
     * - Oferta: janela por cima de tudo com o valor que o motorista ganha (txtValorGanho),
     *   o trecho "Embarque - (543m)" e "Destino - (2,63 km)" + "6 min", e o número da OS.
     * - Em andamento: cabeçalho "Vá até <passageiro>" / "Aguarde o passageiro" / "Vá até o destino",
     *   com "OS: número", total cobrado, pagamento e nome do passageiro.
     * - Fim: aviso "Você ganhou +R$ ...".
     */
    private fun easy(j: Tela.Janela, agora: Long): List<Leitura> {
        val app = "Easy"

        if (j.tem("txtValorGanho")) {
            val valor = dinheiro(j.texto("txtValorGanho"))
            val trechos = j.busca("clRota")
            if (valor <= 0 || trechos.isEmpty()) return emptyList()
            var c = Corrida(
                aplicativo = app,
                valor = valor,
                nota = j.texto("txtAvaliacaoMedia").replace(',', '.').toDoubleOrNull() ?: 0.0,
                categoria = j.texto("txtTag"),
                codigo = numero(j.texto("txtOS")),
                dataHora = agora,
                atualizado = agora
            )
            for (t in trechos) {
                val titulo = t.texto("txtTitulo")
                val info = listOf(titulo, t.texto("txtDistancia"), t.texto("txtTempo")).joinToString(" ")
                val lugar = endereco(t.texto("txtEndereco"))
                if (titulo.startsWith("Embarque", true)) {
                    c = c.copy(origem = lugar, embarqueKm = km(info), embarqueMinutos = minutos(info))
                } else if (titulo.startsWith("Destino", true)) {
                    c = c.copy(destino = lugar, distanciaKm = km(info), tempoMinutos = minutos(info))
                }
            }
            return listOf(Leitura(c, destaque = true))
        }

        val os = numero(j.texto("txtNumOS"))
        if (os.isNotEmpty()) {
            val cabecalho = j.texto("txtHeader").lowercase()
            val status = when {
                cabecalho.startsWith("vá até o destino") -> Status.EM_CORRIDA
                cabecalho.startsWith("aguarde") -> Status.NO_LOCAL
                else -> Status.ACEITA   // "Vá até <passageiro>"
            }
            // o endereço do topo é o do próximo ponto: embarque antes, destino depois
            val lugar = endereco(j.texto("txtEndereco"))
            val cobrado = dinheiro(j.texto("txtTotalValor")).takeIf { it > 0 } ?: dinheiro(j.texto("txtValorTotalValue"))
            return listOf(
                Leitura(
                    Corrida(
                        aplicativo = app,
                        valorCobrado = cobrado,
                        pagamento = pagamento(j.texto("txtPagamento")),
                        origem = if (status == Status.EM_CORRIDA) "" else lugar,
                        destino = if (status == Status.EM_CORRIDA) lugar else "",
                        distanciaKm = km(j.texto("txtDistanciaPercurso")),
                        cliente = j.texto("txtNomePassageiroCLiente"),
                        categoria = j.texto("txtCategoria"),
                        codigo = os,
                        status = status,
                        dataHora = agora,
                        atualizado = agora
                    )
                )
            )
        }

        if (j.texto("txtNotificationTitle").contains("ganhou", true)) {
            val ganho = dinheiro(j.texto("txtNotificationDescription"))
            return listOf(
                Leitura(
                    Corrida(aplicativo = app, valor = ganho, status = Status.CONCLUIDA, dataHora = agora, atualizado = agora),
                    podeCriar = false,
                    ultimaAtiva = true
                )
            )
        }

        return emptyList()
    }


    // ---------- juntar com o que já foi lido ----------

    fun aplicar(dep: Deposito, l: Leitura, agora: Long) {
        val c = l.corrida
        val existente = procurar(dep, l, agora)
        if (existente == null) {
            if (l.podeCriar && valida(c)) dep.inserir(c)
            return
        }
        val nova = mesclar(existente, c, agora)
        if (nova != existente) dep.atualizar(nova)
    }

    private fun procurar(dep: Deposito, l: Leitura, agora: Long): Corrida? {
        val c = l.corrida

        if (l.ateData > 0) {
            return dep.recentes(c.aplicativo, l.ateData - 6 * HORA)
                .firstOrNull { it.dataHora <= l.ateData && mesmaViagem(it, c) }
        }

        val recentes = dep.recentes(c.aplicativo, agora - 12 * HORA)

        if (l.ultimaAtiva) {
            return recentes.firstOrNull { !Status.final(it.status) && it.atualizado >= agora - JANELA }
        }

        if (c.codigo.isNotEmpty()) {
            recentes.firstOrNull { it.codigo == c.codigo }?.let { return it }
        }

        return recentes.firstOrNull {
            it.dataHora >= agora - JANELA &&
                !Status.final(it.status) &&
                (it.codigo.isEmpty() || c.codigo.isEmpty()) &&
                mesmaViagem(it, c)
        }
    }

    private fun mesmaViagem(a: Corrida, b: Corrida): Boolean {
        if (a.origem.isEmpty() || b.origem.isEmpty() || !a.origem.equals(b.origem, true)) return false
        if (a.destino.isNotEmpty() && b.destino.isNotEmpty() && !a.destino.equals(b.destino, true)) return false
        if (a.valor > 0 && b.valor > 0 && abs(a.valor - b.valor) > 0.009) return false
        return true
    }

    /** O que já se sabia fica; o que faltava é preenchido; o status só avança. */
    private fun mesclar(a: Corrida, b: Corrida, agora: Long): Corrida {
        val m = a.copy(
            // o valor do fechamento ("Você ganhou", histórico) vale mais que o da oferta
            valor = if (Status.final(b.status) && b.valor > 0 && !Status.final(a.status)) b.valor
                    else if (a.valor > 0) a.valor else b.valor,
            valorCobrado = if (a.valorCobrado > 0) a.valorCobrado else b.valorCobrado,
            pagamento = a.pagamento.ifEmpty { b.pagamento },
            origem = a.origem.ifEmpty { b.origem },
            destino = a.destino.ifEmpty { b.destino },
            distanciaKm = if (a.distanciaKm > 0) a.distanciaKm else b.distanciaKm,
            tempoMinutos = if (a.tempoMinutos > 0) a.tempoMinutos else b.tempoMinutos,
            embarqueKm = if (a.embarqueKm > 0) a.embarqueKm else b.embarqueKm,
            embarqueMinutos = if (a.embarqueMinutos > 0) a.embarqueMinutos else b.embarqueMinutos,
            cliente = a.cliente.ifEmpty { b.cliente },
            nota = if (a.nota > 0) a.nota else b.nota,
            categoria = a.categoria.ifEmpty { b.categoria },
            codigo = a.codigo.ifEmpty { b.codigo },
            status = Status.avancar(a.status, b.status)
        )
        return if (m == a) a else m.copy(atualizado = agora)
    }

    private fun valida(c: Corrida) =
        (c.valor > 0 || c.valorCobrado > 0 || c.codigo.isNotEmpty()) &&
            (c.origem.isNotEmpty() || c.codigo.isNotEmpty())


    // ---------- textos ----------

    private val reDinheiro = Regex("""R\$[\s ]*(\d{1,3}(?:\.\d{3})+,\d{2}|\d+(?:[.,]\d{1,2})?)""")
    private val reKm = Regex("""(\d+(?:[.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE)
    private val reMetros = Regex("""(\d+(?:[.,]\d+)?)\s*m\b""")
    private val reMinutos = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)
    private val reCliente = Regex("""^cliente\s*:?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val reEspacos = Regex("""\s+""")

    /** "R$ 17,00", "+R$ 8,75", "R$ 1.234,56" → número. */
    fun dinheiro(s: String): Double {
        val v = reDinheiro.find(s)?.groupValues?.get(1) ?: return 0.0
        val n = if (v.contains(',')) v.replace(".", "").replace(',', '.') else v
        return n.toDoubleOrNull() ?: 0.0
    }

    /** "4.4 km.", "2,63 km", "(543m)" → km. */
    fun km(s: String): Double {
        reKm.find(s)?.let { return it.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0 }
        reMetros.find(s)?.let { return (it.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0) / 1000 }
        return 0.0
    }

    /** "11 min.", "6 min" → minutos. */
    fun minutos(s: String) = reMinutos.find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun pagamento(s: String): String {
        val t = s.lowercase()
        return when {
            t.isBlank() -> ""
            t.contains("dinheiro") -> "Dinheiro"
            t.contains("pix") -> "Pix"
            t.contains("cartão") || t.contains("cartao") || t.contains("crédito") || t.contains("débito") -> "Cartão"
            t.contains("saldo") || t.contains("carteira") -> "Saldo"
            else -> s.trim()
        }
    }

    /** Tira os emojis 🏠 🏁 e os espaços duplicados, para a mesma rua sempre bater. */
    fun endereco(s: String) = s.replace("🏠", "").replace("🏁", "").replace(reEspacos, " ").trim()

    /** Comentário da Maxim "Cliente juliana" → "juliana". Outros comentários não viram nome. */
    private fun cliente(comentario: String) = reCliente.find(comentario.trim())?.groupValues?.get(1)?.trim() ?: ""

    private fun numero(s: String) = s.filter { it.isDigit() }
}
