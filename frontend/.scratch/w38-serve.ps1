# W38 SPA server wrapper —— 用 Start-Process 启动并立即返回
$ErrorActionPreference = 'Continue'
$base = 'C:\Users\yueyanxi\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a4f2d1cc136385614c38b39\USN-lab-hub'
$node = "C:\Program Files\nodejs\node.exe"
$serverScript = Join-Path $base 'frontend\.scratch\w38-spa-server.cjs'
$outLog = Join-Path $env:TEMP ("w38-serve-" + (Get-Random) + ".log")
$errLog = Join-Path $env:TEMP ("w38-serve-err-" + (Get-Random) + ".log")
$env:PATH = "C:\Program Files\nodejs;$env:PATH"
# 使用 System.Diagnostics.Process 启动，独立于 PowerShell 进程组
try {
  $p = [System.Diagnostics.Process]::Start($node, "`"$serverScript`"")
  Write-Output "server pid=$($p.Id)"
} catch {
  Write-Output "Process.Start failed: $($_.Exception.Message)"
  exit 1
}
Start-Sleep -Seconds 3
$ready = Test-NetConnection -ComputerName 127.0.0.1 -Port 4174 -InformationLevel Quiet -WarningAction SilentlyContinue
if ($ready) { Write-Output "ready" } else { Write-Output "not ready" }
