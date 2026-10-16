"""Convert locally exported CS2 glTF models to compact Minecraft render data.

Usage: python tools/convert_cs2.py local-assets/source local-assets/generated
Requires numpy and Pillow. Source2Viewer-CLI creates the source GLBs and PNGs.
"""

import json
import math
import shutil
import struct
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
from PIL import Image


COMPONENT_TYPES = {
    5121: np.dtype('u1'),
    5123: np.dtype('<u2'),
    5125: np.dtype('<u4'),
    5126: np.dtype('<f4'),
}
COMPONENT_COUNTS = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}
RIGHT_GRIP_SHIFT = (0.000, 0.105, 0.020)
WRITTEN_VARIANT_TEXTURES = set()


def read_glb(path):
    data = path.read_bytes()
    if data[:4] != b'glTF':
        raise ValueError(f'{path} is not a GLB')
    json_length, json_type = struct.unpack_from('<I4s', data, 12)
    if json_type != b'JSON':
        raise ValueError(f'{path} has no JSON chunk')
    gltf = json.loads(data[20:20 + json_length])
    bin_start = 20 + json_length
    bin_length, bin_type = struct.unpack_from('<I4s', data, bin_start)
    if bin_type != b'BIN\0':
        raise ValueError(f'{path} has no BIN chunk')
    return gltf, memoryview(data)[bin_start + 8:bin_start + 8 + bin_length]


def accessor(gltf, binary, index):
    entry = gltf['accessors'][index]
    view = gltf['bufferViews'][entry['bufferView']]
    dtype = COMPONENT_TYPES[entry['componentType']]
    width = COMPONENT_COUNTS[entry['type']]
    start = view.get('byteOffset', 0) + entry.get('byteOffset', 0)
    stride = view.get('byteStride', dtype.itemsize * width)
    array = np.ndarray((entry['count'], width), dtype=dtype, buffer=binary,
                       offset=start, strides=(stride, dtype.itemsize))
    return np.asarray(array)


def rotate_y(points, degrees):
    angle = math.radians(degrees)
    sine, cosine = math.sin(angle), math.cos(angle)
    result = points.copy()
    result[:, 0] = points[:, 0] * cosine + points[:, 2] * sine
    result[:, 2] = -points[:, 0] * sine + points[:, 2] * cosine
    return result


def rotate_x(points, degrees):
    angle = math.radians(degrees)
    sine, cosine = math.sin(angle), math.cos(angle)
    result = points.copy()
    result[:, 1] = points[:, 1] * cosine - points[:, 2] * sine
    result[:, 2] = points[:, 1] * sine + points[:, 2] * cosine
    return result


def node_matrix(node, curl_degrees=0.0):
    if 'matrix' in node:
        return np.asarray(node['matrix'], dtype=np.float64).reshape(4, 4).T
    x, y, z, w = node.get('rotation', (0, 0, 0, 1))
    rotation = np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ], dtype=np.float64)
    if curl_degrees:
        a = math.radians(curl_degrees)
        curl = np.array([[math.cos(a), -math.sin(a), 0],
                         [math.sin(a), math.cos(a), 0], [0, 0, 1]])
        rotation = rotation @ curl
    transform_matrix = np.eye(4, dtype=np.float64)
    transform_matrix[:3, :3] = rotation @ np.diag(node.get('scale', (1, 1, 1)))
    transform_matrix[:3, 3] = node.get('translation', (0, 0, 0))
    return transform_matrix


def sampled_clip(source, name):
    gltf, binary = read_glb(source / f'{name}_idle.glb')
    nodes = [dict(node) for node in gltf['nodes']]
    animation = gltf['animations'][0]
    for channel in animation['channels']:
        sampler = animation['samplers'][channel['sampler']]
        nodes[channel['target']['node']][channel['target']['path']] = accessor(gltf, binary, sampler['output'])[0].tolist()
    return nodes


