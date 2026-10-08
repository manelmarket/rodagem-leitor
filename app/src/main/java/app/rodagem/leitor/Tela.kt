package app.rodagem.leitor

/**
 * Lê o texto que o LeitorService monta de cada tela (o mesmo formato do registro)
 * e devolve as janelas com seus elementos em árvore, para o extrator procurar pelos IDs.
 *
 * Formato de cada elemento (a indentação mostra quem está dentro de quem):
 *   TextView #tv_price | texto: R$ 17,00 | desc: ... | clicável | [234,1219 612,1350]
 */
class Tela private constructor(val janelas: List<Janela>) {

    class No(
        val nivel: Int,
        val classe: String,
        val id: String,
        val texto: String,
        val desc: String
    ) {
        var pai: No? = null
        val filhos = mutableListOf<No>()

        /** Este elemento e tudo o que está dentro dele, na ordem da tela. */
        fun todos(): List<No> {
            val lista = mutableListOf<No>()
            juntar(this, lista)
            return lista
        }

        private fun juntar(no: No, lista: MutableList<No>) {
            lista.add(no)
            no.filhos.forEach { juntar(it, lista) }
        }

        fun busca(id: String): List<No> = todos().filter { it.id == id }

        fun texto(id: String): String = busca(id).firstOrNull { it.texto.isNotEmpty() }?.texto ?: ""

        fun tem(id: String) = todos().any { it.id == id }
    }

    class Janela(val pacote: String, val tipo: String, val nos: List<No>) {

        fun busca(id: String): List<No> = nos.filter { it.id == id }

        fun texto(id: String): String = busca(id).firstOrNull { it.texto.isNotEmpty() }?.texto ?: ""

        fun textos(id: String): List<String> = busca(id).map { it.texto }.filter { it.isNotEmpty() }

        fun tem(id: String) = nos.any { it.id == id }
    }

    companion object {

        private val cabecalho = Regex("""^-- janela (.+?) \| pacote (\S+)\s*$""")
        private val linha = Regex("""^( *)(\S+)(?: #(\S+))?(.*?) \| \[-?\d+,-?\d+ -?\d+,-?\d+]\s*$""")
        private val campoTexto = Regex(""" \| texto: (.*?)(?= \| desc: | \| clicável|$)""")
        private val campoDesc = Regex(""" \| desc: (.*?)(?= \| clicável|$)""")

        fun ler(dump: String): Tela {
            val janelas = mutableListOf<Janela>()
            var pacote: String? = null
            var tipo = ""
            var nos = mutableListOf<No>()
            val pilha = ArrayList<No>()

            fun fechar() {
                pacote?.let { janelas.add(Janela(it, tipo, nos)) }
            }

            for (l in dump.lineSequence()) {
                val c = cabecalho.find(l)
                if (c != null) {
                    fechar()
                    tipo = c.groupValues[1]
                    pacote = c.groupValues[2]
                    nos = mutableListOf()
                    pilha.clear()
                    continue
                }
                if (pacote == null) continue
                val m = linha.find(l) ?: continue
                val resto = m.groupValues[4]
                val no = No(
                    nivel = m.groupValues[1].length / 2,
                    classe = m.groupValues[2],
                    id = m.groupValues[3],
                    texto = campoTexto.find(resto)?.groupValues?.get(1)?.trim() ?: "",
                    desc = campoDesc.find(resto)?.groupValues?.get(1)?.trim() ?: ""
                )
                while (pilha.isNotEmpty() && pilha.last().nivel >= no.nivel) pilha.removeAt(pilha.size - 1)
                pilha.lastOrNull()?.let { pai ->
                    no.pai = pai
                    pai.filhos.add(no)
                }
                pilha.add(no)
                nos.add(no)
            }
            fechar()
            return Tela(janelas)
        }
    }
}
