package app.rodagem.leitor

import kotlin.math.max

/** As três cores do semáforo, da pior para a melhor. */
enum class Cor { VERMELHO, AMARELO, VERDE }


/**
 * Faixas e limites do semáforo. Nas faixas, abaixo de "vermelho" é ruim,
 * a partir de "verde" é bom e o meio é atenção. Limite 0 = desligado.
 */
data class Regras(
    val kmVermelho: Double = 1.50,
    val kmVerde: Double = 2.00,
    val horaVermelho: Double = 25.0,
    val horaVerde: Double = 35.0,
    val notaVermelho: Double = 4.50,
    val notaVerde: Double = 4.80,

    val valorMinimo: Double = 0.0,
    val lucroMinimo: Double = 0.0,
    val lucroMinimoPct: Double = 0.0,
    val embarqueMaxKm: Double = 5.0,
    val embarqueMaxMin: Double = 15.0,
    val viagemMaxKm: Double = 0.0,
    val viagemMaxMin: Double = 0.0,

    /** Para estimar o tempo quando o app só mostra os km. */
    val velocidadeMedia: Double = 25.0,

    /** Custo do carro por km (da calculadora). */
    val custoKm: Double = 0.0,

    /** Comissão que o app desconta depois do valor mostrado, em %, por app. */
    val comissao: Map<String, Double> = emptyMap()
)


/** Calculadora de custo do carro: tudo vira R$ por km rodado. */
data class Custo(
    val combustivelLitro: Double = 6.30,
    val kmPorLitro: Double = 10.0,
    val kmPorMes: Double = 3000.0,
    val parcelaMes: Double = 0.0,
    val seguroMes: Double = 0.0,
    val ipvaAno: Double = 0.0,
    val manutencaoMes: Double = 0.0,
    val depreciacaoMes: Double = 0.0,
    val outrosMes: Double = 0.0
) {
    fun combustivelKm() = if (kmPorLitro > 0) combustivelLitro / kmPorLitro else 0.0

    fun fixosMes() = parcelaMes + seguroMes + ipvaAno / 12 + manutencaoMes + depreciacaoMes + outrosMes

    fun fixosKm() = if (kmPorMes > 0) fixosMes() / kmPorMes else 0.0

    /** Abaixo deste valor por km, o motorista roda de graça. */
    fun porKm() = combustivelKm() + fixosKm()
}


/** O resultado do semáforo para uma oferta. Valores nulos: a tela não deu informação para calcular. */
data class Avaliacao(
    val cor: Cor,
    val valor: Double,
    val kmTotal: Double,
    val kmConhecido: Boolean,
    val minutos: Double,
    val minutosEstimados: Boolean,
    val porKm: Double?,
    val porHora: Double?,
    val custo: Double,
    val lucro: Double?,
    val lucroPct: Double?,
    /**
     * Sem o km da corrida (destino não informado), só se sabe o caminho até o passageiro.
     * O ganho de verdade só pode ser menor que estes "no máximo".
     */
    val porKmMax: Double? = null,
    val porHoraMax: Double? = null,
    val lucroMax: Double? = null,
    val nota: Double,
    /** Por que não ficou verde: primeiro o que deixou vermelho, depois o que deixou amarelo. */
    val motivos: List<String>
)


object Avaliador {

