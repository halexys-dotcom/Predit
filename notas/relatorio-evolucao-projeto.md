# Predit — Relatório de evolução do projeto

**Documento de registo.** Produzido por leitura do repositório (histórico git completo, código-fonte, documentação e relatório de auditoria estática de 2026-10-20). **Nenhum ficheiro de código, configuração ou documento existente foi alterado** — este relatório é o único artefacto criado.

| Item | Valor |
|---|---|
| Projeto | Predit — agenda profissional do APA-A (Assistente Principal de Portaria e Vigilância Aeroportuária) |
| Repositório | `github.com/halexys-dotcom/Predit` (branch `main`) |
| Commit de referência | `178c68a` — 2026-10-28 13:08 UTC |
| Versão em produção | **2.1.4** / `versionCode 29` |
| Esquema da base de dados | versão **24**, 16 entidades, 23 migrações |
| Commits no histórico | **132** (2026-09-11 → 2026-10-28) |
| Tags | `v1.0.0`, `v1.1.0`, `v1.1.1`, `v1.1.2`, `v2.0.0-rc1`, `v2.0.0`, `v2.0.1`, `v2.0.3`, `v2.0.4`, `v2.0.4-estavel`, `v2.1.0`, `v2.1.1`, `v2.1.2`, `v2.1.3`, `v2.1.4`, `parser-icts-base`, `videos-tutorial` |
| Testes unitários (JVM) | **282** em 33 ficheiros |
| Testes instrumentados | 6 ficheiros (Room, migrações, Compose) — ver §5.8 |
| Stack | Kotlin 2.0.21, Jetpack Compose (BOM 2024.11.00) + Material 3, Room 2.6.1 (KSP), Navigation Compose 2.8.5, PDFBox-Android 2.0.27.0 |
| Build | AGP 8.13.2, Gradle 8.13, `minSdk 26`, `compileSdk`/`targetSdk 36`, Java 11 |

## Índice

1. O que é o Predit
2. Arquitetura e convenções de engenharia (R1–R14)
3. Cronologia: da Fase 0 à 2.1.4
4. Regras de negócio implementadas
5. Bugs, incidentes e correções
6. Qualidade e validação
7. Releases publicadas
8. Estado atual e pendências
9. Anexos

## Fontes consultadas

- `git log` completo (132 commits), tags, corpos de mensagem e `--stat` dos commits de correção.
- Código atual: `domain/calc` (Escala, EstimadorRecibo, CalculoIRS, CalculoCCT, IrsAnual, IrsJovem, Jornada, Feriados, Subsidios, ContagemAusencia, ComparacaoDia, Duracao), `domain/importacao`, `data/local` (PreditDatabase e migrações), `data/backup`, `data/update`, `ui/*`, `PreditApplication`.
- Documentação: `README.md`, `RELEASING.md`, `release-notes.md`, `notas/relatorio-site-e-videos.md`, `docs/version.json`, `docs/index.html`.
- Relatório de análise estática `RELATORIO-ANALISE.md`, existente apenas no branch `docs/relatorio-analise` (commit `41842b3`, 2026-10-20) — não está no `main`.
- KDoc e comentários do código, que neste projeto funcionam como registo de decisões: datam a regra, mostram o número que a sustenta e dizem qual foi a alternativa rejeitada.

---

## 1. O que é o Predit

Aplicação Android **offline** para quem trabalha em escalas de segurança privada aeroportuária, sob o CCT do setor. Não é um calendário genérico: cruza a escala planeada, as horas realmente feitas e o recibo de vencimento, aplicando as regras do CCT e as tabelas de retenção de IRS.

### 1.1 Módulos visíveis

| Separador | O que faz |
|---|---|
| **Escala** | Calendário mensal sem scroll vertical, projeção da rotação, importação do PDF da empresa, feriados nacionais e municipal, ausências (férias, baixa, folga, feriado), registo do dia e total de horas no rodapé |
| **Horário** | Registo diário das horas reais, comparação projetado vs. real, ciclo semestral de jornada (173:19/mês) e acesso à conferência do recibo |
| **Turnos** | Tipos de turno personalizáveis (nome, abreviatura, cor entre 24 opções, horário, pausa, posto) e rotações com o ciclo de dias que o utilizador definir |
| **Mais** | Contrato (categoria CCT, região fiscal, município, IRS Jovem, desfasamento de variáveis, sindicato), Recibo, Simulador de IRS, Ausências, Importar Horário (PDF), Backups, Atualizações e Acerca |

### 1.2 Distribuição

- **Google Play** (`pt.haconnect.predit`, flavor `playstore`): sem permissão `INTERNET`, sem OTA.
- **GitHub Releases** (flavor `github`): APK assinado e OTA próprio a partir do `version.json` publicado na release *latest*.
- **Site** em GitHub Pages (`docs/`): página pública, política de privacidade, guia "Como usar o Predit" e seis vídeos de tutorial.

---

## 2. Arquitetura e convenções de engenharia

### 2.1 Camadas

```
app/src/main/java/pt/haconnect/predit/
  domain/calc/         motor de cálculo puro (sem Android, sem Room)
  domain/importacao/   parsers de PDF e mapeamento de rubricas (puro)
  domain/model/        modelos de domínio + Dinheiro (aritmética exata)
  data/local/          Room: 16 entidades, migrações, sementes
  data/repository/     mediação DAO <-> domínio (Flow)
  data/pdf/            extração de texto com coordenadas (PDFBox)
  data/backup/         backups internos e restauro
  data/update/         verificação, download e instalação OTA
  ui/<ecra>/           Compose + ViewModel por ecrã
  ui/navigation/       NavHost, barra inferior e gate de primeiro arranque
```

O domínio é testável sem emulador: `Escala`, `EstimadorRecibo`, `CalculoIRS`, `CalculoCCT`, `Jornada`, `Feriados`, `IrsAnual`/`IrsJovem` são funções puras que recebem listas e devolvem resultados.

### 2.2 Regras de engenharia aplicadas (R1–R14)

Não estão num único sítio: foram-se fixando ao longo do projeto e vivem nos comentários, nos testes e no histórico. São o contrato a respeitar em qualquer alteração futura.

- **R1 — Domínio puro.** Nada de `Context`, Room ou `android.*` em `domain/calc` e `domain/importacao`. Se precisa de dados, recebe listas.
- **R2 — Dinheiro em inteiros.** Todos os valores monetários são `Long` em unidades de **1/10000 €** (65 653 = 6,5653 €). Nunca `Double`.
- **R3 — Multiplicar antes de dividir, arredondar no fim.** `dividirArredondando` é o único arredondamento: 65 653 × 35 ÷ 16 = 143 615,9375 → 143 616 (14,36 €), nunca 14,37 €.
- **R4 — Cada versão da BD tem migração e esquema exportado.** `version` (24) e `VERSAO_BD` andam juntos; as 23 migrações estão encadeadas e há teste instrumentado de migração.
- **R5 — `versionCode` sempre crescente, `versionName` X.Y.Z.** O OTA rejeita versões iguais ou inferiores; `docs/version.json` acompanha sempre o `build.gradle.kts` e aponta para o asset com o nome exato do output (`app-github-release.apk`).
- **R6 — Dois flavors, uma base de código.** `github` (`OTA_ATIVO = true`, com `INTERNET`) e `playstore` (`OTA_ATIVO = false`, sem `INTERNET`). Nenhuma funcionalidade pode obrigar a versão da Play Store a pedir rede.
- **R7 — Texto em espaço apertado é `TextoSemQuebra`.** `maxLines = 1` e `softWrap = false`. Uma série de bugs de quebra ("Hoj/e", "TRABALH/O", "Calendári/o", "Em/vigo/r", "08:/0/0") fixou a regra.
- **R8 — Ícones só do conjunto base.** O projeto **não** usa `material-icons-extended`; um ícone novo não justifica a dependência.
- **R9 — Teste para cada regra de domínio.** As correções de cálculo entram com teste (`EstimadorReciboTest`, `EscalaTotalHorasTest`, `EscalaModoPdfTest`, `ReciboPdfParserTest`, ...). Quando a regra vem do CCT e foi confirmada pelo utilizador, o teste cita a data e a origem.
- **R10 — Histórico legível.** Mensagens em português, prefixos `feat/fix/chore/docs/refactor/style`, referência à fase ("Fase 28g") e, quando útil, corpo a explicar causa e efeito. Trabalho em branches (`claude/...`, `fix/auditoria-v2.1.0`) integrado por pull request.
- **R11 — Privacidade por omissão.** Dados só no telemóvel; nada é enviado para servidores. A versão da Play Store nem tem permissão de rede.
- **R12 — Backups com cópia consistente.** `VACUUM INTO` (com *fallback* para checkpoint + cópia em Android 8), ficheiros em `Android/data/<pacote>/files/backups/` e expurgo dos mais antigos.
- **R13 — A decisão fica escrita no código.** Os KDoc registam a data da decisão, o número que a sustenta e a alternativa rejeitada — é o que permite hoje reconstruir o "porquê" de cada fórmula.
- **R14 — Validação antes de publicar.** `test` verde, os dois flavors a compilar, APK assinado com a keystore de release, SHA-256 do APK descarregado igual ao local e confirmação de que a release ficou *latest*.

