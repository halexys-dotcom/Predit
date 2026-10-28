# Relatório — site do Predit e vídeos do tutorial

Período: 25 a 28 de outubro de 2026. Todo o trabalho foi feito no branch
`claude/predit-site-update-u2x2v7` e entrou no `main` por pull requests
(#1 a #6). O site é publicado pelo GitHub Pages a partir da pasta `docs/`.

Este ficheiro tem duas partes:

1. **O que foi feito** — resumo para consulta.
2. **Tarefa para o Cline** — instruções para pôr no ecrã **Acerca** um link
   "Como usar o Predit" que abre o guia do site no browser.

---

## Parte 1 — O que foi feito

### 1. Site passa da fase de teste para a versão pública (PR #1)

A fase de teste fechado terminou e a app está em produção no Google Play.

- Os botões do Google Play passam a abrir a página pública da app:
  `https://play.google.com/store/apps/details?id=pt.haconnect.predit`
  (antes: link do teste interno `play.google.com/apps/internaltest/...`).
- Saem todas as referências a "Versão Beta", "Teste interno ativo" e
  "Aceitar o convite". O botão do topo passa a "Instalar o Predit".
- A secção final passa de `#teste` ("Entre no teste interno") a `#instalar`
  ("Instale o Predit").
- `docs/privacy.html` e `README.md` atualizados da mesma forma.
- Tema, imagens, ecrãs e gráficos mantidos.

### 2. Secção "Como usar o Predit" (PR #2)

Nova secção `#como-usar` em `docs/index.html`, com link no menu do topo.
Seis passos, cada um com o caminho nos menus da app:

| # | Passo | Onde na app |
|---|---|---|
| 1 | Configure o contrato | Mais → Contrato de Trabalho |
| 2 | Crie os tipos de turno | Turnos → Adicionar tipo de turno |
| 3 | Monte a escala (rotação fixa ou PDF mensal) | Turnos → Rotações · Mais → Importar Horário |
| 4 | Marque as ausências | Escala → tocar num dia |
| 5 | Registe as horas reais | Escala → tocar num dia · Horário |
| 6 | Confira o recibo | Mais → Recibo de vencimento |

Mais um bloco "Dicas para o dia a dia" (backups, atualizações, Simulador de
IRS, botão «Hoje»).

### 3. Vídeos do tutorial (PRs #3, #4 e #6)

- **PR #3** — cada passo ganhou um botão "▶ Ver vídeo" (o passo 3 tem dois:
  "Vídeo: rotação fixa" e "Vídeo: Importar Horário"), com os vídeos no
  Google Drive a abrir numa janela com o leitor do Drive.
- **PR #4** — o leitor embebido do Drive não tocava no telemóvel (precisa de
  cookies de terceiros). Os botões passaram a abrir o Drive diretamente.
- **PR #6** — os vídeos passaram a viver no próprio site, em `docs/videos/`,
  e voltaram a tocar numa janela dentro do site, com o leitor nativo do
  browser (`<video>`).

Os originais foram carregados no release `videos-tutorial` (pre-release, não
interfere com o "Latest" que a app usa para as atualizações) e comprimidos com
ffmpeg:

```
ffmpeg -i ORIGINAL.mp4 -an -vf "fps=30,scale=540:-2:flags=lanczos,format=yuv420p" \
  -c:v libx264 -preset slow -crf 30 -profile:v high -level 4.0 -movflags +faststart SAIDA.mp4
```

(`-an` tira o áudio: as gravações não tinham som, máximo -91 dB.)

| Ficheiro em `docs/videos/` | Passo | Duração | Original | Comprimido |
|---|---|---|---|---|
| `contrato.mp4` | 1. Configure o contrato | 1:38 | 38 MB | 1,7 MB |
| `turnos.mp4` | 2. Crie os tipos de turno | 4:08 | 66 MB | 2,3 MB |
| `importar-horario.mp4` | 3. Importar Horário (PDF) | 0:42 | 16 MB | 0,8 MB |
| `ausencias.mp4` | 4. Marque as ausências | 1:55 | 91 MB | 2,0 MB |
| `horas-reais.mp4` | 5. Registe as horas reais | 0:40 | 32 MB | 0,8 MB |
| `recibo.mp4` | 6. Confira o recibo | 1:35 | 58 MB | 1,7 MB |

Total: ~300 MB → 9,1 MB. Cada vídeo tem uma capa `.jpg` com o mesmo nome.
Resolução final 540 × ~1200 (vertical), 30 fps, H.264, sem áudio.

**Em falta:** o vídeo da rotação fixa ("Monte a escala rotativos.mp4") não
está no release. No site, o botão "Vídeo: rotação fixa" continua a abrir o
Google Drive.

### 4. Selo da versão automático (PR #5)

O selo "Versão 2.1.2" no topo do site estava escrito à mão e ficou para trás
com a 2.1.3 e a 2.1.4. Agora `docs/index.html` lê o `versionName` de
`docs/version.json` (o mesmo manifesto que a app consulta em Mais →
Atualizações), por isso cada release atualiza o site sozinho. `README.md`
passou para 2.1.4 e o `RELEASING.md` tem uma nota sobre isto.

### Pendentes

- Juntar o vídeo da rotação fixa ao release `videos-tutorial`, comprimi-lo
  com o comando acima e pô-lo em `docs/videos/rotacao.mp4` (+ `.jpg`); depois
  trocar o botão do passo 3 em `docs/index.html` para o ficheiro local (como
  os outros: `href="videos/rotacao.mp4" data-titulo="Monte a escala por rotação"`).
