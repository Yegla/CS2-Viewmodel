$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $projectRoot
try {
    python tools/convert_cs2.py local-assets/source local-assets/generated
    if ($LASTEXITCODE -ne 0) { throw 'Mesh conversion failed' }
    python tools/convert_skins.py local-assets/skins/source local-assets/generated
    if ($LASTEXITCODE -ne 0) { throw 'Skin conversion failed' }
    python tools/make_skin_previews.py
    if ($LASTEXITCODE -ne 0) { throw 'Skin preview generation failed' }
    python tools/check_assets.py local-assets/generated
    if ($LASTEXITCODE -ne 0) { throw 'Asset validation failed' }

    $logo = 'local-assets\branding\cs2_logo.png'
    if (-not (Test-Path -LiteralPath $logo)) { throw "Missing local CS2 logo: $logo" }
    Copy-Item -LiteralPath $logo -Destination 'src\main\resources\assets\cs2viewmodel\icon.png' -Force

    & .\gradlew.bat build --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }

    $version = [regex]::Match((Get-Content 'gradle.properties' -Raw), '(?m)^mod_version=(\S+)\s*$').Groups[1].Value
    if (-not $version) { throw 'mod_version is missing from gradle.properties' }
    $jarName = "cs2-viewmodel-$version.jar"
    $builtJar = Join-Path 'build\libs' $jarName
    if (-not (Test-Path -LiteralPath $builtJar)) { throw "Built jar is missing: $builtJar" }
    New-Item -ItemType Directory -Path 'dist' -Force | Out-Null
    Copy-Item -LiteralPath $builtJar -Destination (Join-Path 'dist' $jarName) -Force
    Write-Host "Ready: dist\$jarName"
} finally {
    Pop-Location
}