### 2.3 Dependências e build

- `libs.versions.toml` centraliza versões: AGP 8.13.2, Kotlin 2.0.21, KSP `2.0.21-1.0.28`, Room 2.6.1, Compose BOM `2024.11.00`, Navigation 2.8.5, Espresso 3.6.1, JUnit 4.13.2.
- `pdfbox-android 2.0.27.0` é a dependência de terceiros relevante: extrai texto **com posição**, indispensável para ler recibos e planeamentos.
- `buildConfig` ligado para expor `OTA_ATIVO`; `isMinifyEnabled = false`; assinatura de release a partir de `keystore.properties` (nunca versionado).
- `androidTest` recebe os esquemas do Room e os PDFs de origem como *assets*, para os testes de migração e de extração.

---

## 3. Cronologia: da Fase 0 à 2.1.4

O desenvolvimento seguiu fases numeradas, cada uma com um objetivo fechado e testes próprios. Em 48 dias (11 de setembro a 28 de outubro de 2026) saíram 132 commits e 15 versões instaláveis.

### 3.1 Mapa de fases

| Fase | Data | Commits de referência | O que ficou |
|---|---|---|---|
| 0 | 11/09 | `d062cee` | Setup Gradle, navegação base, Room configurado |
| 1 + 1.1 | 11/09 | `d191290` | CRUD de tipos de turno, sub-separadores Turnos/Rotações, esquemas exportados |
| 2 + 2B | 11–12/09 | `7f35342`, `2a91701`, `90958c4`, `a6c9649` | Rotações, motor de projeção, editor em ecrã completo, paleta compacta, resumo do padrão |
| 3 | 12/09 | `c89625f`, `4be16f2`, `3c80103` | Motor de projeção (`Escala.kt`), vista mensal sem scroll, modal do dia |
| 4 | 12/09 | `498b9f4`, `114935c`, `7e9f491` | Ausências: entidade, migração 2→3, `aplicarAusencias`, UI de marcação |
| 5 (pré) | 12/09 | `88bc222`, `f0d1d21`, `d9d9149` | Contrato do utilizador, migração 3→4, janela noturna, gate de primeiro arranque |
| 5 + 6 | 15/09 | `ce856c6` | Registo real do dia e importação do planeamento mensal por PDF |
| 7 | 15/09 | `47d5a28` | Motor de jornada, ciclo semestral e ecrã de Conferência |
| 8 / 8.2b / 8.2c / 8.3 / 8.4 / 8.5 | 16–17/09 | `e640998`, `041a177`, `3733c8d`, `df2e9c8`, `684e51e` | Motor CCT e IRS, catálogo de rubricas, estimador, persistência e conferência do recibo, subsídios, `DESC_FER`/`DESC_DESC` |
| 10 + 10b | 17/09 | `fbc93ce`, `1554523` | Feriados nacionais e municipais; feriados no contador de ausências |
| 11b | 17/09 | `1ff08b7`, `68b049e` | Backups internos e restauro; mais tarde exportar/importar por SAF |
| 11d.1 + 11d.2 | 18/09 | `78e9013`, `b260dd8` | Fundação OTA (keystore, signing, permissões) e verificação/instalação |
| 12 / 12c / 12d | 18–20/09 | `5d1b0af`, `044ba9c`, `b0dc150` | Polimento visual, calendário maior, feriados na célula, 24 cores, categorias CCT, subsídio de função |
| 13a–13c | 20/09 | `b0dc150` | Subsídio de função, categorias CCT e etiquetas do recibo |
| **1.0.0** | 20/09 | `563076f`, `50bd828` | Primeira release instalável (`versionCode 15`) e README completo |
| 15 + 16 | 22/09 | `b1fd9c5` | Versão 1.1.1 e rodapé do Recibo |
| 17 + 18 | 22/09 | `f3ee53b` | Correção das horas noturnas e tipografia dos ecrãs completos |
| 1.1.0 / 1.1.2 | 22/09 | `e9cf32a` | Rotação em vigor herda a âncora; OTA por *polling* do DownloadManager |
| 12d–21d | 25/09 | `d0657aa`, `3fc4eac` | Polimento, IRS Jovem, simulador anual → **2.0.0** (`versionCode 20`) |
| Play Store | 28–29/09 | `05eb18f`, `bcf5373`, `7d1f02d`, `3818a5f`, `c5279cc` | OTA desligado no flavor `playstore`, permissão de instalação removida, política de privacidade, ícone do Mais → **2.0.1** |
| 26 + 27 | 02–05/10 | `4597db8`, `7480356`, `82ad9fb`, `b917af8`, `4afd19d`, `687f621` | Parser de noturnos no PDF (**2.0.3**), folga a substituir turno, desfasamento de variáveis, D04 condicional → **2.0.4** |
| 28a–28h | 15–18/10 | `5c3c1c9`, `e472d82`, `95c3676`, `cc75adf`, `a9b0e0e`, `3dfc141`, `1a135a9`, `e09a8fa` | Parser do recibo PDF, kerning, mapeamento configurável de rubricas, parser adaptativo, fusão de folhas, crachás |
| 29 (regras) | 19–21/10 | `ffc2b12`, `d59ca07`, `bc8792e`, `0bc5e53`, `90840a0`, `bda5333`, `8e3c1af`, `3485376`, `8ef456f` | Regras CCT de atrasos e horas extra, VENC proporcional, correções da auditoria, R51, D05/OUTROS_DESC, visibilidade de rubricas → **2.1.0** e **2.1.1** |
| 2.1.2 | 21/10 | `56e2154` | Fórmula composta do IRS, modo PDF no recibo, gravação do cabeçalho |
| 2.1.3 | 27/10 | `61c4948` | Previsto do PDF guardado, folgas implícitas no recibo, migração 23→24 |
| 2.1.4 | 27/10 | `d548ff5` | Férias ou baixa num dia que já era folga deixam de somar 8h ao total |
| Site e vídeos | 25–28/10 | `5b0ba1b`, `bbb2e54`, `71fb01b`, `650b6af`, `3f044f5`, `e648357`, `178c68a` | Site na versão pública da Play Store, guia "Como usar", seis vídeos, link no ecrã Acerca |

### 3.2 Fundações e escala (11–12/09)

Os dois primeiros dias fecharam o esqueleto: um `NavHost` com quatro separadores, Room com esquemas exportados e a regra de que o motor vive fora do Android. A Fase 2 trouxe a peça que sustenta tudo o resto — a **rotação** (ciclo de N dias, um tipo de turno por posição) e a **projeção** que a repete no calendário a partir de uma data-âncora. A Fase 2B foi quase toda UX: o editor passou a ecrã completo, a paleta cresceu, o resumo do padrão ficou legível ("1" omitido em ocorrências únicas) e o toque acidental no `+` foi travado.

A Fase 3 separou motor e vista: `projetarIntervalo` devolve os dias de um intervalo e a grelha mensal só desenha. A Fase 4 acrescentou as ausências e, com elas, a primeira regra difícil: uma ausência **não apaga** folgas nem feriados (`7e9f491`), e um feriado projetado continua feriado mesmo que haja férias por cima. No mesmo dia entrou a correção das chaves de composição por data (`c94a7b8`), porque as células recicladas do `LazyVerticalGrid` mostravam estado do dia anterior.

### 3.3 Contrato, importação e recibo (15–17/09)

A Fase 5 introduziu o contrato como fonte única de verdade (vencimento base, horário semanal, data de admissão, município, situação familiar) e o *gate* de primeiro arranque que impede usar a app sem ele. Com o contrato no sítio, a Fase 5+6 ligou o dia real — registo manual e importação do PDF do planeamento — e a Fase 7 fechou o ciclo semestral de 173:19 por mês. A Fase 8 montou o recibo: motor CCT e IRS, catálogo de rubricas com incidências, estimador, persistência e ecrã de conferência.

### 3.4 Distribuição e a primeira v2.0 (18–25/09)