- ~~O `recibo.mp4` mostra o nome da entidade empregadora.~~ Feito (28/10):
  o nome da empresa na linha "Recibo de Agosto 2026 (…) importado." está
  desfocado a partir dos 39,2 s (quando a linha aparece) até ao fim; durante
  a animação de entrada (39,15–39,45 s) desfoca-se a linha inteira. Os
  valores do recibo ficam visíveis (decisão do dono).
- Apagar o release `videos-tutorial` quando já não for preciso (o site não
  depende dele).

---

## Parte 2 — Tarefa para o Cline: link "Como usar o Predit" no ecrã Acerca

### Objetivo

No ecrã **Mais → Acerca**, logo a seguir ao texto de descrição da app e antes
de "Agradecimentos", pôr uma linha clicável **"Como usar o Predit"** que abre
o guia do site no browser do telemóvel:

`https://halexys-dotcom.github.io/Predit/#como-usar`

### Decisão (28/10/2026): porque não vão vídeos dentro da app

A primeira versão desta tarefa punha os 6 vídeos em `res/raw` com um leitor
`VideoView`. Foi posta de parte:

- O APK passaria de ~17 MB para ~26 MB.
- Cada mudança de ecrã na app obrigaria a regravar os vídeos **e** a lançar
  uma versão nova. No site, basta trocar o ficheiro em `docs/videos/`.
- Abrir o site com um `Intent` não exige a permissão `INTERNET` (quem vai à
  Internet é o browser), por isso funciona também no flavor `playstore`.

**Não** copiar vídeos para `res/raw`, **não** criar ecrã de leitor e **não**
criar entrada nova em Mais.

### Contexto do código (verificado no `main`)

- Ecrã: `app/src/main/java/pt/haconnect/predit/ui/mais/AcercaScreen.kt`.
  É uma `Column` com `verticalScroll` e `spacedBy(16.dp)`. Ordem atual:
  ícone → "Predit" → versão → divisor → "Diário de orientação pessoal…" →
  "O Predit projeta a sua escala…" → **divisor** → "Agradecimentos" → … →
  nota de privacidade (último bloco).
- Cartões de Mais (`ui/mais/MaisScreen.kt`): `ElevatedCard` clicável →
  `Row(padding 16.dp, SpaceBetween)` → ícone `primary` + `Column(spacedBy 2.dp)`
  com título (`titleMedium`, bold) e subtítulo (`bodySmall`,
  `onSurfaceVariant`) → seta `Icons.AutoMirrored.Filled.KeyboardArrowRight`.
- **Ícones:** o projeto **não** tem `material-icons-extended`. Usar só ícones
  do conjunto base (`Icons.Filled.PlayArrow`, `KeyboardArrowRight`). Não
  acrescentar a dependência por causa de um ícone.
- Só `app/src/github/AndroidManifest.xml` declara `INTERNET`; o flavor
  `playstore` não tem, e assim deve continuar.
- Já existe um `Intent(Intent.ACTION_VIEW)` em `data/update/ApkInstaller.kt`
  (outro uso, só como referência de estilo).

### Passos

1. **Constante do link** — no `AcercaScreen.kt` (ou num `object` pequeno ao
   lado), por exemplo:

   ```kotlin
   private const val URL_COMO_USAR = "https://halexys-dotcom.github.io/Predit/#como-usar"
   ```

2. **Linha "Como usar o Predit"** — inserir entre o parágrafo "O Predit
   projeta a sua escala…" e o `HorizontalDivider` que antecede
   "Agradecimentos". Usar um `ElevatedCard` com o mesmo layout dos cartões de
   `MaisScreen`:
   - ícone `Icons.Filled.PlayArrow`, `tint = primary`;
   - título **"Como usar o Predit"**;
   - subtítulo **"Guia e vídeos no site (abre no browser)"**;
   - seta `KeyboardArrowRight` à direita;
   - `Modifier.testTag("acerca-como-usar")`.

   Não usar texto azul solto: os nomes dos Agradecimentos já estão a azul
   (`primary`) e não são links.

3. **Abrir o browser** — no `onClick`:

   ```kotlin
   val context = LocalContext.current
   // ...
   try {
       context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(URL_COMO_USAR)))
   } catch (e: ActivityNotFoundException) {
       Toast.makeText(context, "Não foi encontrado nenhum browser para abrir o guia.", Toast.LENGTH_SHORT).show()
   }
   ```

   - Não é preciso `<queries>` no manifesto (não se consulta o
     `PackageManager`, só se apanha a exceção).
   - Não usar `WebView` (precisaria de `INTERNET` no flavor `playstore`).

4. **Nota de privacidade** — manter o texto como está. O subtítulo "(abre no
   browser)" já deixa claro que é o browser, não a app, que vai à Internet.

5. **Comentário/KDoc** — atualizar o KDoc de `AcercaScreen` para referir o
   link do guia.

### Testes e verificação

- `.\gradlew.bat test` tem de continuar verde.
- Compilar os dois flavors (`assembleGithubDebug` e `assemblePlaystoreDebug`).
- No telemóvel, nos dois flavors:
  - Mais → Acerca → tocar em "Como usar o Predit" → abre o browser na secção
    "Como usar o Predit" do site.
  - Voltar do browser regressa ao ecrã Acerca.
  - Com Internet desligada: a app não fecha (o browser mostra o erro dele).
- Confirmar que o `AndroidManifest.xml` do flavor `playstore` continua sem
  `INTERNET` (APK de release → `aapt dump permissions` ou o Analyze APK do
  Android Studio).

### Fora do âmbito

- Não mexer em `docs/` (o site já está feito).
- Não adicionar vídeos à app nem permissão de Internet ao flavor `playstore`.
- Não mudar `versionCode`/`versionName` nesta tarefa; isso fica para o
  release seguinte, conforme `RELEASING.md`.