def global_matrices(nodes):
    parents = {child: parent for parent, node in enumerate(nodes) for child in node.get('children', ())}
    matrices = {}

    def calculate(index):
        if index not in matrices:
            local = node_matrix(nodes[index])
            matrices[index] = calculate(parents[index]) @ local if index in parents else local
        return matrices[index]

    return [calculate(index) for index in range(len(nodes))], parents


def skin_arms(gltf, binary, points, normals, attributes, clip_nodes, support_lift=False,
              joint_indices=None, weights=None):
    nodes = gltf['nodes']
    skin = gltf['skins'][0]
    bind_global, parents = global_matrices(nodes)
    clip_global, _ = global_matrices(clip_nodes)
    clip_by_name = {node.get('name'): clip_global[index] for index, node in enumerate(clip_nodes)}

    # The knife idle clip bends the open left forearm sharply inward. Rotate
    # the full lower-arm chain at the elbow toward the upper-arm direction;
    # the glove, fingers, twist joints and sleeve then remain connected.
    knife_forearm_correction = None
    if not support_lift:
        shoulder = clip_by_name['armUpperShoulder_L'][:3, 3]
        elbow = clip_by_name['arm_lower_L'][:3, 3]
        wrist = clip_by_name['hand_L'][:3, 3]
        upper = elbow - shoulder
        lower = wrist - elbow
        axis = np.cross(lower, upper)
        axis /= np.linalg.norm(axis)
        x, y, z = axis
        sine, cosine = math.sin(math.radians(15)), math.cos(math.radians(15))
        knife_forearm_correction = np.eye(4)
        knife_forearm_correction[:3, :3] = np.array([
            [cosine + x*x*(1-cosine), x*y*(1-cosine)-z*sine, x*z*(1-cosine)+y*sine],
            [y*x*(1-cosine)+z*sine, cosine + y*y*(1-cosine), y*z*(1-cosine)-x*sine],
            [z*x*(1-cosine)-y*sine, z*y*(1-cosine)+x*sine, cosine + z*z*(1-cosine)],
        ])
        knife_forearm_correction[:3, 3] = elbow - knife_forearm_correction[:3, :3] @ elbow

    def below_bone(index, bone_name):
        while index in parents:
            if nodes[index].get('name') == bone_name:
                return True
            index = parents[index]
        return nodes[index].get('name') == bone_name

    def posed_matrix(index):
        name = nodes[index].get('name')
        if name in clip_by_name:
            matrix = clip_by_name[name]
        else:
            # Source 2's standalone clip omits twist bones. Inherit the nearest
            # animated parent's transform while retaining the bind-space offset.
            ancestor = parents[index]
            while nodes[ancestor].get('name') not in clip_by_name:
                ancestor = parents[ancestor]
            matrix = clip_by_name[nodes[ancestor]['name']] @ np.linalg.inv(bind_global[ancestor]) @ bind_global[index]

        if knife_forearm_correction is not None and below_bone(index, 'arm_lower_L'):
            matrix = knife_forearm_correction @ matrix
        if support_lift:
            shift = np.zeros(3)
            if below_bone(index, 'hand_L'):
                shift[1] = 0.075
            elif below_bone(index, 'armUpperShoulder_R'):
                # Move every right-arm joint by the same amount. Translating
                # only hand_R stretched the glove/sleeve between wrist and arm.
                shift[:] = RIGHT_GRIP_SHIFT
            if np.any(shift):
                adjustment = np.eye(4)
                adjustment[:3, 3] = shift
                matrix = adjustment @ matrix
        return matrix

    inverse_bind = accessor(gltf, binary, skin['inverseBindMatrices']).reshape(-1, 4, 4).transpose(0, 2, 1)
    pose = np.asarray([posed_matrix(index) for index in skin['joints']]) @ inverse_bind
    if joint_indices is None:
        joint_indices = accessor(gltf, binary, attributes['JOINTS_0']).astype(np.int32)
    if weights is None:
        weights = accessor(gltf, binary, attributes['WEIGHTS_0']).astype(np.float64)
    homogeneous = np.column_stack((points, np.ones(len(points))))
    result = np.zeros((len(points), 4), dtype=np.float64)
    posed_normals = np.zeros((len(points), 3), dtype=np.float64)
    for influence in range(4):
        transformed = np.einsum('nij,nj->ni', pose[joint_indices[:, influence]], homogeneous)
        result += transformed * weights[:, influence:influence + 1]
        rotated_normals = np.einsum('nij,nj->ni', pose[joint_indices[:, influence], :3, :3], normals)
        posed_normals += rotated_normals * weights[:, influence:influence + 1]
    posed_normals /= np.maximum(np.linalg.norm(posed_normals, axis=1)[:, None], 1e-8)
    return result[:, :3].astype(np.float32), posed_normals.astype(np.float32)


