# Kio Desktop Fast Build & Package Script
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Building Kio Browser for PC Desktop (< 2 MB) " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

$root = Split-Path -Parent $PSScriptRoot
$desktopDir = Join-Path $root "desktop"
$outDir = Join-Path $desktopDir "bin\Release"
$releasesDir = Join-Path $root "releases"

if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
if (-not (Test-Path $releasesDir)) { New-Item -ItemType Directory -Path $releasesDir -Force | Out-Null }

$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path $csc)) {
    throw "CSC compiler not found at: $csc"
}

$sources = Get-ChildItem (Join-Path $desktopDir "src\*.cs") | Select-Object -ExpandProperty FullName
$coreDll = Join-Path $desktopDir "lib\net462\Microsoft.Web.WebView2.Core.dll"
$winFormsDll = Join-Path $desktopDir "lib\net462\Microsoft.Web.WebView2.WinForms.dll"
$loaderDll = Join-Path $desktopDir "lib\runtimes\win-x64\native\WebView2Loader.dll"

$references = @(
    "System.dll",
    "System.Core.dll",
    "System.Drawing.dll",
    "System.Windows.Forms.dll",
    "`"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\WPF\System.Speech.dll`"",
    "`"$coreDll`"",
    "`"$winFormsDll`""
) -join ","

$outputExe = Join-Path $outDir "Kio.exe"

Write-Host "Compiling C# sources..." -ForegroundColor Yellow
$compileArgs = @(
    "/target:winexe",
    "/optimize+",
    "/platform:x64",
    "/out:`"$outputExe`"",
    "/r:$references"
) + ($sources | ForEach-Object { "`"$_`"" })

$process = Start-Process -FilePath $csc -ArgumentList ($compileArgs -join " ") -NoNewWindow -Wait -PassThru
if ($process.ExitCode -ne 0) {
    throw "Compilation failed with exit code $($process.ExitCode)"
}

Write-Host "Compilation successful!" -ForegroundColor Green

# Copy DLL dependencies next to Kio.exe
Copy-Item $coreDll $outDir -Force
Copy-Item $winFormsDll $outDir -Force
Copy-Item $loaderDll $outDir -Force

$totalBytes = (Get-ChildItem $outDir -Recurse | Measure-Object -Property Length -Sum).Sum
$totalMB = [math]::Round($totalBytes / 1MB, 2)
Write-Host "Kio Desktop app directory size: $totalBytes bytes ($totalMB MB)" -ForegroundColor Cyan

# Package into ZIP for distribution in releases/
$zipOutput = Join-Path $releasesDir "Kio-Desktop-v2.0.0-windows-x64.zip"
if (Test-Path $zipOutput) { Remove-Item $zipOutput -Force }

Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($outDir, $zipOutput, [System.IO.Compression.CompressionLevel]::Optimal, $false)

$zipBytes = (Get-Item $zipOutput).Length
$zipMB = [math]::Round($zipBytes / 1MB, 2)

Write-Host "=============================================" -ForegroundColor Green
Write-Host " SUCCESS: Kio Desktop built & packaged!" -ForegroundColor Green
Write-Host " Distribution: $zipOutput" -ForegroundColor Green
Write-Host " Archive Size: $zipBytes bytes ($zipMB MB)" -ForegroundColor Green
Write-Host " Strictly Under 2 MB limit: $(if ($zipBytes -lt 2097152) {'PASSED'} else {'FAILED'})" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green

