"""Box models as Minecraft's ModelRenderer draws them, written as JSON for the game to load, with their textures
painted from code and pictures drawn the way the game draws them.

A model is a tree of parts. Each part turns about its pivot (Z, then Y, then X, as ModelRenderer does) and holds
boxes, laid out in the texture the way ModelBox lays them out: the top and bottom faces above, the sides in a row
below them. Coordinates are in model units (a pixel for entities), y down; an entity's feet are at y = 24.

The game reads the JSON with com.warofthegods.client.model.JsonModel, so a part named here is a part it can animate.
"""

import json
import math
import random

import numpy as np
from PIL import Image

TEAM = "team"


class Box:
    def __init__(self, x, y, z, w, h, d, paint, inflate=0.0, mirror=False):
        self.x, self.y, self.z = x, y, z
        self.w, self.h, self.d = w, h, d
        self.paint = paint
        self.inflate = inflate
        self.mirror = mirror
        self.u = self.v = None

    @property
    def tex_w(self):
        return 2 * (self.d + self.w)

    @property
    def tex_h(self):
        return self.d + self.h


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rotation=(0, 0, 0), glow=False):
        self.name = name
        self.pivot = tuple(float(c) for c in pivot)
        self.rotation = tuple(float(c) for c in rotation)
        self.glow = glow
        self.boxes = []
        self.children = []

    def box(self, x, y, z, w, h, d, paint, inflate=0.0, mirror=False):
        self.boxes.append(Box(x, y, z, w, h, d, paint, inflate, mirror))
        return self

    def child(self, name, pivot=(0, 0, 0), rotation=(0, 0, 0), glow=False):
        part = Part(name, pivot, rotation, glow)
        self.children.append(part)
        return part

    def walk(self):
        yield self
        for child in self.children:
            yield from child.walk()


class Model:
    def __init__(self, name, tex_w, tex_h, seed=1):
        self.name = name
        self.tex_w, self.tex_h = tex_w, tex_h
        self.roots = []
        self.seed = seed

    def part(self, name, pivot=(0, 0, 0), rotation=(0, 0, 0), glow=False):
        part = Part(name, pivot, rotation, glow)
        self.roots.append(part)
        return part

    def parts(self):
        for root in self.roots:
            yield from root.walk()

    def boxes(self):
        for part in self.parts():
            yield from part.boxes

    # ------------------------------------------------------------ texture

    def pack(self):
        """Lays every box out in the texture, tallest first along shelves."""
        boxes = sorted(self.boxes(), key=lambda b: (-b.tex_h, -b.tex_w))
        x = y = shelf = 0
        for box in boxes:
            if x + box.tex_w > self.tex_w:
                x, y, shelf = 0, y + shelf, 0
            if y + box.tex_h > self.tex_h:
                raise SystemExit("%s: the texture is too small for its boxes" % self.name)
            box.u, box.v = x, y
            x += box.tex_w
            shelf = max(shelf, box.tex_h)

    def paint(self):
        """The texture and its team mask: white where the team's color goes, clear elsewhere."""
        self.pack()
        image = Image.new("RGBA", (self.tex_w, self.tex_h), (0, 0, 0, 0))
        mask = Image.new("RGBA", (self.tex_w, self.tex_h), (0, 0, 0, 0))
        rng = random.Random(self.seed)
        for box in self.boxes():
            for face, fu, fv, fw, fh in faces(box):
                for py in range(fh):
                    for px in range(fw):
                        color = box.paint(face, px, py, fw, fh)
                        if color is None:
                            continue
                        n = rng.randint(-5, 5)
                        if isinstance(color, tuple) and len(color) == 2 and color[0] == TEAM:
                            level = color[1]
                            image.putpixel((fu + px, fv + py), (level, level, level, 255))
                            mask.putpixel((fu + px, fv + py), (clamp(level + n), clamp(level + n), clamp(level + n),
                                                               255))
                            continue
                        image.putpixel((fu + px, fv + py), tuple(clamp(c + n) for c in color[:3]) + (255,))
        return image, mask

    def has_team(self, mask):
        return mask.getbbox() is not None

    # ------------------------------------------------------------ JSON

    def to_json(self):
        def part_json(part, parent):
            return {
                "name": part.name,
                "parent": parent,
                "pivot": list(part.pivot),
                "rotation": [round(a, 5) for a in part.rotation],
                "glow": part.glow,
                "boxes": [{"uv": [b.u, b.v], "from": [b.x, b.y, b.z], "size": [b.w, b.h, b.d],
                           "inflate": b.inflate, "mirror": b.mirror} for b in part.boxes],
            }

        parts = []

        def walk(part, parent):
            parts.append(part_json(part, parent))
            for child in part.children:
                walk(child, part.name)

        for root in self.roots:
            walk(root, None)
        return json.dumps({"texture": [self.tex_w, self.tex_h], "parts": parts}, indent=1)


