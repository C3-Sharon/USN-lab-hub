# W38 shoot - all-in-one, fully synchronous
$ErrorActionPreference = 'Continue'
$base = 'C:\Users\yueyanxi\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a4f2d1cc136385614c38b39\USN-lab-hub'
$outRoot = Join-Path $base 'docs\evidence\2026-W38\frontend'
$port = 4174
$node = "C:\Program Files\nodejs\node.exe"
$serverScript = Join-Path $base 'frontend\.scratch\w38-spa-server.cjs'

$edge = $null
$edgeCands = @(
  "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
  "${env:ProgramFiles}\Microsoft\Edge\Application\msedge.exe"
)
foreach ($c in $edgeCands) { if (Test-Path $c) { $edge = $c; break } }
if (-not $edge) { Write-Output "Edge not found"; exit 1 }

# Clean port
$existing = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
foreach ($c in $existing) { Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue }
Get-CimInstance Win32_Process | Where-Object { $_.Name -eq "node.exe" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep -Milliseconds 800

# Start server in a new detached PowerShell process via Start-Process -PassThru (no -Wait)
# This returns a process object; the actual node child is created via cmd /c start /B so
# it survives the ps1 host exiting later.
$env:PATH = "C:\Program Files\nodejs;$env:PATH"
$serverOutLog = Join-Path $env:TEMP ("w38-serve-out.log")
$serverErrLog = Join-Path $env:TEMP ("w38-serve-err.log")
# Use Start-Process with -RedirectStandardOutput/-RedirectStandardError; even if PowerShell kills
# the immediate child, the node process forked via start /B should survive.
$ps1 = $PSCommandPath
$wrapScript = Join-Path $env:TEMP ("w38-serve-wrap.ps1")
@"
`$node = '$node'
`$serverScript = '$serverScript'
`$env:PATH = 'C:\Program Files\nodejs;' + `$env:PATH
Set-Location (Split-Path `$serverScript -Parent)
& `$node `$serverScript
"@ | Out-File -FilePath $wrapScript -Encoding utf8

# 用 WMI 直接启 node（脱离 ps1 进程组）
$cmdLine = "`"$node`" `"$serverScript`""
$wmiProc = Invoke-WmiMethod -Class Win32_Process -Name Create -ArgumentList $cmdLine
$serverPid = $wmiProc.ProcessId
Write-Output "server pid=$serverPid via WMI"

# wait for ready
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
  $conn = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue
  if ($conn) { $ready = $true; break }
  Start-Sleep -Milliseconds 500
}
if (-not $ready) {
  Write-Output "server not ready"
  Get-Content $serverOutLog -ErrorAction SilentlyContinue | Select-Object -First 5
  Get-Content $serverErrLog -ErrorAction SilentlyContinue | Select-Object -First 5
  exit 1
}
Write-Output "server ready"

$viewports = @(
  @{ w = 1440; h = 900 },
  @{ w = 1280; h = 800 },
  @{ w = 390;  h = 844 }
)

$roles = @('admin','teacher','member')
$pages = @('01-login','02-workbench','04-admin-members','05-admin-attendance','06-iot-devices','07-iot-projects','08-iot-alerts','09-iot-commands','10-iot-logs','11-iot-device-detail','12-iot-project-detail')
$publicPages = @(
  @{ name='01-login';      url="http://127.0.0.1:$port/login" },
  @{ name='03-iot-public'; url="http://127.0.0.1:$port/iot/public" }
)

function Shoot {
  param([int]$w, [int]$h, [string]$name, [string]$role, [string]$url)
  $outDir = Join-Path $outRoot ("$w" + "x" + "$h" + "\" + $role)
  New-Item -ItemType Directory -Force -Path $outDir | Out-Null
  $outFile = Join-Path $outDir ($name + '.png')
  $profile = Join-Path $env:TEMP ("edge-w38-" + $role + "-" + $w + "x" + $h + "-" + $name + "-" + (Get-Random))
  New-Item -ItemType Directory -Force -Path $profile | Out-Null
  $argList = @(
    '--headless', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
    "--window-size=$w,$h",
    "--user-data-dir=`"$profile`"",
    '--virtual-time-budget=20000',
    "--screenshot=`"$outFile`"",
    $url
  )
  & $edge $argList 2>$null 3>$null
  if (Test-Path $outFile) {
    $size = (Get-Item $outFile).Length
    Write-Output ("OK   {0}x{1}  {2}  {3} -> {4} ({5} bytes)" -f $w,$h,$role,$name,$outFile,$size)
  } else {
    Write-Output ("FAIL {0}x{1}  {2}  {3}" -f $w,$h,$role,$name)
  }
  Remove-Item -Recurse -Force $profile -ErrorAction SilentlyContinue
}

foreach ($vp in $viewports) {
  foreach ($r in $roles) {
    foreach ($n in $pages) {
      $url = "http://127.0.0.1:$port/seed-$r.html"
      Shoot -w $vp.w -h $vp.h -name $n -role $r -url $url
    }
  }
  foreach ($pp in $publicPages) {
    $outDir = Join-Path $outRoot ("$w" + "x" + "$h" + "\public")
    # 改用 $vp.w
    $outDir = Join-Path $outRoot ($vp.w.ToString() + "x" + $vp.h.ToString() + "\public")
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $outFile = Join-Path $outDir ($pp.name + '.png')
    $profile = Join-Path $env:TEMP ("edge-w38-public-" + $vp.w + "x" + $vp.h + "-" + $pp.name + "-" + (Get-Random))
    New-Item -ItemType Directory -Force -Path $profile | Out-Null
    $argList = @(
      '--headless', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
      "--window-size=$($vp.w),$($vp.h)",
      "--user-data-dir=`"$profile`"",
      '--virtual-time-budget=20000',
      "--screenshot=`"$outFile`"",
      $pp.url
    )
    & $edge $argList 2>$null 3>$null
    if (Test-Path $outFile) {
      Write-Output ("OK   {0}x{1}  public  {2} -> {3}" -f $vp.w,$vp.h,$pp.name,$outFile)
    } else {
      Write-Output ("FAIL {0}x{1}  public  {2}" -f $vp.w,$vp.h,$pp.name)
    }
    Remove-Item -Recurse -Force $profile -ErrorAction SilentlyContinue
  }
}

# Cleanup
Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | ForEach-Object {
  Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
}
Remove-Item $serverOutLog,$serverErrLog,$wrapScript -ErrorAction SilentlyContinue
Write-Output "DONE"