def transform(name, positions, clip_nodes=None):
    positions = positions.astype(np.float32, copy=True)
    if name in ('ak47', 'knife') or name.startswith('knife_'):
        clip_global, _ = global_matrices(clip_nodes)
        clip_by_name = {node.get('name'): clip_global[index] for index, node in enumerate(clip_nodes)}
        # The clip's weapon joint is the viewmodel attachment for the source
        # weapon mesh. Both are in metres, so no per-vertex warping is needed.
        attachment = clip_by_name['wpn'] @ np.linalg.inv(clip_by_name['weapon'])
        positions = (np.column_stack((positions, np.ones(len(positions)))) @ attachment.T)[:, :3]
    # A single rigid camera placement keeps the hands and weapon aligned.
    positions = rotate_y(positions, 192.0)
    positions += np.array([0.24, -0.18, -0.25], dtype=np.float32)
    return positions


def transform_normals(name, normals, clip_nodes):
    if name in ('ak47', 'knife') or name.startswith('knife_'):
        clip_global, _ = global_matrices(clip_nodes)
        clip_by_name = {node.get('name'): clip_global[index] for index, node in enumerate(clip_nodes)}
        attachment = clip_by_name['wpn'] @ np.linalg.inv(clip_by_name['weapon'])
        normals = normals @ attachment[:3, :3].T
    normals = rotate_y(normals, 192.0)
    normals /= np.maximum(np.linalg.norm(normals, axis=1)[:, None], 1e-8)
    return normals


