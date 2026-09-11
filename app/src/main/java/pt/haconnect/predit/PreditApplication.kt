package pt.haconnect.predit

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pt.haconnect.predit.data.local.PreditDatabase
import pt.haconnect.predit.data.local.TipoTurnoEntity
import pt.haconnect.predit.domain.model.CategoriaTurno

class PreditApplication : Application() {

    lateinit var database: PreditDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            PreditDatabase::class.java,
            "predit.db"
        ).addMigrations(PreditDatabase.MIGRATION_1_2)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    preencherDadosIniciais()
                }
            }
        }).build()
    }

    private suspend fun preencherDadosIniciais() {
        val dao = database.tipoTurnoDao()
        val iniciais = listOf(
            TipoTurnoEntity(
                nome = "Tarde 8h",
                abreviatura = "T08H",
                cor = 0xFFC0392BL, // vermelho-tijolo
                emoji = "🔴",
                inicioMin = 13 * 60, // 13:00
                fimMin = 21 * 60,    // 21:00
                pausaMin = 0,
                categoria = CategoriaTurno.TRABALHO,
                ativo = true
            ),
            TipoTurnoEntity(
                nome = "Folga",
                abreviatura = "F",
                cor = 0xFF1ABC9CL, // verde-água
                emoji = "🟢",
                inicioMin = 0,
                fimMin = 0,
                pausaMin = 0,
                categoria = CategoriaTurno.FOLGA,
                ativo = true
            ),
            TipoTurnoEntity(
                nome = "Férias",
                abreviatura = "FER",
                cor = 0xFF2980B9L, // azul
                emoji = "🏖️",
                inicioMin = 0,
                fimMin = 0,
                pausaMin = 0,
                categoria = CategoriaTurno.FERIAS,
                ativo = true
            ),
            TipoTurnoEntity(
                nome = "Baixa médica",
                abreviatura = "BM",
                cor = 0xFF8E44ADL, // roxo
                emoji = "🏥",
                inicioMin = 0,
                fimMin = 0,
                pausaMin = 0,
                categoria = CategoriaTurno.BAIXA,
                ativo = true
            )
        )
        iniciais.forEach { dao.inserir(it) }
    }
}
