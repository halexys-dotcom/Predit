package pt.haconnect.predit.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigracaoTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PreditDatabase::class.java
    )

    @Test
    fun migrar1Para2() {
        helper.createDatabase("teste-migracao-1-2", 1).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-1-2", 2, true, PreditDatabase.MIGRATION_1_2)
    }

    @Test
    fun migrar2Para3() {
        helper.createDatabase("teste-migracao-2-3", 2).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-2-3", 3, true, PreditDatabase.MIGRATION_2_3)
    }

    @Test
    fun migrar3Para4() {
        helper.createDatabase("teste-migracao-3-4", 3).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-3-4", 4, true, PreditDatabase.MIGRATION_3_4)
    }

    @Test
    fun migrar4Para5() {
        helper.createDatabase("teste-migracao-4-5", 4).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-4-5", 5, true, PreditDatabase.MIGRATION_4_5)
    }

    @Test
    fun migrar5Para6() {
        helper.createDatabase("teste-migracao-5-6", 5).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-5-6", 6, true, PreditDatabase.MIGRATION_5_6)
    }

    @Test
    fun migrar6Para7() {
        helper.createDatabase("teste-migracao-6-7", 6).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-6-7", 7, true, PreditDatabase.MIGRATION_6_7)
    }

    @Test
    fun migrar7Para8() {
        helper.createDatabase("teste-migracao-7-8", 7).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-7-8", 8, true, PreditDatabase.MIGRATION_7_8)
    }

    @Test
    fun migrar8Para9() {
        helper.createDatabase("teste-migracao-8-9", 8).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-8-9", 9, true, PreditDatabase.MIGRATION_8_9)
    }

    @Test
    fun migrar9Para10() {
        helper.createDatabase("teste-migracao-9-10", 9).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-9-10", 10, true, PreditDatabase.MIGRATION_9_10)
    }

    @Test
    fun migrar10Para11() {
        helper.createDatabase("teste-migracao-10-11", 10).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-10-11", 11, true, PreditDatabase.MIGRATION_10_11)
    }

    @Test
    fun migrar11Para12() {
        helper.createDatabase("teste-migracao-11-12", 11).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-11-12", 12, true, PreditDatabase.MIGRATION_11_12)
    }

    @Test
    fun migrar12Para13() {
        helper.createDatabase("teste-migracao-12-13", 12).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-12-13", 13, true, PreditDatabase.MIGRATION_12_13)
    }

    /**
     * 13a: as cinco colunas novas entram com DEFAULT, logo uma BD que já tenha parâmetros
     * salariais e um contrato passa a tê-los como APAA sem perder nada do que lá estava.
     */
    @Test
    fun migrar13Para14() {
        helper.createDatabase("teste-migracao-13-14", 13).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-13-14", 14, true, PreditDatabase.MIGRATION_13_14)
    }

    /**
     * 13a (correção): o Team Leader é nível XIII do CCT (equivalente ao APA-A), não um nível
     * "TL" à parte. A migração só mexe em dados — corrige as linhas semeadas com o nome antigo.
     */
    @Test
    fun migrar14Para15() {
        helper.createDatabase("teste-migracao-14-15", 14).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-14-15", 15, true, PreditDatabase.MIGRATION_14_15)
    }

    /**
     * 13b: a BD fica com as 11 categorias de segurança (22 linhas, 11 × 2 vigências), o Team
     * Leader passa a nível XXX e o SUP_FUNCAO sobe para a ordem 17. Só dados — o esquema é o mesmo.
     */
    @Test
    fun migrar15Para16() {
        helper.createDatabase("teste-migracao-15-16", 15).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-15-16", 16, true, PreditDatabase.MIGRATION_15_16)
    }

    /**
     * 13c: o subsídio de função do Team Leader passa a 52,46 € (524 600 em 1/10000 €). Só dados.
     */
    @Test
    fun migrar16Para17() {
        helper.createDatabase("teste-migracao-16-17", 16).apply { close() }
        helper.runMigrationsAndValidate("teste-migracao-16-17", 17, true, PreditDatabase.MIGRATION_16_17)
    }
}
