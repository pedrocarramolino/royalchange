# Genera los iconos PNG de la PWA a partir de los SVG maestros de esta carpeta.
# Requiere Google Chrome (modo headless). Uso, desde la raíz del repositorio:
#   powershell -ExecutionPolicy Bypass -File branding/render-icons.ps1
#
# Chrome headless no admite ventanas por debajo de ~500 px de ancho, así que cada SVG se
# renderiza a 512 px y después se reduce con interpolación bicúbica de alta calidad.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$chrome = @(
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $chrome) { throw 'Google Chrome no encontrado.' }

$branding = $PSScriptRoot
$out = Join-Path $branding '..\web\public\icons'
New-Item -ItemType Directory -Force $out | Out-Null
$out = (Resolve-Path $out).Path

$work = Join-Path ([System.IO.Path]::GetTempPath()) 'rc-icon-render'
New-Item -ItemType Directory -Force $work | Out-Null

function Render-Svg([string] $svgName) {
    $svgUri = ([System.Uri](Join-Path $branding $svgName)).AbsoluteUri
    $png = Join-Path $work ($svgName -replace '\.svg$', '-512.png')
    if (Test-Path $png) { Remove-Item $png }
    # Chrome escribe avisos inofensivos en stderr: no deben detener el script.
    $ErrorActionPreference = 'Continue'
    & $chrome --headless=new --disable-gpu --hide-scrollbars --no-first-run `
        --user-data-dir="$(Join-Path $work 'profile')" `
        --default-background-color=00000000 `
        --window-size=512,512 `
        --screenshot="$png" $svgUri 2>&1 | Out-Null
    $ErrorActionPreference = 'Stop'
    if (-not (Test-Path $png)) { throw "Error renderizando $svgName" }
    return $png
}

function Save-Resized([string] $source, [int] $size, [string] $name) {
    $image = [System.Drawing.Image]::FromFile($source)
    try {
        $bitmap = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.DrawImage($image, 0, 0, $size, $size)
        $graphics.Dispose()
        $bitmap.Save((Join-Path $out $name), [System.Drawing.Imaging.ImageFormat]::Png)
        $bitmap.Dispose()
    } finally {
        $image.Dispose()
    }
    Write-Host "OK $name (${size}px)"
}

$rounded = Render-Svg 'icon.svg'
$fullBleed = Render-Svg 'icon-maskable.svg'

Save-Resized $rounded 32 'favicon-32.png'
Save-Resized $rounded 192 'icon-192.png'
Save-Resized $rounded 512 'icon-512.png'
Save-Resized $fullBleed 512 'icon-maskable-512.png'
Save-Resized $fullBleed 180 'apple-touch-icon.png'

Copy-Item (Join-Path $branding 'icon.svg') (Join-Path $out 'icon.svg') -Force
Write-Host 'OK icon.svg'
