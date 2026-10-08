package app.rodagem.leitor

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper


/** Corridas lidas, guardadas só neste aparelho. */
class BancoCorridas private constructor(context: Context) :

    SQLiteOpenHelper(
        context,
        "corridas.db",
        null,
        4
    ),

    Deposito {


    companion object {

        @Volatile
        private var unico: BancoCorridas? = null

        /** Um banco só para o app inteiro (a tela e o leitor usam o mesmo). */
        fun de(ctx: Context): BancoCorridas =
            unico ?: synchronized(this) {
                unico ?: BancoCorridas(ctx.applicationContext).also { unico = it }
            }
    }


    override fun onCreate(db: SQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE corridas (

                id INTEGER PRIMARY KEY AUTOINCREMENT,

                aplicativo TEXT,

                valor REAL,

                valor_cobrado REAL,

                pagamento TEXT,

                origem TEXT,

                destino TEXT,

                distancia REAL,

                tempo INTEGER,

                embarque_km REAL,

                embarque_min INTEGER,

                cliente TEXT,

                nota REAL DEFAULT 0,

                categoria TEXT,

                codigo TEXT,

                status TEXT,

                data INTEGER,

                atualizado INTEGER

            )
            """
        )

        db.execSQL("CREATE INDEX corridas_app_data ON corridas (aplicativo, data)")

    }


    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        if (oldVersion < 3) {
            // Até a versão 2 as corridas eram lidas por regras provisórias (o valor podia vir
            // do saldo da Maxim e o app era adivinhado pelo texto). Elas são descartadas;
            // o botão "Reler registro" refaz tudo a partir das telas guardadas.
            db.execSQL(
                "DROP TABLE IF EXISTS corridas"
            )
            onCreate(db)
            return
        }

        // Versão 4: nota do passageiro. As corridas já lidas ficam.
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE corridas ADD COLUMN nota REAL DEFAULT 0")
        }

    }



    override fun recentes(aplicativo: String, desde: Long): List<Corrida> =
        ler(
            "aplicativo = ? AND data >= ?",
            arrayOf(aplicativo, desde.toString()),
            "id DESC",
            "50"
        )


    /** As últimas corridas lidas, de qualquer app, da mais nova para a mais antiga. */
    fun ultimas(limite: Int): List<Corrida> =
        ler(null, null, "data DESC, id DESC", limite.toString())


    fun total(): Int =
        DatabaseUtils.queryNumEntries(readableDatabase, "corridas").toInt()


    fun apagarTudo() {
        writableDatabase.delete("corridas", null, null)
    }


    override fun inserir(c: Corrida): Long =
        writableDatabase.insert(
            "corridas",
            null,
            valores(c)
        )


    override fun atualizar(c: Corrida) {
        writableDatabase.update(
            "corridas",
            valores(c),
            "id = ?",
            arrayOf(c.id.toString())
        )
    }



    private fun valores(c: Corrida) = ContentValues().apply {
        put("aplicativo", c.aplicativo)
        put("valor", c.valor)
        put("valor_cobrado", c.valorCobrado)
        put("pagamento", c.pagamento)
        put("origem", c.origem)
        put("destino", c.destino)
        put("distancia", c.distanciaKm)
        put("tempo", c.tempoMinutos)
        put("embarque_km", c.embarqueKm)
        put("embarque_min", c.embarqueMinutos)
        put("cliente", c.cliente)
        put("nota", c.nota)
        put("categoria", c.categoria)
        put("codigo", c.codigo)
        put("status", c.status)
        put("data", c.dataHora)
        put("atualizado", c.atualizado)
    }


    private fun ler(
        onde: String?,
        args: Array<String>?,
        ordem: String,
        limite: String
    ): List<Corrida> {

        val lista = mutableListOf<Corrida>()

        readableDatabase.query("corridas", null, onde, args, null, null, ordem, limite).use { cur ->
            while (cur.moveToNext()) lista.add(corrida(cur))
        }

        return lista

    }


    private fun corrida(cur: Cursor): Corrida {

        fun texto(coluna: String) = cur.getString(cur.getColumnIndexOrThrow(coluna)) ?: ""
        fun real(coluna: String) = cur.getDouble(cur.getColumnIndexOrThrow(coluna))
        fun inteiro(coluna: String) = cur.getInt(cur.getColumnIndexOrThrow(coluna))
        fun longo(coluna: String) = cur.getLong(cur.getColumnIndexOrThrow(coluna))

        return Corrida(
            id = longo("id"),
            aplicativo = texto("aplicativo"),
            valor = real("valor"),
            valorCobrado = real("valor_cobrado"),
            pagamento = texto("pagamento"),
            origem = texto("origem"),
            destino = texto("destino"),
            distanciaKm = real("distancia"),
            tempoMinutos = inteiro("tempo"),
            embarqueKm = real("embarque_km"),
            embarqueMinutos = inteiro("embarque_min"),
            cliente = texto("cliente"),
            nota = real("nota"),
            categoria = texto("categoria"),
            codigo = texto("codigo"),
            status = texto("status"),
            dataHora = longo("data"),
            atualizado = longo("atualizado")
        )

    }

}