A Fase 11d foi o primeiro contacto com o mundo exterior: keystore, assinatura de release, permissão de instalação e um repositório de atualizações que compara `versionCode`, descarrega o APK e lança o instalador. A Fase 12 poliu o que já existia (ações da barra em ícone para o título caber, calendário maior, feriados na célula) e a Fase 12c expandiu a paleta de 8 para 24 cores.

O bloco 12d–21d (`d0657aa`) é o maior salto funcional do projeto: categorias CCT completas, IRS Jovem, simulador de IRS anual e o polimento que levou à **v2.0.0** (`versionCode 20`), no dia 25. No mesmo dia, o teste `ReciboConferenciaTest` passou a derivar o esperado do CCT da categoria do contrato, em vez de números escritos à mão.

### 3.5 Play Store e site (28/09–05/10)

Publicar na Play Store obrigou a separar o que a app pode fazer consoante a origem: o flavor `playstore` ficou **sem** `INTERNET`, sem permissão de instalação e com o ecrã de atualizações a explicar que as versões vêm da loja (`05eb18f`, `bcf5373`). Ao mesmo tempo nasceu o site (`docs/index.html`), a política de privacidade e o ícone próprio do ecrã Mais.

A 2.0.3 (`4597db8`) corrigiu a importação de turnos noturnos do PDF, em que o turno atravessa a meia-noite. A 2.0.4 trouxe o desfasamento de pagamento das variáveis (`82ad9fb`, `b917af8`, `953b424`) — para quem recebe em atraso, como a ICTS, em que suplementares e feriados do recibo refletem o mês anterior enquanto o VENC é do mês corrente —, o switch de sindicalizado com D04 condicional (`4afd19d`) e a regra "FOLGA manda sobre tudo" (`7480356`).

### 3.6 O recibo real: PDF, mapeamento e adaptação (15–18/10)

A Fase 28 foi a mais longa e a que mais ensinou sobre o mundo real. O parser do recibo (`5c3c1c9`) lê o PDF por posição, não por ordem de extração; `e472d82` corrigiu o *kerning*, que partia palavras em duas ("Venci mento"); a migração seguinte trouxe o **mapeamento configurável** de rubricas por empresa (`95c3676`, `aac91dd`, `adfb0cc`, `cc75adf`), para que um código desconhecido (o D05 da Fundação) não bloqueasse a importação.

Ainda no mesmo bloco: somar em vez de substituir quando o mesmo código aparece em duas linhas (`aa43e34`, o caso dos turnos ICTS), *guard* de mês e nota dos totais declarados (`8c4b96a`), rubricas não conferidas em modo leitura (`4292dc1`), fusão de folhas duplicadas do mesmo recibo (`3dfc141`), parser adaptativo a layouts de outras empresas (`a9b0e0e`) e conclusão da fase com o ecrã de conferência (`1a135a9`).

### 3.7 Auditoria estática e a v2.1.0/2.1.1 (19–21/10)

A 19 de outubro o motor foi confrontado com o recibo real e com uma auditoria estática. `ffc2b12` e `d59ca07` fixaram três regras do CCT que estavam a ser aplicadas a mais:

1. os **atrasos deduzem-se uma única vez, no VENC** — a rubrica "Ausências" fica informativa, senão o mesmo valor saía duas vezes do líquido;
2. as **horas extra são por dia**: um dia curto não se compensa com um dia longo, e um dia coberto por ausência não paga suplemento;
3. o **VENC é proporcional aos dias efetivos** — férias contam como pagas, a baixa corta.

Seguiu-se a ronda de correções da auditoria (`bc8792e`, achados A1, A2, A3, B2, B6, C7 e o VENC absoluto), o complemento de baixa R51 a incidir SS e IRS (`90840a0`), o D05 a mapear para uma rubrica de desconto própria (`0bc5e53`) e a visibilidade das linhas do recibo: feriados só em meses com feriado (`8e3c1af`), Natal e férias só quando fazem sentido (`3485376`), "Outros" a zero escondido (`bda5333`, `8ef456f`).

### 3.8 2.1.2, 2.1.3 e 2.1.4 (21–27/10)

A 2.1.2 (`56e2154`) corrigiu a **fórmula composta** do IRS: os dois primeiros escalões de 2026 não têm parcela fixa, trazem `taxa × k × (X − R)`, e sem isso uma base de 1 105,35 € dava 0,00 € de retenção quando a tabela manda reter ~7,15 %. No mesmo commit, o modo PDF passou a valer também para o recibo e o cabeçalho passou a gravar-se de forma estável.

A 2.1.3 (`61c4948`) guardou o **previsto do PDF** em colunas próprias (migração 23→24) e acrescentou as folgas implícitas: no modo PDF, os dias do mês sem linha do PDF passam a ser folga, para que um dia trabalhado de folga seja pago como descanso e não como dia normal.

A 2.1.4 (`d548ff5`) fechou o total de horas do rodapé da Escala: férias ou baixa num dia que já era folga deixam de somar 8h. O total passou a ser calculado em `minutosDoDiaNaEscala`, no domínio, e não no ViewModel — para poder ser testado.

No mesmo período, o site passou a divulgar a versão pública da Play Store, ganhou o guia "Como usar o Predit" passo a passo, seis vídeos com leitor próprio e um selo de versão que segue o `docs/version.json`. O último commit (`178c68a`) levou o guia para dentro da app, como link no cartão "Como usar" do ecrã Acerca.

---

## 4. Regras de negócio implementadas

Esta secção descreve o que a app faz hoje, com os números tal como estão no código. Onde a regra foi confirmada pelo utilizador ou por um recibo real, a data aparece — é a garantia de que não é uma interpretação da app.

### 4.1 Rotação, aplicação e projeção

- Uma **rotação** é um ciclo de N dias — o comprimento é o número de posições definidas no editor — com um tipo de turno por posição; uma **aplicação** liga a rotação a uma data de início.
- A projeção repete o ciclo a partir da **âncora** da aplicação em vigor; ao aplicar uma nova rotação, a âncora herdada é a da anterior, para o ciclo não reiniciar (`e9cf32a`).
- Sem aplicação não há projeção: a grelha mostra o mês vazio e o ecrã explica o que falta.
- As ausências são aplicadas **por cima** da projeção, com três regras fixas: um dia projetado como `FERIADO` mantém-se feriado (as férias não apagam feriados); ao fim de semana sobrepõe-se o tipo de folga do catálogo (`folgaTipoId`); a ausência só substitui um dia vazio ou de categoria `TRABALHO`.

### 4.2 Modo de escala por PDF mensal

- `TIPO_ESCALA_PDF_MENSAL` faz a grelha ler os `dia_real` importados do PDF da empresa em vez da rotação (`diasDoPdf`).
- Um dia conta como vindo do PDF se tiver origem `PDF` **ou** se guardar as colunas `previsto*` — é isso que mantém na grelha um dia importado e depois corrigido pelo utilizador (`veioDoPdf`).
- O tipo de turno **não** é guardado: deriva-se das horas previstas (`tipoDerivadoDoPdf`). Sem horas é folga; com horas procura um tipo de trabalho com o mesmo horário e, se não existir, usa o primeiro de trabalho.
- `minutosPrevistos` leva a duração do turno impresso. É esta a régua dos suplementos e dos atrasos — a duração do tipo derivado não serve, senão um turno de 12h medido contra um tipo de 8h inventava 4h de atraso (ou 4h de suplemento).
- Desde a 2.1.3, os dias do mês **sem linha no PDF** tornam-se folgas implícitas (`minutosPrevistos = 0`), para que trabalhar uma dessas folgas seja pago como descanso trabalhado.

### 4.3 Ausências e dias consumidos

- Categorias: `FERIAS`, `BAIXA`, `FOLGA`, `FERIADO`.
- `diasConsumidos` conta **dias úteis** (segunda a sexta) entre o início e o fim, excluindo feriados nacionais — usado para mostrar o custo da ausência em dias.
- No total do rodapé, uma ausência efetiva vale **8h fixas** se o dia era de trabalho, e **0h** se já era folga. O valor é fixo (e não a duração do turno) porque é assim que a app conta desde a primeira versão e mudar isso alterava meses já validados.
- Uma ausência de categoria `FOLGA` vale sempre 0, mesmo em cima de um dia de trabalho — é a regra "FOLGA manda sobre tudo" (`7480356`).

### 4.4 Total de horas da Escala (2.1.4)

`minutosDoDiaNaEscala` (domínio, testado) decide o que cada dia soma:

