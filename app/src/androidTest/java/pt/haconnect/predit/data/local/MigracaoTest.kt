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
}
