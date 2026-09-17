package pt.haconnect.predit

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pt.haconnect.predit.data.local.ContratoUtilizadorEntity
import pt.haconnect.predit.data.local.MunicipioEntity
import pt.haconnect.predit.data.local.ParametrosCCTEntity
import pt.haconnect.predit.data.local.PreditDatabase
import pt.haconnect.predit.data.local.RubricaEntity
import pt.haconnect.predit.data.local.TipoCalculo
import pt.haconnect.predit.data.local.TipoTurnoEntity
import pt.haconnect.predit.data.local.TABELAS_IRS_2026
import pt.haconnect.predit.data.repository.CicloJornadaRepository
import pt.haconnect.predit.data.repository.PlanejamentoMesRepository
import pt.haconnect.predit.data.repository.ReciboRepository
import pt.haconnect.predit.domain.model.CategoriaTurno
import java.time.LocalDate

class PreditApplication : Application() {

    companion object {
        /** Ano das tabelas de retenção carregadas. */
        const val ANO_IRS = 2026
    }

    lateinit var database: PreditDatabase
        private set

    val planejamentoMesRepository by lazy {
        PlanejamentoMesRepository(database.planejamentoMesDao())
    }

    val cicloJornadaRepository by lazy {
        CicloJornadaRepository(database.cicloJornadaDao())
    }