| Situação | Minutos |
|---|---|
| Ausência efetiva de categoria `FOLGA` | 0 |
| Ausência efetiva noutra categoria, em dia que era de trabalho | 480 (8h) |
| Ausência efetiva, em dia que já era folga | 0 |
| Sem ausência, com registo real | duração real (fim − início − pausa) |
| Sem ausência e sem registo | duração do turno efetivo, se for de trabalho; senão 0 |

"Era de trabalho" lê-se de forma diferente em cada modo: no modo PDF pelas horas previstas do dia (uma folga do PDF não tem linha), na rotação pela categoria do tipo projetado.

### 4.5 Jornada e ciclo semestral

- Jornada mensal do CCT (cláusula 79.ª): **173,33 h** → 10 399 min = **173:19**. A base legal de 40 h/semana daria 173:20 (10 400); fica 173:19 por decisão registada (1 min/mês, 6 min/semestre).
- O ciclo é de 6 meses: 62 394 min = **1039:54**, com dois semestres civis (jan–jun, jul–dez).
- Conta mensal: `delta = real − 10 399`. O saldo acumula; só quando fica **positivo** é que há extra a pagar — e nesse mês o extra pago é o saldo inteiro, com o saldo a voltar a zero. Em défice, o saldo negativo transita e abate aos meses seguintes.

### 4.6 Valor/hora e multiplicadores

- Valor/hora (arredondado ao cêntimo, para bater com o recibo): `(vencimentoBase × 12) ÷ (52 × horárioSemanal)`. Em 2026 com 40 h: 65 700 → **6,57 €/h**; em 2025: 62 100 → 6,21 €/h.
- Valor/dia de 8h = valor/hora × 8; valor/minuto = valor/hora ÷ 60 (1 095 = 0,1095 €).
- Multiplicadores em fracções **exatas**, nunca `Double`:

| Tipo de hora | Fracção | Decimal |
|---|---|---|
| Normal | 1/1 | 1,0 |
| Acréscimo noturno | 1/4 | 0,25 (só o acréscimo) |
| Sup. diurno dia normal | 3/2 | 1,5 |
| Sup. noturno dia normal | 35/16 | 2,1875 |
| Sup. diurno feriado | 3/1 | 3,0 |
| Sup. noturno feriado | 35/8 | 4,375 |
| Sup. diurno descanso | 9/2 | 4,5 |
| Sup. noturno descanso | 21/4 | 5,25 |

A distinção entre "multiplicador total" e "acréscimo" é deliberada: as `HSUP_*` pagam a hora por inteiro (a hora extra não está no VENC), mas a hora noturna normal já está paga no VENC e a empresa só soma os 25% — é o que o recibo confirma ("H01 Horas nocturnas 25% — 1,64 €/h").

### 4.7 Contagem do dia e horas suplementares

- `duracaoMinutos(início, fim, pausa)` trata turnos que atravessam a meia-noite (`fim < início` ⇒ soma 1440).
- **HNOT** conta só a janela noturna **dentro da jornada prevista do dia**. As horas extra noturnas saem pelas `HSUP_NT*`, para não pagar duas vezes.
- Só há suplemento quando o dia rende **mais** do que a jornada prevista desse dia: `extra = real − jornadaPrevista`. Um dia curto não se compensa com um dia longo, e um dia coberto por ausência não paga suplemento.
- Do extra, a parte noturna é a janela entre o fim da jornada e o fim real, limitada ao próprio extra (a pausa está dentro do intervalo e faria a conta exceder); o resto é diurno.
- Classificação por prioridade: **feriado** → `HSUP_*_FER`; **descanso** → `HSUP_*_DESC`; resto → `HSUP_*` do dia normal.
- Feriado ou folga trabalhados pagam também a jornada: 100 % em feriado (`DESC_FER`) e 200 % em folga de escala (`DESC_DESC`), mas só até 8h — acima disso é suplementar. Um dia que seja feriado **e** folga paga como feriado, porque o feriado é o que o CCT protege primeiro.

### 4.8 Atrasos e VENC

- Atraso do dia = `prevista − real`, só para dias com turno projetado de trabalho, registo de origem `MANUAL` e fora de ausência. A prevista é o `minutosPrevistos` do PDF quando existe; senão a duração do tipo.
- Valor do atraso = minutos × valor/minuto (0,1095 € em 2026).
- O atraso deduz-se **uma única vez, no VENC**: `VENC = max(0, VENC proporcional − atraso)`. A rubrica "Ausências" (código `ATRASO`) é **informativa**: mostra o valor mas não entra nos descontos, senão saía duas vezes.
- O VENC é proporcional aos dias efetivos: `VENC × (dias úteis do mês − dias úteis em baixa) ÷ dias úteis do mês`. Férias contam como pagas (não cortam), a baixa corta. Sem dias no mês (nem projeção nem registos), fica o vencimento base do contrato.

### 4.9 Subsídios

- `SUP_ALIM` = (dias úteis do mês − dias úteis cobertos por ausência) × valor/dia. Não incide SS nem IRS (é cartão de refeição) — é o que torna detetável o erro de agosto de 2026, em que o recibo descontou sobre o subsídio.
- `SUP_TRAN` = valor/mês × (30 − dias de ausência) ÷ 30.
- `SUP_FUNCAO` = valor/mês × (22 − dias de ausência) ÷ 22, só para categorias que o têm (Team Leader, 43,00 €/mês). A base de 22 dias é regra pedida pelo utilizador e não deve ser "corrigida" para 30 sem nova instrução.
- Mês **inteiro** coberto por ausência zera os três e anula o cartão de refeição: num mês de 31 dias, a conta sobre 30 deixaria dias por cortar (é o que os recibos de março de 2026 mostram).
- O subsídio de férias aparece no recibo quando há férias no próprio mês **ou no mês seguinte** (quem marca 10 a 24 de agosto quer vê-lo em julho e em agosto, não em setembro); o de Natal segue a mesma lógica de época.

### 4.10 IRS mensal (retenção na fonte)

- **Escolha da tabela** a partir do contrato (`selecionarTabela`), pelos subtítulos dos XLSX oficiais: I não casado sem dependentes ou casado 2 titulares; II não casado com dependentes; III casado único titular; IV–VII as variantes com pessoa com deficiência (IV = I, V = II, VI = casado 2 titulares com dependentes, VII = III).
- **Filtro** dos escalões por ano, região (Continente/Açores/Madeira), categoria (`TRABALHO` para I–VII, `PENSOES` para VIII–XI) e número da tabela. Sem o filtro de categoria, um número repetido entre as duas famílias escolheria escalões errados.
- **Algoritmo**: primeiro escalão cujo `limiteAte` cobre a base (acima do último → retenção 0); `bruto = base × taxaBps ÷ 100000`; parcela fixa **ou** fórmula composta; `liquido = bruto − parcela − parcelaAdicionalDep × dependentes`; negativo → 0.
- **Fórmula composta** (2.1.2): `parcela = taxa × k × (X − R) ÷ (100000 × 10000)`. É o que o XLSX de 2026 traz nos dois primeiros escalões (tabela I do Continente: k = 2,6 e X = 1 273,85 € no 1.º; k = 1,35 e X = 1 554,83 € no 2.º). Sem ela, bases entre o 1.º e o 2.º limite davam 0,00 €.
- A multiplicação `base × taxaBps` passa de 10^11, por isso todos os passos são em `Long`.

### 4.11 IRS Jovem

- Percentagem de isenção por ano de obtenção de rendimentos: 1.º ano **100 %**, 2.º–4.º **75 %**, 5.º–7.º **50 %**, 8.º–10.º **25 %**; fora disso (ou com mais de 35 anos no fim do ano) não se aplica.
- Teto legal: 55 × IAS por ano, repartido por 14 prestações (limite mensal). IAS tabelado: 2025 = 5 225 000 (522,50 €), 2026 = 5 371 300 (537,13 €).
- Aplicação: taxa efetiva = retenção normal ÷ base; parte isenta = `min(base × %, 55 × IAS ÷ 14)`; a taxa efetiva aplica-se só à parte não isenta, arredondando apenas no fim.
- *Fail-safe*: sem base, sem retenção ou num ano sem IAS tabelado, devolve a retenção normal — nunca rebenta nem inventa isenção.

### 4.12 IRS anual e simulador

