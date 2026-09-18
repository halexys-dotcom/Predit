# Script que cria a keystore de release do Predit.
# CORRE-SE UMA UNICA VEZ. Se a keystore se perder, e impossivel atualizar
# a app instalada nos telemoveis dos colegas.
#
# Depois de correr:
#   1. Guarda o ficheiro keystore\predit-release.jks num cofre/Drive
#   2. Guarda o keystore.properties no mesmo sitio
#   3. Apaga este script ou deixa ficar (nao estraga nada)
#
# Detalhes tecnicos que importam:
#   - A keystore e PKCS12 (formato padrao do keytool atual). Em PKCS12 existe
#     UM so password: o da chave fica igual ao do ficheiro.
#   - O keystore.properties e gravado em UTF-8 SEM BOM: o PowerShell 5 grava
#     BOM com -Encoding UTF8, e o Gradle passaria a ler a chave como
#     "<BOM>storeFile" e rebentava no arranque do build.
#   - Usa passwords simples (letras, digitos, - _ . @): sem "\", sem espacos
#     no inicio/fim e sem caracteres fora do ASCII.

$ErrorActionPreference = 'Stop'

$raiz = Split-Path -Parent $PSScriptRoot
$keystoreRel = 'keystore/predit-release.jks'
$keystorePath = Join-Path $raiz 'keystore\predit-release.jks'
$propertiesPath = Join-Path $raiz 'keystore.properties'
$alias = 'predit'

if (Test-Path $keystorePath) {
    Write-Host "Ja existe uma keystore em $keystorePath." -ForegroundColor Yellow
    Write-Host "Se quiseres criar uma nova, apaga-a primeiro." -ForegroundColor Yellow
    exit 1
}

function Get-Keytool {
    $noPath = Get-Command keytool -ErrorAction SilentlyContinue
    if ($noPath) { return $noPath.Source }
    $candidatos = @(
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\keytool.exe')
    )
    if ($env:LOCALAPPDATA) {
        $candidatos += (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr\bin\keytool.exe')
    }
    foreach ($c in $candidatos) { if (Test-Path $c) { return $c } }
    throw 'keytool nao encontrado. Instala um JDK (Adoptium) ou usa o jbr do Android Studio.'
}

function Read-PasswordASCIINaoVazia {
    param([string]$Pedido)
    $seguro = Read-Host -AsSecureString $Pedido
    $texto = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro))
    if ([string]::IsNullOrEmpty($texto)) { throw "Password vazia em '$Pedido'." }
    if ($texto -match '\\') { throw "Em '$Pedido' nao podes usar o caracter \ (o formato .properties trata-o como escape)." }
    if ($texto -ne $texto.Trim()) { throw "Em '$Pedido' nao podes comecar nem acabar com espacos." }
    if ($texto -match '[^\x20-\x7E]') { throw "Em '$Pedido' usa apenas caracteres ASCII (sem acentos)." }
    return $texto
}

$keytool = Get-Keytool
Write-Host "keytool: $keytool" -ForegroundColor DarkGray
Write-Host "Keystore: $keystorePath" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Vamos criar a keystore de release." -ForegroundColor Cyan
Write-Host ""
Write-Host "Escolhe DUAS passwords (podem ser iguais):" -ForegroundColor Cyan
Write-Host "  - store password (protege o ficheiro .jks inteiro)"
Write-Host "  - key password (protege a chave dentro do ficheiro)"
Write-Host ""
Write-Host "Guarda-as em sitios seguros. Sem elas, nunca consegues republicar." -ForegroundColor Yellow
Write-Host ""

$storePassword = Read-PasswordASCIINaoVazia 'Store password'
$keyPassword = Read-PasswordASCIINaoVazia 'Key password'

if ($storePassword -ne $keyPassword) {
    Write-Host ""
    Write-Host "Nota: a keystore e PKCS12, que so tem um password." -ForegroundColor DarkGray
    Write-Host "      A password da chave fica igual a do ficheiro (a que deste agora e ignorada)." -ForegroundColor DarkGray
    $keyPassword = $storePassword
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $keystorePath) | Out-Null

# O keytool escreve avisos no stderr e, com ErrorActionPreference = 'Stop', o
# PowerShell 5 transforma essa linha num erro terminativo que esconde a causa real.
$eapAnterior = $ErrorActionPreference
$ErrorActionPreference = 'Continue'

& $keytool -genkeypair `
    -v `
    -keystore $keystorePath `
    -storetype PKCS12 `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -alias $alias `
    -storepass $storePassword `
    -keypass $keyPassword `
    -dname "CN=Hugo Alexandre Henriques Correia, OU=Predit, O=HAConnect, L=Lisboa, ST=Lisboa, C=PT"

$codigoGenkey = $LASTEXITCODE

if ($codigoGenkey -ne 0) {
    Write-Host "keytool falhou (codigo $codigoGenkey). Verifica se o Java esta no PATH." -ForegroundColor Red
    exit 1
}

$conteudo = @"
storeFile=$keystoreRel
storePassword=$storePassword
keyAlias=$alias
keyPassword=$keyPassword
"@
[System.IO.File]::WriteAllText($propertiesPath, $conteudo, [System.Text.UTF8Encoding]::new($false))

$listagem = @(& $keytool -list -keystore $keystorePath -storepass $storePassword 2>&1)
$codigoListagem = $LASTEXITCODE

$ErrorActionPreference = $eapAnterior

if ($codigoListagem -ne 0 -or -not ($listagem -match [regex]::Escape($alias))) {
    Write-Host "A keystore foi gravada mas o alias '$alias' nao aparece na listagem:" -ForegroundColor Red
    $listagem | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    exit 1
}

$hash = (Get-FileHash -Algorithm SHA256 -Path $keystorePath).Hash

Write-Host ""
Write-Host "Keystore criada: $keystorePath" -ForegroundColor Green
Write-Host "Credenciais gravadas: $propertiesPath" -ForegroundColor Green
Write-Host "SHA-256 da keystore: $hash" -ForegroundColor Green
Write-Host ""
Write-Host "PROXIMOS PASSOS (importante):" -ForegroundColor Yellow
Write-Host "  1. Copia keystore\predit-release.jks e keystore.properties para um Drive/cofre"
Write-Host "  2. Ambos estao no .gitignore e nunca vao para o GitHub"
Write-Host "  3. Se algum dia perderes estes ficheiros, perdes a capacidade de atualizar"
Write-Host "  4. Guarda o SHA-256 acima: serve para confirmar as copias mais tarde"
Write-Host "     (Get-FileHash -Algorithm SHA256 -Path <copia>)"
Write-Host ""
Write-Host "Depois disto podes pedir os Passos C a I (signing, manifest, version.json)." -ForegroundColor Cyan
