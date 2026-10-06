package app.rodagem.leitor

import android.content.Context


object ExtratorCorrida {


    private var ultimaCorrida = ""


    fun analisar(
        contexto: Context,
        texto: String
    ) {


        val aplicativo = identificarAplicativo(texto)


        val valor = extrairValor(texto)

        if (valor <= 0) return


        val origemDestino = extrairEnderecos(texto)

        val origem = origemDestino.first

        val destino = origemDestino.second


        if (origem.isEmpty() || destino.isEmpty()) {
            return
        }


        val pagamento =
            Regex(
                "(?i)(dinheiro|cartão|cartao|pix)"
            )
                .find(texto)
                ?.value
                ?: ""


        val distancia =
            Regex(
                """(\d+[,.]?\d*)\s?km"""
            )
                .findAll(texto)
                .lastOrNull()
                ?.groupValues
                ?.get(1)
                ?.replace(",", ".")
                ?.toDoubleOrNull()
                ?: 0.0


        val tempo =
            Regex(
                """(\d+)\s?min"""
            )
                .find(texto)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: 0



        val chave =
            "$aplicativo|$valor|$origem|$destino"


        if (chave == ultimaCorrida) {
            return
        }


        ultimaCorrida = chave



        val corrida = Corrida(

            aplicativo = aplicativo,

            valor = valor,

            pagamento = pagamento,

            origem = origem,

            destino = destino,

            distanciaKm = distancia,

            tempoMinutos = tempo

        )


        BancoCorridas(contexto)
            .salvar(corrida)


    }



    private fun identificarAplicativo(
        texto: String
    ): String {


        return when {

            texto.contains(
                "taxsee",
                true
            ) ||
            texto.contains(
                "maxim",
                true
            ) ->
                "Maxim"


            texto.contains(
                "easymob",
                true
            ) ->
                "Easy"


            texto.contains(
                "99",
                true
            ) ->
                "99"


            texto.contains(
                "uber",
                true
            ) ->
                "Uber"


            else ->
                "Desconhecido"
        }

    }




    private fun extrairValor(
        texto: String
    ): Double {


        return Regex(
            """R\$\s?(\d+[,.]?\d*)"""
        )
            .find(texto)
            ?.groupValues
            ?.get(1)
            ?.replace(",", ".")
            ?.toDoubleOrNull()
            ?: 0.0

    }



    private fun extrairEnderecos(
        texto: String
    ): Pair<String,String> {


        val enderecos =
            Regex(
                """tv_main_address.*?texto:\s*(.*?)\s*\|"""
            )
                .findAll(texto)
                .map {
                    it.groupValues[1]
                        .replace("🏠","")
                        .replace("🏁","")
                        .trim()
                }
                .toList()


        if (enderecos.size >= 2) {

            return Pair(
                enderecos[0],
                enderecos[1]
            )

        }


        return Pair(
            "",
            ""
        )

    }

}
