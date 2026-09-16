<#
.SYNOPSIS
    Guarda uma cópia da base de dados real do Predit (predit.db) do telefone ligado.

.DESCRIPTION
    Só lê o dispositivo: não altera nada. Para a app, extrai predit.db (+ -wal/-shm)
    e, se houver Python, faz o checkpoint do WAL e produz UM único ficheiro
    consistente em tools/db-backups/.

    Serve para ter controlo explícito antes de correr `./gradlew connectedAndroidTest`:
    esse comando desinstala a app no fim da corrida e apaga a base de dados. O Android
    Auto Backup (allowBackup=true) repõe os dados depois de um novo installDebug, mas
    não é instantâneo — há alguns segundos em que a app arranca como primeiro arranque.

.PARAMETER Pacote
    applicationId de debug. Por omissão: pt.haconnect.predit.debug

.PARAMETER Pasta
    Pasta de destino. Por omissão: <raiz do repo>\tools\db-backups

.EXAMPLE
    .\tools\save-db.ps1
#>
[CmdletBinding()]
param(
    [string]$Pacote = 'pt.haconnect.predit.debug',
    [string]$Pasta,
    [string]$Device
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'db-comum.ps1')

$adb = Get-Adb
Assert-DispositivoLigado -Adb $adb -Device $Device
Assert-PacoteInstalado -Adb $adb -Pacote $Pacote
Stop-App -Adb $adb -Pacote $Pacote

$existentes = Get-FicheirosBdNoDispositivo -Adb $adb -Pacote $Pacote
Write-Host ("Ficheiros na BD: " + ($existentes -join ', '))
if ($existentes -notcontains 'predit.db') { throw 'predit.db não existe no dispositivo (app nunca arrancou?).' }

$temp = Join-Path $env:TEMP 'predit-save-db'
if (Test-Path $temp) { Remove-Item $temp -Recurse -Force }
New-Item -ItemType Directory -Force -Path $temp | Out-Null

foreach ($nome in @('predit.db', 'predit.db-wal', 'predit.db-shm')) {
    if ($existentes -notcontains $nome) { continue }
    $destino = Join-Path $temp $nome
    Invoke-ExecOutParaFicheiro -Adb $adb -ComandoAdb "exec-out run-as $Pacote cat databases/$nome" -Destino $destino
    Write-Host ("  extraído $nome ({0} bytes)" -f (Get-Item $destino).Length)
}

$pastaBackups = Get-PastaBackups -Pasta $Pasta
$selo = Get-Date -Format 'yyyyMMdd-HHmmss'
$python = Get-Python
$final = Join-Path $pastaBackups "predit-$selo.db"

if ($python) {
    $script = Join-Path $temp 'checkpoint.py'
    @'
import os, sqlite3, sys
origem = sys.argv[1]
destino = sys.argv[2]
con = sqlite3.connect(origem)
con.execute('PRAGMA wal_checkpoint(TRUNCATE)')
con.commit()
con.execute("VACUUM INTO ?", (destino,))
cur = con.execute("SELECT (SELECT COUNT(*) FROM dia_real), (SELECT COUNT(*) FROM tipo_turno), (SELECT COUNT(*) FROM rotacao), (SELECT COUNT(*) FROM rotacao_slot), (SELECT COUNT(*) FROM planejamento_mes), (SELECT COUNT(*) FROM ciclo_jornada)")
linha = cur.fetchone()
print('  dia_real=%d tipo_turno=%d rotacao=%d slots=%d planejamento=%d ciclos=%d' % linha)
print('  user_version=%d' % con.execute('PRAGMA user_version').fetchone()[0])
con.close()
'@ | Out-File -Encoding ascii $script
    Write-Host 'Checkpoint do WAL + VACUUM INTO:'
    & $python $script (Join-Path $temp 'predit.db') $final
    if ($LASTEXITCODE -ne 0) { throw 'Falhou o checkpoint do WAL.' }
} else {
    # Sem Python: fica a cópia em bruto (db + wal + shm têm de ficar juntos)
    $final = Join-Path $pastaBackups "predit-$selo"
    New-Item -ItemType Directory -Force -Path $final | Out-Null
    Copy-Item (Join-Path $temp 'predit.db*') -Destination $final
    Write-Host 'AVISO: sem Python — backup em bruto (db + wal + shm juntos). O restore precisa dos três ficheiros.'
}

Remove-Item $temp -Recurse -Force

$sha = Get-Sha256 -Ficheiro (Get-Item $final).FullName
Write-Host ''
Write-Host "Backup: $final"
Write-Host ("Tamanho: {0} bytes" -f (Get-Item $final).Length)
Write-Host "SHA256: $sha"