# Relatório — site do Predit e vídeos do tutorial

Período: 25 a 28 de outubro de 2026. Todo o trabalho foi feito no branch
`claude/predit-site-update-u2x2v7` e entrou no `main` por pull requests
(#1 a #6). O site é publicado pelo GitHub Pages a partir da pasta `docs/`.

Este ficheiro tem duas partes:

1. **O que foi feito** — resumo para consulta.
2. **Tarefa para o Cline** — instruções completas para pôr os vídeos dentro
   da app, numa nova área em **Mais**.

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
- O `recibo.mp4` mostra o nome da entidade empregadora e valores reais de um
  recibo. Ponderar regravar com um recibo de exemplo (o rodapé do site diz
  que o Predit não tem vínculo a entidades empregadoras).
- Apagar o release `videos-tutorial` quando já não for preciso (o site não
  depende dele).

---

## Parte 2 — Tarefa para o Cline: vídeos do tutorial dentro da app

### Objetivo

Criar em **Mais** uma nova entrada **"Tutorial em vídeo"** que abre um ecrã
com a lista dos passos do tutorial; tocar num passo reproduz o vídeo dentro
da app, sem Internet.

### Contexto do código (verificado)

- Ecrã Mais: `app/src/main/java/pt/haconnect/predit/ui/mais/MaisScreen.kt`.
  Cada entrada é um `ElevatedCard` clicável com ícone, título
  (`titleMedium`, bold) e subtítulo (`bodySmall`, `onSurfaceVariant`) e uma
  seta `KeyboardArrowRight`. Os destinos chegam por callbacks
  `onNavegarParaX: () -> Unit = {}`.
- Navegação: `app/src/main/java/pt/haconnect/predit/ui/navigation/Navegacao.kt`.
  As rotas de Mais seguem o padrão `composable("mais/<nome>") { XScreen(onVoltar = { navController.popBackStack() }) }`.
- Ecrãs de exemplo com `onVoltar`: `ui/mais/AcercaScreen.kt`,
  `ui/mais/BackupsScreen.kt`.
- **Sem Internet no flavor `playstore`:** só
  `app/src/github/AndroidManifest.xml` declara `INTERNET`. Por isso os vídeos
  **têm de ir dentro da app** (`res/raw`), não podem ser lidos do site.
- **Sem biblioteca de vídeo:** não há Media3/ExoPlayer nas dependências. Usar
  `android.widget.VideoView` + `MediaController` dentro de `AndroidView`
  (zero dependências novas). Media3 só se houver motivo forte.
- `minSdk 26`; o H.264 dos vídeos toca em todos os Android suportados.

### Passos

1. **Copiar os vídeos** de `docs/videos/` para
   `app/src/main/res/raw/`, com nomes válidos para recursos Android (só
   minúsculas, dígitos e `_`; sem hífen):

   | Origem | Destino | Recurso |
   |---|---|---|
   | `docs/videos/contrato.mp4` | `res/raw/tutorial_contrato.mp4` | `R.raw.tutorial_contrato` |
   | `docs/videos/turnos.mp4` | `res/raw/tutorial_turnos.mp4` | `R.raw.tutorial_turnos` |
   | `docs/videos/importar-horario.mp4` | `res/raw/tutorial_importar_horario.mp4` | `R.raw.tutorial_importar_horario` |
   | `docs/videos/ausencias.mp4` | `res/raw/tutorial_ausencias.mp4` | `R.raw.tutorial_ausencias` |
   | `docs/videos/horas-reais.mp4` | `res/raw/tutorial_horas_reais.mp4` | `R.raw.tutorial_horas_reais` |
   | `docs/videos/recibo.mp4` | `res/raw/tutorial_recibo.mp4` | `R.raw.tutorial_recibo` |

   Não copiar os `.jpg` (a lista usa ícones; o `VideoView` mostra o 1.º
   frame). O APK cresce ~9 MB (o atual tem ~17 MB).

2. **Catálogo** — novo ficheiro `ui/mais/tutorial/TutorialVideos.kt` com
   uma lista estática, na ordem do guia do site:

   ```kotlin
   data class VideoTutorial(val titulo: String, val descricao: String, @RawRes val recurso: Int)
   ```

   | Título | Descrição |
   |---|---|
   | Configure o contrato | Mais → Contrato de Trabalho |
   | Crie os tipos de turno | Turnos → Adicionar tipo de turno |
   | Importar Horário | Mais → Importar Horário (PDF mensal) |
   | Marque as ausências | Escala → tocar num dia → Marcar ausência |
   | Registe as horas reais | Escala → tocar num dia → Registar horas |
   | Confira o recibo | Mais → Recibo de vencimento |

   Deixar preparado (comentário) o lugar do vídeo "Monte a escala por
   rotação", que ainda não existe — **não** criar entrada sem ficheiro.

3. **Ecrã de lista** — `ui/mais/tutorial/TutorialScreen.kt`
   (`onVoltar`, `onAbrirVideo: (Int) -> Unit` com o índice). `TopAppBar` com
   seta de voltar e título "Tutorial em vídeo"; cartões iguais aos de
   `MaisScreen` (ícone `PlayArrow`, título, descrição, duração opcional).

4. **Ecrã do leitor** — `ui/mais/tutorial/VideoTutorialScreen.kt`:
   - `TopAppBar` com voltar e o título do vídeo.
   - `AndroidView` com `VideoView`; `setVideoURI(Uri.parse("android.resource://${context.packageName}/$recurso"))`,
     `MediaController` ancorado ao `VideoView`, `start()` ao abrir.
   - Vídeo vertical: ocupar a altura disponível, centrado, fundo preto.
   - Parar e libertar ao sair (`DisposableEffect` → `stopPlayback()`);
     pausar em `ON_PAUSE` do ciclo de vida.
   - Manter o ecrã ligado enquanto toca (`keepScreenOn = true` no `VideoView`).

5. **Navegação** em `Navegacao.kt`:
   - `composable("mais/tutorial") { TutorialScreen(onVoltar = …, onAbrirVideo = { i -> navController.navigate("mais/tutorial/$i") }) }`
   - `composable("mais/tutorial/{indice}", arguments = listOf(navArgument("indice") { type = NavType.IntType })) { … VideoTutorialScreen(…) }`
     (validar o índice; fora do intervalo → `popBackStack()`).

6. **Entrada em Mais** — em `MaisScreen.kt`, novo callback
   `onNavegarParaTutorial: () -> Unit = {}` e novo `ElevatedCard`
   "Tutorial em vídeo" / "Vídeos curtos de cada passo da app", com o mesmo
   layout dos outros. Posição sugerida: logo antes de "Acerca do Predit".
   Visível nos dois flavors (não depende de `OTA_ATIVO`).

7. **Testes e verificação**
   - `.\gradlew.bat test` tem de continuar verde.
   - Teste unitário simples ao catálogo: 6 entradas, títulos não vazios,
     sem recursos repetidos.
   - Testar no telemóvel **em modo avião** com os dois flavors
     (`github` e `playstore`): abrir cada vídeo, pausar, avançar, rodar o
     ecrã, voltar atrás a meio (o vídeo tem de parar).
   - Confirmar o tamanho do APK/AAB de release.

### Fora do âmbito

- Não mexer em `docs/` (o site já está feito).
- Não adicionar permissão de Internet ao flavor `playstore`.
- Não mudar `versionCode`/`versionName` nesta tarefa; isso fica para o
  release seguinte, conforme `RELEASING.md`.
