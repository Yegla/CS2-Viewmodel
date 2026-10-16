"""Build AK finish textures from local CS2 materials."""

import sys
from pathlib import Path

from PIL import Image


def save(image, path):
    image.save(path, optimize=True)
    print(path.name, image.size)


def build(source, generated):
    textures = generated / 'assets' / 'cs2viewmodel' / 'textures'
    output = textures / 'skins'
    output.mkdir(parents=True, exist_ok=True)
    for obsolete in ('knife_crimson.png', 'knife_cobalt.png',
                     'knife_gold.png', 'knife_tiger.png'):
        (output / obsolete).unlink(missing_ok=True)

    for name in ('ak_porcelain', 'ak_bus', 'ak_explosive', 'ak_crane'):
        with Image.open(source / f'{name}.png') as image:
            image = image.convert('RGB')
            if image.size != (2048, 2048):
                image = image.resize((2048, 2048), Image.Resampling.LANCZOS)
            save(image, output / f'{name}.png')

if __name__ == '__main__':
    build(Path(sys.argv[1]), Path(sys.argv[2]))
