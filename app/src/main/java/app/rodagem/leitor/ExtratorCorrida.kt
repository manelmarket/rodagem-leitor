package app.rodagem.leitor

import android.content.Context


object ExtratorCorrida {


    private var ultimaChave = ""


    fun analisar(
        contexto: Context,
        texto: String
    ) {


        val valor = extrairValor(texto)

        if (valor <= 0) return


        val aplicativo = identificarApp(texto)


        val enderecos = extrairEnderecos(texto)


        val origem = enderecos.first

        val destino = enderecos.second


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
                .find(texto)
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



        if (chave == ultimaChave) {
            return
        }


        ultimaChave = chave



        BancoCorridas(contexto)
            .salvar(

                Corrida(

                    aplicativo = aplicativo,

                    valor = valor,

                    pagamento = pagamento,

                    origem = origem,

                    destino = destino,

                    distanciaKm = distancia,

                    tempoMinutos = tempo

                )

            )

    }



    private fun identificarApp(
        texto: String
    ): String {


        return when {


            texto.contains(
                "taxsee",
                true
            )
            ||
            texto.contains(
                "maxim",
                true
            )
            ->
                "Maxim"



            texto.contains(
                "easymob",
                true
            )
            ->
                "Easy"



            texto.contains(
                "99",
                true
            )
            ->
                "99"



            texto.contains(
                "uber",
                true
            )
            ->
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


        val lista = mutableListOf<String>()


        val regex = Regex(
            """#(?:tv_main_address|tv_title_start_address|tv_title_final_address)\s*\|\s*texto:\s*(.*?)\s*\|"""
        )


        regex.findAll(texto)
            .forEach {


                val endereco =
                    it.groupValues[1]
                        .replace("🏠","")
                        .replace("🏁","")
                        .trim()


                if(endereco.isNotEmpty())
                    lista.add(endereco)

            }



        if(lista.size >= 2){

            return Pair(
                lista.first(),
                lista.last()
            )

        }



        return Pair(
            "",
            ""
        )

    }


}
