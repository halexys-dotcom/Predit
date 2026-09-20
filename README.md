# Predit

Agenda profissional do APA-A (Assistente Principal de Portaria e Vigilância Aeroportuária).
App Android offline para planeamento de escalas de seguranca privada aeroportuaria: escala
mensal com projecao da rotacao, registo de horas reais, conferencia do recibo e estimador
salarial com o CCT.

**Versao atual: [1.0.0](https://github.com/halexys-dotcom/Predit/releases/latest)** — requer Android 8.0 (API 26) ou superior.

## Funcionalidades

- **Escala** — calendario mensal com projecao da rotacao, importacao do PDF do planeamento da empresa, feriados nacionais e municipais e ausencias (ferias, baixa, feriados)
- **Horario** — registo diario de horas reais, ciclo semestral de jornada e conferencia do recibo contra os calculos da app
- **Turnos** — tipos de turno personalizaveis (nome, abreviatura, cor, horario), 24 cores e rotacoes de 17 dias
- **Mais** — contrato de trabalho com categoria CCT, recibo de vencimento, ausencias registadas, importacao do horario, backups, atualizacoes e acerca

O estimador de recibo usa o motor CCT (valor/hora e multiplicadores), o IRS regional
(Continente, Acores, Madeira) e sinaliza automaticamente divergencias entre o calculo e o
recibo real (IRS, subsidios, horas). Os dados ficam todos no telemovel, com backups internos
e auto-backup no arranque.

## Instalacao

1. Descarregar `app-release.apk` do [ultimo release](https://github.com/halexys-dotcom/Predit/releases/latest)
2. Ativar "Instalar de fontes desconhecidas" nas definicoes do Android
3. Instalar o APK
4. Abrir a app e configurar o Contrato de Trabalho (categoria, municipio, data de admissao)

## Atualizacoes

A app verifica sozinha se ha versao nova em **Mais → Atualizacoes → Verificar agora**, a partir do
manifesto publicado em
`https://github.com/halexys-dotcom/Predit/releases/latest/download/version.json`.
Se houver versao nova, e so tocar em "Descarregar e instalar".

## Desenvolvimento

    .\gradlew.bat test                 # testes unitarios (JVM)
    .\gradlew.bat connectedAndroidTest # testes instrumentados (telemovel/emulador ligado)
    .\gradlew.bat assembleDebug        # APK de debug
    .\gradlew.bat assembleRelease      # APK de release (assinado)

- `minSdk 26`, `targetSdk 35`, Jetpack Compose + Room (SQLite local).
- As migracoes da base de dados vivem em `PreditDatabase` e os esquemas exportados em
  `app/schemas/` — cada versao nova precisa da sua migracao e do JSON gerado pelo KSP.
- Para publicar um release (keystore, AAB, APK, GitHub) ver [RELEASING.md](RELEASING.md).

