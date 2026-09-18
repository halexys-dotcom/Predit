# Como publicar uma nova versao do Predit

## Preparar

1. Garantir que `keystore/predit-release.jks` e `keystore.properties` existem no PC.
   Se nao existirem, correr `.\tools\criar-keystore.ps1` uma unica vez.
   Estes ficheiros sao irrecuperaveis — se se perderem, nao ha mais atualizacoes OTA.

2. Editar `app/build.gradle.kts` e subir:
   - `versionCode` (inteiro, sempre crescente)
   - `versionName` (string, "X.Y.Z")

## Build

    .\gradlew.bat clean assembleRelease

Se o `clean` falhar com `Unable to delete directory ... app\build` (no Windows o daemon
do Gradle costuma ter os jars do lint-cache abertos), parar o daemon e repetir:

    .\gradlew.bat --stop
    .\gradlew.bat clean assembleRelease

O APK fica em `app\build\outputs\apk\release\app-release.apk`.

Confirmar que esta assinado com a release key:

    $apksigner = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\apksigner.bat" | Sort-Object FullName | Select-Object -Last 1
    & $apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk

Deve dizer:

    V2 Signer: certificate DN: CN=Hugo Alexandre Henriques Correia, OU=Predit, O=HAConnect, L=Lisboa, ST=Lisboa, C=PT

Se disser `CN=Android Debug`, a keystore nao foi encontrada — verificar
`keystore.properties` e nao publicar antes de resolver.

Nota: com `minSdk = 26` a AGP assina apenas com o esquema **v2**, por isso
`keytool -printcert -jarfile` responde `Not a signed jar file`. Nao e erro: o v1
(JAR signing) so faz falta para Android 7 e anterior, e este projeto comeca no 8.

## Publicar no GitHub

1. Ir a https://github.com/halexys-dotcom/Predit/releases/new
2. Tag: `v0.11.0` (corresponde ao versionName)
3. Titulo: `Predit 0.11.0`
4. Corpo: o changelog
5. Anexar **dois ficheiros**:
   - `app-release.apk`
   - `version.json` (criado a partir de `docs/version.json`, com a URL correta)
6. Publicar

## Verificar

A URL do manifesto deve responder:

    https://github.com/halexys-dotcom/Predit/releases/latest/download/version.json

Abre no browser. Devolve JSON? Pronto para distribuir.

## Distribuir a colegas

**Primeira vez (sideload):**

1. Enviar o `app-release.apk` ao colega (email, WhatsApp, Drive)
2. Ele ativa "Instalar de fontes desconhecidas" nas Definicoes
3. Instala o APK manualmente

**Atualizacoes seguintes (OTA):**

1. O colega abre o Predit
2. Mais → Verificar atualizacoes
3. Se houver nova versao, toca em "Descarregar e instalar"
4. O Android pede permissao para instalar a atualizacao
5. Aceita — a app reinicia com a versao nova

## Migrar os dados do debug para a release

A app de release (`pt.haconnect.predit`) tem `applicationId` diferente da de debug
(`pt.haconnect.predit.debug`). Instalar a release **nao herda os dados do debug** —
arranca com base de dados vazia.

Para levar os dados de desenvolvimento para o primeiro telemovel de um colega:

1. Extrair a BD do debug com `.\tools\save-db.ps1` (fica em `tools/db-backups/`)
2. Enviar esse `.db` para o telemovel do colega (email, Drive, USB)
3. No telemovel, colocar o ficheiro em:
   `Android/data/pt.haconnect.predit/files/backups/`
   (criar a pasta `backups/` se nao existir)
4. Instalar a release e abrir
5. Ir a Mais → Backups, e o ficheiro aparece listado
6. Restaurar — a app reinicia e fica com os dados

## Numero de versao a subir

Cada release tem de ter `versionCode` maior que o anterior. A app rejeita atualizar
para uma versao com `versionCode` igual ou menor.

Historico de versoes ja publicadas fica registado nas GitHub Releases (tags v*).
