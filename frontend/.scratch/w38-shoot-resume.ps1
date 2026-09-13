$ErrorActionPreference = 'Continue'
$base = 'C:\Users\yueyanxi\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6a4f2d1cc136385614c38b39\USN-lab-hub'
$outRoot = Join-Path $base 'docs\evidence\2026-W38\frontend'
$port = 4174
$edge = "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
if (-not (Test-Path $edge)) { $edge = "${env:ProgramFiles}\Microsoft\Edge\Application\msedge.exe" }
if (-not (Test-Path $edge)) { Write-Output "Edge not found"; exit 1 }

# 校验 server
$ready = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue
if (-not $ready) { Write-Output "server not up"; exit 1 }

# 任务列表
$viewports = @(1440, 1280, 390)
$heights = @(900, 800, 844)
$roles = @('admin','teacher','member')
$pages = @('01-login','02-workbench','04-admin-members','05-admin-attendance','06-iot-devices','07-iot-projects','08-iot-alerts','09-iot-commands','10-iot-logs','11-iot-device-detail','12-iot-project-detail')

$todo = @()
for ($i = 0; $i -lt 3; $i++) {
  $w = $viewports[$i]
  $h = $heights[$i]
  foreach ($r in $roles) {
    foreach ($n in $pages) {
      $outFile = Join-Path $outRoot ("$w" + "x" + "$h" + "\" + $r + "\" + $n + ".png")
      if (Test-Path $outFile) {
        $sz = (Get-Item $outFile).Length
        if ($sz -gt 10000) { continue }
      }
      $url = "http://127.0.0.1:$port/seed-$r.html"
      $todo += ,@{w=$w; h=$h; role=$r; name=$n; url=$url; outFile=$outFile}
    }
  }
  # public pages
  foreach ($pp in @(@{n='01-login'; u="http://127.0.0.1:$port/login"}, @{n='03-iot-public'; u="http://127.0.0.1:$port/iot/public"})) {
    $outFile = Join-Path $outRoot ("$w" + "x" + "$h" + "\public\" + $pp.n + ".png")
    if (Test-Path $outFile) {
      $sz = (Get-Item $outFile).Length
      if ($sz -gt 10000) { continue }
    }
    $todo += ,@{w=$w; h=$h; role='public'; name=$pp.n; url=$pp.u; outFile=$outFile}
  }
}

Write-Output ("todo count: " + $todo.Count)

foreach ($t in $todo) {
  $w = $t.w; $h = $t.h; $r = $t.role; $n = $t.name; $url = $t.url; $outFile = $t.outFile
  $outDir = Split-Path $outFile -Parent
  New-Item -ItemType Directory -Force -Path $outDir | Out-Null
  $profile = Join-Path $env:TEMP ("edge-w38-" + $r + "-" + $w + "x" + $h + "-" + $n + "-" + (Get-Random))
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
  if ((Test-Path $outFile) -and (Get-Item $outFile).Length -gt 10000) {
    $size = (Get-Item $outFile).Length
    Write-Output ("OK   {0}x{1}  {2}  {3} ({4} bytes)" -f $w,$h,$r,$n,$size)
  } else {
    Write-Output ("FAIL {0}x{1}  {2}  {3}" -f $w,$h,$r,$n)
  }
  Remove-Item -Recurse -Force $profile -ErrorAction SilentlyContinue
}

Write-Output "DONE"
