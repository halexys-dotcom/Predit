# Relatório de análise — Predit v2.1.0

Data: 2026-10-20 · Commit analisado: `18c1a84` (main)

## Âmbito e limites

- Análise **estática** (leitura de código). **Nada foi compilado nem executado**: o ambiente não tem Android SDK nem dependências Gradle, por isso os testes unitários e instrumentados não foram corridos.
- Foco: motor de cálculo (`EstimadorRecibo`, CCT, IRS), recibo/importações, backups, atualizações OTA, base de dados.
- Os achados de regra de negócio (CCT, IRS) estão marcados **VALIDAR**: o código pode estar certo e a regra ser intencional. Caminhos relativos a `app/src/main/java/pt/haconnect/predit/`.
- Estado de cada achado: **Por validar** (nenhum foi ainda confirmado em execução nem corrigido).

---

## A. Prioridade alta

### A1. Recibo inutilizável em 2027 (e em meses de 2025)
- **Onde:** `PreditApplication.kt:33,237` (só semeia IRS de 2026); `ReciboViewModel.kt:322` (pede escalões do ano do mês visível); `domain/calc/CalculoIRS.kt:92` (`require(escaloes.isNotEmpty())`); `ReciboViewModel.kt:373-398` (`runCatching` + `getOrElse`).
- **Cenário:** abrir a conferência de um mês de 2027 (ou 2025, cujos parâmetros CCT existem mas o IRS não) → `calcularIRS` lança → o `runCatching` apanha tudo e mostra "Ainda a carregar os parâmetros e as tabelas de retenção", para sempre, sem causa real.
- **Sugestão:** fallback para o último ano com tabelas (com aviso visível); distinguir "a carregar" de "sem tabela para o ano"; não engolir todas as exceções (nem `CancellationException`). Adicionar tabelas 2027 quando publicadas.
- **Urgência:** ~10 semanas até 01/01/2027.

### A2. Horas extra noturnas erradas quando o turno tem pausa
- **Onde:** `domain/calc/EstimadorRecibo.kt:200-201`.
- **Causa:** `minutosNaJanela(dia.inicioMin + jornadaDoDia, dia.fimMin, janela)`. A pausa está dentro do intervalo início–fim, logo a janela começa antes do fim real da jornada e pode medir mais que o `extra`; `diurno = extra - noturno` fica negativo.
- **Exemplo (cálculo à mão):** turno 14:00–22:30, pausa 30, trabalhado até 00:30, janela 21h–06h → extra = 120 min, noturno medido = 150 min, diurno = −30. Resultado: HSUP_NT sobrepago e minutos diurnos negativos a abater a outros dias.
- **Cobertura:** todos os testes de `EstimadorReciboTest.kt` usam `pausaMin = 0` → bug não coberto.
- **Sugestão:** iniciar a janela em `inicio + jornada + pausa`, limitar `noturno` a `extra` (`minOf`), `diurno = extra - noturno >= 0`. Acrescentar testes com pausa > 0 (incluindo atravessar a meia-noite). A mesma imprecisão de pausa existe no HNOT (`EstimadorRecibo.kt`, cálculo de `minutosNoturnos`).

### A3. Importação de recibo aplica-se ao mês errado
- **Onde:** `ui/horario/ReciboViewModel.kt:553-565` (`aplicarReciboImportado` usa `_anoMesAtual`, não `recibo.mesReferencia`).
- **Cenário:** PDF de agosto importado com setembro aberto → substitui as edições de setembro; ao "Guardar" grava em setembro. A mensagem diz "Recibo de agosto importado".
- **Sugestão:** comparar `mesReferencia` com o mês visível; pedir confirmação ou navegar para o mês do recibo. Se `mesReferencia` for null, pedir ao utilizador.

