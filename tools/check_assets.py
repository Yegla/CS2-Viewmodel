"""Check every converted viewmodel part has a packaged texture."""

import struct
import sys
from pathlib import Path


root = Path(sys.argv[1]) / 'assets' / 'cs2viewmodel'
meshes = list((root / 'meshes').glob('*.c2m'))
missing = []
parts = 0
for mesh in meshes:
    data = mesh.read_bytes()
    if data[:4] != b'C2M1':
        raise ValueError(f'{mesh}: invalid header')
    offset = 8
    for _ in range(struct.unpack_from('>I', data, 4)[0]):
        length = struct.unpack_from('>H', data, offset)[0]
        offset += 2
        name = data[offset:offset + length].decode('utf-8')
        offset += length
        vertex_count = struct.unpack_from('>I', data, offset)[0]
        offset += 4 + vertex_count * 32
        parts += 1
        if not (root / 'textures' / name).is_file():
            missing.append(f'{mesh.name}: {name}')
    if offset != len(data):
        raise ValueError(f'{mesh}: trailing or truncated vertex data')
if missing:
    raise FileNotFoundError('\n'.join(missing))
print(f'{len(meshes)} meshes, {parts} parts, all textures present')
