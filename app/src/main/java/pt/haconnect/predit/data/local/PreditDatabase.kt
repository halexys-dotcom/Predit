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
        CicloJornadaEntity::class,
        ParametrosCCTEntity::class,
        RubricaEntity::class,
        TabelaIRSEntity::class,
        ReciboMesEntity::class,
        ReciboLinhaEntity::class,
        MunicipioEntity::class
    ],
    version = 13,
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
    abstract fun parametrosCCTDao(): ParametrosCCTDao
    abstract fun rubricaDao(): RubricaDao
    abstract fun tabelaIRSDao(): TabelaIRSDao
    abstract fun reciboMesDao(): ReciboMesDao
    abstract fun reciboLinhaDao(): ReciboLinhaDao
    abstract fun municipioDao(): MunicipioDao

    companion object {
        /**
         * Versão do esquema, para quem a lê de fora do Room: os testes de backup comparam-na
         * com o `user_version` lido dos ficheiros, e o restauro recusa cópias mais recentes
         * do que isto. O KSP não aceita uma constante no `@Database(version = ...)`, por isso
         * o valor vive duplicado lá em cima — o T1 do BackupManagerTest guarda a sincronia:
         * se um subir e o outro não, o teste falha.
         */
        const val VERSAO_BD = 13

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

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `parametros_cct` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `validoDe` INTEGER NOT NULL, `vencimentoBaseMil` INTEGER NOT NULL, `subAlimentacaoDiaMil` INTEGER NOT NULL, `subTransporteMesMil` INTEGER NOT NULL, `horarioSemanalReferencia` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `rubrica` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `codigo` TEXT NOT NULL, `nome` TEXT NOT NULL, `incideSS` INTEGER NOT NULL, `incideIRS` INTEGER NOT NULL, `incideSindicato` INTEGER NOT NULL, `tipoCalculo` TEXT NOT NULL, `ativaConferencia` INTEGER NOT NULL, `ordem` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `contrato_utilizador` ADD COLUMN `regiao` TEXT NOT NULL DEFAULT 'CONTINENTE'")
                db.execSQL("CREATE TABLE IF NOT EXISTS `tabela_irs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ano` INTEGER NOT NULL, `regiao` TEXT NOT NULL, `categoria` TEXT NOT NULL, `tabelaNumero` INTEGER NOT NULL, `ordemEscalao` INTEGER NOT NULL, `limiteAte` INTEGER NOT NULL, `taxaBasisPoints` INTEGER NOT NULL, `parcelaAbater` INTEGER NOT NULL, `parcelaAdicionalDep` INTEGER NOT NULL, `formulaComposta` INTEGER NOT NULL)")
            }
        }

        /**
         * SQL copiado do 12.json gerado pelo Room (só a substituição de ${TABLE_NAME}
         * pelo nome da tabela, como nas migrações anteriores). Não escrever à mão.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recibo_mes` (`anoMes` TEXT NOT NULL, `dataFecho` INTEGER NOT NULL, `vencimentoBase` INTEGER NOT NULL, `vencimentoHora` INTEGER NOT NULL, `numDiasUteis` INTEGER NOT NULL, `irsRetidoAno` INTEGER NOT NULL, `totalAbonos` INTEGER NOT NULL, `totalDescontos` INTEGER NOT NULL, `liquido` INTEGER NOT NULL, `nota` TEXT, `dataCriacao` INTEGER NOT NULL, PRIMARY KEY(`anoMes`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `recibo_linha` (`reciboMesId` TEXT NOT NULL, `rubricaId` INTEGER NOT NULL, `valorEstimado` INTEGER NOT NULL, `valorReal` INTEGER NOT NULL, `natureza` TEXT NOT NULL, `ordem` INTEGER NOT NULL, PRIMARY KEY(`reciboMesId`, `rubricaId`), FOREIGN KEY(`reciboMesId`) REFERENCES `recibo_mes`(`anoMes`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`rubricaId`) REFERENCES `rubrica`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recibo_linha_rubricaId` ON `recibo_linha` (`rubricaId`)")
            }
        }

        /**
         * Fase 10: catálogo de municípios (feriado municipal) e o município escolhido no
         * contrato. SQL copiado do 13.json gerado pelo Room (só a substituição de
         * ${TABLE_NAME} pelo nome da tabela, como nas migrações anteriores).
         *
         * O municipioId entra sem FOREIGN KEY de propósito: sem ON DELETE a FK traria
         * complicações (a tabela é catálogo semeado, não muda em runtime) e o valor nulo
         * significa "município não escolhido".
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `municipio` (`id` INTEGER NOT NULL, `nome` TEXT NOT NULL, `distrito` TEXT NOT NULL, `regiao` TEXT NOT NULL, `feriadoDia` INTEGER NOT NULL, `feriadoMes` INTEGER NOT NULL, `feriadoNome` TEXT NOT NULL, `verificado` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("ALTER TABLE `contrato_utilizador` ADD COLUMN `municipioId` INTEGER")
            }
        }
    }
}