### A4. Restauro de backup sem rede de segurança
- **Onde:** `data/backup/BackupManager.kt:278-283` (apaga `predit.db`, `-wal`, `-shm`, `-journal` e só depois `copyTo`); `importarDeUri` valida apenas cabeçalho SQLite + `user_version`.
- **Riscos:** (1) falha na cópia (disco cheio) → sem BD e sem rollback; (2) ficheiro com versão certa mas esquema errado/corrompido → Room rejeita a abertura em cada arranque (crash loop), sendo a flag de restauro já limpa.
- **Sugestão:** copiar para ficheiro temporário na mesma pasta, validar (`PRAGMA integrity_check` + abrir com o esquema), guardar a BD atual como `.bak` e só então renomear; reverter se a abertura falhar.

---

## B. Prioridade média

### B1. Backups automáticos duram só 10 arranques
- `PreditApplication.kt:118-122`: um backup por arranque + `limparAntigos(manterUltimos = 10)`. Após 10 aberturas, todos os automáticos anteriores (incluindo os bons) desaparecem; os manuais ficam.
- **Sugestão:** retenção por dia (ex.: 1/dia nos últimos 7–14 dias), ou não criar se já existir um automático recente. `listar()` abre cada ficheiro para ler `user_version` (custo em listas longas).

### B2. Valores inválidos no contrato "avariam" o recibo
- `ui/contrato/ContratoScreen.kt:139,174`: só a data de admissão é validada; `horarioSemanalH = 0` ou negativo é aceite.
- `domain/calc/CalculoCCT.kt` faz `require(0 < horarioSemanalH)` → cai no `runCatching` de A1 → "A carregar" para sempre.
- **Sugestão:** validar no ecrã (horas 1–60, dependentes ≥ 0 e limite razoável, titulares ∈ {1,2}) e coagir no `ContratoRepository`.

### B3. VENC a zero sem projeção nem registos (desfasamento = 1)
- `domain/calc/EstimadorRecibo.kt:276`: `diasUteisTotais == 0 → vencBruto = 0`. A saída antecipada só olha para o mês das variáveis. Com desfasamento 1 e mês corrente sem rotação nem dias reais (ex.: modo PDF, PDF por importar) o VENC estima 0 em vez do fixo.

### B4. Estimador ignora `tipoEscala = PDF_MENSAL` — VALIDAR
- O estimador só usa a projeção da rotação; dias importados do PDF têm `tipoTurnoId = null` e a jornada prevista cai para 8 h (`jornadaPrevistaDoDia`). Um turno de 10 h do PDF gera 2 h extra a 150%. Pode ser intencional. `HorarioViewModel` tem uma NOTA a admitir limitação equivalente.

### B5. Atualização OTA sem verificação do APK
- `data/update/ApkDownloader.kt`, `UpdateRepository.kt`, `domain/update/ResultadoVerificacao.kt`: `apkUrl` do manifesto não validado (https/host); sem hash/tamanho; `compararVersoes` ignora `manifest.minSdk` (oferece update que não instala em aparelhos antigos).
- O Android ainda verifica a assinatura na instalação → risco real baixo.
- Polling: se o download desaparecer do DownloadManager (cursor vazio) espera os 15 min de timeout. Duplo toque em "Descarregar" pode lançar dois downloads (`VerificarAtualizacoesViewModel.descarregar` sem guarda).

### B6. Importação do PDF do planeamento
- `domain/importacao/PdfPlannerParser.kt:36`: `\b(20\d{2})\b` não encontra o ano em `Horario_2026.pdf` (o `_` é carácter de palavra) → sem ano, `mesReferencia = null` e a importação fica bloqueada.
- `ui/importacao/ImportacaoViewModel.kt:149`: `numFolgas = lengthOfMonth - dias.size` conta dias de fora do mês.
- `ImportacaoViewModel.confirmarImportacao`: `existentesMap` só cobre o mês de referência; dias fora dele não são comparados com registos existentes. O `@Upsert` com índice único em `dia_real.data` e `id = 0` pode falhar silenciosamente, e a contagem "importados" fica errada.
- `aCarregar` fica a `true` se a gravação lançar exceção (sem `try/catch`).

