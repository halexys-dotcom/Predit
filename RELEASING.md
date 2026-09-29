# Como publicar uma nova versao do Predit

> **IMPORTANTE — apkUrl do version.json**
>
> Desde a v2.0.1, o asset APK anexado as releases chama-se
> `app-github-release.apk` (nome do output do flavor github). O
> `apkUrl` do `version.json` tem de usar exatamente o mesmo nome de
> ficheiro, senao o OTA faz 404 no download.
>
> Exemplo correto:
>
>     "apkUrl": "https://github.com/halexys-dotcom/Predit/releases/download/vX.Y.Z/app-github-release.apk"
>
> Excecao historica: a release **v2.0.1** ficou com o asset `app-release.apk`,
> porque o `apkUrl` desse manifesto ja apontava para esse nome e foi preciso
> copiar o ficheiro antes do upload. Da v2.0.2 em diante nao se copia nada:
> anexa-se o output do Gradle com o nome que ele ja tem.

## Preparar

1. Garantir que `keystore/predit-release.jks` e `keystore.properties` existem no PC.
   Se nao existirem, correr `.\tools\criar-keystore.ps1` uma unica vez.
   Estes ficheiros sao irrecuperaveis — se se perderem, nao ha mais atualizacoes OTA.

2. Editar `app/build.gradle.kts` e subir:
   - `versionCode` (inteiro, sempre crescente)
   - `versionName` (string, "X.Y.Z")

3. Editar `docs/version.json` com o mesmo `versionCode`/`versionName` e com o
   `apkUrl` a apontar para a tag nova (ver a nota no topo).

## Build

    .\gradlew.bat clean assembleRelease

Se o `clean` falhar com `Unable to delete directory ... app\build` (no Windows o daemon
do Gradle costuma ter os jars do lint-cache abertos), parar o daemon e repetir:

    .\gradlew.bat --stop
    .\gradlew.bat clean assembleRelease

O APK fica em `app\build\outputs\apk\github\release\app-github-release.apk`.

Este e o flavor `github`, o unico com OTA ativo (`OTA_ATIVO = true`). O flavor
`playstore` tem `OTA_ATIVO = false` e nao faz pedidos de rede — e esse que vai
para a Play Store.

Confirmar que esta assinado com a release key:

    $apksigner = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\apksigner.bat" | Sort-Object FullName | Select-Object -Last 1
    & $apksigner verify --print-certs app\build\outputs\apk\github\release\app-github-release.apk

Deve dizer:

    V2 Signer: certificate DN: CN=Hugo Alexandre Henriques Correia, OU=Predit, O=HAConnect, L=Lisboa, ST=Lisboa, C=PT

Se disser `CN=Android Debug`, a keystore nao foi encontrada — verificar
`keystore.properties` e nao publicar antes de resolver.

Nota: com `minSdk = 26` a AGP assina apenas com o esquema **v2**, por isso
`keytool -printcert -jarfile` responde `Not a signed jar file`. Nao e erro: o v1
(JAR signing) so faz falta para Android 7 e anterior, e este projeto comeca no 8.

## Publicar no GitHub

### Via CLI (fluxo atual)

Criar primeiro um ficheiro `Temp\release-notes-vX.Y.Z.md` com o changelog.

Depois:

    Test-Path "Temp\release-notes-vX.Y.Z.md"
    Test-Path "app\build\outputs\apk\github\release\app-github-release.apk"
    Test-Path "docs\version.json"

    gh release create vX.Y.Z `
        --title "Predit X.Y.Z" `
        --notes-file "Temp\release-notes-vX.Y.Z.md" `
        --latest `
        --verify-tag `
        "app\build\outputs\apk\github\release\app-github-release.apk" `
        "docs\version.json"

Notas obrigatorias:

- Aspas explicitas em `--title` (o `Start-Process` sem aspas parte o argumento em
  dois, erro visto na v2.0.0-rc1).
- `--verify-tag` so passa se a tag ja existir no remote (`git push origin vX.Y.Z`
  antes).
- `--notes-file` evita problemas com multiplas strings na linha de comando.

### Via UI web (alternativa)

1. Ir a https://github.com/halexys-dotcom/Predit/releases/new
2. Tag: `vX.Y.Z` (corresponde ao versionName)
3. Titulo: `Predit X.Y.Z`
4. Corpo: o changelog
5. Anexar **dois ficheiros**:
   - `app-github-release.apk` (o output do Gradle tal como esta — nao copiar
     nem renomear; o `apkUrl` do `version.json` usa este mesmo nome)
   - `version.json` (o proprio ficheiro `docs/version.json`, ja com o `apkUrl`
     da versao nova)
6. Publicar

## Verificar

1. A URL do manifesto deve responder:

       https://github.com/halexys-dotcom/Predit/releases/latest/download/version.json

   Abre no browser. Devolve JSON? Pronto para distribuir.

2. Confirmar que o `versionCode`/`versionName` que vem no manifesto sao os da
   versao que acabaste de publicar.

3. Confirmar que a release ficou marcada como `Latest`, sem draft nem prerelease —
   o OTA le `releases/latest/download/`, por isso uma release nao-latest serve o
   manifesto antigo e os colegas nunca veem a versao nova.

4. Descarregar o APK pelo URL que o proprio manifesto declara e comparar o
   SHA-256 com o APK local:

       $manifest = Invoke-RestMethod `
           -Uri "https://github.com/halexys-dotcom/Predit/releases/latest/download/version.json"

       Invoke-WebRequest -Uri $manifest.apkUrl `
           -OutFile "$env:TEMP\apk-verify.apk" -UseBasicParsing

       (Get-FileHash "$env:TEMP\apk-verify.apk" -Algorithm SHA256).Hash
       (Get-FileHash "app\build\outputs\apk\github\release\app-github-release.apk" -Algorithm SHA256).Hash

   Os dois hashes tem de ser identicos. Se um der 404 ou os hashes forem
   diferentes, o `apkUrl` do manifesto nao aponta para o asset anexado —
   corrigir antes de anunciar a release.

## Distribuir a colegas

**Primeira vez (sideload):**

1. Enviar o `app-github-release.apk` ao colega (email, WhatsApp, Drive)
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