def convert(name, source, output):
    weapon_name = 'knife' if name == 'arms_knife' else 'ak' if name == 'arms_ak' else 'ak' if name == 'ak47' else 'knife'
    clip_nodes = sampled_clip(source, weapon_name)
    gltf, binary = read_glb(source / ('arms_animated.glb' if name.startswith('arms') else f'{name}.glb'))
    materials = gltf['materials']
    grouped = defaultdict(list)
    for mesh in gltf['meshes']:
        if name == 'ak47' and 'body_legacy' in mesh.get('name', ''):
            continue
        for primitive in mesh['primitives']:
            material_id = primitive.get('material', 0)
            if materials[material_id].get('name') == 'sticker_gaps':
                continue
            attributes = primitive['attributes']
            points = accessor(gltf, binary, attributes['POSITION'])
            normals = accessor(gltf, binary, attributes['NORMAL']).astype(np.float32)
            uv = accessor(gltf, binary, attributes['TEXCOORD_0'])
            indices = accessor(gltf, binary, primitive['indices']).reshape(-1)
            if name.startswith('arms'):
                points, normals = skin_arms(gltf, binary, points, normals, attributes, clip_nodes,
                                           support_lift=name == 'arms_ak')
            points = transform(name, points, clip_nodes)
            normals = transform_normals(name, normals, clip_nodes)
            vertices = np.concatenate((points[indices], uv[indices], normals[indices]), axis=1)
            # Minecraft's entity cutout pipeline consumes quads. Repeat the final
            # triangle corner so its second half is degenerate.
            triangles = vertices.reshape(-1, 3, 8)
            vertices = np.concatenate((triangles, triangles[:, 2:3, :]), axis=1).reshape(-1, 8)
            grouped[material_id].append(vertices)

    root = output / 'assets' / 'cs2viewmodel'
    mesh_dir = root / 'meshes'
    texture_dir = root / 'textures'
    mesh_dir.mkdir(parents=True, exist_ok=True)
    texture_dir.mkdir(parents=True, exist_ok=True)
    parts = []
    for material_id, arrays in grouped.items():
        material = materials[material_id]
        texture_index = material['pbrMetallicRoughness']['baseColorTexture']['index']
        image_index = gltf['textures'][texture_index]['source']
        image_name = gltf['images'][image_index]['uri']
        # Both arm poses use the same source materials. Share their textures
        # instead of packaging and uploading two identical 5 MB images.
        texture_name = f'{"arms_ak" if name.startswith("arms") else name}_{material_id}.png'
        source_texture = source / image_name
        if not (texture_dir / texture_name).exists() or name != 'arms_knife':
            with Image.open(source_texture) as image:
                if max(image.size) <= 4096:
                    shutil.copyfile(source_texture, texture_dir / texture_name)
                else:
                    image = image.convert('RGBA')
                    image.thumbnail((4096, 4096), Image.Resampling.LANCZOS)
                    image.save(texture_dir / texture_name)
        parts.append((texture_name, np.concatenate(arrays).astype('>f4')))

    with (mesh_dir / f'{name}.c2m').open('wb') as file:
        file.write(b'C2M1')
        file.write(struct.pack('>I', len(parts)))
        for texture_name, vertices in parts:
            encoded_name = texture_name.encode('utf-8')
            file.write(struct.pack('>H', len(encoded_name)))
            file.write(encoded_name)
            file.write(struct.pack('>I', len(vertices)))
            file.write(vertices.tobytes())
    print(name, 'parts:', len(parts), 'triangles:', sum(len(v) // 4 for _, v in parts))


def write_mesh(output, name, parts):
    mesh_dir = output / 'assets' / 'cs2viewmodel' / 'meshes'
    mesh_dir.mkdir(parents=True, exist_ok=True)
    with (mesh_dir / f'{name}.c2m').open('wb') as file:
        file.write(b'C2M1')
        file.write(struct.pack('>I', len(parts)))
        for texture_name, vertices in parts:
            data = vertices.astype('>f4')
            encoded_name = texture_name.encode('utf-8')
            file.write(struct.pack('>H', len(encoded_name)))
            file.write(encoded_name)
            file.write(struct.pack('>I', len(data)))
            file.write(data.tobytes())
    print(name, 'parts:', len(parts), 'triangles:', sum(len(v) // 4 for _, v in parts))


def quad_vertices(points, uv, normals, indices):
    vertices = np.concatenate((points[indices], uv[indices], normals[indices]), axis=1)
    triangles = vertices.reshape(-1, 3, 8)
    return np.concatenate((triangles, triangles[:, 2:3, :]), axis=1).reshape(-1, 8)


def transfer_weights(base_gltf, base_binary, donor_points):
    """Map a CS2 glove's bind pose to the weapon-arms rig by nearest vertex."""
    base_attributes = base_gltf['meshes'][0]['primitives'][0]['attributes']
    base_points = accessor(base_gltf, base_binary, base_attributes['POSITION']).astype(np.float32)
    base_joints = accessor(base_gltf, base_binary, base_attributes['JOINTS_0']).astype(np.int32)
    base_weights = accessor(base_gltf, base_binary, base_attributes['WEIGHTS_0']).astype(np.float64)
    squared = np.sum(base_points * base_points, axis=1)
    nearest = np.empty(len(donor_points), dtype=np.int32)
    # Batched matrix products avoid SciPy and keep memory bounded.
    for start in range(0, len(donor_points), 128):
        chunk = donor_points[start:start + 128].astype(np.float32)
        distance = np.sum(chunk * chunk, axis=1)[:, None] + squared[None, :] - 2 * chunk @ base_points.T
        nearest[start:start + len(chunk)] = np.argmin(distance, axis=1)
    return base_joints[nearest], base_weights[nearest]


def copy_texture(source, output, image_name, target_name):
    if target_name in WRITTEN_VARIANT_TEXTURES:
        return
    target = output / 'assets' / 'cs2viewmodel' / 'textures' / target_name
    target.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(source / image_name) as image:
        if max(image.size) <= 4096:
            shutil.copyfile(source / image_name, target)
        else:
            image = image.convert('RGBA')
            image.thumbnail((4096, 4096), Image.Resampling.LANCZOS)
            image.save(target)
    WRITTEN_VARIANT_TEXTURES.add(target_name)


def compose_paintkit_glove(variant, source, output, target_name):
    if target_name in WRITTEN_VARIANT_TEXTURES:
        return
    if variant == 'specialist' and all((source / name).exists() for name in
                                      ('kimono_pattern.png', 'kimono_mask.png', 'kimono_ao.png')):
        import subprocess
        subprocess.run([sys.executable, str(Path(__file__).with_name('bake_crimson_kimono.py'))], check=True)
        WRITTEN_VARIANT_TEXTURES.add(target_name)
        return
    # Sport and Specialist gloves use 1x1 base colors in the model. CS2's
    # runtime paintkit supplies their visible pattern and packed AO texture.
    # Bake those locally exported maps into a regular albedo for Minecraft.
    with Image.open(source / f'{variant}_ao.png') as image:
        ao = np.asarray(image.convert('RGB'), dtype=np.float32)
    shade = np.clip(.38 + ao[:, :, 0:1] / 255.0 * .65, .2, 1.0)
    if variant == 'sporty':
        with Image.open(source / 'sporty_flame.png') as image:
            color = np.asarray(image.convert('RGB'), dtype=np.float32)
        color = color * shade
    else:
        with Image.open(source / 'specialist_trim.png') as image:
            detail = np.asarray(image.convert('RGB'), dtype=np.float32)[:, :, 0:1]
        # The packed CS2 map's green channel separates dark leather from
        # lighter padded panels. The earlier near-uniform tint hid that detail.
        panel = np.clip((ao[:, :, 1:2] / 255.0 - .12) * 1.5, 0, 1)
        dark = np.array([34, 38, 42], dtype=np.float32)
        light = np.array([144, 149, 151], dtype=np.float32)
        color = (dark[None, None, :] * (1 - panel)
                 + light[None, None, :] * panel)
        color *= np.clip(.43 + ao[:, :, 0:1] / 255.0 * .58, .45, 1.0)
        color *= 1 - np.clip((183 - detail) / 70.0, 0, .35)
    target = output / 'assets' / 'cs2viewmodel' / 'textures' / target_name
    target.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.rint(np.clip(color, 0, 255)).astype(np.uint8), 'RGB').save(target)
    WRITTEN_VARIANT_TEXTURES.add(target_name)


def convert_glove_variant(variant, pose, source, skin_source, output):
    base_gltf, base_binary = read_glb(source / 'arms_animated.glb')
    donor_gltf, donor_binary = read_glb(skin_source / f'glove_{variant}.glb')
    clip = sampled_clip(source, 'ak' if pose == 'ak' else 'knife')
    grouped = defaultdict(list)
    # The second CS2 mesh is the first-person version; the first is a lower
    # detail world model. It includes matching exposed skin for each glove.
    for primitive in donor_gltf['meshes'][-1]['primitives']:
        material_id = primitive.get('material', 0)
        attributes = primitive['attributes']
        points = accessor(donor_gltf, donor_binary, attributes['POSITION'])
        normals = accessor(donor_gltf, donor_binary, attributes['NORMAL']).astype(np.float32)
        # Some CS2 right-hand glove UVs are negative to request texture
        # repeat. Minecraft's dynamic texture path clamps them instead, so
        # wrap explicitly or the right glove samples a single edge pixel.
        uv = np.mod(accessor(donor_gltf, donor_binary, attributes['TEXCOORD_0']), 1.0)
        indices = accessor(donor_gltf, donor_binary, primitive['indices']).reshape(-1)
        joints, weights = transfer_weights(base_gltf, base_binary, points)
        points, normals = skin_arms(base_gltf, base_binary, points, normals, attributes, clip,
                                    support_lift=pose == 'ak', joint_indices=joints, weights=weights)
        points = transform('arms_' + pose, points)
        normals = transform_normals('arms_' + pose, normals, clip)
        grouped[material_id].append(quad_vertices(points, uv, normals, indices))
    parts = []
    for material_id, arrays in grouped.items():
        material = donor_gltf['materials'][material_id]
        image_index = donor_gltf['textures'][material['pbrMetallicRoughness']['baseColorTexture']['index']]['source']
        image_name = donor_gltf['images'][image_index]['uri']
        if material.get('name') == 'bare_arm_133':
            texture_name = 'arms_ak_0.png'
        elif variant in ('sporty', 'specialist'):
            texture_name = f'variants/glove_{variant}_0.png'
            compose_paintkit_glove(variant, skin_source, output, texture_name)
        else:
            texture_name = f'variants/glove_{variant}_{material_id}.png'
            copy_texture(skin_source, output, image_name, texture_name)
        parts.append((texture_name, np.concatenate(arrays)))
    write_mesh(output, f'arms_{pose}_glove_{variant}', parts)


def convert_knife_variant(variant, source, skin_source, output):
    gltf, binary = read_glb(skin_source / f'knife_{variant}.glb')
    clip = sampled_clip(source, 'knife')
    parts = []
    for material_id, material in enumerate(gltf['materials']):
        arrays = []
        for mesh in gltf['meshes']:
            for primitive in mesh['primitives']:
                if primitive.get('material', 0) != material_id:
                    continue
                attributes = primitive['attributes']
                points = accessor(gltf, binary, attributes['POSITION'])
                normals = accessor(gltf, binary, attributes['NORMAL']).astype(np.float32)
                uv = accessor(gltf, binary, attributes['TEXCOORD_0'])
                indices = accessor(gltf, binary, primitive['indices']).reshape(-1)
                points = transform('knife_' + variant, points, clip)
                normals = transform_normals('knife_' + variant, normals, clip)
                arrays.append(quad_vertices(points, uv, normals, indices))
        if arrays:
            image_index = gltf['textures'][material['pbrMetallicRoughness']['baseColorTexture']['index']]['source']
            image_name = gltf['images'][image_index]['uri']
            texture_name = f'variants/knife_{variant}_{material_id}.png'
            copy_texture(skin_source, output, image_name, texture_name)
            parts.append((texture_name, np.concatenate(arrays)))
    write_mesh(output, f'knife_{variant}', parts)


def convert_sleeve_variant(variant, source, skin_source, output):
    # CS2 stores these sleeve looks as agent materials, with no independent
    # first-person sleeve model in the VPK. Build a distinct fitted shell from
    # the original CS2 forearm surface and give each option a different cuff.
    gltf, binary = read_glb(source / 'arms_animated.glb')
    primitive = gltf['meshes'][0]['primitives'][0]
    attributes = primitive['attributes']
    base_points = accessor(gltf, binary, attributes['POSITION']).astype(np.float32)
    base_normals = accessor(gltf, binary, attributes['NORMAL']).astype(np.float32)
    indices = accessor(gltf, binary, primitive['indices']).reshape(-1, 3)
    cuffs = {'fbi': (1.145, .019), 'sas': (1.140, .022), 'balkan': (1.150, .016)}
    cuff_y, thickness = cuffs[variant]
    # The right glove ends slightly farther down the wrist. Extend only that
    # sleeve cuff toward the hand, preserving the already aligned left cuff.
    right_arm = base_points[:, 0] < 0
    cuff_at_vertex = cuff_y - np.where(right_arm, .035, 0.0)
    mesh_cutoff = np.where(right_arm, 1.075, 1.105)
    chosen_triangles = indices[np.all(base_points[indices, 1] > mesh_cutoff[indices], axis=1)]
    sides = np.mean(base_points[chosen_triangles, 0], axis=1)
    if not np.any(sides < 0) or not np.any(sides > 0):
        raise ValueError(f'{variant} sleeve must cover both forearms')
    chosen = chosen_triangles.reshape(-1)
    # Hide the distal edge beneath the glove, then raise the cloth and a
    # narrow cuff above the skin along the entire forearm.
    wrist = np.clip((base_points[:, 1] - cuff_at_vertex) / .025, 0, 1)
    wrist = wrist * wrist * (3 - 2 * wrist)
    seam = .007 * np.exp(-((base_points[:, 1] - cuff_at_vertex - .024) / .015) ** 2)
    raised = -.003 + (thickness + .003) * wrist + seam * wrist
    expanded = base_points + base_normals * raised[:, None]
    uv = np.column_stack((np.mod(np.abs(base_points[:, 0]) * 4.0, 1.0),
                          np.mod((base_points[:, 1] - cuff_at_vertex) * 2.0, 1.0))).astype(np.float32)
    parts = []
    for pose in ('ak', 'knife'):
        clip = sampled_clip(source, pose)
        points, normals = skin_arms(gltf, binary, expanded, base_normals, attributes, clip,
                                    support_lift=pose == 'ak')
        points = transform('arms_' + pose, points)
        normals = transform_normals('arms_' + pose, normals, clip)
        parts.append((pose, quad_vertices(points, uv, normals, chosen)))
    # The sleeve atlas is exported directly from the local CS2 install. A
    # small fabric swatch is tiled over the modeled shell, avoiding mismatched
    # UV islands from the separate agent rig.
    swatch_y = {'fbi': 1000, 'sas': 1080, 'balkan': 1140}[variant]
    with Image.open(skin_source / f'sleeve_{variant}.png') as image:
        swatch = image.convert('RGB').crop((256, swatch_y, 768, swatch_y + 512))
        target = output / 'assets' / 'cs2viewmodel' / 'textures' / 'variants' / f'sleeve_{variant}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        swatch.save(target)
    for pose, vertices in parts:
        write_mesh(output, f'sleeve_{pose}_{variant}', [(f'variants/sleeve_{variant}.png', vertices)])


if __name__ == '__main__':
    source_dir = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    for stale in ('meshes/arms.c2m', 'textures/arms_0.png', 'textures/arms_1.png',
                  'textures/ak47_0.png', 'textures/arms_knife_0.png',
                  'textures/arms_knife_1.png'):
        (output_dir / 'assets' / 'cs2viewmodel' / stale).unlink(missing_ok=True)
    for model_name in ('arms_ak', 'arms_knife', 'ak47', 'knife'):
        convert(model_name, source_dir, output_dir)
    skin_source = source_dir.parent / 'skins' / 'source'
    for variant in ('fullfinger', 'hardknuckle', 'specialist', 'sporty'):
        for pose in ('ak', 'knife'):
            convert_glove_variant(variant, pose, source_dir, skin_source, output_dir)
    for variant in ('karambit', 'bayonet', 'butterfly'):
        convert_knife_variant(variant, source_dir, skin_source, output_dir)
    for variant in ('fbi', 'sas', 'balkan'):
        convert_sleeve_variant(variant, source_dir, skin_source, output_dir)
