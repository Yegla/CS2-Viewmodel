param(
    [string]$GameDirectory = 'E:\SteamLibrary\steamapps\common\Counter-Strike Global Offensive'
)

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$cli = Join-Path $projectRoot '.tools\source2viewer\Source2Viewer-CLI.exe'
$archive = Join-Path $GameDirectory 'game\csgo\pak01_dir.vpk'
$output = Join-Path $projectRoot 'local-assets\source'

if (-not (Test-Path -LiteralPath $cli)) {
    throw "Source 2 Viewer CLI is missing at $cli. Download the Windows CLI from https://github.com/ValveResourceFormat/ValveResourceFormat/releases"
}
if (-not (Test-Path -LiteralPath $archive)) {
    throw "CS2 VPK is missing at $archive"
}
New-Item -ItemType Directory -Path $output -Force | Out-Null

$models = @(
    @{ Path = 'weapons/models/shared/arms/weapon_arms.vmdl_c'; Name = 'arms.glb'; Animate = $false },
    @{ Path = 'weapons/models/shared/arms/weapon_arms.vmdl_c'; Name = 'arms_animated.glb'; Animate = $true },
    @{ Path = 'weapons/models/ak47/weapon_rif_ak47.vmdl_c'; Name = 'ak47.glb'; Animate = $false },
    @{ Path = 'weapons/models/knife/knife_default_ct/weapon_knife_default_ct.vmdl_c'; Name = 'knife.glb'; Animate = $false }
)

foreach ($model in $models) {
    $arguments = @('-i', $archive, '-f', $model.Path, '-d', '-o', (Join-Path $output $model.Name),
                   '--gltf_export_format', 'glb', '--gltf_export_materials')
    if ($model.Animate) {
        $arguments += '--gltf_export_animations'
    } else {
        $arguments += '--gltf_textures_adapt'
    }
    & $cli @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "CS2 export failed for $($model.Path)"
    }
}

$clips = @(
    @{ Path = 'animation/anims/viewmodel/rifle/rifle_ak/idle_ak.vnmclip_c'; Name = 'ak_idle.glb' },
    @{ Path = 'animation/anims/viewmodel/knife/_default_knife/idle_knife.vnmclip_c'; Name = 'knife_idle.glb' }
)
foreach ($clip in $clips) {
    & $cli -i $archive -f $clip.Path -d -o (Join-Path $output $clip.Name) --gltf_export_format glb
    if ($LASTEXITCODE -ne 0) {
        throw "CS2 idle clip export failed for $($clip.Path)"
    }
}

Write-Host "CS2 exports are in $output"
