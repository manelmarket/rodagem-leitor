package app.rodagem.leitor

import android.content.Context


object ExtratorCorrida {


    fun analisar(
        contexto: Context,
        texto: String
    ) {


        val linhas = texto
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }


        val valor = Regex(
            """R\$\s?(\d+[,.]?\d*)"""
        )
            .find(texto)
            ?.groupValues
            ?.get(1)
            ?.replace(",", ".")
            ?.toDoubleOrNull()
            ?: return



        val origemDestino = linhas.filter {

            it.contains("texto:", true) &&
            !it.contains("R$", true) &&
            it.length > 15

        }


        if (origemDestino.size < 2) {
            return
        }


        val origem = origemDestino
            .first()
            .substringAfter("texto:")
            .trim()


        val destino = origemDestino
            .last()
            .substringAfter("texto:")
            .trim()



        val aplicativo = when {

            texto.contains("taxsee", true) ||
            texto.contains("maxim", true) ->
                "Maxim"


            texto.contains("easymob", true) ->
                "Easy"


            texto.contains("99", true) ->
                "99"


            texto.contains("uber", true) ->
                "Uber"


            else ->
                "Desconhecido"
        }



        val corrida = Corrida(

            aplicativo = aplicativo,

            valor = valor,

            origem = origem,

            destino = destino

        )


        BancoCorridas(contexto)
            .salvar(corrida)


    }

}