    val reciboRepository by lazy {
        ReciboRepository(database)
    }

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            PreditDatabase::class.java,
            "predit.db"
        ).addMigrations(
            PreditDatabase.MIGRATION_1_2,
            PreditDatabase.MIGRATION_2_3,
            PreditDatabase.MIGRATION_3_4,
            PreditDatabase.MIGRATION_4_5,
            PreditDatabase.MIGRATION_5_6,
            PreditDatabase.MIGRATION_6_7,
            PreditDatabase.MIGRATION_7_8,
            PreditDatabase.MIGRATION_8_9,
            PreditDatabase.MIGRATION_9_10,
            PreditDatabase.MIGRATION_10_11,
            PreditDatabase.MIGRATION_11_12,
            PreditDatabase.MIGRATION_12_13
        )
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    preencherDadosIniciais()
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // O SQLite não impõe as FOREIGN KEY sem este pragma, e o Room 2.6.1 não tem
                // sequer opção no builder para isso. Sem ele o CASCADE de
                // recibo_mes→recibo_linha e o RESTRICT de rubrica ficavam inertes.
                // O onOpen corre fora de transação (RoomOpenHelper.onOpen), logo pega.
                db.execSQL("PRAGMA foreign_keys = ON")
                CoroutineScope(Dispatchers.IO).launch {
                    garantirContratoInicial()
                    garantirParametrosCCT()
                    garantirRubricas()
                    garantirTabelasIRS()
                    garantirMunicipios()
                }
            }
        }).build()
    }

    private suspend fun garantirContratoInicial() {
        val dao = database.contratoDao()
        if (dao.contar() == 0) {
            dao.guardar(
                ContratoUtilizadorEntity(
                    id = 1,
                    categoriaNivel = "XIII, Vigilante Aeroportuário/APA-A",
                    dataAdmissao = null,
                    regimeHorario = "NORMAL",
                    horarioSemanalH = 40,
                    numeroDependentes = 0,
                    estadoCivil = "SOLTEIRO",
                    titulares = 1,
                    primeiroArranqueConcluido = false
                )
            )
        }
    }

    /**
     * Parâmetros do CCT, versionados por vigência. Idempotente: só escreve se a tabela
     * estiver vazia. Unidade: 1/10000 € (ver domain/model/Dinheiro.kt).
     */
    private suspend fun garantirParametrosCCT() {
        val dao = database.parametrosCCTDao()
        if (dao.contar() != 0) return
        dao.inserir(
            ParametrosCCTEntity(
                validoDe = LocalDate.of(2025, 1, 1).toEpochDay(),
                vencimentoBaseMil = 10_760_000,   // 1 076,00 €
                subAlimentacaoDiaMil = 74_200,    // 7,42 €/dia
                subTransporteMesMil = 492_500,    // 49,25 €/mês
                horarioSemanalReferencia = 40
            )
        )
        dao.inserir(
            ParametrosCCTEntity(
                validoDe = LocalDate.of(2026, 1, 1).toEpochDay(),
                vencimentoBaseMil = 11_379_800,   // 1 137,98 €
                subAlimentacaoDiaMil = 78_500,    // 7,85 €/dia
                subTransporteMesMil = 520_900,    // 52,09 €/mês
                horarioSemanalReferencia = 40
            )
        )
    }

    /**
     * Catálogo de rubricas do recibo. Idempotente. As 12 primeiras e os três descontos
     * derivados entram ligados à conferência; as restantes ficam na BD mas fora dela por
     * omissão (ligam-se na 8.3).
     * Argumentos por ordem: id (0 = autoGenerate), codigo, nome, incideSS, incideIRS,
     * incideSindicato, tipoCalculo, ativaConferencia, ordem.
     *
     * A inserção é feita por código individual: um catálogo que já exista recebe as
     * rubricas que faltarem (as três de desconto, da Fase 8.2b) sem as duplicar.
     */
    private suspend fun garantirRubricas() {
        val dao = database.rubricaDao()
        val iniciais = listOf(
            RubricaEntity(0L, "VENC", "Vencimento", true, true, true, TipoCalculo.FIXO, true, 1),
            RubricaEntity(0L, "HNOT", "Horas noturnas", true, true, true, TipoCalculo.HORAS, true, 2),
            RubricaEntity(0L, "HSUP_DN", "Sup. diurno dia normal", true, true, true, TipoCalculo.HORAS, true, 3),
            RubricaEntity(0L, "HSUP_NT", "Sup. noturno dia normal", true, true, true, TipoCalculo.HORAS, true, 4),
            RubricaEntity(0L, "HSUP_DN_FER", "Sup. diurno feriado", true, true, true, TipoCalculo.HORAS, true, 5),
            RubricaEntity(0L, "HSUP_NT_FER", "Sup. noturno feriado", true, true, true, TipoCalculo.HORAS, true, 6),
            RubricaEntity(0L, "HSUP_DN_DESC", "Sup. diurno descanso", true, true, true, TipoCalculo.HORAS, true, 7),
            RubricaEntity(0L, "HSUP_NT_DESC", "Sup. noturno descanso", true, true, true, TipoCalculo.HORAS, true, 8),
            RubricaEntity(0L, "SUP_ALIM", "Sub. alimentação", false, false, false, TipoCalculo.FIXO, true, 9),
            RubricaEntity(0L, "SUP_TRAN", "Sub. transporte", true, true, false, TipoCalculo.FIXO, true, 10),
            RubricaEntity(0L, "DESC_FER", "Dia feriado trabalhado", true, true, true, TipoCalculo.HORAS, true, 11),
            RubricaEntity(0L, "DESC_DESC", "Dia descanso trabalhado", true, true, true, TipoCalculo.HORAS, true, 12),
            RubricaEntity(0L, "ACR_NOT_FER", "Acrésc. noite feriado", true, true, true, TipoCalculo.HORAS, false, 13),
            RubricaEntity(0L, "ACR_NOT_DESC", "Acrésc. noite descanso", true, true, true, TipoCalculo.HORAS, false, 14),
            RubricaEntity(0L, "FERIAS", "Subsídio de férias", true, false, false, TipoCalculo.FIXO, false, 15),
            RubricaEntity(0L, "NATAL", "Subsídio de Natal", true, false, false, TipoCalculo.FIXO, false, 16),
            // Descontos derivados: calculados sobre as bases de incidência (Fase 8.2b.1).
            // Não incidem sobre si mesmas — são descontos, não matéria colectável.
            RubricaEntity(0L, "D01", "Segurança Social (11%)", false, false, false, TipoCalculo.DERIVADO, true, 20),
            RubricaEntity(0L, "D02", "IRS", false, false, false, TipoCalculo.DERIVADO, true, 21),
            RubricaEntity(0L, "D04", "Sindicato (1%)", false, false, false, TipoCalculo.DERIVADO, true, 22),
            RubricaEntity(0L, "OUTROS", "Outros", true, true, true, TipoCalculo.MANUAL, false, 99)
        )
        iniciais.forEach { rubrica ->
            if (dao.contarPorCodigo(rubrica.codigo) == 0) dao.inserir(rubrica)
        }
    }

    /**
     * Tabelas de retenção na fonte de 2026 (3 regiões × 11 tabelas). Idempotente.
     * Os dados estão em TabelasIRSIniciais.kt, gerados dos XLSX oficiais
     * (tools/extrair-tabelas-irs.py) — não se escrevem à mão.
     */
    private suspend fun garantirTabelasIRS() {
        val dao = database.tabelaIRSDao()
        if (dao.contarPorAno(ANO_IRS) != 0) return
        dao.inserirTodas(TABELAS_IRS_2026)
    }

    /**
     * Catálogo de municípios com feriado municipal (Fase 10). Idempotente: só escreve se a
     * tabela estiver vazia, porque os ids são fixos e apontados pelo contrato — reinserir
     * com REPLACE seria inofensivo, mas reescrever 22 linhas em cada arranque não.
     *
     * [MunicipioEntity.verificado] = false marca os que ainda não foram confirmados com a
     * fonte oficial; nesses, o próprio nome do feriado leva "(por verificar)" para a UI o
     * poder mostrar como aviso. Corrigir aqui à medida que forem validados.
     */
    private suspend fun garantirMunicipios() {
        val dao = database.municipioDao()
        if (dao.contar() != 0) return
        dao.inserirTodos(
            listOf(
                // Confirmados (verificado = true)
                MunicipioEntity(1, "Aveiro", "Aveiro", "CONTINENTE", 12, 5, "Santa Joana Princesa", true),
                MunicipioEntity(3, "Braga", "Braga", "CONTINENTE", 24, 6, "São João", true),
                MunicipioEntity(4, "Bragança", "Bragança", "CONTINENTE", 22, 8, "Nossa Senhora das Graças", true),
                MunicipioEntity(6, "Coimbra", "Coimbra", "CONTINENTE", 4, 7, "Rainha Santa Isabel", true),
                MunicipioEntity(7, "Évora", "Évora", "CONTINENTE", 29, 6, "São Pedro", true),
                MunicipioEntity(11, "Lisboa", "Lisboa", "CONTINENTE", 13, 6, "Santo António", true),
                MunicipioEntity(13, "Porto", "Porto", "CONTINENTE", 24, 6, "São João", true),
                MunicipioEntity(15, "Setúbal", "Setúbal", "CONTINENTE", 15, 9, "Bocage", true),
                MunicipioEntity(16, "Viana do Castelo", "Viana do Castelo", "CONTINENTE", 20, 8, "Nossa Senhora da Agonia", true),
                MunicipioEntity(18, "Viseu", "Viseu", "CONTINENTE", 21, 9, "São Mateus", true),
                MunicipioEntity(22, "Funchal", "Funchal", "MADEIRA", 21, 8, "Nossa Senhora do Monte", true),

                // Por verificar (verificado = false)
                MunicipioEntity(2, "Beja", "Beja", "CONTINENTE", 6, 3, "São Tiago (por verificar)", false),
                MunicipioEntity(5, "Castelo Branco", "Castelo Branco", "CONTINENTE", 23, 4, "Nossa Senhora de Mércoles (por verificar)", false),
                MunicipioEntity(8, "Faro", "Faro", "CONTINENTE", 7, 9, "Nossa Senhora do Carmo (por verificar)", false),
                MunicipioEntity(9, "Guarda", "Guarda", "CONTINENTE", 27, 11, "Nossa Senhora da Guia (por verificar)", false),
                MunicipioEntity(10, "Leiria", "Leiria", "CONTINENTE", 22, 5, "Nossa Senhora da Encarnação (por verificar)", false),
                MunicipioEntity(12, "Portalegre", "Portalegre", "CONTINENTE", 23, 5, "São Tomé (por verificar)", false),
                MunicipioEntity(14, "Santarém", "Santarém", "CONTINENTE", 19, 3, "São José (por verificar)", false),
                MunicipioEntity(17, "Vila Real", "Vila Real", "CONTINENTE", 13, 6, "Santo António (por verificar)", false),
                MunicipioEntity(19, "Angra do Heroísmo", "Açores", "ACORES", 24, 6, "São João (por verificar)", false),
                MunicipioEntity(20, "Horta", "Açores", "ACORES", 24, 6, "São João (por verificar)", false),
                MunicipioEntity(21, "Ponta Delgada", "Açores", "ACORES", 29, 6, "São Pedro (por verificar)", false)
            )
        )
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
