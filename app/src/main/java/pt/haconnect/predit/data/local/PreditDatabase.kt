package pt.haconnect.predit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TipoTurnoEntity::class,
        RotacaoEntity::class,
        RotacaoSlotEntity::class,
        AplicacaoRotacaoEntity::class,
        AusenciaEntity::class,
        ContratoUtilizadorEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Conversores::class)
abstract class PreditDatabase : RoomDatabase() {
    abstract fun tipoTurnoDao(): TipoTurnoDao
    abstract fun rotacaoDao(): RotacaoDao
    abstract fun ausenciaDao(): AusenciaDao
    abstract fun contratoDao(): ContratoUtilizadorDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `rotacao` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nome` TEXT NOT NULL, `comprimentoCiclo` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `rotacao_slot` (`rotacaoId` INTEGER NOT NULL, `posicao` INTEGER NOT NULL, `tipoTurnoId` INTEGER NOT NULL, PRIMARY KEY(`rotacaoId`, `posicao`), FOREIGN KEY(`rotacaoId`) REFERENCES `rotacao`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tipoTurnoId`) REFERENCES `tipo_turno`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rotacao_slot_rotacaoId` ON `rotacao_slot` (`rotacaoId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rotacao_slot_tipoTurnoId` ON `rotacao_slot` (`tipoTurnoId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `aplicacao_rotacao` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rotacaoId` INTEGER NOT NULL, `dataAncora` INTEGER NOT NULL, `validoDe` INTEGER NOT NULL, `validoAte` INTEGER, FOREIGN KEY(`rotacaoId`) REFERENCES `rotacao`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_aplicacao_rotacao_rotacaoId` ON `aplicacao_rotacao` (`rotacaoId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ausencia` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tipoTurnoId` INTEGER NOT NULL, `dataInicio` INTEGER NOT NULL, `dataFim` INTEGER NOT NULL, `nota` TEXT, FOREIGN KEY(`tipoTurnoId`) REFERENCES `tipo_turno`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ausencia_tipoTurnoId` ON `ausencia` (`tipoTurnoId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `contrato_utilizador` (`id` INTEGER NOT NULL, `categoriaNivel` TEXT NOT NULL, `dataAdmissao` INTEGER, `regimeHorario` TEXT NOT NULL, `horarioSemanalH` INTEGER NOT NULL, `numeroDependentes` INTEGER NOT NULL, `estadoCivil` TEXT NOT NULL, `titulares` INTEGER NOT NULL, `primeiroArranqueConcluido` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}
