param([string]$GameDirectory = 'E:\SteamLibrary\steamapps\common\Counter-Strike Global Offensive')

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$cli = Join-Path $root '.tools\source2viewer\Source2Viewer-CLI.exe'
$archive = Join-Path $GameDirectory 'game\csgo\pak01_dir.vpk'
$output = Join-Path $root 'local-assets\skins\previews\source'
if (-not (Test-Path -LiteralPath $cli)) { throw "Missing Source 2 Viewer CLI: $cli" }
if (-not (Test-Path -LiteralPath $archive)) { throw "Missing CS2 archive: $archive" }
New-Item -ItemType Directory -Path $output -Force | Out-Null

$images = @{
    'ak_default' = 'panorama/images/econ/weapons/base_weapons/weapon_ak47_png.vtex_c'
    'ak_porcelain' = 'panorama/images/econ/default_generated/weapon_ak47_ak_porcelain_heavy_png.vtex_c'
    'ak_bus' = 'panorama/images/econ/default_generated/weapon_ak47_ak47_t_bus_heavy_png.vtex_c'
    'ak_explosive' = 'panorama/images/econ/default_generated/weapon_ak47_ak47_explosive_heavy_png.vtex_c'
    'ak_crane' = 'panorama/images/econ/default_generated/weapon_ak47_ak47_crane_flight_heavy_png.vtex_c'
    'knife_default' = 'panorama/images/econ/weapons/base_weapons/weapon_knife_png.vtex_c'
    'knife_karambit' = 'panorama/images/econ/weapons/base_weapons/weapon_knife_karambit_png.vtex_c'
    'knife_bayonet' = 'panorama/images/econ/weapons/base_weapons/weapon_bayonet_png.vtex_c'
    'knife_butterfly' = 'panorama/images/econ/weapons/base_weapons/weapon_knife_butterfly_png.vtex_c'
    'glove_default' = 'panorama/images/econ/weapons/base_weapons/t_gloves_png.vtex_c'
    'glove_hardknuckle' = 'panorama/images/econ/weapons/base_weapons/ct_gloves_png.vtex_c'
    'glove_specialist' = 'panorama/images/econ/default_generated/specialist_gloves_specialist_kimono_diamonds_red_heavy_png.vtex_c'
    'glove_sporty' = 'panorama/images/econ/default_generated/sporty_gloves_glove_sport_flames_orange_heavy_png.vtex_c'
}
foreach ($entry in $images.GetEnumerator()) {
    & $cli -i $archive -f $entry.Value -d -o (Join-Path $output ($entry.Key + '.png')) | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to export $($entry.Key)" }
}
Write-Host "CS2 inventory images are in $output"