- Motor separado do mensal: escalões progressivos do Continente para 2026, da Lei n.º 108/XVII/2 (12,2 % / 15,2 % / 20,7 % / 23,6 % / 30,6 % / 34,6 % / 43,1 % / 44,6 % / 48 %), com limites de 8 342 € a acima de 86 634 €.
- **Dedução específica** da categoria A: 8,54 × IAS = **4 587,09 €**.
- **Mínimo de existência** (art. 70.º): reduz o rendimento coletável, por titular, em três patamares (R ≤ V; V < R ≤ L; R > L), com V = 12 880,00 € e L = 14 628,01 €; nunca negativo nem superior a `R − deduções específicas`. Não se aplica quando a soma dos brutos do agregado excede 2,2 × 14 × IAS por sujeito passivo (16 543,6040 €).
- `ANO_IRS_ANUAL = 2026`: um ano sem IAS/escalões tabelados é recusado, em vez de simular coleta a mais.
- O simulador corre individual e conjunta, e mostra escalão, taxa efetiva e coleta — sempre como estimativa.

### 4.13 Desfasamento de pagamento das variáveis

- `desfasamentoPagamentoVariaveis = 0` (mesmo mês) ou `1` (mês anterior), configurado no contrato.
- Com 1, as rubricas variáveis (suplementares, feriados, noturnas, subsídios) e a régua dos feriados passam a ler o mês **anterior**; o VENC e os atrasos ficam no mês corrente. É o regime de quem recebe em atraso (ICTS: SAC, R14 e feriados do recibo de um mês são do anterior).

### 4.14 Complemento de baixa (R51) e sindicato (D04)

- `COMP_BAIXA` (R51 do recibo) **não é estimado**: vem do PDF ou é introduzido à mão.
- Incide **SS e IRS**, não sindicato. A prova está no recibo real de maio/2026: D01 = 31,51 € sobre 286,41 € de complemento (286,41 × 11 % = 31,505). O IRS desse mês deu 0 por o valor ficar abaixo do 1.º escalão, não por isenção.
- Entra nas bases pela mão do catálogo (`incideSS`/`incideIRS`), não por um `if` fixo, e é espelhado no abono estimado para o líquido fechar.
- `D04` (sindicato, 1 %) só existe para contratos com o switch de sindicalizado ligado e incide apenas sobre o VENC.

### 4.15 Feriados

- 10 feriados fixos (1 jan, 25 abr, 1 mai, 10 jun, 15 ago, 5 out, 1 nov, 1 dez, 8 dez, 25 dez) e 4 móveis: Carnaval (−47 dias), Sexta-feira Santa (−2), Domingo de Páscoa e Corpo de Deus (+60).
- A Páscoa é calculada pelo algoritmo de Meeus/Jones/Butcher (gregoriano).
- O feriado **municipal** vem da tabela `municipio` (dia/mês fixos); municípios com feriado móvel ficam por suportar (devolvem `null`, nunca uma data errada).
- `temFeriadoNoMes` decide se as linhas de feriado do recibo se desenham: num mês sem feriado não há horas de feriado nem descanso trabalhado em feriado, e as linhas ficariam todas a 0,00.

### 4.16 Importação do planeamento mensal (PDF da empresa)

- `parsePdfPlanner` lê o ano do **nome do ficheiro** (regex sem `\b`, para aceitar `Horario_2026.pdf`), o "Contrato de trabalho: N" (horas → minutos) e o mês de referência pelos nomes de mês em português, francês e inglês.
- Cada dia dá um `DiaImportado` (início, fim, pausa, duração, posto); turnos que atravessam a meia-noite são tratados como tal (correção da 2.0.3) e dias sem linha ficam fora do PDF.
- O resultado traz **avisos** (ano não detetado, mês não reconhecido, dia sem horas) que a UI mostra antes de gravar. A importação grava `dia_real` com origem `PDF`.

### 4.17 Importação do recibo (PDF) e mapeamento de rubricas

- O parser é **posicional**: procura etiquetas e valores por coordenadas, com tolerâncias calibradas no recibo real (valor a menos de 18 pt da etiqueta, duas posições a menos de 1 pt na mesma linha).
- Devolve **um recibo por página** (um PDF com folha principal e suplemento dá dois), com empresa emissora lida do rodapé ("Licença de: ..."), mês de referência, data de fecho, vencimento base/hora, dias úteis, IRS retido no ano, totais e linhas (código, descrição, valor, quantidade, data, ordem, aviso).
- O **mapeamento é configurável por empresa** (`MapeamentoRubricaEntity`, chave = empresa emissora): um código que a app não conhece não bloqueia a importação, passa a ser mapeado uma vez e reutilizado.
- Códigos desconhecidos sem mapeamento vão para "Outros" (abono) ou "Outros" de desconto (`OUTROS_DESC`) conforme a natureza, e ficam listados em `codigosDesconhecidos`.
- Linhas com o mesmo código são **somadas** (o caso dos turnos ICTS em duas linhas) e folhas duplicadas do mesmo recibo são fundidas.
- O mês de referência vem do "Período" com o **ano da data de fecho** — nas páginas de continuação a data de fecho é a da última linha (a 2.ª página do recibo de março/2026 traz 11/02/2026), mas o "Período" continua a dizer o mês do recibo.

### 4.18 Backups e restauro

- Cópia com `VACUUM INTO` — um único ficheiro consistente mesmo com o WAL aberto; em Android 8 (SQLite sem `VACUUM INTO`) faz checkpoint e copia.
- Ficheiros em `Android/data/<pacote>/files/backups/`, prefixo `predit-`, marca `-auto` nos automáticos, expurgo dos mais antigos.
- Exportar/importar por Storage Access Framework (01/10), para guardar fora do telemóvel.
- Restauro: valida o cabeçalho `SQLite format 3` (recusa lixo), recusa cópias de versão **superior** à da app, apaga `-wal`/`-shm`/`-journal` antigos antes de repor e aplica o ficheiro pendente no arranque seguinte (`aplicarRestauroPendente`).
- Limite conhecido: o restauro **substitui** a BD sem rede de segurança própria (ver achado A4 em §5.6).

### 4.19 Atualizações (OTA)

- `UpdateRepository` lê o `version.json` da última release com `HttpURLConnection` e `org.json` (sem OkHttp/Retrofit/Gson), seguindo o 302 do GitHub.
- 404 = "sem release publicada"; outro código = erro HTTP; tudo devolvido como `Result`, nunca com exceção a subir à UI.
- Só no flavor `github`: no `playstore`, `obterManifesto()` falha de imediato (regra da Play: a app não se pode auto-atualizar fora da loja).
- O download passa pelo `DownloadManager` com *polling*, exige `https`, e a instalação usa o `FileProvider` do próprio flavor.

---

## 5. Bugs, incidentes e correções

### 5.1 De onde vieram os bugs

Quatro fontes, por ordem de importância: **recibos reais** (ICTS, PSG, Fundação) confrontados com a estimativa; **uso continuado** em telemóvel real (Honor) e emuladores de 720×1600 e 1080×2400; **auditoria estática** de 20/10; e **testes** que falharam ao mudar uma regra. A regra R9 nasceu daqui: nenhuma correção de cálculo sai sem teste que a fixe.

### 5.2 Interface e layout

| Sintoma | Causa | Correção | Commit |
|---|---|---|---|
| "Hoj/e", "TRABALH/O", "Calendári/o", "Em/vigo/r", "08:/0/0" — palavras partidas em duas linhas | `Text` a quebrar em colunas de 40 dp | `TextoSemQuebra` (1 linha, sem *soft wrap*) aplicado a todas as etiquetas apertadas | `e5ea846`, `900f815`, `f3ee53b` |
| Toque acidental no `+` ao rolar a lista de turnos | Botão sem guarda contra deslocamento | Guarda de toque e validação mais estrita do nome | `b1f5f1c` |
| Células do calendário a mostrar o estado do dia anterior | Chaves de composição recicladas pela grelha | Chave estrita por data em `EscalaScreen` | `c94a7b8` |
| Título do recibo cortado pelas ações da barra | Três ações em texto | Ações passaram a ícone | `900f815` |
| Crachás de posto com contorno, diferentes dos de turno | Dois estilos para a mesma informação | Pílula sólida, sem borda, igual aos crachás de turno | `e09a8fa` |
| Cursor a saltar nas caixas do recibo | Recomposição a repor o texto a cada tecla | Estado local + gravação explícita | `a2915f9`, `56e2154` |
| Ícone genérico no ecrã Mais | Falta de ícone próprio | `ic_predit_sobre` a 120 dp | `3818a5f` |

### 5.3 Domínio e cálculo

