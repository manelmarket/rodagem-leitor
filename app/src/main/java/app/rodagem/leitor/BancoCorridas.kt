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
        2
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

                cliente TEXT,

                status TEXT,

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



    fun salvar(
        corrida: Corrida
    ) {


        val valores = ContentValues()


        valores.put(
            "aplicativo",
            corrida.aplicativo
        )


        valores.put(
            "valor",
            corrida.valor
        )


        valores.put(
            "pagamento",
            corrida.pagamento
        )


        valores.put(
            "origem",
            corrida.origem
        )


        valores.put(
            "destino",
            corrida.destino
        )


        valores.put(
            "distancia",
            corrida.distanciaKm
        )


        valores.put(
            "tempo",
            corrida.tempoMinutos
        )


        valores.put(
            "cliente",
            corrida.cliente
        )


        valores.put(
            "status",
            corrida.status
        )


        valores.put(
            "data",
            corrida.dataHora
        )


        writableDatabase.insert(
            "corridas",
            null,
            valores
        )

    }

}
