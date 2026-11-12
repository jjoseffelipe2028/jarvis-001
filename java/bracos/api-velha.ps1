# Jarvis 001 - confere se a API ligada na porta 8080 e "velha" (melhoria do teste real, 12/11).
# Velha = ligada ANTES da ultima mudanca no codigo Java (src/main ou pom.xml).
# So desliga se tiver CERTEZA de que e a API do Jarvis (java + "bracos" na linha de comando).
# Nunca le o .env nem o token. So olha a porta 8080 em 127.0.0.1.
# Saida: 0 = API em dia (nao faz nada) | 2 = API velha, foi desligada | 1 = nao deu para saber (nao faz nada)

$ErrorActionPreference = 'Stop'
try {
    $pasta = $PSScriptRoot
    $conexao = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 8080 -State Listen | Select-Object -First 1
    if (-not $conexao) { exit 1 }

    $processo = Get-Process -Id $conexao.OwningProcess
    if ($processo.ProcessName -notin @('java', 'javaw')) { exit 1 }

    $linha = (Get-CimInstance Win32_Process -Filter "ProcessId = $($processo.Id)").CommandLine
    if (-not $linha -or $linha -notmatch 'bracos') { exit 1 }   # nao e o Jarvis: nao mexe

    $arquivos = @(Get-ChildItem -Path (Join-Path $pasta 'src\main') -Recurse -File)
    $arquivos += Get-Item (Join-Path $pasta 'pom.xml')
    $ultimaMudanca = ($arquivos | Measure-Object -Property LastWriteTime -Maximum).Maximum

    if ($processo.StartTime -ge $ultimaMudanca) { exit 0 }

    Write-Host "A API ligada e de antes da ultima mudanca no codigo. Reiniciando..."
    Stop-Process -Id $processo.Id
    # Espera a porta 8080 liberar (ate 30 s)
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        if (-not (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) { exit 2 }
    }
    exit 1
} catch {
    exit 1
}