| Sintoma | Causa | Correção | Commit |
|---|---|---|---|
| Férias apagavam folgas e feriados projetados | A ausência substituía qualquer dia | `aplicarAusencias` passou a proteger `FERIADO` e a usar o tipo de folga ao fim de semana | `7e9f491` |
| Ao aplicar nova rotação, o ciclo recomeçava do dia 1 | Âncora reiniciada | A aplicação nova herda a âncora da anterior | `e9cf32a` |
| Horas extra noturnas acima do real com pausa; minutos diurnos negativos | Janela noturna começava antes do fim da jornada (a pausa está dentro do intervalo) | `noturno = min(janela, extra)`, `diurno = extra − noturno`; testes com pausa > 0 | `bc8792e` (achado A2) |
| HNOT a pagar também as horas extra noturnas | Janela noturna contada sobre o dia inteiro | HNOT limitado à jornada prevista; o extra sai pelas `HSUP_NT*` | `f3ee53b`, `bc8792e` |
| Atraso descontado duas vezes do líquido | Rubrica de atraso entrava nos descontos **e** o VENC já era líquido | Atraso deduzido só no VENC; rubrica `ATRASO` passou a INFORMATIVA | `ffc2b12` |
| VENC fixo ignorava a baixa | Auditoria fixou o valor base do contrato | VENC proporcional aos dias efetivos (férias pagam, baixa corta), reposto em 21/10 | `bc8792e`, `d59ca07` |
| VENC estimado a 0,00 € com desfasamento 1 e mês sem dias | `diasUteisTotais == 0` devolvia 0 | Sem dias, fica o vencimento base do contrato | `bc8792e` (achado B3) |
| Recibo em "A carregar…" para sempre com `horarioSemanalH` inválido | `require` em `valorHoraMil` apanhado por um `runCatching` que escondia a causa | Validação no ecrã do contrato + coerção no repositório; mensagens distintas para "a carregar" e "sem tabela" | `bc8792e` (achados A1, B2) |
| Férias ou baixa num dia que já era folga somavam 8h ao total | Total calculado sem distinguir folga de dia de trabalho | `minutosDoDiaNaEscala` no domínio; ausência em folga vale 0 | `d548ff5` (2.1.4) |
| Total do rodapé impossível de testar | Conta vivia no ViewModel | Passou para o domínio, com `EscalaTotalHorasTest` | `d548ff5` |

### 5.4 Importação e parsers

| Sintoma | Causa | Correção | Commit |
|---|---|---|---|
| Ano não detetado em `Horario_2026.pdf` | `\b(20\d{2})\b` — o `_` é carácter de palavra | `(?<!\d)(20\d{2})(?!\d)` | `bc8792e` (achado B6) |
| Turnos noturnos do PDF lidos com duração errada | Fim antes do início (atravessa a meia-noite) | Soma de 1440 min no cálculo da duração | `4597db8` (2.0.3) |
| Palavras partidas na extração do recibo ("Venci mento") | *Kerning* separava os fragmentos | Junção de fragmentos na mesma linha visual | `e472d82` |
| Turnos ICTS com o mesmo código em duas linhas: uma desaparecia | Mapa por código substituía | Soma dos valores do mesmo código | `aa43e34` |
| Recibo importado com o mês errado | `aplicarReciboImportado` usava o mês aberto, não o `mesReferencia` | Comparação com o mês do recibo e confirmação/navegação | `bc8792e` (achado A3) |
| Duas folhas do mesmo recibo (principal + suplemento) duplicavam rubricas | Sem fusão | `FusaoRecibos` funde folhas do mesmo mês | `3dfc141` |
| Código de desconto desconhecido (D05) entrava como abono | Só existia o balde "Outros" de abono | Criada `OUTROS_DESC`, com natureza de desconto | `0bc5e53` (achado C7) |
| Recibo de 2027 (ou de 2025) abria sem tabelas de IRS | Só o ano de 2026 estava semeado | *Fallback* para o último ano com tabelas, com aviso visível | `bc8792e` (achado A1) |
| Editar um dia do PDF apagava o previsto | A edição reescrevia a linha e a régua passava a ser o registo | Colunas `previsto*` (migração 23→24) preservadas na edição | `61c4948` (2.1.3) |
| Contagem de dias importados errada; importação a bloquear | `numFolgas` contava dias fora do mês; `@Upsert` falhava em silêncio; exceção deixava `aCarregar` a true | Comparação restrita ao mês e tratamento de erros | `bc8792e` (achado B6) |

### 5.5 Recibo, IRS e visibilidade

| Sintoma | Causa | Correção | Commit |
|---|---|---|---|
| Retenção 0,00 € em bases do 1.º/2.º escalão (ex.: 1 105,35 €) | Escalões de 2026 trazem fórmula composta, não parcela fixa | `parcelaDaFormulaComposta` = `taxa × k × (X − R)` | `56e2154` (2.1.2) |
| Complemento de baixa (R51) aparecia mas não descontava SS/IRS | Rubrica manual fora das bases | Passou a incidir SS e IRS pelo catálogo | `90840a0` |
| Linhas de feriado todas a 0,00 em meses sem feriado | Sem filtro por mês | `temFeriadoNoMes` esconde as rubricas de feriado | `8e3c1af` (2.1.1) |
| Subsídios de Natal e de férias visíveis o ano inteiro | Sem regra de época | Férias no mês ou no seguinte; Natal na época própria | `3485376` (2.1.1) |
| "Outros (Desconto)" e linhas a 0,00 a poluir o recibo | Duas linhas de recurso com nomes distintos | Ambas passaram a "Outros" (o grupo distingue); zeros escondidos | `bda5333`, `8ef456f` |
| VENC a parecer completo num mês de baixa | VENC calculado pelo base do contrato | VENC proporcional e complemento espelhado | `a2915f9` |

### 5.6 Distribuição e atualizações

| Sintoma | Causa | Correção | Commit |
|---|---|---|---|
| OTA ficava preso a 100 % e não abria o instalador | Dependência de *broadcast* do `DownloadManager` que não chegava | *Polling* do cursor do `DownloadManager` até ao estado final | `e9cf32a` |
| Release publicada com o nome de asset errado (app não encontrava o APK) | Nome do output diferente do documentado | `RELEASING.md` corrigido e nome fixado (`app-github-release.apk`) | `626112d` |
| Versão da Play Store anunciava atualizações e pedia `INTERNET` | Um só canal para duas lojas | Flavors `github`/`playstore`, `OTA_ATIVO` por flavor e remoção da permissão de instalação | `05eb18f`, `bcf5373` |

### 5.7 Estado dos achados da auditoria de 20/10

A auditoria estática correu sobre a v2.1.0 e produziu o `RELATORIO-ANALISE.md` (branch `docs/relatorio-analise`, commit `41842b3`). O documento **não está no `main`** — vive no branch, como registo. Estado à data deste relatório:

| # | Tema | Estado | Evidência |
|---|---|---|---|
| A1 | Recibo inutilizável em 2027/2025 (exceção engolida) | **Corrigido** | `bc8792e`; mensagens distintas e *fallback* de ano |
| A2 | Horas extra noturnas erradas com pausa | **Corrigido** | `minOf` + testes com pausa > 0 |
| A3 | Importação aplicada ao mês errado | **Corrigido** | `mesReferencia` respeitado |
| A4 | Restauro de backup sem rede de segurança | **Pendente** | Ainda apaga `predit.db`/`-wal`/`-shm`/`-journal` antes de copiar, sem `.bak` nem `integrity_check` |
| B1 | Backups automáticos limitados a 10 | **Pendente** | `limparAntigos(manterUltimos = 10)` no arranque |
| B2 | Contrato inválido a avariar o recibo | **Corrigido** | Validação no ecrã + coerção no repositório |
| B3 | VENC a zero sem dias (desfasamento 1) | **Corrigido** | Fica o vencimento base |
| B4 | Estimador ignorava o modo PDF | **Corrigido** | `projecaoParaEstimativa` + `minutosPrevistos` |
| B5 | OTA sem verificação do APK | **Pendente** | Sem validação de `apkUrl`/hash; `minSdk` lido mas não comparado; duplo toque pode lançar dois downloads |
| B6 | Importação do PDF do planeamento | **Corrigido** | Regex do ano, contagem de folgas, erros tratados |
| B7 | Ano do mês de referência do recibo | **Pendente (por decisão)** | Mantém o ano da data de fecho com o mês do "Período"; documentado, porque é o que acerta nas páginas de continuação |
| C1 | Carnaval como feriado nacional | **Mantido** | Tolerância de ponto tratada como feriado; falta confronto com recibo de fevereiro |
| C2 | Corte do VENC por baixa | **Resolvido como regra** | Confirmado pelo utilizador em 19/10: proporcional aos dias úteis efetivos |
| C3 | Jornada do ciclo fixa em 173:19 | **Limitação assumida** | Não acompanha `horarioSemanalH` nem desconta férias/baixas |
| C4 | Escalões do IRS anual | **Fonte atualizada** | Lei n.º 108/XVII/2 (setembro de 2026) citada no código |
| C5 | IRS Jovem no anual | **Limitação documentada** | Isenção aplicada sobre o rendimento coletável apurado |
| C6 | Dedução específica 8,54 × IAS | **Limitação documentada** | Ignora contribuições SS (relevante acima de ~41,7 k€/ano) |
| C7 | Descontos desconhecidos como abono | **Corrigido** | `OUTROS_DESC` (D05) |
| D | Higiene | **Parcial** | README já diz 2.1.4, mas ainda `targetSdk 35`; release cai para a chave de *debug* sem `keystore.properties`; `gradle.properties` com caminhos absolutos do Windows |

