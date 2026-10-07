param([string]$JavaHome = '')
$ErrorActionPreference = 'Stop'
$veyraRoot = Split-Path $PSScriptRoot -Parent
$veyraIsolated = Join-Path $veyraRoot '.tools/firebase-cli-isolated'
$veyraEmulators = Join-Path $veyraRoot '.tools/firebase-emulators'
New-Item -ItemType Directory -Path $veyraIsolated,$veyraEmulators -Force | Out-Null
$veyraPrevious = @{}
foreach ($veyraKey in @('XDG_CONFIG_HOME','FIREBASE_EMULATORS_PATH','FIREBASE_TOKEN','GOOGLE_APPLICATION_CREDENTIALS','JAVA_HOME','Path')) {
    $veyraPrevious[$veyraKey] = [Environment]::GetEnvironmentVariable($veyraKey,'Process')
}
try {
    $env:XDG_CONFIG_HOME = $veyraIsolated
    $env:FIREBASE_EMULATORS_PATH = $veyraEmulators
    Remove-Item Env:FIREBASE_TOKEN,Env:GOOGLE_APPLICATION_CREDENTIALS -ErrorAction SilentlyContinue
    # CLI's emulator runtime is independent of the Android Gradle toolchain.
    $veyraCandidates = @($JavaHome,$env:JAVA_HOME)
    $veyraCandidates += @(Get-ChildItem (Join-Path $veyraRoot '.tools/jdk21') -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)
    $veyraCandidates += @(Get-ChildItem 'C:\Program Files\Java','C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)
    $veyraSelectedJava = $null
    foreach ($veyraCandidate in $veyraCandidates) {
        if (-not $veyraCandidate) { continue }
        $veyraJavaExe = Join-Path $veyraCandidate 'bin/java.exe'
        if (-not (Test-Path -LiteralPath $veyraJavaExe)) { continue }
        $veyraVersion = & $veyraJavaExe -version 2>&1 | Out-String
        if ($veyraVersion -match 'version "(\d+)' -and [int]$Matches[1] -ge 21) { $veyraSelectedJava=$veyraCandidate;break }
    }
    if (-not $veyraSelectedJava) { throw 'Firebase CLI requer JDK 21 ou superior. Use -JavaHome com a pasta de um JDK compatível; o build Android continua com JDK 17.' }
    $env:JAVA_HOME = $veyraSelectedJava
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    Push-Location (Join-Path $veyraRoot 'firebase')
    try {
        & firebase emulators:exec --project demo-veyra --only auth,firestore,storage 'npm test'
        if ($LASTEXITCODE -ne 0) { throw "Testes Firebase falharam (código $LASTEXITCODE)." }
    } finally { Pop-Location }
} finally {
    foreach ($veyraKey in $veyraPrevious.Keys) {
        [Environment]::SetEnvironmentVariable($veyraKey,$veyraPrevious[$veyraKey],'Process')
    }
}
