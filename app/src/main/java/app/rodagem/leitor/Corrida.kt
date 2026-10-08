package app.rodagem.leitor

/**
 * Uma corrida lida das telas. A mesma corrida é atualizada conforme avança
 * (oferta → aceita → no local → em corrida → concluída ou cancelada).
 */
data class Corrida(

    val id: Long = 0,

    val aplicativo: String,

    /** Valor em destaque na oferta (Maxim: preço da corrida; Easy: o que o motorista ganha). */
    val valor: Double = 0.0,

    /** Total cobrado do passageiro, quando o app mostra à parte (Easy). */
    val valorCobrado: Double = 0.0,

    val pagamento: String = "",

    val origem: String = "",

    val destino: String = "",

    /** Km e minutos da corrida em si (do embarque ao destino). */
    val distanciaKm: Double = 0.0,

    val tempoMinutos: Int = 0,

    /** Km e minutos do motorista até o passageiro, como apareciam na oferta. */
    val embarqueKm: Double = 0.0,

    val embarqueMinutos: Int = 0,

    val cliente: String = "",

    /** Nota do passageiro, quando a oferta mostra (Easy). 0 = não mostrou. */
    val nota: Double = 0.0,

    val categoria: String = "",

    /** Número do pedido no app (Easy: OS). Vazio quando o app não mostra. */
    val codigo: String = "",

    val status: String = Status.OFERTA,

    /** Quando a corrida foi lida pela primeira vez. */
    val dataHora: Long = System.currentTimeMillis(),

    /** Última vez que alguma tela acrescentou algo a ela. */
    val atualizado: Long = dataHora

)


object Status {

    const val OFERTA = "oferta"
    const val ACEITA = "aceita"
    const val NO_LOCAL = "no_local"
    const val EM_CORRIDA = "em_corrida"
    const val CONCLUIDA = "concluida"
    const val CANCELADA = "cancelada"

    private val ordem = listOf(OFERTA, ACEITA, NO_LOCAL, EM_CORRIDA, CONCLUIDA)

    fun final(s: String) = s == CONCLUIDA || s == CANCELADA

    /** O status só anda para frente; concluída e cancelada não mudam mais. */
    fun avancar(atual: String, novo: String): String = when {
        final(atual) -> atual
        final(novo) -> novo
        ordem.indexOf(novo) > ordem.indexOf(atual) -> novo
        else -> atual
    }

    fun nome(s: String) = when (s) {
        OFERTA -> "Oferta"
        ACEITA -> "Aceita"
        NO_LOCAL -> "No local"
        EM_CORRIDA -> "Em corrida"
        CONCLUIDA -> "Concluída"
        CANCELADA -> "Cancelada"
        else -> s
    }
}
