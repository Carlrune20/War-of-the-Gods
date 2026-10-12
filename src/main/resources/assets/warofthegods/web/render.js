// The 3D map: block data from the server, meshing it into faces, and drawing it with WebGL 2 through an
// orthographic RTS camera, the way the beta map editor does.
"use strict";

(function () {
    const B = window.WotgBlocks;
    // What lies below a column's base: solid ground that is never drawn.
    const FILLER = 1 << 4;

    // ------------------------------------------------------------------ small matrix helpers (column-major)

    const M = {
        mul(a, b) {
            const o = new Float32Array(16);
            for (let c = 0; c < 4; c++) {
                for (let r = 0; r < 4; r++) {
                    let s = 0;
                    for (let k = 0; k < 4; k++) {
                        s += a[k * 4 + r] * b[c * 4 + k];
                    }
                    o[c * 4 + r] = s;
                }
            }
            return o;
        },
        ortho(l, r, b, t, n, f) {
            const o = new Float32Array(16);
            o[0] = 2 / (r - l);
            o[5] = 2 / (t - b);
            o[10] = -2 / (f - n);
            o[12] = -(r + l) / (r - l);
            o[13] = -(t + b) / (t - b);
            o[14] = -(f + n) / (f - n);
            o[15] = 1;
            return o;
        },
        lookAt(eye, target, up) {
            const f = norm(sub(target, eye));
            const s = norm(cross(f, up));
            const u = cross(s, f);
            const o = new Float32Array(16);
            o[0] = s[0]; o[4] = s[1]; o[8] = s[2];
            o[1] = u[0]; o[5] = u[1]; o[9] = u[2];
            o[2] = -f[0]; o[6] = -f[1]; o[10] = -f[2];
            o[12] = -dot(s, eye);
            o[13] = -dot(u, eye);
            o[14] = dot(f, eye);
            o[15] = 1;
            return o;
        },
        invert(m) {
            const inv = new Float32Array(16);
            inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
            inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
            inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
            inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
            inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
            inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
            inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
            inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
            inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
            inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
            inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
            inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
            inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
            inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
            inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
            inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
            const det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
            for (let i = 0; i < 16; i++) {
                inv[i] /= det;
            }
            return inv;
        },
        apply(m, v) {
            return [
                m[0] * v[0] + m[4] * v[1] + m[8] * v[2] + m[12] * v[3],
                m[1] * v[0] + m[5] * v[1] + m[9] * v[2] + m[13] * v[3],
                m[2] * v[0] + m[6] * v[1] + m[10] * v[2] + m[14] * v[3],
                m[3] * v[0] + m[7] * v[1] + m[11] * v[2] + m[15] * v[3],
            ];
        },
    };

    function sub(a, b) {
        return [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
    }

    function cross(a, b) {
        return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
    }

    function dot(a, b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    function norm(a) {
        const l = Math.hypot(a[0], a[1], a[2]) || 1;
        return [a[0] / l, a[1] / l, a[2] / l];
    }

    // ------------------------------------------------------------------ block data

    // One chunk's visible blocks: per column a base height and a run of blocks up from it.
    class ChunkData {
        constructor(cx, cz, bytes) {
            this.cx = cx;
            this.cz = cz;
            this.base = new Uint8Array(256);
            this.count = new Uint8Array(256);
            this.offset = new Uint32Array(257);
            let total = 0;
            let minY = 255;
            let maxY = 0;
            for (let i = 0; i < 256; i++) {
                this.base[i] = bytes[i * 2];
                this.count[i] = bytes[i * 2 + 1];
                this.offset[i] = total;
                total += this.count[i];
                if (this.count[i]) {
                    minY = Math.min(minY, this.base[i]);
                    maxY = Math.max(maxY, this.base[i] + this.count[i]);
                }
            }
            this.offset[256] = total;
            this.blocks = new Uint16Array(total);
            for (let i = 0; i < total; i++) {
                this.blocks[i] = bytes[512 + i * 2] << 8 | bytes[512 + i * 2 + 1];
            }
            this.minY = minY;
            this.maxY = Math.max(maxY, minY + 1);
        }

        // Block id << 4 | metadata at a column (x + z * 16) and height.
        at(column, y) {
            const base = this.base[column];
            if (y < base) {
                return FILLER;
            }
            const k = y - base;
            return k < this.count[column] ? this.blocks[this.offset[column] + k] : 0;
        }
    }

    class MapData {
        constructor() {
            this.chunks = new Map();
        }

        static key(cx, cz) {
            return cx + "," + cz;
        }

        chunk(cx, cz) {
            return this.chunks.get(MapData.key(cx, cz));
        }

        // Block id << 4 | metadata anywhere; ground nobody has sent counts as solid.
        block(x, y, z) {
            if (y < 0) {
                return FILLER;
            }
            if (y > 255) {
                return 0;
            }
            const c = this.chunk(x >> 4, z >> 4);
            if (!c) {
                return FILLER;
            }
            return c.at((x & 15) + (z & 15) * 16, y);
        }

        // Reads a tile blob (see MapTiles.java); returns the chunks it held.
        static parseTile(tx, tz, buffer) {
            const view = new DataView(buffer);
            const bytes = new Uint8Array(buffer);
            if (String.fromCharCode(bytes[0], bytes[1], bytes[2], bytes[3]) !== "WOTG" || bytes[4] !== 1) {
                throw new Error("Unknown map data format");
            }
            let pos = 5;
            const result = [];
            for (let i = 0; i < 64; i++) {
                const length = view.getUint32(pos);
                pos += 4;
                if (length) {
                    const cx = tx * 8 + (i & 7);
                    const cz = tz * 8 + (i >> 3);
                    result.push(new ChunkData(cx, cz, bytes.subarray(pos, pos + length)));
                    pos += length;
                }
            }
            return result;
        }
    }

    // ------------------------------------------------------------------ meshing

    const DIRS = [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];

    // One 8-byte instance per visible face: x, y, z, face, block, data, unused, part flags (1 = second part,
    // 2 = liquid drawn full height). Opaque faces first, then translucent ones.
    function meshChunk(map, c) {
        const op = [];
        const tr = [];
        const around = [];
        for (let f = 0; f < 6; f++) {
            around.push(map.chunk(c.cx + DIRS[f][0], c.cz + DIRS[f][2]));
        }
        const neighbour = (x, y, z) => {
            if (x >= 0 && x < 16 && z >= 0 && z < 16) {
                return y < 0 ? FILLER : y > 255 ? 0 : c.at(x + z * 16, y);
            }
            return map.block(c.cx * 16 + x, y, c.cz * 16 + z);
        };
        for (let column = 0; column < 256; column++) {
            const x = column & 15;
            const z = column >> 4;
            const base = c.base[column];
            const count = c.count[column];
            for (let k = 0; k < count; k++) {
                const y = base + k;
                const value = c.blocks[c.offset[column] + k];
                const id = B.index(value >> 4);
                if (B.isInvisible(id)) {
                    continue;
                }
                const d = value & 15;
                const full = B.isFull(id);
                const fam = B.family(id);
                const trans = B.isTranslucent(id);
                const ids = [];
                let enclosed = true;
                for (let f = 0; f < 6; f++) {
                    const n = B.index(neighbour(x + DIRS[f][0], y + DIRS[f][1], z + DIRS[f][2]) >> 4);
                    ids.push(n);
                    enclosed = enclosed && B.isFull(n);
                }
                if (full && enclosed) {
                    continue;
                }
                const parts = B.parts(id, d);
                for (let part = 0; part < parts; part++) {
                    let edges = B.edges(id, d, part);
                    let flags = part;
                    if ((fam === 1 || fam === 2) && B.family(ids[2]) === fam) {
                        // Liquid under liquid fills the whole block.
                        edges |= 4;
                        flags |= 2;
                    }
                    for (let f = 0; f < 6; f++) {
                        const n = ids[f];
                        const nfam = B.family(n);
                        if (edges & (1 << f)) {
                            if (B.isFull(n) || (fam !== 0 && nfam === fam)) {
                                continue;
                            }
                        }
                        if ((fam === 1 || fam === 2) && nfam === fam) {
                            continue;
                        }
                        (trans ? tr : op).push(x, y, z, f, id, d, 0xF0, flags);
                    }
                }
            }
        }
        const inst = new Uint8Array(op.length + tr.length);
        inst.set(op);
        inst.set(tr, op.length);
        return {inst, nOp: op.length / 8, nTr: tr.length / 8};
    }

    // ------------------------------------------------------------------ shaders

    const HEADER = "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n";

    const BLOCK_VS = `
layout(location = 0) in uvec4 a_pos;
layout(location = 1) in uvec4 a_blk;
uniform mat4 u_mvp;
uniform vec3 u_origin;
uniform sampler2D u_tab;
out vec3 v_color;
out vec3 v_local;
out vec2 v_uv;
flat out ivec4 v_info;
flat out vec3 v_top;
flat out float v_alpha;
void main() {
    int id = int(a_blk.x);
    int dat = int(a_blk.y);
    int face = int(a_pos.w);
    int part = int(a_blk.w & 1u);
    bool fullh = (a_blk.w & 2u) != 0u;
    vec3 mn = texelFetch(u_tab, ivec2(id, 32 + part * 32 + dat), 0).rgb * (255.0 / 16.0);
    vec3 mx = texelFetch(u_tab, ivec2(id, 48 + part * 32 + dat), 0).rgb * (255.0 / 16.0);
    float a = float(gl_VertexID & 1);
    float b = float(gl_VertexID >> 1);
    vec3 c;
    if (face == 0) c = vec3(1.0, a, b);
    else if (face == 1) c = vec3(0.0, a, b);
    else if (face == 2) c = vec3(a, 1.0, b);
    else if (face == 3) c = vec3(a, 0.0, b);
    else if (face == 4) c = vec3(a, b, 1.0);
    else c = vec3(a, b, 0.0);
    vec3 local = mix(mn, mx, c);
    if (fullh) local.y = c.y;
    vec3 wp = u_origin + vec3(a_pos.xyz) + local;
    gl_Position = u_mvp * vec4(wp, 1.0);
    vec4 side = texelFetch(u_tab, ivec2(id, dat), 0);
    vec4 top = texelFetch(u_tab, ivec2(id, 16 + dat), 0);
    vec3 base = (face == 2 || face == 3) ? top.rgb : side.rgb;
    float shade = face == 2 ? 1.0 : (face == 3 ? 0.5 : ((face == 4 || face == 5) ? 0.8 : 0.66));
    v_color = base * shade;
    v_top = top.rgb * shade;
    v_alpha = side.a;
    v_local = local;
    if (face < 2) v_uv = local.zy; else if (face < 4) v_uv = local.xz; else v_uv = local.xy;
    v_info = ivec4(id, face, int(a_pos.x) + int(u_origin.x) * 7 + int(a_pos.y) * 131, int(a_pos.z) + int(u_origin.z) * 13);
}`;

    const BLOCK_FS = `
in vec3 v_color;
in vec3 v_local;
in vec2 v_uv;
flat in ivec4 v_info;
flat in vec3 v_top;
flat in float v_alpha;
uniform float u_detail;
out vec4 o_color;
float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}
void main() {
    vec2 t = floor(v_uv * 16.0);
    float n = hash(vec3(t.x + float(v_info.z) * 0.37, t.y + float(v_info.w) * 0.61, float(v_info.x) * 1.7 + float(v_info.y)));
    vec3 col = v_color;
    int id = v_info.x;
    int face = v_info.y;
    // Grass and mycelium sides get a strip of the top colour.
    if ((id == 2 || id == 110) && face != 2 && face != 3 && v_local.y > 0.8 + 0.07 * n) col = v_top;
    // Speckles and dark block edges, faded out when blocks get too small on screen to show them cleanly.
    float amp = ((id == 9 || id == 8 || id == 20 || id == 79 || id == 95) ? 0.08 : 0.22) * u_detail;
    col *= (1.0 - amp * 0.5) + amp * n;
    vec2 f = fract(v_uv);
    float e = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y));
    if (e < 0.035) col *= mix(1.0, 0.82, u_detail);
    o_color = vec4(col, v_alpha);
}`;

    const LINE_VS = `
layout(location = 0) in vec3 a_p;
layout(location = 1) in vec4 a_c;
uniform mat4 u_mvp;
out vec4 v_c;
void main() { gl_Position = u_mvp * vec4(a_p, 1.0); gl_Position.z -= 0.0004 * gl_Position.w; v_c = a_c; }`;

    const LINE_FS = `
in vec4 v_c;
uniform float u_alpha;
out vec4 o_color;
void main() { o_color = vec4(v_c.rgb, v_c.a * u_alpha); }`;

    const BOX_VS = `
layout(location = 0) in vec3 a_p;
layout(location = 1) in vec3 a_n;
uniform mat4 u_mvp;
uniform vec3 u_min;
uniform vec3 u_size;
uniform vec4 u_color;
out vec4 v_c;
void main() {
    gl_Position = u_mvp * vec4(u_min + a_p * u_size, 1.0);
    float s = a_n.y > 0.5 ? 1.0 : (a_n.y < -0.5 ? 0.5 : (abs(a_n.z) > 0.5 ? 0.8 : 0.66));
    v_c = vec4(u_color.rgb * s, u_color.a);
}`;

    const BOX_FS = `
in vec4 v_c;
out vec4 o_color;
void main() { o_color = v_c; }`;

    function compile(gl, vs, fs) {
        const program = gl.createProgram();
        for (const [type, source] of [[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]]) {
            const shader = gl.createShader(type);
            gl.shaderSource(shader, HEADER + source);
            gl.compileShader(shader);
            if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
                throw new Error(gl.getShaderInfoLog(shader));
            }
            gl.attachShader(program, shader);
        }
        gl.linkProgram(program);
        if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
            throw new Error(gl.getProgramInfoLog(program));
        }
        const uniforms = {};
        const count = gl.getProgramParameter(program, gl.ACTIVE_UNIFORMS);
        for (let i = 0; i < count; i++) {
            const name = gl.getActiveUniform(program, i).name;
            uniforms[name] = gl.getUniformLocation(program, name);
        }
        return {program, uniforms};
    }

    // A unit cube as 36 vertices of position and normal.
    function cubeVertices() {
        const v = [];
        const faces = [
            [[1, 0, 0], [[1, 0, 0], [1, 1, 0], [1, 1, 1], [1, 0, 1]]],
            [[-1, 0, 0], [[0, 0, 1], [0, 1, 1], [0, 1, 0], [0, 0, 0]]],
            [[0, 1, 0], [[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]]],
            [[0, -1, 0], [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]]],
            [[0, 0, 1], [[1, 0, 1], [1, 1, 1], [0, 1, 1], [0, 0, 1]]],
            [[0, 0, -1], [[0, 0, 0], [0, 1, 0], [1, 1, 0], [1, 0, 0]]],
        ];
        for (const [n, q] of faces) {
            for (const i of [0, 1, 2, 0, 2, 3]) {
                v.push(...q[i], ...n);
            }
        }
        return new Float32Array(v);
    }

    // ------------------------------------------------------------------ the view

    const MESH_BUDGET_MS = 8;

    class MapView {
        constructor(canvas) {
            const gl = canvas.getContext("webgl2", {antialias: true});
            if (!gl) {
                throw new Error("This browser cannot show the 3D map: it needs WebGL 2.");
            }
            this.canvas = canvas;
            this.gl = gl;
            this.map = new MapData();
            this.meshes = new Map(); // chunk key -> {buffer, nOp, nTr, cx, cz, minY, maxY}
            this.queue = new Set(); // chunk keys waiting to be meshed
            this.game = null;
            this.cam = {target: [0, 64, 0], yaw: Math.PI / 4, yawGoal: Math.PI / 4, pitch: 0.62, zoom: 60};
            this.vp = null;
            this.dirty = true;

            this.blockProgram = compile(gl, BLOCK_VS, BLOCK_FS);
            this.lineProgram = compile(gl, LINE_VS, LINE_FS);
            this.boxProgram = compile(gl, BOX_VS, BOX_FS);
            this.blockVao = gl.createVertexArray();
            this.lineVao = gl.createVertexArray();
            this.lineBuffer = gl.createBuffer();
            this.boxVao = gl.createVertexArray();
            gl.bindVertexArray(this.boxVao);
            const cube = gl.createBuffer();
            gl.bindBuffer(gl.ARRAY_BUFFER, cube);
            gl.bufferData(gl.ARRAY_BUFFER, cubeVertices(), gl.STATIC_DRAW);
            gl.enableVertexAttribArray(0);
            gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 24, 0);
            gl.enableVertexAttribArray(1);
            gl.vertexAttribPointer(1, 3, gl.FLOAT, false, 24, 12);
            gl.bindVertexArray(null);

            this.table = gl.createTexture();
            gl.bindTexture(gl.TEXTURE_2D, this.table);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
            gl.pixelStorei(gl.UNPACK_ALIGNMENT, 1);
            gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, 256, 96, 0, gl.RGBA, gl.UNSIGNED_BYTE, B.table());
            gl.bindTexture(gl.TEXTURE_2D, null);
        }

        // ---------------------------------------------------------------- data

        setGame(game) {
            for (const mesh of this.meshes.values()) {
                this.gl.deleteBuffer(mesh.buffer);
            }
            this.meshes.clear();
            this.queue.clear();
            this.map = new MapData();
            this.game = game;
            this.dirty = true;
        }

        setTile(tx, tz, buffer) {
            const chunks = MapData.parseTile(tx, tz, buffer);
            for (const c of chunks) {
                this.map.chunks.set(MapData.key(c.cx, c.cz), c);
                // Neighbours may now hide or show faces along the shared edge.
                for (const [dx, dz] of [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1]]) {
                    const key = MapData.key(c.cx + dx, c.cz + dz);
                    if (this.map.chunks.has(key)) {
                        this.queue.add(key);
                    }
                }
            }
        }

        // Meshes queued chunks for a few milliseconds; returns true if anything changed.
        meshSome() {
            if (!this.queue.size) {
                return false;
            }
            const gl = this.gl;
            const deadline = performance.now() + MESH_BUDGET_MS;
            for (const key of this.queue) {
                this.queue.delete(key);
                const c = this.map.chunks.get(key);
                const old = this.meshes.get(key);
                if (old) {
                    gl.deleteBuffer(old.buffer);
                    this.meshes.delete(key);
                }
                if (c) {
                    const m = meshChunk(this.map, c);
                    if (m.nOp + m.nTr > 0) {
                        const buffer = gl.createBuffer();
                        gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
                        gl.bufferData(gl.ARRAY_BUFFER, m.inst, gl.STATIC_DRAW);
                        this.meshes.set(key, {buffer, nOp: m.nOp, nTr: m.nTr, cx: c.cx, cz: c.cz, minY: c.minY, maxY: c.maxY});
                    }
                }
                if (performance.now() > deadline) {
                    break;
                }
            }
            gl.bindBuffer(gl.ARRAY_BUFFER, null);
            this.dirty = true;
            return true;
        }

        get pendingChunks() {
            return this.queue.size;
        }

        // ---------------------------------------------------------------- camera

        eyeDir() {
            const {yaw, pitch} = this.cam;
            return [Math.cos(pitch) * Math.sin(yaw), Math.sin(pitch), Math.cos(pitch) * Math.cos(yaw)];
        }

        size() {
            return [this.canvas.clientWidth || 1, this.canvas.clientHeight || 1];
        }

        viewProj() {
            const [w, h] = this.size();
            const aspect = w / h;
            const t = this.cam.target;
            const d = this.eyeDir();
            const eye = [t[0] + d[0] * 800, t[1] + d[1] * 800, t[2] + d[2] * 800];
            const z = this.cam.zoom;
            return M.mul(M.ortho(-z * aspect, z * aspect, -z, z, 1, 2000), M.lookAt(eye, t, [0, 1, 0]));
        }

        right() {
            return [Math.cos(this.cam.yaw), 0, -Math.sin(this.cam.yaw)];
        }

        forwardGround() {
            return [-Math.sin(this.cam.yaw), 0, -Math.cos(this.cam.yaw)];
        }

        // Pans by a drag of dx, dy screen pixels, so the ground follows the pointer.
        pan(dx, dy) {
            const [, h] = this.size();
            const upp = 2 * this.cam.zoom / h;
            const r = this.right();
            const f = this.forwardGround();
            const k = upp / Math.max(Math.sin(this.cam.pitch), 0.2);
            const t = this.cam.target;
            t[0] += -r[0] * dx * upp + f[0] * dy * k;
            t[2] += -r[2] * dx * upp + f[2] * dy * k;
            this.clampTarget();
            this.dirty = true;
        }

        // Moves the camera along the ground by a number of blocks to the right and forward on screen.
        move(right, forward) {
            const r = this.right();
            const f = this.forwardGround();
            this.cam.target[0] += r[0] * right + f[0] * forward;
            this.cam.target[2] += r[2] * right + f[2] * forward;
            this.clampTarget();
            this.dirty = true;
        }

        // Tilts and turns by a drag of dx, dy pixels.
        orbit(dx, dy) {
            this.cam.yaw -= dx * 0.008;
            this.cam.yawGoal = this.cam.yaw;
            this.cam.pitch = Math.min(1.55, Math.max(0.25, this.cam.pitch + dy * 0.006));
            this.dirty = true;
        }

        // Turns the view a quarter round; dir is 1 or -1.
        turn(dir) {
            const step = Math.PI / 2;
            this.cam.yawGoal = Math.round((this.cam.yawGoal - Math.PI / 4) / step) * step + Math.PI / 4 + dir * step;
            this.dirty = true;
        }

        // Looks straight down with north up, like a paper map, or back to the angled view.
        setTopView(on) {
            if (on && !this.isTopView) {
                this.savedYaw = this.cam.yawGoal;
                this.cam.yaw = this.cam.yawGoal = 0;
            } else if (!on && this.isTopView) {
                this.cam.yaw = this.cam.yawGoal = this.savedYaw ?? Math.PI / 4;
            }
            this.cam.pitch = on ? 1.55 : 0.62;
            this.dirty = true;
        }

        get isTopView() {
            return this.cam.pitch > 1.5;
        }

        zoomAt(px, py, factor) {
            const before = this.groundAt(px, py, this.cam.target[1]);
            this.cam.zoom = Math.min(900, Math.max(4, this.cam.zoom / factor));
            const after = this.groundAt(px, py, this.cam.target[1]);
            if (before && after) {
                this.cam.target[0] += before[0] - after[0];
                this.cam.target[2] += before[2] - after[2];
                this.clampTarget();
            }
            this.dirty = true;
        }

        focus(x, y, z) {
            this.cam.target = [x, y, z];
            this.clampTarget();
            this.dirty = true;
        }

        // Shows the whole map and its margin.
        fitMap() {
            const g = this.game;
            if (!g) {
                return;
            }
            const [w, h] = this.size();
            const width = g.maxX - g.minX + 1 + 2 * g.margin;
            this.cam.target = [(g.minX + g.maxX + 1) / 2, g.seaLevel, (g.minZ + g.maxZ + 1) / 2];
            // The map is a diamond on screen at the usual angle; this fits its widest extent.
            const extent = width * 0.75;
            this.cam.zoom = Math.min(900, Math.max(4, Math.max(extent / (w / h), extent * Math.sin(this.cam.pitch))));
            this.dirty = true;
        }

        clampTarget() {
            const g = this.game;
            if (!g) {
                return;
            }
            const t = this.cam.target;
            t[0] = Math.min(g.maxX + g.margin, Math.max(g.minX - g.margin, t[0]));
            t[2] = Math.min(g.maxZ + g.margin, Math.max(g.minZ - g.margin, t[2]));
        }

        // Animates turning; returns true while the view is still moving.
        animate(dt) {
            const d = this.cam.yawGoal - this.cam.yaw;
            if (Math.abs(d) < 1e-4) {
                this.cam.yaw = this.cam.yawGoal;
                return false;
            }
            this.cam.yaw += d * Math.min(1, dt * 12);
            this.dirty = true;
            return true;
        }

        // ---------------------------------------------------------------- picking

        // The ray under a screen point, as origin and direction.
        ray(px, py) {
            const [w, h] = this.size();
            const inv = M.invert(this.viewProj());
            const nx = 2 * px / w - 1;
            const ny = 1 - 2 * py / h;
            const a = M.apply(inv, [nx, ny, -1, 1]);
            const b = M.apply(inv, [nx, ny, 1, 1]);
            const pa = [a[0] / a[3], a[1] / a[3], a[2] / a[3]];
            const pb = [b[0] / b[3], b[1] / b[3], b[2] / b[3]];
            return [pa, norm(sub(pb, pa))];
        }

        // Where the ray under a screen point crosses the level y.
        groundAt(px, py, y) {
            const [o, d] = this.ray(px, py);
            if (Math.abs(d[1]) < 1e-6) {
                return null;
            }
            const t = (y - o[1]) / d[1];
            return [o[0] + d[0] * t, y, o[2] + d[2] * t];
        }

        // The block under a screen point and the point hit on it, or null. Walks the ray block by block.
        pick(px, py) {
            let [o, d] = this.ray(px, py);
            // Start where the ray comes down to the top of the world.
            if (d[1] < 0 && o[1] > 256) {
                const t = (256 - o[1]) / d[1];
                o = [o[0] + d[0] * t, 256, o[2] + d[2] * t];
            }
            let x = Math.floor(o[0]);
            let y = Math.floor(o[1]);
            let z = Math.floor(o[2]);
            const step = d.map((v) => (v > 0 ? 1 : -1));
            const tDelta = d.map((v) => (v === 0 ? Infinity : Math.abs(1 / v)));
            const next = (p, v, c) => (v > 0 ? c + 1 - p : p - c) / Math.abs(v || 1e-9);
            let tMax = [next(o[0], d[0], x), next(o[1], d[1], y), next(o[2], d[2], z)];
            let t = 0;
            for (let i = 0; i < 2000 && y >= 0; i++) {
                if (y <= 255 && this.map.chunk(x >> 4, z >> 4)) {
                    const value = this.map.block(x, y, z);
                    const id = B.index(value >> 4);
                    if (value && !B.isInvisible(id)) {
                        return {x, y, z, block: value >> 4, point: [o[0] + d[0] * t, o[1] + d[1] * t, o[2] + d[2] * t]};
                    }
                }
                const axis = tMax[0] < tMax[1] ? (tMax[0] < tMax[2] ? 0 : 2) : (tMax[1] < tMax[2] ? 1 : 2);
                t = tMax[axis];
                tMax[axis] += tDelta[axis];
                if (axis === 0) {
                    x += step[0];
                } else if (axis === 1) {
                    y += step[1];
                } else {
                    z += step[2];
                }
            }
            return null;
        }

        // Screen position of a world point, in CSS pixels.
        project(x, y, z) {
            const [w, h] = this.size();
            const c = M.apply(this.vp || this.viewProj(), [x, y, z, 1]);
            return [(c[0] + 1) / 2 * w, (1 - c[1]) / 2 * h];
        }

        // ---------------------------------------------------------------- drawing

        resize() {
            const ratio = window.devicePixelRatio || 1;
            const [w, h] = this.size();
            const width = Math.round(w * ratio);
            const height = Math.round(h * ratio);
            if (this.canvas.width !== width || this.canvas.height !== height) {
                this.canvas.width = width;
                this.canvas.height = height;
                this.dirty = true;
            }
        }

        visible(mesh, vp) {
            const x0 = mesh.cx * 16;
            const z0 = mesh.cz * 16;
            let left = 0, right = 0, below = 0, above = 0;
            for (let i = 0; i < 8; i++) {
                const c = M.apply(vp, [x0 + (i & 1) * 16, i & 2 ? mesh.maxY : mesh.minY, z0 + (i & 4 ? 16 : 0), 1]);
                left += c[0] < -1 ? 1 : 0;
                right += c[0] > 1 ? 1 : 0;
                below += c[1] < -1 ? 1 : 0;
                above += c[1] > 1 ? 1 : 0;
            }
            return left < 8 && right < 8 && below < 8 && above < 8;
        }

        // Draws the map, then boxes ({min: [x,y,z], size: [x,y,z], color: [r,g,b,a]}) and lines
        // ([[x,y,z], [x,y,z], [r,g,b,a]]). Boxes with an alpha below 1 are see-through and drawn after
        // everything solid.
        draw(boxes, lines) {
            const gl = this.gl;
            this.resize();
            const vp = this.viewProj();
            this.vp = vp;
            gl.viewport(0, 0, this.canvas.width, this.canvas.height);
            gl.clearColor(0.11, 0.18, 0.27, 1);
            gl.clearDepth(1);
            gl.depthMask(true);
            gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
            gl.enable(gl.DEPTH_TEST);
            gl.depthFunc(gl.LEQUAL);
            gl.disable(gl.CULL_FACE);
            gl.disable(gl.BLEND);

            const visible = [];
            for (const mesh of this.meshes.values()) {
                if (this.visible(mesh, vp)) {
                    visible.push(mesh);
                }
            }

            const bp = this.blockProgram;
            gl.useProgram(bp.program);
            gl.bindVertexArray(this.blockVao);
            gl.enableVertexAttribArray(0);
            gl.enableVertexAttribArray(1);
            gl.vertexAttribDivisor(0, 1);
            gl.vertexAttribDivisor(1, 1);
            gl.activeTexture(gl.TEXTURE0);
            gl.bindTexture(gl.TEXTURE_2D, this.table);
            gl.uniform1i(bp.uniforms.u_tab, 0);
            gl.uniformMatrix4fv(bp.uniforms.u_mvp, false, vp);
            const pixelsPerBlock = this.canvas.height / (2 * this.cam.zoom);
            gl.uniform1f(bp.uniforms.u_detail, Math.min(1, Math.max(0, (pixelsPerBlock - 8) / 12)));
            const drawMeshes = (translucent) => {
                for (const m of visible) {
                    const n = translucent ? m.nTr : m.nOp;
                    if (!n) {
                        continue;
                    }
                    const first = translucent ? m.nOp : 0;
                    gl.uniform3f(bp.uniforms.u_origin, m.cx * 16, 0, m.cz * 16);
                    gl.bindBuffer(gl.ARRAY_BUFFER, m.buffer);
                    gl.vertexAttribIPointer(0, 4, gl.UNSIGNED_BYTE, 8, first * 8);
                    gl.vertexAttribIPointer(1, 4, gl.UNSIGNED_BYTE, 8, first * 8 + 4);
                    gl.drawArraysInstanced(gl.TRIANGLE_STRIP, 0, 4, n);
                }
            };
            drawMeshes(false);

            // Units and players, then see-through boxes such as building sites.
            const drawBoxes = (seeThrough) => {
                gl.bindVertexArray(this.boxVao);
                const xp = this.boxProgram;
                gl.useProgram(xp.program);
                gl.uniformMatrix4fv(xp.uniforms.u_mvp, false, vp);
                for (const box of boxes) {
                    if ((box.color[3] < 1) !== seeThrough) {
                        continue;
                    }
                    gl.uniform3fv(xp.uniforms.u_min, box.min);
                    gl.uniform3fv(xp.uniforms.u_size, box.size);
                    gl.uniform4fv(xp.uniforms.u_color, box.color);
                    gl.drawArrays(gl.TRIANGLES, 0, 36);
                }
            };
            drawBoxes(false);

            gl.enable(gl.BLEND);
            gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
            gl.depthMask(false);
            gl.useProgram(bp.program);
            gl.bindVertexArray(this.blockVao);
            drawMeshes(true);
            gl.vertexAttribDivisor(0, 0);
            gl.vertexAttribDivisor(1, 0);
            drawBoxes(true);

            if (lines.length) {
                const v = new Float32Array(lines.length * 14);
                let i = 0;
                for (const [a, b, c] of lines) {
                    v.set(a, i); v.set(c, i + 3); v.set(b, i + 7); v.set(c, i + 10);
                    i += 14;
                }
                const lp = this.lineProgram;
                gl.useProgram(lp.program);
                gl.uniformMatrix4fv(lp.uniforms.u_mvp, false, vp);
                gl.bindVertexArray(this.lineVao);
                gl.bindBuffer(gl.ARRAY_BUFFER, this.lineBuffer);
                gl.bufferData(gl.ARRAY_BUFFER, v, gl.STREAM_DRAW);
                gl.enableVertexAttribArray(0);
                gl.enableVertexAttribArray(1);
                gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 28, 0);
                gl.vertexAttribPointer(1, 4, gl.FLOAT, false, 28, 12);
                // Faint where hidden behind terrain, then full strength where in view.
                gl.depthFunc(gl.GREATER);
                gl.uniform1f(lp.uniforms.u_alpha, 0.3);
                gl.drawArrays(gl.LINES, 0, lines.length * 2);
                gl.depthFunc(gl.LEQUAL);
                gl.uniform1f(lp.uniforms.u_alpha, 1);
                gl.drawArrays(gl.LINES, 0, lines.length * 2);
            }
            gl.depthMask(true);
            gl.bindVertexArray(null);
            gl.bindBuffer(gl.ARRAY_BUFFER, null);
            this.dirty = false;
        }
    }

    window.WotgRender = {MapView, MapData, meshChunk};
})();
