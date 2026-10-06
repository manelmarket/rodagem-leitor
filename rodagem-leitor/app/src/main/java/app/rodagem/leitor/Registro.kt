package app.rodagem.leitor

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda no aparelho os textos que o leitor enxergou nas telas de oferta. */
object Registro {
    private const val ARQUIVO = "registro_leitor.txt"
    private const val LIMITE = 1_500_000L   // ~1,5 MB; passou disso, descarta a metade mais antiga
    private const val PREFS = "leitor"

    fun arquivo(ctx: Context) = File(ctx.filesDir, ARQUIVO)

    private fun agora() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())

    @Synchronized
    fun gravar(ctx: Context, titulo: String, corpo: String) {
        val f = arquivo(ctx)
        if (f.exists() && f.length() > LIMITE) {
            val txt = f.readText()
            val meio = txt.indexOf("\n=====", txt.length / 2)
            f.writeText(if (meio >= 0) txt.substring(meio + 1) else "")
        }
        f.appendText("===== ${agora()} | $titulo\n$corpo\n")
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putInt("total", p.getInt("total", 0) + 1).putString("ultimo", agora()).apply()
    }

    fun total(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("total", 0)
    fun ultimo(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ultimo", null)

    @Synchronized
    fun limpar(ctx: Context) {
        arquivo(ctx).delete()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
