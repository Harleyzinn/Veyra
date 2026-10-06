param([string]$Version='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
$repo='Harleyzinn/Veyra'
$stage='verificação local'
$credentials=$null;$headers=$null;$credentialLines=$null
try {
    if(-not $Version){$Version=[regex]::Match([IO.File]::ReadAllText((Join-Path $projectRoot 'app/build.gradle.kts')),'versionName = "([^"]+)"').Groups[1].Value}
    if($Version -notmatch '^\d+\.\d+\.\d+$'){throw 'Versão inválida.'}
    & git diff --quiet
    if($LASTEXITCODE -ne 0){throw 'Há mudanças não commitadas.'}
    & git diff --cached --quiet
    if($LASTEXITCODE -ne 0){throw 'Há mudanças não commitadas.'}
    $commit=(& git rev-parse HEAD).Trim()
    $remote=(& git ls-remote "https://github.com/$repo.git" refs/heads/main)
    if($LASTEXITCODE -ne 0 -or -not $remote.StartsWith($commit)){throw 'Faça push do commit para main antes de publicar.'}
    $apkPath="dist/Veyra-$Version.apk"
    if(-not(Test-Path -LiteralPath $apkPath)){throw 'Gere e assine o APK antes de publicar.'}
    if(Test-Path '.tools/jdk17'){$env:JAVA_HOME=(Get-ChildItem '.tools/jdk17' -Directory | Select-Object -First 1).FullName;$env:Path="$env:JAVA_HOME/bin;$env:Path"}
    $sdkRoot=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
    $buildTools=Join-Path $sdkRoot 'build-tools/35.0.0'
    $certificate=& "$buildTools/apksigner.bat" verify --print-certs $apkPath
    if($LASTEXITCODE -ne 0 -or -not($certificate -match 'certificate SHA-256 digest: 8595f5acf3676e236dd32198a08e71bf5d04d875697d395f5bccd6b7a4498f21')){throw 'A assinatura não corresponde à chave original do Veyra.'}
    $badging=& "$buildTools/aapt.exe" dump badging $apkPath
    $expected="package: name='app\.veyra\.life'.*versionName='$([regex]::Escape($Version))'"
    if($LASTEXITCODE -ne 0 -or -not($badging -match $expected)){throw 'O pacote/versão do APK não corresponde à release.'}
    $zipPath="dist/Veyra-Android-$Version.zip"
    & git archive --format=zip --prefix=Veyra-Android/ "--output=$zipPath" HEAD
    if($LASTEXITCODE -ne 0){throw 'Não foi possível gerar o ZIP do código.'}
    Get-FileHash $apkPath,$zipPath -Algorithm SHA256 | ForEach-Object { "$($_.Hash.ToLower())  $(Split-Path $_.Path -Leaf)" } | Set-Content 'dist/SHA256SUMS.txt'
    $files=@($apkPath,$zipPath,'dist/SHA256SUMS.txt')
    $apkHash=(Get-FileHash $apkPath -Algorithm SHA256).Hash.ToLower()
    $minor=$Version -replace '\.\d+$',''
    $notesPath="docs/RELEASE-$minor.md"
    $body=if(Test-Path -LiteralPath $notesPath){[IO.File]::ReadAllText((Join-Path $projectRoot $notesPath))}else{"Veyra Life $Version"}
    $body=$body.Replace('(PLAY-PROTECT.md)',"(https://github.com/$repo/blob/$commit/docs/PLAY-PROTECT.md)")
    $body+="`n`n## Download e integridade`n`nBaixe **Veyra-$Version.apk** abaixo. Android 8.0 ou superior. APK release assinado com a chave original; faça backup antes de atualizar.`n`nSHA-256: ``$apkHash``.`n`nO ZIP contém o código-fonte. Chaves, senhas e dados de teste não estão incluídos."
    $stage='autenticação'
    # Capture Git's credential manager output without logging or writing credentials.
    $credentialLines="protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
    if($LASTEXITCODE -ne 0){throw 'Credencial Git indisponível.'}
    $credentials=@{}
    foreach($line in $credentialLines){if($line -match '^([^=]+)=(.*)$'){$credentials[$matches[1]]=$matches[2]}}
    if(-not $credentials['password']){throw 'Credencial Git indisponível.'}
    $headers=@{Authorization="Bearer $($credentials['password'])";Accept='application/vnd.github+json';'X-GitHub-Api-Version'='2022-11-28';'User-Agent'='Veyra-release'}
    $stage='criação da release'
    $releases=Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers
    $release=$releases | Where-Object {$_.tag_name -eq "v$Version"} | Select-Object -First 1
    if($release -and $release.target_commitish -ne $commit){throw 'A tag já está associada a outro commit.'}
    if(-not $release){
        $payload=@{tag_name="v$Version";target_commitish=$commit;name="Veyra Life $Version";body=$body;draft=$true;prerelease=$false} | ConvertTo-Json
        $release=Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($payload))
    }
    $releaseId=$release.id
    foreach($path in $files){
        $name=Split-Path $path -Leaf;$stage="upload de $name"
        $localHash=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLower()
        $existing=$release.assets | Where-Object {$_.name -eq $name} | Select-Object -First 1
        if($existing){if($existing.digest -ne "sha256:$localHash"){throw 'Já existe um arquivo diferente com este nome.'};continue}
        $contentType=if($name.EndsWith('.apk')){'application/vnd.android.package-archive'}elseif($name.EndsWith('.zip')){'application/zip'}else{'text/plain'}
        $asset=Invoke-RestMethod -Method Post -Uri "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=$([Uri]::EscapeDataString($name))" -Headers $headers -ContentType $contentType -InFile (Resolve-Path -LiteralPath $path).Path
        if($asset.state -ne 'uploaded' -or $asset.size -ne (Get-Item -LiteralPath $path).Length -or $asset.digest -ne "sha256:$localHash"){throw 'Arquivo enviado não passou na verificação.'}
        Write-Output "Arquivo verificado: $name ($($asset.size) bytes)"
    }
    $stage='publicação'
    $payload=@{name="Veyra Life $Version";body=$body;draft=$false;prerelease=$false;make_latest='true'} | ConvertTo-Json
    $published=Invoke-RestMethod -Method Patch -Uri "https://api.github.com/repos/$repo/releases/$releaseId" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($payload))
    if($published.draft -or $published.prerelease){throw 'Release não publicada como estável.'}
    Write-Output "Release publicada: $($published.html_url)"
    $published.assets | ForEach-Object {Write-Output "$($_.name): $($_.browser_download_url)"}
} catch {
    $status=if($_.Exception.Response){[int]$_.Exception.Response.StatusCode}else{'indisponível'}
    Write-Output "Falha em '$stage' (HTTP $status). Verifique a release antes de repetir; ela pode estar em rascunho."
    exit 1
} finally {
    if($headers){$headers.Clear()};if($credentials){$credentials.Clear()};$credentialLines=$null
    Pop-Location
}
