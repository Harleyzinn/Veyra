param([switch]$SkipBuild)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if(Test-Path '.tools/jdk17') { $env:JAVA_HOME=(Get-ChildItem '.tools/jdk17' -Directory | Select-Object -First 1).FullName }
    if(-not $env:JAVA_HOME) { throw 'Configure JAVA_HOME com um JDK 17.' }
    $env:Path="$env:JAVA_HOME/bin;$env:Path"
    $sdkRoot=$env:ANDROID_HOME
    if(-not $sdkRoot) { $sdkRoot=Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    if(-not (Test-Path $sdkRoot)) { throw 'Configure ANDROID_HOME com o caminho do Android SDK.' }
    $env:ANDROID_HOME=$sdkRoot
    if(-not $SkipBuild) {
        & ./gradlew.bat :core:model:test :feature:finance:test :core:cloud:testDebugUnitTest :app:lintDebug :app:assembleRelease --console=plain
        if($LASTEXITCODE -ne 0) { throw 'Compilação ou validação falhou.' }
    }
    $version=[regex]::Match([IO.File]::ReadAllText((Join-Path $projectRoot 'app/build.gradle.kts')), 'versionName = "([^"]+)"').Groups[1].Value
    $apkPath="dist/Veyra-$version.apk"
    $signDir=Join-Path $projectRoot '.signing'
    $keyFile=Join-Path $signDir 'veyra-life.jks'
    $passwordFile=Join-Path $signDir 'password.txt'
    New-Item -ItemType Directory -Force -Path $signDir,'dist' | Out-Null
    if(-not (Test-Path $keyFile)) {
        if(Test-Path $passwordFile) { throw 'A senha existe, mas a chave não. Restaure a chave original antes de gerar atualizações.' }
        $randomBytes=New-Object byte[] 32
        [System.Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
        $env:VEYRA_STORE_PASS=[Convert]::ToHexString($randomBytes)
        [IO.File]::WriteAllText($passwordFile,$env:VEYRA_STORE_PASS)
        & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore $keyFile -storepass:env VEYRA_STORE_PASS -keypass:env VEYRA_STORE_PASS -alias veyra -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Veyra Life, OU=Personal App, O=Veyra, C=BR'
        if($LASTEXITCODE -ne 0) { throw 'Não foi possível gerar a chave de assinatura.' }
    } else { $env:VEYRA_STORE_PASS=[IO.File]::ReadAllText($passwordFile).Trim() }
    $buildTools=Join-Path $sdkRoot 'build-tools/35.0.0'
    & "$buildTools/zipalign.exe" -P 16 -f -v 4 'app/build/outputs/apk/release/app-release-unsigned.apk' 'dist/veyra-aligned.apk' | Out-Null
    if($LASTEXITCODE -ne 0) { throw 'Alinhamento do APK falhou.' }
    & "$buildTools/apksigner.bat" sign --ks $keyFile --ks-key-alias veyra --ks-pass env:VEYRA_STORE_PASS --key-pass env:VEYRA_STORE_PASS --out $apkPath 'dist/veyra-aligned.apk'
    if($LASTEXITCODE -ne 0) { throw 'Assinatura do APK falhou.' }
    & "$buildTools/apksigner.bat" verify --verbose $apkPath
    if($LASTEXITCODE -ne 0) { throw 'Verificação da assinatura falhou.' }
    Get-FileHash $apkPath -Algorithm SHA256 | ForEach-Object { "$($_.Hash.ToLower())  Veyra-$version.apk" } | Set-Content 'dist/SHA256SUMS.txt'
    Write-Host "APK pronto: $projectRoot\dist\Veyra-$version.apk"
} finally {
    Remove-Item Env:VEYRA_STORE_PASS -ErrorAction SilentlyContinue
    Pop-Location
}
