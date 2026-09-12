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
}
