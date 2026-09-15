# Funções partilhadas pelos scripts de base de dados do Predit.
# Não é para correr sozinho — é dot-sourced:  . "$PSScriptRoot\db-comum.ps1"

function Get-Adb {
    $candidatos = @()
    if ($env:ANDROID_SDK_ROOT) { $candidatos += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe') }
    if ($env:ANDROID_HOME) { $candidatos += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') }
    if ($env:LOCALAPPDATA) { $candidatos += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe') }
    foreach ($c in $candidatos) { if (Test-Path $c) { return $c } }
    $noPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($noPath) { return $noPath.Source }
    throw 'adb não encontrado. Define ANDROID_SDK_ROOT ou instala o platform-tools.'
}

function Assert-DispositivoLigado {
    param([string]$Adb)
    $linhas = @(& $Adb devices | Where-Object { $_ -match '\tdevice$' })
    if ($linhas.Count -eq 0) {
        throw "Nenhum dispositivo ligado (adb devices não mostra nenhum 'device'). Liga o telefone e volta a tentar."
    }
    $script:Dispositivo = ($linhas[0] -replace '\tdevice$', '').Trim()
    Write-Host "Dispositivo: $script:Dispositivo"
}

function Assert-PacoteInstalado {
    param([string]$Adb, [string]$Pacote)
    $lista = & $Adb shell pm list packages
    if (-not ($lista -match ('package:' + [regex]::Escape($Pacote)))) {
        throw "O pacote $Pacote não está instalado. Corre .\gradlew.bat installDebug e espera uns segundos (o Auto Backup repõe os dados depois da instalação)."
    }
}

function Stop-App {
    param([string]$Adb, [string]$Pacote)
    & $Adb shell am force-stop $Pacote | Out-Null
    Start-Sleep -Milliseconds 800
}

function Get-FicheirosBdNoDispositivo {
    param([string]$Adb, [string]$Pacote)
    $saida = & $Adb shell run-as $Pacote ls databases
    @($saida | Where-Object { $_ -match '^predit\.db' } | ForEach-Object { $_.Trim() })
}

function Invoke-ExecOutParaFicheiro {
    # adb exec-out com redirecionamento binário: tem de ser o cmd a redirecionar,
    # caso contrário o PowerShell 5 corrompe o binário.
    param([string]$Adb, [string]$ComandoAdb, [string]$Destino)
    $bat = Join-Path $env:TEMP 'predit-execout.bat'
    "`"$Adb`" $ComandoAdb > `"$Destino`"" | Out-File -Encoding ascii $bat
    & cmd.exe /c $bat | Out-Null
    if (-not (Test-Path $Destino)) { throw "Falhou a extração para $Destino" }
    if ((Get-Item $Destino).Length -eq 0) { throw "Ficheiro extraído vazio: $Destino" }
}

function Get-PastaBackups {
    param([string]$Pasta)
    if (-not $Pasta) {
        $raiz = Split-Path -Parent $PSScriptRoot
        $Pasta = Join-Path $raiz 'tools\db-backups'
    }
    if (-not (Test-Path $Pasta)) { New-Item -ItemType Directory -Force -Path $Pasta | Out-Null }
    return $Pasta
}

function Get-Python {
    $py = Get-Command python -ErrorAction SilentlyContinue
    if ($py) { return $py.Source }
    $pyLauncher = Get-Command py -ErrorAction SilentlyContinue
    if ($pyLauncher) { return $pyLauncher.Source }
    return $null
}

function Get-Sha256 {
    param([string]$Ficheiro)
    (Get-FileHash -Algorithm SHA256 -Path $Ficheiro).Hash
}

function Test-FicheiroSQLite {
    param([string]$Ficheiro)
    $bytes = [System.IO.File]::ReadAllBytes($Ficheiro)
    if ($bytes.Length -lt 100) { return $false }
    $magic = [System.Text.Encoding]::ASCII.GetString($bytes[0..15])
    return ($magic -eq 'SQLite format 3' + [char]0)
}