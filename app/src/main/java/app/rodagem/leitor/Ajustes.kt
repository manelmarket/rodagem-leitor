package app.rodagem.leitor

import android.content.Context

/** Ajustes do semáforo e do custo do carro, guardados no aparelho. */
object Ajustes {

    private const val PREFS = "semaforo"

    /** Apps que o extrator já entende (para a comissão por app). */
    val appsLidos = listOf("Maxim", "Easy")

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun num(ctx: Context, chave: String, padrao: Double): Double =
        p(ctx).getString(chave, null)?.toDoubleOrNull() ?: padrao


    // ---------- semáforo ----------

    fun ligado(ctx: Context) = p(ctx).getBoolean("ligado", true)

    fun emCima(ctx: Context) = p(ctx).getBoolean("em_cima", true)

    fun salvarSemaforo(ctx: Context, ligado: Boolean, emCima: Boolean) {
        p(ctx).edit().putBoolean("ligado", ligado).putBoolean("em_cima", emCima).apply()
    }


    // ---------- faixas e limites ----------

    fun regras(ctx: Context): Regras {
        val d = Regras()
        return Regras(
            kmVermelho = num(ctx, "km_vermelho", d.kmVermelho),
            kmVerde = num(ctx, "km_verde", d.kmVerde),
            horaVermelho = num(ctx, "hora_vermelho", d.horaVermelho),
            horaVerde = num(ctx, "hora_verde", d.horaVerde),
            notaVermelho = num(ctx, "nota_vermelho", d.notaVermelho),
            notaVerde = num(ctx, "nota_verde", d.notaVerde),
            valorMinimo = num(ctx, "valor_minimo", d.valorMinimo),
            lucroMinimo = num(ctx, "lucro_minimo", d.lucroMinimo),
            lucroMinimoPct = num(ctx, "lucro_minimo_pct", d.lucroMinimoPct),
            embarqueMaxKm = num(ctx, "embarque_max_km", d.embarqueMaxKm),
            embarqueMaxMin = num(ctx, "embarque_max_min", d.embarqueMaxMin),
            viagemMaxKm = num(ctx, "viagem_max_km", d.viagemMaxKm),
            viagemMaxMin = num(ctx, "viagem_max_min", d.viagemMaxMin),
            velocidadeMedia = num(ctx, "velocidade_media", d.velocidadeMedia),
            custoKm = custo(ctx).porKm(),
            comissao = appsLidos.associateWith { num(ctx, "comissao_$it", 0.0) }
        )
    }

    fun salvarRegras(ctx: Context, r: Regras) {
        p(ctx).edit().apply {
            putString("km_vermelho", r.kmVermelho.toString())
            putString("km_verde", r.kmVerde.toString())
            putString("hora_vermelho", r.horaVermelho.toString())
            putString("hora_verde", r.horaVerde.toString())
            putString("nota_vermelho", r.notaVermelho.toString())
            putString("nota_verde", r.notaVerde.toString())
            putString("valor_minimo", r.valorMinimo.toString())
            putString("lucro_minimo", r.lucroMinimo.toString())
            putString("lucro_minimo_pct", r.lucroMinimoPct.toString())
            putString("embarque_max_km", r.embarqueMaxKm.toString())
            putString("embarque_max_min", r.embarqueMaxMin.toString())
            putString("viagem_max_km", r.viagemMaxKm.toString())
            putString("viagem_max_min", r.viagemMaxMin.toString())
            putString("velocidade_media", r.velocidadeMedia.toString())
            r.comissao.forEach { (app, pct) -> putString("comissao_$app", pct.toString()) }
        }.apply()
    }


    // ---------- custo do carro ----------

    fun custo(ctx: Context): Custo {
        val d = Custo()
        return Custo(
            combustivelLitro = num(ctx, "combustivel_litro", d.combustivelLitro),
            kmPorLitro = num(ctx, "km_por_litro", d.kmPorLitro),
            kmPorMes = num(ctx, "km_por_mes", d.kmPorMes),
            parcelaMes = num(ctx, "parcela_mes", d.parcelaMes),
            seguroMes = num(ctx, "seguro_mes", d.seguroMes),
            ipvaAno = num(ctx, "ipva_ano", d.ipvaAno),
            manutencaoMes = num(ctx, "manutencao_mes", d.manutencaoMes),
            depreciacaoMes = num(ctx, "depreciacao_mes", d.depreciacaoMes),
            outrosMes = num(ctx, "outros_mes", d.outrosMes)
        )
    }

    fun salvarCusto(ctx: Context, c: Custo) {
        p(ctx).edit()
            .putString("combustivel_litro", c.combustivelLitro.toString())
            .putString("km_por_litro", c.kmPorLitro.toString())
            .putString("km_por_mes", c.kmPorMes.toString())
            .putString("parcela_mes", c.parcelaMes.toString())
            .putString("seguro_mes", c.seguroMes.toString())
            .putString("ipva_ano", c.ipvaAno.toString())
            .putString("manutencao_mes", c.manutencaoMes.toString())
            .putString("depreciacao_mes", c.depreciacaoMes.toString())
            .putString("outros_mes", c.outrosMes.toString())
            .apply()
    }
}