    fun avaliar(c: Corrida, r: Regras): Avaliacao {

        val bruto = if (c.valor > 0) c.valor else c.valorCobrado
        val comissao = (r.comissao[c.aplicativo] ?: 0.0).coerceIn(0.0, 100.0)
        val valor = bruto * (1 - comissao / 100)

        val kmConhecido = c.distanciaKm > 0
        val kmTotal = c.embarqueKm + c.distanciaKm

        var estimado = false
        fun minutos(min: Int, km: Double): Double = when {
            min > 0 -> min.toDouble()
            km > 0 && r.velocidadeMedia > 0 -> { estimado = true; km / r.velocidadeMedia * 60 }
            else -> 0.0
        }
        val minEmbarque = minutos(c.embarqueMinutos, c.embarqueKm)
        val minViagem = minutos(c.tempoMinutos, c.distanciaKm)
        val minTotal = minEmbarque + minViagem
        val viagemConhecida = kmConhecido || c.tempoMinutos > 0

        val porKm = if (kmConhecido && kmTotal > 0) valor / kmTotal else null
        val porHora = if (viagemConhecida && minTotal > 0) valor / (minTotal / 60) else null
        val custo = r.custoKm * kmTotal
        val lucro = if (kmConhecido) valor - custo else null
        val lucroPct = if (lucro != null && valor > 0) lucro / valor * 100 else null

        // sem a corrida, conta só o caminho até o passageiro: se nem assim compensa, já é vermelho
        val porKmMax = if (porKm == null && c.embarqueKm > 0) valor / c.embarqueKm else null
        val porHoraMax = if (porHora == null && minEmbarque > 0) valor / (minEmbarque / 60) else null
        val lucroMax = if (lucro == null && c.embarqueKm > 0) valor - r.custoKm * c.embarqueKm else null
        val lucroAte = lucro ?: lucroMax
        val lucroPctAte = if (lucroAte != null && valor > 0) lucroAte / valor * 100 else null

        val vermelhos = mutableListOf<String>()
        val amarelos = mutableListOf<String>()

        fun faixa(v: Double?, vermelho: Double, verde: Double, nome: String) {
            if (v == null || (vermelho <= 0 && verde <= 0)) return
            when {
                v < vermelho -> vermelhos += "$nome baixo"
                v < max(verde, vermelho) -> amarelos += "$nome no meio"
            }
        }

        if (porKm == null) amarelos += "sem km da corrida (destino não informado)"
        faixa(porKm, r.kmVermelho, r.kmVerde, "R$/km")
        faixa(porHora, r.horaVermelho, r.horaVerde, "R$/hora")
        if (porKmMax != null && porKmMax < r.kmVermelho) vermelhos += "R$/km baixo (só até o passageiro)"
        if (porHoraMax != null && porHoraMax < r.horaVermelho) vermelhos += "R$/hora baixo (só até o passageiro)"
        if (c.nota > 0) faixa(c.nota, r.notaVermelho, r.notaVerde, "nota do passageiro")

        if (r.valorMinimo > 0 && valor < r.valorMinimo) vermelhos += "valor abaixo do mínimo"
        if (r.custoKm > 0 && lucroAte != null && lucroAte <= 0) vermelhos += "não paga o custo do carro"
        if (r.lucroMinimo > 0 && lucroAte != null && lucroAte < r.lucroMinimo) vermelhos += "lucro abaixo do mínimo"
        if (r.lucroMinimoPct > 0 && lucroPctAte != null && lucroPctAte < r.lucroMinimoPct) vermelhos += "lucro % abaixo do mínimo"
        if (r.embarqueMaxKm > 0 && c.embarqueKm > r.embarqueMaxKm) vermelhos += "passageiro longe"
        if (r.embarqueMaxMin > 0 && minEmbarque > r.embarqueMaxMin) vermelhos += "embarque demorado"
        if (r.viagemMaxKm > 0 && c.distanciaKm > r.viagemMaxKm) vermelhos += "viagem longa demais"
        if (r.viagemMaxMin > 0 && minViagem > r.viagemMaxMin) vermelhos += "viagem demorada demais"

        val cor = when {
            vermelhos.isNotEmpty() -> Cor.VERMELHO
            amarelos.isNotEmpty() -> Cor.AMARELO
            else -> Cor.VERDE
        }

        return Avaliacao(
            cor = cor,
            valor = valor,
            kmTotal = kmTotal,
            kmConhecido = kmConhecido,
            minutos = minTotal,
            minutosEstimados = estimado,
            porKm = porKm,
            porHora = porHora,
            custo = custo,
            lucro = lucro,
            lucroPct = lucroPct,
            porKmMax = porKmMax,
            porHoraMax = porHoraMax,
            lucroMax = lucroMax,
            nota = c.nota,
            motivos = vermelhos + amarelos
        )
    }
}
