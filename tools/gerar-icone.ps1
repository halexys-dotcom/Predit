# Gera os icones de launcher do Predit a partir de icon-source.png (na raiz do repo).
# RE-CORRER QUANDO A FONTE icon-source.png MUDAR.
#
# O que este script escreve (tudo dentro de app/src/main/res):
#   mipmap-mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi\ic_launcher.png            48/72/96/144/192 px
#   mipmap-mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi\ic_launcher_round.png      idem, recortado em circulo
#   mipmap-mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi\ic_launcher_foreground.png 108/162/216/324/432 px
#   mipmap-anydpi-v26\ic_launcher.xml
#   mipmap-anydpi-v26\ic_launcher_round.xml
#   values\ic_launcher_background.xml                                cor #0A1929
#
# Regras seguidas:
#   - Adaptativo (Android 8+): canvas de 108dp e o conteudo (a fonte inteira) dentro de
#     um quadrado de 66dp centrado no canvas (66/108 = 61,1%); o resto fica transparente.
#     O fundo do adaptativo vem de values\ic_launcher_background.xml.
#   - Legacy (Android 7-): a fonte ocupa o quadrado todo, sobre fundo #0A1929; a versao
#     _round e o mesmo desenho recortado em circulo.
#   - O AndroidManifest.xml nao e tocado: ja aponta android:icon="@mipmap/ic_launcher"
#     e android:roundIcon="@mipmap/ic_launcher_round".
#
# Notas:
#   - Usa System.Drawing (nativo do PowerShell 5.1). Sem dependencias externas.
#   - SO caracteres ASCII: o PowerShell 5 le um .ps1 sem BOM como ANSI, por isso um
#     acento aqui dentro partiria o script. Os comentarios ficam sem acentos de proposito.
#   - Os XML do template (drawable\ic_launcher_background.xml com #3D5AFE e
#     drawable\ic_launcher_foreground.xml com o quadrado branco) deixam de ser usados
#     quando este script corre. O script avisa no fim mas nao os apaga.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$raiz = Split-Path -Parent $PSScriptRoot
$origemPath = Join-Path $raiz 'icon-source.png'
$resPath = Join-Path $raiz 'app\src\main\res'
$corFundoHex = '#0A1929'
$fracaoConteudoAdaptativo = 66.0 / 108.0

if (-not (Test-Path $origemPath)) { throw "Fonte nao encontrada: $origemPath" }
if (-not (Test-Path $resPath)) { throw "Pasta res nao encontrada: $resPath" }

# 108/162/216/324/432 px == 108dp nas cinco densidades; 432x432 e o foreground do xxxhdpi.
$densidades = @(
    [pscustomobject]@{ Nome = 'mdpi';    Legado = 48;  Adaptativo = 108 },
    [pscustomobject]@{ Nome = 'hdpi';    Legado = 72;  Adaptativo = 162 },
    [pscustomobject]@{ Nome = 'xhdpi';   Legado = 96;  Adaptativo = 216 },
    [pscustomobject]@{ Nome = 'xxhdpi';  Legado = 144; Adaptativo = 324 },
    [pscustomobject]@{ Nome = 'xxxhdpi'; Legado = 192; Adaptativo = 432 }
)

