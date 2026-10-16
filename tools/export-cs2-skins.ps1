param(
    [string]$GameDirectory = 'E:\SteamLibrary\steamapps\common\Counter-Strike Global Offensive'
)

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$cli = Join-Path $projectRoot '.tools\source2viewer\Source2Viewer-CLI.exe'
$archive = Join-Path $GameDirectory 'game\csgo\pak01_dir.vpk'
$output = Join-Path $projectRoot 'local-assets\skins\source'
if (-not (Test-Path -LiteralPath $cli)) { throw "Missing Source 2 Viewer CLI: $cli" }
if (-not (Test-Path -LiteralPath $archive)) { throw "Missing CS2 archive: $archive" }
New-Item -ItemType Directory -Path $output -Force | Out-Null

$textures = @{
    'ak_porcelain' = 'items/assets/paintkits/community/community_33/ak_porcelain_albedo_texture_tga_a86d8420.vtex_c'
    'ak_bus' = 'items/assets/paintkits/community/community_34/ak47_t_bus_albedo_texture_tga_856aaf50.vtex_c'
    'ak_explosive' = 'items/assets/paintkits/community/community_35/ak47_explosive_albedo_texture_psd_14b0eb90.vtex_c'
    'ak_crane' = 'items/assets/paintkits/community/community_37/ak47_crane_flight_color_tga_bcbe11b4.vtex_c'
    'sleeve_fbi' = 'characters/models/ctm_fbi/sleeve/materials/sleeve_fbi_color_psd_46dce074.vtex_c'
    'sleeve_sas' = 'characters/models/ctm_sas/sleeve/materials/sleeve_sas_color_psd_b45d8ff2.vtex_c'
    'sleeve_balkan' = 'characters/models/tm_balkan/sleeve/materials/sleeve_balkan_color_psd_e81e319d.vtex_c'
    'sporty_ao' = 'items/assets/paintkits/volatile_02/sporty_glove_ao_psd_706aaf06.vtex_c'
    'sporty_flame' = 'items/assets/paintkits/volatile_02/sporty_glove_flame_color_psd_11be2dbc.vtex_c'
    'specialist_ao' = 'items/assets/paintkits/volatile_02/glove_specialist_ao_psd_15015b90.vtex_c'
    'specialist_trim' = 'items/assets/paintkits/volatile_02/patterns/glove_specialist_trim_quilted_detail_color_psd_bf381c1.vtex_c'
    'kimono_pattern' = 'materials/models/weapons/customization/paints/hydrographic/kimono_diamonds_psd_bd898c73.vtex_c'
    'kimono_mask' = 'characters/models/shared/arms/glove_specialist/materials/glove_specialist_01_mask_psd_8f587917.vtex_c'
    'kimono_ao' = 'characters/models/shared/arms/glove_specialist/materials/glove_specialist_01_ao_psd_c0371e7e.vtex_c'
}
foreach ($entry in $textures.GetEnumerator()) {
    & $cli -i $archive -f $entry.Value -d -o (Join-Path $output ($entry.Key + '.png')) | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to export $($entry.Key)" }
}
$models = @{
    'glove_fullfinger' = 'agents/models/shared/arms/glove_fullfinger/glove_fullfinger.vmdl_c'
    'glove_hardknuckle' = 'agents/models/shared/arms/glove_hardknuckle/glove_hardknuckle.vmdl_c'
    'glove_specialist' = 'agents/models/shared/arms/glove_specialist/glove_specialist.vmdl_c'
    'glove_sporty' = 'agents/models/shared/arms/glove_sporty/glove_sporty.vmdl_c'
    'knife_karambit' = 'weapons/models/knife/knife_karambit/weapon_knife_karambit.vmdl_c'
    'knife_bayonet' = 'weapons/models/knife/knife_bayonet/weapon_knife_bayonet.vmdl_c'
    'knife_butterfly' = 'weapons/models/knife/knife_butterfly/weapon_knife_butterfly.vmdl_c'
}
foreach ($entry in $models.GetEnumerator()) {
    & $cli -i $archive -f $entry.Value -d -o (Join-Path $output ($entry.Key + '.glb')) `
        --gltf_export_format glb --gltf_export_materials --gltf_textures_adapt | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to export $($entry.Key)" }
}
Write-Host "Skin source models and textures are in $output"
