package pt.haconnect.predit.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Feriados nacionais e municipais (Fase 10). As datas esperadas são as oficiais: Páscoa pelo
 * calendário gregoriano e os feriados móveis derivados dela.
 */
class FeriadosTest {

    @Test
    fun `T1 - pascoa de 2026 e 5 de abril`() {
        assertEquals(LocalDate.of(2026, 4, 5), calcularPascoa(2026))
    }

    @Test
    fun `T2 - pascoa de 2024 e 31 de marco`() {
        assertEquals(LocalDate.of(2024, 3, 31), calcularPascoa(2024))
    }

    @Test
    fun `T3 - pascoa de 2025 e 20 de abril`() {
        assertEquals(LocalDate.of(2025, 4, 20), calcularPascoa(2025))
    }

    @Test
    fun `T4 - feriados nacionais de 2026 incluem fixos e moveis`() {
        val feriados = feriadosNacionais(2026)

        val esperados = listOf(
            LocalDate.of(2026, 1, 1),    // Ano Novo
            LocalDate.of(2026, 2, 17),   // Carnaval (Páscoa − 47)
            LocalDate.of(2026, 4, 3),    // Sexta-feira Santa (Páscoa − 2)
            LocalDate.of(2026, 4, 5),    // Domingo de Páscoa
            LocalDate.of(2026, 4, 25),   // Dia da Liberdade
            LocalDate.of(2026, 5, 1),    // Dia do Trabalhador
            LocalDate.of(2026, 6, 4),    // Corpo de Deus (Páscoa + 60)
            LocalDate.of(2026, 6, 10),   // Dia de Portugal
            LocalDate.of(2026, 8, 15),   // Assunção de Nossa Senhora
            LocalDate.of(2026, 10, 5),   // Implantação da República
            LocalDate.of(2026, 11, 1),   // Todos os Santos
            LocalDate.of(2026, 12, 1),   // Restauração da Independência
            LocalDate.of(2026, 12, 8),   // Imaculada Conceição
            LocalDate.of(2026, 12, 25)   // Natal
        )

        esperados.forEach { data ->
            assertTrue(
                "falta o feriado de $data",
                data.toEpochDay() in feriados
            )
        }
        // 10 fixos + 4 móveis, todos distintos: se dois coincidissem, o conjunto encolhia.
        assertEquals(14, feriados.size)
    }

    @Test
    fun `T5 - feriado municipal de 2026 usa o dia e o mes do municipio`() {
        val esperado = LocalDate.of(2026, 6, 13).toEpochDay()   // Santo António, Lisboa

        assertEquals(esperado, feriadoMunicipal(2026, 13, 6) ?: 0L)

        // Feriado móvel (dia/mês por definir) não se calcula: fica para futura extensão.
        assertNull(feriadoMunicipal(2026, 0, 0))
        // Data impossível (31 de fevereiro) também não rebenta: devolve null.
        assertNull(feriadoMunicipal(2026, 31, 2))
    }
}