### 5.8 Limitação atual dos testes instrumentados

Os 6 ficheiros de `androidTest` (Room, migrações, Compose) **não correm** no emulador Android 17: o Espresso 3.6.1 chama `InputManager.getInstance`, removido nessa versão da plataforma, e a suite rebenta com `NoSuchMethodException` antes de testar nada. Não é uma falha do projeto — é a biblioteca a estar desatualizada face à API. Consequências práticas:

- A rede de segurança passou a ser a suite JVM (282 testes), que cobre todo o domínio e os parsers.
- As migrações e o restauro de backups só se validam em emulador/dispositivo com API antiga, ou depois de atualizar o Espresso.
- Nenhuma release recente foi validada por testes instrumentados; a validação foi manual (emulador e Honor).

---

## 6. Qualidade e validação

### 6.1 Suite JVM (282 testes em 33 ficheiros)

| Área | Ficheiros | O que garantem |
|---|---|---|
| Escala e projeção | `EscalaTest`, `ProjecaoTest`, `EscalaModoPdfTest`, `EscalaTotalHorasTest`, `EscalaEstatisticasTest`, `ResumoPadraoTest` | Ciclo, âncora, ausências sobre folgas/feriados, modo PDF, total do rodapé, resumo do padrão |
| Ausências | `ContagemAusenciaTest`, `ContagemDiasTest` | Dias úteis consumidos, feriados excluídos |
| Tempo | `DuracaoTest`, `ComparacaoDiaTest`, `JornadaTest` | Turnos a atravessar a meia-noite, projetado vs. real, ciclo semestral |
| CCT e dinheiro | `CalculoCCTTest`, `DinheiroTest`, `ParametrosCCTLookupTest`, `SubsidiosTest`, `SubsidioFuncaoTest`, `PostoTest`, `ContratoTest` | Valor/hora, multiplicadores exatos, arredondamento, subsídios, janela noturna |
| Recibo | `EstimadorReciboTest`, `ReciboCabecalhoTest`, `LinhasVisiveisTest` | VENC, atrasos, suplementares, R51, bases, visibilidade de linhas |
| IRS | `CalculoIRSTest`, `IrsAnualTest`, `IrsJovemTest`, `TabelaIRSRecursoTest` | Seleção de tabela, fórmula composta, mínimo de existência, IRS Jovem, tabelas semeadas |
| Feriados | `FeriadosTest` | Fixos, móveis, Páscoa, municipal, "tem feriado no mês" |
| Importação | `PdfPlannerParserTest`, `ReciboPdfParserTest`, `ReciboImportacaoTest`, `ImportacaoMensalTest`, `PlanejamentoMesTest` | Leitura de PDFs reais (incluindo os das empresas), mês de referência, fusão, mapeamento |
| Versões | `VersionComparatorTest`, `VersionManifestParseTest` | Comparação de `versionCode`, leitura do manifesto OTA |

Todas as correções de cálculo desta lista têm teste: os nomes `EscalaTotalHorasTest`, `EscalaModoPdfTest` e `SubsidioFuncaoTest` nasceram com as fases 2.1.3/2.1.4.

### 6.2 Testes instrumentados (6 ficheiros)

`BackupManagerTest`, `ExtrairRecibosTest`, `FecharSemestreTest`, `MigracaoTest`, `ReciboConferenciaTest`, `ReciboDaoTest`. Cobrem o que o JVM não alcança: Room real, migração 3→4 e seguintes, extração de PDFs em *assets* e o ecrã de conferência. **Não correm no emulador Android 17** (§5.8).

### 6.3 Comandos usados no ciclo de trabalho

```powershell
.\gradlew.bat test                     # 282 testes JVM
.\gradlew.bat assembleGithubRelease    # APK com OTA (INTERNET)
.\gradlew.bat assemblePlaystoreRelease # APK sem INTERNET
.\gradlew.bat :app:connectedDebugAndroidTest  # instrumentados (bloqueado em API 37)
```

### 6.4 Validação manual antes de publicar

- Instalação e navegação em emuladores de **720×1600** e **1080×2400** (as grelhas de 7 colunas são o caso limite) e num **Honor** real (`AREE026211000512`).
- Confirmação de que o flavor `playstore` **não** pede `INTERNET` nem instalação de pacotes.
- `SHA-256` do APK descarregado igual ao do ficheiro local, e release confirmada como *latest*.
- Importação repetida dos PDFs reais de referência (planeamento ICTS e recibos ICTS/PSG/Fundação) contra valores conhecidos.

---

## 7. Releases publicadas

| Tag | `versionCode` | `versionName` | Data | Conteúdo |
|---|---|---|---|---|
| `v1.0.0` | 15 | 1.0.0 | 20/09 | Primeira versão instalável: escala, rotações, ausências, contrato, recibo e backups |
| `v1.1.0` | 16 | 1.1.0 | 22/09 | Rotação em vigor herda a âncora; OTA por *polling* |
| `v1.1.1` | 16 | 1.1.0 | 22/09 | Rodapé do Recibo e fases 15/16 — **tag com o mesmo `versionCode` da anterior** (build repetida, não uma versão nova) |
| `v1.1.2` | 18 | 1.1.2 | 22/09 | Horas noturnas corrigidas e tipografia dos ecrãs completos (o `versionCode` 17 não foi usado) |
| `v2.0.0-rc1` | 19 | 2.0.0-rc1 | 25/09 | Candidata: polimento, categorias CCT, IRS Jovem, simulador anual |
| `v2.0.0` | 20 | 2.0.0 | 25/09 | Primeira 2.0 |
| `v2.0.1` | 21 | 2.0.1 | 29/09 | Ícone do Mais, política de privacidade, site, OTA desligado na Play Store |
| `v2.0.3` | 23 | 2.0.3 | 03/10 | Parser de turnos noturnos do PDF (não houve 2.0.2 nem `versionCode` 22) |
| `v2.0.4` | 24 | 2.0.4 | 05/10 | Desfasamento de variáveis, D04 condicional, FOLGA a substituir turno |
| `v2.0.4-estavel` | 24 | 2.0.4 | 05/10 | Marcação da build considerada estável |
| `parser-icts-base` | 24 | 2.0.4 | 15/10 | Tag de referência do parser do recibo ICTS |
| `v2.1.0` | 25 | 2.1.0 | 21/10 | Regras CCT de atrasos/extra, VENC proporcional, correções da auditoria |
| `v2.1.1` | 26 | 2.1.1 | 21/10 | Visibilidade das rubricas do recibo (feriados, Natal, férias, "Outros") |
| `v2.1.2` | 27 | 2.1.2 | 21/10 | Fórmula composta do IRS, modo PDF no recibo, cabeçalho estável |
| `v2.1.3` | 28 | 2.1.3 | 27/10 | Previsto do PDF guardado, folgas implícitas, migração 23→24 |
| `v2.1.4` | 29 | 2.1.4 | 27/10 | Total de horas do rodapé: férias/baixa em folga deixam de somar 8h |
| `videos-tutorial` | 29 | 2.1.4 | 28/10 | Marcação do trabalho dos vídeos/guia do site |

Observações de histórico: nem todos os `versionCode` foram usados (17 e 22 não foram atribuídos), e a tag `v1.1.1` aponta para uma build com o mesmo código 16 — quem usa OTA nunca a recebeu, porque o `versionCode` não subiu.

---

## 8. Estado atual e pendências

### 8.1 Estado à data do relatório

- `main` limpo e sincronizado em `178c68a` (28/10/2026 13:08 UTC); nenhum trabalho por integrar.
- Versão **2.1.4** / `versionCode 29`, base de dados **24**, `docs/version.json` alinhado com o `build.gradle.kts`.
- 132 commits, 17 tags (15 de versão e 2 de marcas de trabalho), 282 testes JVM verdes, 6 ficheiros instrumentados bloqueados pela API do emulador.
- Site publicado, vídeos no ar e ligação ao guia dentro da app.

