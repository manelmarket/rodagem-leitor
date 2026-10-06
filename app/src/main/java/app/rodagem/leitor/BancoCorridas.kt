package app.rodagem.leitor

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues


class BancoCorridas(context: Context) :
    SQLiteOpenHelper(
        context,
        "corridas.db",
        null,
        1
    ) {


    override fun onCreate(db: SQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE corridas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                aplicativo TEXT,
                valor REAL,
                pagamento TEXT,
                origem TEXT,
                destino TEXT,
                distancia REAL,
                tempo INTEGER,
                data INTEGER
            )
            """
        )
    }


    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        db.execSQL(
            "DROP TABLE IF EXISTS corridas"
        )

        onCreate(db)
    }


    fun salvar(corrida: Corrida) {

        val db = writableDatabase

        val valores = ContentValues()

        valores.put("aplicativo", corrida.aplicativo)
        valores.put("valor", corrida.valor)
        valores.put("pagamento", corrida.pagamento)
        valores.put("origem", corrida.origem)
        valores.put("destino", corrida.destino)
        valores.put("distancia", corrida.distanciaKm)
        valores.put("tempo", corrida.tempoMinutos)
        valores.put("data", corrida.dataHora)

        db.insert(
            "corridas",
            null,
            valores
        )

        db.close()
    }
}
