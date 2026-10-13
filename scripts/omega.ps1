param([Parameter(ValueFromRemainingArguments=$true)][string[]]$OmegaArguments)
$ErrorActionPreference='Stop'
$taskRepoRoot=Split-Path $PSScriptRoot -Parent
$taskCandidates=@($env:OMEGA_PYTHON,(Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'))
$taskPython=$taskCandidates | Where-Object {$_ -and (Test-Path -LiteralPath $_ -PathType Leaf)} | Select-Object -First 1
if(-not $taskPython){
    $taskCommand=Get-Command python.exe -ErrorAction SilentlyContinue
    if($taskCommand -and $taskCommand.Source -notlike '*WindowsApps*'){$taskPython=$taskCommand.Source}
}
if(-not $taskPython){throw 'Instale Python 3.12+ e defina OMEGA_PYTHON com o caminho do python.exe. Nenhum pacote pip é necessário.'}
if(-not $OmegaArguments){$OmegaArguments=@('health')}
$taskPriorEncoding=[Console]::OutputEncoding
$taskPriorPythonEncoding=$env:PYTHONIOENCODING
try{
    [Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
    $env:PYTHONIOENCODING='utf-8'
    & $taskPython (Join-Path $taskRepoRoot 'tools/omega/brain.py') --repo $taskRepoRoot @OmegaArguments
    $taskResult=$LASTEXITCODE
}finally{
    [Console]::OutputEncoding=$taskPriorEncoding
    $env:PYTHONIOENCODING=$taskPriorPythonEncoding
}
if($taskResult -ne 0){exit $taskResult}