### 8.2 Pendências por prioridade

| Prioridade | Item | Porquê |
|---|---|---|
| Alta | **A4** — restauro de backup com `.bak` + `integrity_check` + *rollback* | Hoje, uma falha a meio do restauro deixa o utilizador sem BD. É o único ponto onde um erro custa dados |
| Alta (prazo) | Tabelas de IRS de **2027** e parâmetros CCT do ano | A 01/01/2027 a conferência passa a viver de *fallback* com aviso |
| Média | **B5** — validar `apkUrl` (https/host), tamanho/hash e `minSdk` antes de oferecer o update; guarda contra downloads duplicados | O Android verifica a assinatura na instalação, mas o manifesto é conteúdo remoto |
| Média | **B1** — retenção de backups por dia (7–14 dias) em vez de 10 arranques | Com 10 arranques, os automáticos bons desaparecem em poucos dias |
| Média | Atualizar **Espresso** (ou fixar emulador API ≤ 36) para voltar a correr os instrumentados | Sem eles, migrações e restauro ficam sem teste automático |
| Baixa | **B7** — decidir o ano do mês de referência (dezembro com fecho em janeiro) | Caso real ainda não apareceu; a regra atual acerta nas páginas de continuação |
| Baixa | `README.md`: `targetSdk 35` (o código está em 36) | Documentação desalinhada |
| Baixa | Falhar o `assembleRelease` quando não há `keystore.properties` | Hoje assina com a chave de *debug* em silêncio |
| Baixa | `gradle.properties` com caminhos absolutos do Windows | Impede build noutra máquina/CI |
| Baixa | `isMinifyEnabled = false` | APK maior; ativar exige regras de *keep* para o Room/PDFBox |
| Baixa | Backups em `Android/data/.../files/backups` legíveis por outras apps em Android 8–10 | Dados salariais; alternativa é `filesDir` + exportação SAF |
| Baixa | `try/catch` em `ReciboViewModel.confirmarMapeamento` e `importarRecibo` | Uma exceção da BD/parser fecha a app |
| A validar | **C1** Carnaval como feriado; **C3** jornada fixa em 173:19; **C5/C6** IRS anual | Regras assumidas; confirmar com recibos/contabilista antes de mexer |

### 8.3 O que não deve ser "corrigido" sem instrução

Três decisões estão fixadas por regra do utilizador e têm comentário no código a avisar disso: a **base de 22 dias** do subsídio de função (não 30), as **8h fixas** de uma ausência no total da Escala (não a duração do turno) e a **dedução única do atraso no VENC** (com a rubrica informativa ao lado). Mexer nestes números altera meses já validados contra recibos reais.

---

## 9. Anexos

### 9.1 Rotas da app

```
calendario | horario | turnos | mais
mais/importar-pdf | mais/recibo | mais/simulador-irs | mais/backups | mais/atualizacoes | mais/acerca
contrato | ausencias
escala/ausencia/nova?dataInicio={dataInicio} | escala/ausencia/{id}
turnos/tipo/novo | turnos/tipo/{id}
turnos/rotacao/nova | turnos/rotacao/{id} | turnos/rotacao/{id}/aplicar
horario/dia/{epochDay} | horario/dia/{epochDay}/editar
```

### 9.2 Entidades (16)

| Entidade | Papel |
|---|---|
| `TipoTurnoEntity` | Tipo de turno: nome, abreviatura, cor, horário, pausa, posto, categoria |
| `RotacaoEntity` / `RotacaoSlotEntity` | Rotação e o tipo de cada posição do ciclo |
| `AplicacaoRotacaoEntity` | Ligação rotação ↔ data de início (âncora) |
| `AusenciaEntity` | Ausência: tipo, início e fim |
| `DiaRealEntity` | Registo do dia (manual ou PDF), com colunas `previsto*` |
| `ContratoUtilizadorEntity` | Contrato: categoria CCT, vencimento, horário, admissão, município, IRS Jovem, desfasamento, sindicato |
| `ParametrosCCTEntity` | Parâmetros do CCT por ano e categoria |
| `MunicipioEntity` | Município, distrito e feriado municipal |
| `RubricaEntity` | Catálogo de rubricas com incidências e ordem |
| `TabelaIRSEntity` | Escalões de retenção por ano/região/categoria/tabela |
| `ReciboMesEntity` / `ReciboLinhaEntity` | Cabeçalho e linhas do recibo real do mês |
| `MapeamentoRubricaEntity` | Mapeamento código da empresa → código interno, por empresa |
| `PlanejamentoMesEntity` | Estado da importação do planeamento por mês |
| `CicloJornadaEntity` | Saldo e fecho do ciclo semestral |

### 9.3 Catálogo de rubricas (semente)

| Ordem | Código | Nome | Natureza | Incide SS / IRS / Sindicato |
|---|---|---|---|---|
| 1 | `VENC` | Vencimento | Abono (fixo) | Sim / Sim / Sim |
| 2 | `HNOT` | Horas noturnas | Abono (horas) | Sim / Sim / Sim |
| 3–8 | `HSUP_DN`, `HSUP_NT`, `HSUP_DN_FER`, `HSUP_NT_FER`, `HSUP_DN_DESC`, `HSUP_NT_DESC` | Suplementares (dia normal, feriado, descanso; diurno/noturno) | Abono (horas) | Sim / Sim / Sim |
| 9 | `SUP_ALIM` | Subsídio de alimentação | Abono (fixo) | Não / Não / Não |
| 10 | `SUP_TRAN` | Subsídio de transporte | Abono (fixo) | Sim / Sim / Não |
| 11–12 | `DESC_FER`, `DESC_DESC` | Dia feriado / descanso trabalhado | Abono (horas) | Sim / Sim / Sim |
| 13–14 | `ACR_NOT_FER`, `ACR_NOT_DESC` | Acréscimo noite feriado / descanso | Abono (horas) | Sim / Sim / Sim (fora da conferência) |
| 15–16 | `FERIAS`, `NATAL` | Subsídio de férias / Natal | Abono (fixo) | Sim / Não / Não |
| 20 | `D01` | Segurança Social (11 %) | Desconto derivado | — |
| 21 | `D02` | IRS | Desconto derivado | — |
| 22 | `D04` | Sindicato (1 %) | Desconto derivado | — |
| 23 | `SUP_FUNCAO` | Subsídio de função (Team Leader) | Abono derivado | Sim / Sim / Não |
| 24 | `COMP_BAIXA` | Complemento de baixa (R51) | Abono manual | Sim / Sim / Não |
| 25 | `ATRASO` | Ausências (horas em falta) | **Informativa** | Não / Não / Não |
| 98 | `OUTROS_DESC` | Outros (desconto) | Desconto manual | Não / Não / Não |
| 99 | `OUTROS` | Outros | Abono manual | Sim / Sim / Sim |

### 9.4 Ficheiros-chave

```
app/src/main/java/pt/haconnect/predit/
  domain/calc/Escala.kt            projeção, ausências, modo PDF, total do rodapé
  domain/calc/EstimadorRecibo.kt   VENC, suplementares, subsídios, atrasos, bases
  domain/calc/CalculoCCT.kt        valor/hora e multiplicadores exatos
  domain/calc/CalculoIRS.kt        tabelas mensais, fórmula composta
  domain/calc/IrsAnual.kt          escalões anuais, mínimo de existência
  domain/calc/IrsJovem.kt          isenção jovem
  domain/calc/Jornada.kt           ciclo semestral 173:19
  domain/calc/Feriados.kt          nacionais, municipais, Páscoa
  domain/importacao/ReciboPdfParser.kt / PdfPlannerParser.kt / MapeamentoRecibo.kt
  data/local/PreditDatabase.kt     16 entidades, 23 migrações
  data/backup/BackupManager.kt     VACUUM INTO, restauro pendente
  data/update/UpdateRepository.kt  manifesto OTA
  ui/navigation/Navegacao.kt       rotas e gate de primeiro arranque
  PreditApplication.kt             arranque, sementes (CCT, IRS, rubricas, municípios)
```

### 9.5 Nota metodológica

Este relatório foi escrito a partir do repositório, sem alterar código. Onde uma regra parecia dúbia, prevaleceu o que o código faz **hoje** e, quando existe, o comentário que data a decisão. Números de versão e `versionCode` foram lidos das próprias tags; contagens de testes, entidades e migrações foram obtidas por pesquisa no código. Achados assinalados como pendentes continuam pendentes — não foram corrigidos nesta tarefa.
