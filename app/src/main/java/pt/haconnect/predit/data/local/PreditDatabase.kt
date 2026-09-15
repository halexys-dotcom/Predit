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
        ContratoUtilizadorEntity::class,
        DiaRealEntity::class,
        PlanejamentoMesEntity::class,
        CicloJornadaEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Conversores::class)
abstract class PreditDatabase : RoomDatabase() {
    abstract fun tipoTurnoDao(): TipoTurnoDao
    abstract fun rotacaoDao(): RotacaoDao
    abstract fun ausenciaDao(): AusenciaDao
    abstract fun contratoDao(): ContratoUtilizadorDao
    abstract fun diaRealDao(): DiaRealDao
    abstract fun planejamentoMesDao(): PlanejamentoMesDao
    abstract fun cicloJornadaDao(): CicloJornadaDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `dia_real` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `data` INTEGER NOT NULL, `tipoTurnoId` INTEGER, `inicioMin` INTEGER NOT NULL, `fimMin` INTEGER NOT NULL, `pausaMin` INTEGER NOT NULL, `nota` TEXT, `origem` TEXT NOT NULL, FOREIGN KEY(`tipoTurnoId`) REFERENCES `tipo_turno`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dia_real_data` ON `dia_real` (`data`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dia_real_tipoTurnoId` ON `dia_real` (`tipoTurnoId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `dia_real` ADD COLUMN `posto` TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `planejamento_mes` (`anoMes` TEXT NOT NULL, `totalMinutos` INTEGER NOT NULL, `contratoMinutos` INTEGER, `dataImportacao` INTEGER NOT NULL, PRIMARY KEY(`anoMes`))")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `planejamento_mes` ADD COLUMN `numTurnos` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `planejamento_mes` ADD COLUMN `numFolgas` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ciclo_jornada` (`id` TEXT NOT NULL, `inicio` INTEGER NOT NULL, `fim` INTEGER NOT NULL, `realTotalMinutos` INTEGER NOT NULL, `extrasPagosMinutos` INTEGER NOT NULL, `saldoFinalMinutos` INTEGER NOT NULL, `dataFecho` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}
