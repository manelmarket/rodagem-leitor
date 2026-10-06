package app.rodagem.leitor

data class Corrida(
    val id: Long = 0,
    val aplicativo: String,
    val valor: Double = 0.0,
    val pagamento: String = "",
    val origem: String = "",
    val destino: String = "",
    val distanciaKm: Double = 0.0,
    val tempoMinutos: Int = 0,
    val dataHora: Long = System.currentTimeMillis()
)
