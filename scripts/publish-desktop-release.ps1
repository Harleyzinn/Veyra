param([string]$Version='3.0.0')
$ErrorActionPreference='Stop'
$veyraRoot=Split-Path $PSScriptRoot -Parent
Push-Location $veyraRoot
$repo='Harleyzinn/Veyra';$credentials=$null;$headers=$null;$credentialLines=$null;$stage='validação'
try {
    if($Version -notmatch '^\d+\.\d+\.\d+$'){throw 'Versão inválida.'}
    $package=Get-Content 'apps/desktop/package.json' -Raw | ConvertFrom-Json
    if($package.version -ne $Version){throw 'Versão do pacote diferente da release.'}
    & git diff --quiet
    if($LASTEXITCODE -ne 0){throw 'Há mudanças não commitadas.'}
    & git diff --cached --quiet
    if($LASTEXITCODE -ne 0){throw 'Há mudanças não commitadas.'}
    $commit=(& git rev-parse HEAD).Trim()
    $remote=& git ls-remote "https://github.com/$repo.git" refs/heads/main
    if($LASTEXITCODE -ne 0 -or -not $remote.StartsWith($commit)){throw 'Faça push para main antes de publicar.'}
    $installer="apps/desktop/release/VeyraLife-Setup-$Version.exe"
    if(-not(Test-Path -LiteralPath $installer)){throw 'Gere o instalador antes de publicar.'}
    New-Item -ItemType Directory 'dist' -Force | Out-Null
    $env:VEYRA_DESKTOP_VERSION=$Version
    & node 'apps/desktop/scripts/sign-release.mjs'
    if($LASTEXITCODE -ne 0){throw 'Falha na assinatura do manifesto.'}
    $zip="dist/Veyra-Ecosystem-$Version.zip"
    & git archive --format=zip --prefix=Veyra/ "--output=$zip" HEAD
    if($LASTEXITCODE -ne 0){throw 'Falha na geração do código-fonte.'}
    $manifest='dist/desktop-update.json';$sums='dist/DESKTOP-SHA256SUMS.txt'
    Get-FileHash $installer,$manifest,$zip -Algorithm SHA256 | ForEach-Object {"$($_.Hash.ToLower())  $(Split-Path $_.Path -Leaf)"} | Set-Content $sums
    $files=@($installer,$manifest,$zip,$sums)
    $body=[IO.File]::ReadAllText((Join-Path $veyraRoot "docs/RELEASE-DESKTOP-$Version.md"))
    $body+="`n`nSHA-256 do instalador: ``$((Get-FileHash $installer -Algorithm SHA256).Hash.ToLower())``. Manifesto Ed25519 e hashes estão nos arquivos abaixo."
    $stage='autenticação'
    $credentialLines="protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
    if($LASTEXITCODE -ne 0){throw 'Credencial Git indisponível.'}
    $credentials=@{};foreach($line in $credentialLines){if($line -match '^([^=]+)=(.*)$'){$credentials[$matches[1]]=$matches[2]}}
    if(-not $credentials['password']){throw 'Credencial Git indisponível.'}
    $headers=@{Authorization="Bearer $($credentials['password'])";Accept='application/vnd.github+json';'X-GitHub-Api-Version'='2022-11-28';'User-Agent'='Veyra-desktop-release'}
    $stage='criação da release'
    $releases=Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers
    $release=$releases | Where-Object {$_.tag_name -eq "desktop-v$Version"} | Select-Object -First 1
    if($release -and $release.target_commitish -ne $commit){throw 'A tag já está associada a outro commit.'}
    if(-not $release){
        $payload=@{tag_name="desktop-v$Version";target_commitish=$commit;name="Veyra Life Desktop $Version";body=$body;draft=$true;prerelease=$false;make_latest='false'} | ConvertTo-Json
        $release=Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($payload))
    }
    foreach($path in $files){
        $name=Split-Path $path -Leaf;$stage="upload de $name";$hash=(Get-FileHash $path -Algorithm SHA256).Hash.ToLower()
        $existing=$release.assets | Where-Object {$_.name -eq $name} | Select-Object -First 1
        if($existing){if($existing.digest -ne "sha256:$hash"){throw 'Já existe um arquivo diferente com este nome.'};continue}
        $type=if($name.EndsWith('.exe')){'application/octet-stream'}elseif($name.EndsWith('.zip')){'application/zip'}elseif($name.EndsWith('.json')){'application/json'}else{'text/plain'}
        $asset=Invoke-RestMethod -Method Post -Uri "https://uploads.github.com/repos/$repo/releases/$($release.id)/assets?name=$([Uri]::EscapeDataString($name))" -Headers $headers -ContentType $type -InFile (Resolve-Path $path).Path
        if($asset.state -ne 'uploaded' -or $asset.size -ne (Get-Item $path).Length -or $asset.digest -ne "sha256:$hash"){throw 'Upload não passou na verificação.'}
        Write-Output "Arquivo verificado: $name"
    }
    $stage='publicação';$payload=@{draft=$false;prerelease=$false;make_latest='false'} | ConvertTo-Json
    $published=Invoke-RestMethod -Method Patch -Uri "https://api.github.com/repos/$repo/releases/$($release.id)" -Headers $headers -ContentType 'application/json' -Body $payload
    if($published.draft){throw 'Release permaneceu como rascunho.'}
    Write-Output "Release publicada: $($published.html_url)"
} catch {
    $status=if($_.Exception.Response){[int]$_.Exception.Response.StatusCode}else{'indisponível'}
    Write-Output "Falha em '$stage' (HTTP $status). Verifique a release antes de repetir."
    exit 1
} finally {
    if($headers){$headers.Clear()};if($credentials){$credentials.Clear()};$credentialLines=$null
    Remove-Item Env:VEYRA_DESKTOP_VERSION -ErrorAction SilentlyContinue
    Pop-Location
}
