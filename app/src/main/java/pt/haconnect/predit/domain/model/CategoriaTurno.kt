package pt.haconnect.predit.domain.model

enum class CategoriaTurno {
    TRABALHO,   // conta horas para a jornada
    FOLGA,      // descanso semanal — base do acréscimo de 200% da CCT
    FERIAS,     // ausência SDD: não desconta vencimento nem alimentação
    BAIXA,      // ausência CDD: desconta vencimento, alimentação e transporte
    FERIADO     // marcação manual; a partir da Fase 10 vem da tabela de feriados
}
