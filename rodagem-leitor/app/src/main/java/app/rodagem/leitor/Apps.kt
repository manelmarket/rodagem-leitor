package app.rodagem.leitor

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Lista os apps instalados e guarda quais o leitor deve ler (ligados no toggle).
 * Na primeira vez, já liga sozinho os apps de corrida conhecidos.
 */
object Apps {
    data class App(val nome: String, val pacote: String, val corrida: Boolean)

    private const val PREFS = "leitor_apps"
    private const val CHAVE = "ativos"

    // apps de corrida conhecidos: ligados por padrão e mostrados no topo da lista
    private val corridaPorPacote = Regex(
        "(?i)ubercab\\.driver|indriver|taxsee\\.driver|easymob(?!.*passenger)|didi|99"
    )
    private val corridaPorNome = Regex("(?i)uber driver|indrive|taxsee driver|easymob|\\b99\\b")

    private fun ehCorrida(nome: String, pacote: String) =
        corridaPorPacote.containsMatchIn(pacote) ||
        (corridaPorNome.containsMatchIn(nome) && !pacote.contains("passenger", ignoreCase = true))

    /** Todos os apps que aparecem na tela inicial (menos o próprio leitor). Os de corrida vêm primeiro. */
    fun instalados(ctx: Context): List<App> {
        val pm = ctx.packageManager
        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != ctx.packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .map { val nome = pm.getApplicationLabel(it).toString(); App(nome, it.packageName, ehCorrida(nome, it.packageName)) }
            .sortedWith(compareBy<App>({ !it.corrida }, { it.nome.lowercase() }))
            .toList()
    }

    /** Pacotes ligados no toggle. Na primeira vez, liga os apps de corrida encontrados. */
    fun ativos(ctx: Context): Set<String> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(CHAVE)) {
            val padrao = instalados(ctx).filter { it.corrida }.map { it.pacote }.toSet()
            p.edit().putStringSet(CHAVE, padrao).apply()
            return padrao
        }
        return p.getStringSet(CHAVE, emptySet())!!.toSet()
    }

    fun definir(ctx: Context, pacote: String, ligado: Boolean) {
        val atual = ativos(ctx).toMutableSet()
        if (ligado) atual.add(pacote) else atual.remove(pacote)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(CHAVE, atual).apply()
    }
}