### B7. Ano do mês de referência do recibo PDF
- `domain/importacao/ReciboPdfParser.kt:579-585`: usa o ano da data de fecho com o mês do "Período". Recibo de dezembro com fecho em janeiro → "dezembro do ano seguinte".

---

## C. Regras de negócio a VALIDAR com o titular

| # | Tema | Onde | Dúvida |
|---|------|------|--------|
| C1 | Carnaval tratado como feriado nacional | `domain/calc/Feriados.kt:30` | Não é feriado obrigatório (tolerância de ponto). Aqui dispara HSUP_*_FER e DESC_FER. Confirmar com recibos reais. |
| C2 | Corte do VENC por baixa | `EstimadorRecibo.kt:262-282` | Proporcional a dias úteis de trabalho da rotação; a prática legal costuma ser dias de calendário/30. |
| C3 | Jornada do ciclo semestral | `domain/calc/Jornada.kt:12` | Fixa em 173:19 (40 h); ignora `horarioSemanalH` do contrato e férias/baixas. |
| C4 | Escalões IRS anual 2026 | `domain/calc/IrsAnual.kt:27-41` | Fonte citada "Lei n.º 108/XVII/2" tem formato de proposta de lei; confirmar vigência e taxas (12,20%…). |
| C5 | IRS Jovem anual | `IrsAnual.kt` (`calcularIrsAnual`) | Isenção aplicada sobre rendimento coletável após abatimento; confirmar contra a regra legal. |
| C6 | Dedução específica | `IrsAnual.kt` | Usa 8,54×IAS fixo; ignora contribuições SS (maiores acima de ~41,7 k€/ano). Limitação já documentada. |
| C7 | Descontos desconhecidos | `domain/importacao/MapeamentoRecibo.kt` | Códigos de desconto não reconhecidos só mapeiam para o catálogo; se forem para OUTROS entram como abono (incide SS/IRS). |

---

## D. Prioridade baixa / higiene

- `app/build.gradle.kts:68`: release assinado com a chave de **debug** se faltar `keystore.properties`, sem falhar o build. Sugestão: falhar o `assembleRelease` nesse caso.
- `gradle.properties`: caminhos absolutos do Windows (`org.gradle.java.home`, `android.prefs.root`) impedem build noutra máquina/CI.
- `README.md`: "Versão atual 1.0.0" e `targetSdk 35`; o código está em 2.1.0 / `targetSdk 36`.
- Backups em `Android/data/.../files/backups` legíveis por outras apps no Android 8–10 (dados salariais).
- Sem `try/catch` em `ReciboViewModel.confirmarMapeamento` e `importarRecibo`: exceção da BD/parser fecha a app.
- `domain/importacao/MapeamentoRecibo.kt`: primeiro KDoc solto, sem declaração associada.
- `data/pdf/PdfBoxExtractor.kt`: sem limite de tamanho/páginas do PDF escolhido (risco só local).

---

## E. Verificado e correto

- Aritmética monetária (`dividirArredondando`, frações exatas dos multiplicadores CCT).
- Algoritmo da Páscoa (Meeus/Jones/Butcher).
- Migrações Room 1→22 encadeadas; `VERSAO_BD = 22` em sincronia com `@Database`; `schemas/22.json` presente.
- Sem keystore nem `keystore.properties` versionados; `.gitignore` cobre-os.
- `docs/version.json` coerente com `build.gradle.kts` (25 / 2.1.0).
- ATRASO não é descontado duas vezes (informativa, sem incidência SS/IRS; sindicato calculado sobre o VENC já líquido).

---

## F. Ordem de ataque sugerida

1. **A1** (prazo: antes de 01/01/2027) + **B2** (partilham o `runCatching` mascarador).
2. **A2** com testes de pausa > 0.
3. **A3** e **A4**.
4. **B1**, **B3**, **B6**, **B7**.
5. Validações **C1–C7** com o titular antes de alterar regras.
6. Higiene (secção D).

Cada correção deve incluir teste unitário (JVM) quando for lógica de domínio; A4 e B1 exigem teste instrumentado (`BackupManagerTest`).
