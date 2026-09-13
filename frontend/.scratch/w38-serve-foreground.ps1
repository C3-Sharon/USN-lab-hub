# W38 SPA server foreground launcher —— 同步启 server，ps1 不退出则 server 不退出
$ErrorActionPreference = 'Continue'
$base = 'C:\Users\yueyanxi\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a4f2d1cc136385614c38b39\USN-lab-hub'
$node = "C:\Program Files\nodejs\node.exe"
$serverScript = Join-Path $base 'frontend\.scratch\w38-spa-server.cjs'
$env:PATH = "C:\Program Files\nodejs;$env:PATH"
Set-Location (Split-Path $serverScript -Parent)
& $node $serverScript
