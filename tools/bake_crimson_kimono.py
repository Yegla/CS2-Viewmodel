"""Bake CS2's Crimson Kimono pattern, layer mask, and AO to glove UVs."""
"""this here is experimental and idk if i should keep doing this since the thing still looks ugly"""

from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'local-assets/skins/source'
OUTPUT = ROOT / 'local-assets/generated/assets/cs2viewmodel/textures/variants/glove_specialist_0.png'
SIZE = 2048

def load(name, size=SIZE):
    with Image.open(SOURCE / name) as image:
        return np.asarray(image.convert('RGBA').resize((size, size), Image.Resampling.LANCZOS), dtype=np.float32) / 255

mask = load('kimono_mask.png')
ao = load('kimono_ao.png')
pattern = np.asarray(Image.open(SOURCE / 'kimono_pattern.png').convert('RGB'), dtype=np.float32) / 255

# g_fPatternTexCoordScale = 2.3 in specialist_kimono_diamonds_red.vmat.
# The pattern's channels are palette indices, not final RGB values. The three
# colors below use g_vColorTint5, 2, and 3 from the CS2 material, adjusted
# for display-space brightness against the game's inventory render.
coord = (np.arange(SIZE, dtype=np.float32) / SIZE * 2.3 * pattern.shape[0]).astype(np.int32) % pattern.shape[0]
tile = pattern[coord[:, None], coord[None, :], :]
winner = np.argmax(tile, axis=2)
strength = np.max(tile, axis=2)
# Material tints are linear; the exported inventory image shows their brighter
# display-space result. Keep the red near the image's median crimson color.
palette = np.array([[195, 100, 90], [53, 51, 62], [81, 81, 84]], dtype=np.float32)
colored_pattern = palette[winner]
colored_pattern = colored_pattern * (.48 + .52 * strength[..., None])
colored_pattern = np.where(strength[..., None] < .06,
                           np.array([32, 31, 35], dtype=np.float32), colored_pattern)

# A projection of the CS2 mask onto this first-person glove mesh shows that
# the visible back panels are blue in the packed layer mask. Red and green
# cover the palm and trim, so placing the pattern there hid the finish.
base = np.empty((SIZE, SIZE, 3), dtype=np.float32)
base[:] = [42, 41, 46]
base = base * (1 - mask[..., 0:1] * .28) + np.array([65, 43, 46]) * mask[..., 0:1] * .28
base = base * (1 - mask[..., 1:2] * .34) + np.array([59, 56, 63]) * mask[..., 1:2] * .34
mix = np.clip(mask[..., 2:3], 0, 1)
color = base * (1 - mix) + colored_pattern * mix
shade = .69 + ao[..., 0:1] * .31
color = color * shade
# Preserve the pale marks and edge wear visible in the CS2 surface map.
edge = np.clip((ao[..., 1:2] - ao[..., 0:1] - .28) * .38, 0, .14)
color = color * (1 - edge) + 180 * edge

# The source AO map contains the embossed TORTUE marks. Pick their relief
# edges inside the two logo plaques and tint those exact marks crimson.
with Image.open(SOURCE / 'kimono_ao.png') as image:
    relief = np.asarray(image.convert('RGB').resize((1024, 1024), Image.Resampling.LANCZOS),
                        dtype=np.float32)[..., 1]
relief_edges = (np.abs(np.roll(relief, 1, axis=0) - np.roll(relief, -1, axis=0))
                + np.abs(np.roll(relief, 1, axis=1) - np.roll(relief, -1, axis=1))) * .5
# These embossed plaques sit above the patterned shell. Give the red lettering
# a dark surface so the diamonds do not run across the TORTUE wordmark.
plaque_image = Image.new('L', (1024, 1024))
plaque_draw = ImageDraw.Draw(plaque_image)
plaque_draw.polygon([(458, 54), (694, 17), (711, 105), (480, 131)], fill=255)
plaque_draw.polygon([(507, 347), (702, 317), (724, 396), (531, 424)], fill=255)
plaque_image = plaque_image.filter(ImageFilter.GaussianBlur(2)).resize((SIZE, SIZE),
                                                                        Image.Resampling.LANCZOS)
plaque = np.asarray(plaque_image, dtype=np.float32)[..., None] / 255 * .93
color = color * (1 - plaque) + np.array([46, 45, 50]) * shade * plaque
logos = np.zeros((1024, 1024), dtype=np.float32)
logos[20:125, 445:710] = np.clip((relief_edges[20:125, 445:710] - 10) / 50, 0, 1)
logos[320:420, 500:725] = np.clip((relief_edges[320:420, 500:725] - 10) / 50, 0, 1)
logos = np.asarray(Image.fromarray(np.uint8(logos * 255)).resize((SIZE, SIZE),
                                                                 Image.Resampling.LANCZOS),
                   dtype=np.float32)[..., None] / 255
logo_tint = np.array([205, 65, 70], dtype=np.float32)
color = color * (1 - logos * .92) + logo_tint * logos * .92

# Brighten the real material mask's stitched borders around the finger pads.
green = mask[..., 1]
near_green = np.maximum.reduce((np.roll(green, 4, axis=0), np.roll(green, -4, axis=0),
                                np.roll(green, 4, axis=1), np.roll(green, -4, axis=1)))
stitches = np.clip((mask[..., 0:1] - .5) * 2, 0, 1) * near_green[..., None] * .42
color = color * (1 - stitches) + np.array([190, 62, 68]) * stitches
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
Image.fromarray(np.uint8(np.clip(np.rint(color), 0, 255)), 'RGB').save(OUTPUT, optimize=True)
print(OUTPUT)
