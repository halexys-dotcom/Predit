<#
.SYNOPSIS
    Restaura uma cópia da base de dados do Predit para o telefone ligado.

.DESCRIPTION
    Copia um backup anteriormente criado por save-db.ps1 de volta para o
    device. Usado depois de `./gradlew connectedAndroidTest`, que desinstala
    a app e apaga a BD no fim da corrida.

    Os dados também são restaurados automaticamente pelo Auto Backup
    (allowBackup=true) depois de um novo installDebug, mas não é instantâneo:
    há alguns segundos em que a app arranca como primeiro arranque. Este script
    dá-te controlo explícito e imediato.

    Se o backup for um ficheiro .db único (checkpoint do WAL feito com Python
    em save-db.ps1), apenas ele é restaurado.
    Se for uma pasta (backup em bruto sem Python), todos os ficheiros
    predit.db / predit.db-wal / predit.db-shm são restaurados em conjunto.

.PARAMETER Pacote
    applicationId de debug. Por omissão: pt.haconnect.predit.debug

.PARAMETER Caminho
    Caminho para o ficheiro .db ou pasta de backup.
    Se omitido, usa o backup mais recente em tools\db-backups.

.EXAMPLE
    .\tools\restore-db.ps1
    # Restaura o backup mais recente

.EXAMPLE
    .\tools\restore-db.ps1 -Caminho tools\db-backups\predit-20260915-103000.db
    # Restaura um backup específico
#>
[CmdletBinding()]
param(
    [string]$Pacote = 'pt.haconnect.predit.debug',
    [string]$Caminho,
    [string]$Device
)

$ErrorActionPreference = 'Continue'  # cmd nativos como adb escrevem para stderr mesmo em sucesso
. (Join-Path $PSScriptRoot 'db-comum.ps1')

$adb = Get-Adb
Assert-DispositivoLigado -Adb $adb -Device $Device
Assert-PacoteInstalado -Adb $adb -Pacote $Pacote
Stop-App -Adb $adb -Pacote $Pacote

# Determinar qual backup usar
if (-not $Caminho) {
    $pastaBackups = Get-PastaBackups
    # Procurar ficheiro .db único (backup com checkpoint do WAL)
    $candidatos = Get-ChildItem -Path $pastaBackups -Filter 'predit-*.db' -File |
        Sort-Object LastWriteTime -Descending
    if ($candidatos.Count -eq 0) {
        # Procurar pastas (backup em bruto sem Python)
        $candidatos = Get-ChildItem -Path $pastaBackups -Directory |
            Sort-Object LastWriteTime -Descending
    }
    if ($candidatos.Count -eq 0) {
        throw "Nenhum backup encontrado em $pastaBackups. Corre save-db.ps1 primeiro."
    }
    $Caminho = $candidatos[0].FullName
}

Write-Host "A restaurar: $Caminho"

# Determinar a pasta base, ficheiro .db principal e pares origem->device
if (Test-Path $Caminho -PathType Container) {
    # Pasta: contém predit.db (+ -wal/-shm opcionais)
    $base = $Caminho
    $dbPrincipal = Join-Path $base 'predit.db'
} else {
    # Ficheiro .db único (checkpoint do WAL feito com Python)
    $base = Split-Path -Parent $Caminho
    $dbPrincipal = $Caminho
}

if (-not (Test-Path $dbPrincipal)) {
    throw "Ficheiro .db não encontrado: $dbPrincipal"
}
if (-not (Test-FicheiroSQLite -Ficheiro $dbPrincipal)) {
    throw "O ficheiro $dbPrincipal não é um SQLite válido (cabeçal 'SQLite format 3' não encontrado)."
}

# Determinar quais os ficheiros a restaurar (lista de pares origem local -> nome no device).
# Com backup em pasta vão os três (db + wal + shm): o ficheiro .db sozinho pode estar
# desatualizado em relação ao WAL, já que o force-stop não fecha a base com limpeza.
$pares = @()
$pares += , @($dbPrincipal, 'predit.db')
$walLocal = Join-Path $base 'predit.db-wal'
$shmLocal = Join-Path $base 'predit.db-shm'
if (Test-Path $walLocal) { $pares += , @($walLocal, 'predit.db-wal') }
if (Test-Path $shmLocal) { $pares += , @($shmLocal, 'predit.db-shm') }

# Enviar ficheiros para /data/local/tmp/ no device
$ts = Get-Date -Format 'yyyyMMddHHmmss'
$tempRemoto = "/data/local/tmp/predit-restore-$ts"
& $adb -s $script:Dispositivo shell "mkdir -p $tempRemoto" 2>$null | Out-Null
foreach ($par in $pares) {
    $origem = $par[0]
    $nomeDevice = $par[1]
    $remoto = "$tempRemoto/$nomeDevice"
    & $adb -s $script:Dispositivo push $origem $remoto 2>$null | Out-Null
    $pushExit = $LASTEXITCODE
    if ($pushExit -ne 0) { throw "Falhou adb push de $origem -> $remoto (exit $pushExit)" }
    Write-Host "  enviado $nomeDevice -> $remoto"
}

# Limpar ficheiros existentes na pasta databases/ para evitar conflitos WAL
& $adb -s $script:Dispositivo shell "run-as $Pacote rm -f databases/predit.db databases/predit.db-wal databases/predit.db-shm" 2>$null | Out-Null

# Garantir que a pasta databases/ existe (numa instalação limpa ainda não existe,
# e o cp falharia com "No such file or directory")
& $adb -s $script:Dispositivo shell "run-as $Pacote mkdir -p databases" 2>$null | Out-Null

# Copiar para databases/ usando run-as
foreach ($par in $pares) {
    $nomeDevice = $par[1]
    $remoto = "$tempRemoto/$nomeDevice"
    $saida = & $adb -s $script:Dispositivo shell "run-as $Pacote cp $remoto databases/$nomeDevice" 2>&1
    $cpExit = $LASTEXITCODE
    if ($cpExit -ne 0) {
        Write-Host "  AVISO: run-as cp para $nomeDevice devolveu exit $cpExit (pode precisar de root): $saida"
    } else {
        Write-Host "  restaurado $nomeDevice"
    }
}

# Limpar temp no device
& $adb -s $script:Dispositivo shell "rm -rf $tempRemoto" 2>$null | Out-Null

# Verificar integridade
$sha = Get-Sha256 -Ficheiro $dbPrincipal
Write-Host ''
Write-Host "Restore concluído."
Write-Host "SHA256 do backup local: $sha"

# Verificar se os ficheiros foram restaurados
$verificacao = & $adb -s $script:Dispositivo shell "run-as $Pacote ls -l databases/" 2>&1
if ($verificacao -match 'predit\.db') {
    Write-Host "Verificação: ficheiro restaurado na pasta databases/:"
    $verificacao | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "AVISO: não foi possível verificar os ficheiros restaurados."
}

Write-Host ''
Write-Host "Pronto para instalar:  .\\gradlew.bat installDebug"
Write-Host "Depois de instalar, espera ~5s para o Auto Backup terminar de restaurar."