def clamp(c):
    return max(0, min(255, int(c)))


def faces(box):
    """Each face of a box in the texture as (name, u, v, width, height), named as on a skin: right is -x."""
    u, v, w, h, d = box.u, box.v, box.w, box.h, box.d
    return [
        ("top", u + d, v, w, d),
        ("bottom", u + d + w, v, w, d),
        ("right", u, v + d, d, h),
        ("front", u + d, v + d, w, h),
        ("left", u + d + w, v + d, d, h),
        ("back", u + d + w + d, v + d, w, h),
    ]


# ---------------------------------------------------------------- drawing


def rotation_matrix(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    # glRotate Z, then Y, then X: a vertex is turned by X first.
    return mz @ my @ mx


def box_quads(box):
    """The six faces of a box as ModelBox makes them: four corners and the texture rectangle u1, v1, u2, v2."""
    s = box.inflate
    x1, y1, z1 = box.x - s, box.y - s, box.z - s
    x2, y2, z2 = box.x + box.w + s, box.y + box.h + s, box.z + box.d + s
    if box.mirror:
        x1, x2 = x2, x1
    v0, v1, v2, v3 = (x1, y1, z1), (x2, y1, z1), (x2, y2, z1), (x1, y2, z1)
    v4, v5, v6, v7 = (x1, y1, z2), (x2, y1, z2), (x2, y2, z2), (x1, y2, z2)
    u, v, w, h, d = box.u, box.v, box.w, box.h, box.d
    quads = [
        ([v5, v1, v2, v6], (u + d + w, v + d, u + d + w + d, v + d + h)),
        ([v0, v4, v7, v3], (u, v + d, u + d, v + d + h)),
        ([v5, v4, v0, v1], (u + d, v, u + d + w, v + d)),
        ([v2, v3, v7, v6], (u + d + w, v + d, u + d + w + w, v)),
        ([v1, v0, v3, v2], (u + d, v + d, u + d + w, v + d + h)),
        ([v4, v5, v6, v7], (u + d + w + d, v + d, u + d + w + d + w, v + d + h)),
    ]
    out = []
    for corners, (a, b, c, e) in quads:
        uvs = [(c, b), (a, b), (a, e), (c, e)]
        if box.mirror:
            corners = corners[::-1]
            uvs = uvs[::-1]
        out.append((corners, uvs))
    return out


def world_quads(model, pose=None):
    """Every face of the model placed in model space, as (corners, uvs, glow)."""
    pose = pose or {}
    out = []

    def walk(part, matrix, offset):
        extra = pose.get(part.name, (0, 0, 0))
        rot = rotation_matrix(*(r + e for r, e in zip(part.rotation, extra)))
        m = matrix @ rot
        o = offset + matrix @ np.array(part.pivot)
        for box in part.boxes:
            for corners, uvs in box_quads(box):
                placed = [o + m @ np.array(c, dtype=float) for c in corners]
                out.append((placed, uvs, part.glow))
        for child in part.children:
            walk(child, m, o)

    for root in model.roots:
        walk(root, np.identity(3), np.zeros(3))
    return out


def bounds(model, pose=None):
    points = np.array([c for corners, _, _ in world_quads(model, pose) for c in corners])
    return points.min(axis=0), points.max(axis=0)


def render(model, texture, size=512, yaw=30.0, pitch=15.0, pose=None, mask=None, team=None, background=None,
           roll=0.0, margin=0.08, up=None):
    """Draws the model as the game would, lit from above in front, seen from its front turned by yaw degrees and
    looking down by pitch. With a mask and a team color, the team's color is drawn over as the game tints it."""
    tex = np.asarray(texture.convert("RGBA"), dtype=float) / 255
    if mask is not None and team is not None:
        m = np.asarray(mask.convert("RGBA"), dtype=float) / 255
        tint = np.array(team, dtype=float) / 255
        covered = m[:, :, 3:4] > 0.1
        tex = np.where(covered, np.concatenate([m[:, :, :3] * tint, m[:, :, 3:4]], axis=2), tex)
    th, tw = tex.shape[:2]
    quads = world_quads(model, pose)
    # Model space is y down and faces -z; turn it so up is up and its front faces the viewer.
    flip = np.diag([-1.0, -1.0, 1.0]) if up is None else up
    view = rotation_matrix(math.radians(pitch), 0, 0) @ rotation_matrix(0, math.radians(yaw), 0) \
        @ rotation_matrix(0, 0, math.radians(roll)) @ flip
    # Camera looks down -z (toward the model's front at +... ): put the model's front toward the camera.
    face_camera = rotation_matrix(0, math.pi, 0)
    view = face_camera @ view
    light = np.array([0.3, 0.8, 0.55])
    light /= np.linalg.norm(light)
    placed = [([view @ c for c in corners], uvs, glow) for corners, uvs, glow in quads]
    pts = np.array([c for corners, _, _ in placed for c in corners])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    scale = size * (1 - 2 * margin) / span
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    image = np.zeros((size, size, 4))
    if background is not None:
        image[:, :] = np.array(background + (255,), dtype=float) / 255
    depth = np.full((size, size), -np.inf)
    for corners, uvs, glow in placed:
        p = np.array([[(c[0] - cx) * scale + size / 2, size / 2 - (c[1] - cy) * scale, c[2]] for c in corners])
        normal = np.cross(np.array(corners[1]) - corners[0], np.array(corners[2]) - corners[0])
        length = np.linalg.norm(normal)
        if length < 1e-9:
            continue
        normal /= length
        # Faces are wound so their normal points out; skip those facing away (the game culls them too).
        if normal[2] < 0:
            normal = -normal
        brightness = 1.0 if glow else 0.45 + 0.55 * max(0.0, float(normal @ light))
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster(image, depth, p[list(tri)], [uvs[i] for i in tri], tex, tw, th, brightness)
    return Image.fromarray((np.clip(image, 0, 1) * 255).astype(np.uint8), "RGBA")


def raster(image, depth, p, uvs, tex, tw, th, brightness):
    size = image.shape[0]
    xs, ys = p[:, 0], p[:, 1]
    x0, x1 = max(0, int(math.floor(xs.min()))), min(size - 1, int(math.ceil(xs.max())))
    y0, y1 = max(0, int(math.floor(ys.min()))), min(size - 1, int(math.ceil(ys.max())))
    if x0 > x1 or y0 > y1:
        return
    (ax, ay), (bx, by), (cx, cy) = p[0, :2], p[1, :2], p[2, :2]
    det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(det) < 1e-9:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    l0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / det
    l1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / det
    l2 = 1 - l0 - l1
    eps = -1e-6
    inside = (l0 >= eps) & (l1 >= eps) & (l2 >= eps)
    if not inside.any():
        return
    z = l0 * p[0, 2] + l1 * p[1, 2] + l2 * p[2, 2]
    u = l0 * uvs[0][0] + l1 * uvs[1][0] + l2 * uvs[2][0]
    v = l0 * uvs[0][1] + l1 * uvs[1][1] + l2 * uvs[2][1]
    ui = np.clip(np.floor(u - 1e-4 * np.sign(u - (uvs[0][0] + uvs[1][0] + uvs[2][0]) / 3)), 0, tw - 1).astype(int)
    vi = np.clip(np.floor(v - 1e-4 * np.sign(v - (uvs[0][1] + uvs[1][1] + uvs[2][1]) / 3)), 0, th - 1).astype(int)
    texel = tex[vi, ui]
    region = depth[y0:y1 + 1, x0:x1 + 1]
    draw = inside & (texel[:, :, 3] > 0.1) & (z > region)
    if not draw.any():
        return
    color = texel[:, :, :3] * brightness
    target = image[y0:y1 + 1, x0:x1 + 1]
    target[draw, :3] = color[draw]
    target[draw, 3] = 1
    region[draw] = z[draw]


def shade(color, factor):
    return tuple(clamp(c * factor) for c in color)


def mix(a, b, t):
    return tuple(clamp(x + (y - x) * t) for x, y in zip(a, b))