function Novo-Canvas {
    param([int]$Largura, [int]$Altura, [string]$CorHex)

    $bmp = New-Object System.Drawing.Bitmap($Largura, $Altura, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    if ([string]::IsNullOrEmpty($CorHex)) {
        $g.Clear([System.Drawing.Color]::Transparent)
    } else {
        $g.Clear([System.Drawing.ColorTranslator]::FromHtml($CorHex))
    }
    return [pscustomobject]@{ Bmp = $bmp; G = $g }
}

function Get-RetanguloAjustado {
    param([int]$LarguraOrigem, [int]$AlturaOrigem, [int]$Caixa)

    $escala = [Math]::Min($Caixa / [double]$LarguraOrigem, $Caixa / [double]$AlturaOrigem)
    $larg = [int][Math]::Round($LarguraOrigem * $escala)
    $alt = [int][Math]::Round($AlturaOrigem * $escala)
    $x = [int](($Caixa - $larg) / 2)
    $y = [int](($Caixa - $alt) / 2)
    return [System.Drawing.Rectangle]::new($x, $y, $larg, $alt)
}

function Desenha-Ajustado {
    param($G, $Origem, [System.Drawing.Rectangle]$Rect)

    # TileFlipXY evita a borda borrada que o bicubic deixa ao reamostrar as margens.
    $ia = New-Object System.Drawing.Imaging.ImageAttributes
    $ia.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
    $G.DrawImage(
        $Origem,
        $Rect,
        0, 0, $Origem.Width, $Origem.Height,
        [System.Drawing.GraphicsUnit]::Pixel,
        $ia
    )
    $ia.Dispose()
}
Write-Host ''
Write-Host "Fonte:   $origemPath" -ForegroundColor DarkGray
Write-Host "Destino: $resPath" -ForegroundColor DarkGray
Write-Host "Fundo do adaptativo: $corFundoHex" -ForegroundColor DarkGray
Write-Host ''

$origem = [System.Drawing.Image]::FromFile($origemPath)
Write-Host ("Fonte lida: {0}x{1} px ({2})" -f $origem.Width, $origem.Height, $origem.PixelFormat) -ForegroundColor DarkGray

$gerados = New-Object System.Collections.Generic.List[string]
$linhasXml = @(
    '<?xml version="1.0" encoding="utf-8"?>',
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">',
    '    <background android:drawable="@color/ic_launcher_background" />',
    '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />',
    '</adaptive-icon>',
    ''
) -join "`r`n"
$linhasCor = @(
    '<?xml version="1.0" encoding="utf-8"?>',
    '<resources>',
    ('    <color name="ic_launcher_background">' + $corFundoHex + '</color>'),
    '</resources>',
    ''
) -join "`r`n"
$utf8SemBom = New-Object System.Text.UTF8Encoding($false)

try {
    foreach ($d in $densidades) {
        $pasta = Join-Path $resPath ('mipmap-' + $d.Nome)
        New-Item -ItemType Directory -Force -Path $pasta | Out-Null

        # 1) Legacy quadrado: a fonte ocupa a tela toda, sobre o fundo navy.
        $tela = Novo-Canvas -Largura $d.Legado -Altura $d.Legado -CorHex $corFundoHex
        Desenha-Ajustado -G $tela.G -Origem $origem -Rect (
            Get-RetanguloAjustado -LarguraOrigem $origem.Width -AlturaOrigem $origem.Height -Caixa $d.Legado)
        $tela.Bmp.Save((Join-Path $pasta 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
        $tela.G.Dispose(); $tela.Bmp.Dispose()
        $gerados.Add(('mipmap-{0}\ic_launcher.png            {1}x{1}' -f $d.Nome, $d.Legado))

        # 2) Legacy redondo: o mesmo desenho recortado em circulo (cantos transparentes).
        $tela = Novo-Canvas -Largura $d.Legado -Altura $d.Legado -CorHex $null
        $circulo = New-Object System.Drawing.Drawing2D.GraphicsPath
        $circulo.AddEllipse(0, 0, $d.Legado - 1, $d.Legado - 1)
        $tela.G.SetClip($circulo)
        Desenha-Ajustado -G $tela.G -Origem $origem -Rect (
            Get-RetanguloAjustado -LarguraOrigem $origem.Width -AlturaOrigem $origem.Height -Caixa $d.Legado)
        $tela.Bmp.Save((Join-Path $pasta 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png)
        $tela.G.Dispose(); $tela.Bmp.Dispose(); $circulo.Dispose()
        $gerados.Add(('mipmap-{0}\ic_launcher_round.png      {1}x{1}' -f $d.Nome, $d.Legado))

        # 3) Foreground adaptativo: canvas de 108dp, conteudo dentro do quadrado de 66dp.
        $caixa = [int][Math]::Round($d.Adaptativo * $fracaoConteudoAdaptativo)
        $margem = [int][Math]::Round(($d.Adaptativo - $caixa) / 2)
        $interno = Get-RetanguloAjustado -LarguraOrigem $origem.Width -AlturaOrigem $origem.Height -Caixa $caixa
        $rect = [System.Drawing.Rectangle]::new($margem + $interno.X, $margem + $interno.Y, $interno.Width, $interno.Height)
        $tela = Novo-Canvas -Largura $d.Adaptativo -Altura $d.Adaptativo -CorHex $null
        Desenha-Ajustado -G $tela.G -Origem $origem -Rect $rect
        $tela.Bmp.Save((Join-Path $pasta 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
        $tela.G.Dispose(); $tela.Bmp.Dispose()
        $gerados.Add(('mipmap-{0}\ic_launcher_foreground.png {1}x{1} (conteudo {2}x{2})' -f $d.Nome, $d.Adaptativo, $caixa))
    }

    # 4) Adaptive icons (Android 8+) e a cor de fundo.
    $pastaV26 = Join-Path $resPath 'mipmap-anydpi-v26'
    New-Item -ItemType Directory -Force -Path $pastaV26 | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $pastaV26 'ic_launcher.xml'), $linhasXml, $utf8SemBom)
    $gerados.Add('mipmap-anydpi-v26\ic_launcher.xml')
    [System.IO.File]::WriteAllText((Join-Path $pastaV26 'ic_launcher_round.xml'), $linhasXml, $utf8SemBom)
    $gerados.Add('mipmap-anydpi-v26\ic_launcher_round.xml')

    $pastaValues = Join-Path $resPath 'values'
    New-Item -ItemType Directory -Force -Path $pastaValues | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $pastaValues 'ic_launcher_background.xml'), $linhasCor, $utf8SemBom)
    $gerados.Add('values\ic_launcher_background.xml')
} finally {
    $origem.Dispose()
}

Write-Host ''
Write-Host 'GERADO:' -ForegroundColor Green
$gerados | ForEach-Object { Write-Host "  $_" -ForegroundColor Green }
Write-Host ''
Write-Host 'Deixaram de ser usados (ficheiros do template; apaga se quiseres):' -ForegroundColor Yellow
Write-Host '  app\src\main\res\drawable\ic_launcher_background.xml  (#3D5AFE)' -ForegroundColor Yellow
Write-Host '  app\src\main\res\drawable\ic_launcher_foreground.xml  (quadrado branco)' -ForegroundColor Yellow
Write-Host '  (o AndroidManifest.xml ja aponta para @mipmap/ic_launcher e @mipmap/ic_launcher_round)' -ForegroundColor DarkGray
Write-Host ''
Write-Host 'Proximo passo: .\gradlew.bat assembleDebug' -ForegroundColor Cyan

