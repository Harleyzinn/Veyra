param([switch]$Enable)
$ErrorActionPreference='Stop'
$repo='Harleyzinn/Veyra'
$credentials=@{};$headers=@{}
try {
    # Reuse the authorized credential manager; never log or persist tokens.
    $credentialLines="protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
    if($LASTEXITCODE -ne 0){throw 'Acesso GitHub indisponível.'}
    foreach($line in $credentialLines){if($line -match '^([^=]+)=(.*)$'){$credentials[$matches[1]]=$matches[2]}}
    if(-not $credentials['password']){throw 'Acesso GitHub indisponível.'}
    $headers=@{Authorization="Bearer $($credentials['password'])";Accept='application/vnd.github+json';'X-GitHub-Api-Version'='2022-11-28';'User-Agent'='Veyra-web-publish'}
    $pages=$null
    try{$pages=Invoke-RestMethod "https://api.github.com/repos/$repo/pages" -Headers $headers}catch{if([int]$_.Exception.Response.StatusCode -ne 404){throw}}
    if($Enable){
        if(-not $pages){$pages=Invoke-RestMethod -Method Post "https://api.github.com/repos/$repo/pages" -Headers $headers -ContentType 'application/json' -Body '{"build_type":"workflow"}'}
        elseif($pages.build_type -ne 'workflow'){$pages=Invoke-RestMethod -Method Put "https://api.github.com/repos/$repo/pages" -Headers $headers -ContentType 'application/json' -Body '{"build_type":"workflow"}'}
    }
    if($pages){Write-Output "Pages: $($pages.html_url) | modo: $($pages.build_type) | estado: $($pages.status)"}else{Write-Output 'Pages ainda não habilitado. Use -Enable após revisar o conteúdo público.'}
    try{
        $runs=Invoke-RestMethod "https://api.github.com/repos/$repo/actions/workflows/finance-web.yml/runs?per_page=1" -Headers $headers
        $runs.workflow_runs | ForEach-Object{Write-Output "Workflow: $($_.html_url) | $($_.status) | $($_.conclusion) | commit $($_.head_sha)"}
    }catch{if([int]$_.Exception.Response.StatusCode -eq 404){Write-Output 'Workflow ainda não enviado ao GitHub.'}else{throw}}
}catch{
    $status=if($_.Exception.Response){[int]$_.Exception.Response.StatusCode}else{'indisponível'}
    Write-Output "Não foi possível concluir a operação Pages (HTTP $status). Nenhuma credencial foi registrada."
    exit 1
}finally{$credentials.Clear();$headers.Clear();$credentialLines=$null}
