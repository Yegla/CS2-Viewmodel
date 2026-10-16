"""Make compact inventory cards from locally exported CS2 inventory art."""

from pathlib import Path
import shutil
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'local-assets/skins/previews/source'
TEXTURES = ROOT / 'local-assets/generated/assets/cs2viewmodel/textures'
OUTPUT = ROOT / 'src/main/resources/assets/cs2viewmodel/textures/gui/skins'
OUTPUT.mkdir(parents=True, exist_ok=True)

def card(image, center_visible=False):
    image = image.convert('RGBA')
    image.thumbnail((224, 168), Image.Resampling.LANCZOS)
    result = Image.new('RGBA', (224, 168))
    result.alpha_composite(image, ((224 - image.width) // 2, (168 - image.height) // 2))
    if center_visible:
        bounds = result.getbbox()
        if bounds:
            visible = result.crop(bounds)
            result = Image.new('RGBA', (224, 168))
            result.alpha_composite(visible, ((224 - visible.width) // 2,
                                             (168 - visible.height) // 2))
    return result

for kind, ids in {
    'ak': ['default', 'porcelain', 'bus', 'explosive', 'crane'],
    'knife': ['default', 'karambit', 'bayonet', 'butterfly'],
    'glove': ['default', 'hardknuckle', 'specialist', 'sporty'],
}.items():
    for name in ids:
        with Image.open(SOURCE / f'{kind}_{name}.png') as original:
            card(original, center_visible=(kind == 'glove' and name == 'default')).save(
                OUTPUT / f'{kind}_{name}.png', optimize=True)

# Fullfinger uses the same centered T inventory art as the default glove.
shutil.copyfile(OUTPUT / 'glove_default.png', OUTPUT / 'glove_fullfinger.png')

# Sleeve cards show a centered rectangular sample of their exported fabric.
for name, texture in [('default', 'arms_ak_0.png'), ('fbi', 'variants/sleeve_fbi.png'),
                      ('sas', 'variants/sleeve_sas.png'), ('balkan', 'variants/sleeve_balkan.png')]:
    with Image.open(TEXTURES / texture) as source:
        swatch = ImageOps.fit(source.convert('RGB'), (200, 120),
                              method=Image.Resampling.LANCZOS, centering=(0.5, 0.5))
        image = Image.new('RGBA', (224, 168))
        image.paste(swatch, (12, 24))
        image.save(OUTPUT / f'sleeve_{name}.png', optimize=True)

print(f'Wrote {len(list(OUTPUT.glob("*.png")))} skin previews to {OUTPUT}')
