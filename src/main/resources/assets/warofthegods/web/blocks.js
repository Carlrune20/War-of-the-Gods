// Block colours and shapes for the 3D map, for the Minecraft 1.7.10 block ids. Each block is drawn as one or two
// flat-coloured boxes, the way the beta map editor draws them. Blocks of other mods show as grey cubes.
"use strict";

(function () {
    const WOOL = [
        0xE9ECEC, 0xEA7E35, 0xBE49C9, 0x6387D2, 0xC2B51C, 0x39BA2E, 0xD98199, 0x414141,
        0xA0A7A7, 0x267191, 0x7E34BF, 0x253193, 0x56331C, 0x364B18, 0x9E2B27, 0x181414,
    ];
    const CLAY = [
        0xD1B2A1, 0xA05325, 0x95576C, 0x706C8A, 0xBA8523, 0x677534, 0xA14E4E, 0x392A23,
        0x876A61, 0x575B5B, 0x764656, 0x4A3C5B, 0x4D3323, 0x4C532A, 0x8F3D2E, 0x251610,
    ];
    const PLANKS = [0xA2834F, 0x67502F, 0xC3B27B, 0x9A6E4D, 0xAD5D32, 0x3E2912];
    const UNKNOWN = 255;

    // Opaque cubes hide the faces of their neighbours.
    const FULL = new Set([
        1, 2, 3, 4, 5, 7, 12, 13, 14, 15, 16, 17, 19, 21, 22, 23, 24, 25, 29, 33, 35, 41, 42, 43, 45, 46, 47, 48, 49,
        52, 56, 57, 58, 61, 62, 73, 74, 80, 82, 84, 86, 87, 88, 89, 91, 97, 98, 99, 100, 103, 110, 112, 121, 123, 124,
        125, 129, 133, 137, 152, 153, 155, 158, 159, 162, 170, 172, 173, 174, 200, 202,
        212, 213, 214, 215, 216, 217, 218, UNKNOWN,
    ]);
    // Drawn see-through, after everything else.
    const TRANSLUCENT = new Set([8, 9, 20, 30, 79, 90, 95, 102, 160]);
    // Not drawn at all.
    const INVISIBLE = new Set([0, 36, 106, 119, 132]);

    // Neighbouring blocks of one family hide the faces between them: water, lava, glass, ice, portal, leaves.
    function family(id) {
        switch (id) {
            case 8: case 9: return 1;
            case 10: case 11: return 2;
            case 20: case 95: return 3;
            case 79: return 4;
            case 90: return 5;
            case 18: case 161: return 6;
            default: return 0;
        }
    }

    function same(c) {
        return [c, c];
    }

    // [side colour, top colour] as 0xRRGGBB.
    function colors(id, d) {
        switch (id) {
            case 1: return same(0x7D7D7D);
            case 2: return [0x86603F, 0x6FA544];
            case 3: return d === 2 ? [0x86603F, 0x5A3C1E] : same(0x86603F);
            case 4: return same(0x7A7A7A);
            case 5: case 125: case 126: return same(PLANKS[(id === 5 ? d : d & 7) % 6]);
            case 6: return same([0x4C8A2A, 0x3A5A32, 0x7FA35A, 0x4C8A2A, 0x6A8A2A, 0x2E5A1E][(d & 7) % 6]);
            case 7: return same(0x3A3A3A);
            case 8: case 9: return same(0x2F5FE0);
            case 10: case 11: return same(0xE0661A);
            case 12: return same(d === 1 ? 0xA9581E : 0xDBD3A0);
            case 13: return same(0x857F7E);
            case 14: return same(0x8F8B7A);
            case 15: return same(0x8A8580);
            case 16: return same(0x6E6E6E);
            case 17: case 162: {
                const kinds = id === 17
                    ? [[0x665131, 0x9E804D], [0x3D2A16, 0x6B5434], [0xD6D6CF, 0xB59B6A], [0x584419, 0x9A7A4A]]
                    : [[0x696158, 0xA95A32], [0x3A2A16, 0x4A3720], [0x696158, 0xA95A32], [0x3A2A16, 0x4A3720]];
                const [side, top] = kinds[d & 3];
                // Logs lying down show bark on top.
                return (d & 12) === 0 ? [side, top] : same(side);
            }
            case 18: return same([0x3F8A23, 0x30552F, 0x5E8A3C, 0x3A8A1E][d & 3]);
            case 161: return same([0x5E8A2C, 0x2F5A1C, 0x5E8A2C, 0x2F5A1C][d & 3]);
            case 19: return same(0xC3C24A);
            case 20: return same(0xC0E4F0);
            case 21: return same(0x667090);
            case 22: return same(0x1F43A4);
            case 23: case 158: return [0x6E6E6E, 0x7D7D7D];
            case 24: case 128: return [0xD8CB9B, 0xE0D6AA];
            case 25: case 84: return [0x6A4630, 0x5E3E2A];
            case 26: return [0x8E1616, 0xB03030];
            case 27: case 28: case 66: case 157: return same(0x8A7465);
            case 29: case 33: case 34: return [0x8E8E86, id === 29 ? 0x7FAE5E : 0xB09B70];
            case 30: return same(0xDCDCDC);
            case 31: return same(d === 0 ? 0x7A5A2A : d === 2 ? 0x4E8A3A : 0x5E9A3A);
            case 32: return same(0x7A5A2A);
            case 35: case 171: return same(WOOL[d]);
            case 37: return same(0xE6D820);
            case 38: return same([0xC21414, 0x3A8AD0, 0xB060D0, 0xDCDCE6, 0xD03A1A, 0xE07A1A, 0xE6E6E6, 0xE090C0, 0xDCDCB0][d] || 0xC21414);
            case 39: return same(0x9A7558);
            case 40: return same(0xC83030);
            case 41: return same(0xF5E049);
            case 42: return same(0xDCDCDC);
            case 43: case 44: switch (d & 7) {
                case 1: return [0xD8CB9B, 0xE0D6AA];
                case 2: return same(0xA2834F);
                case 3: return same(0x7A7A7A);
                case 4: return same(0x965A49);
                case 5: return same(0x7A7A7A);
                case 6: return same(0x2C1518);
                case 7: return same(0xEAE6DF);
                default: return [0xA5A5A5, 0xB0B0B0];
            }
            case 45: case 108: return same(0x965A49);
            case 46: return [0xC23A2A, 0xD0D0D0];
            case 47: return [0x6E5236, 0xA2834F];
            case 48: return same(0x627A62);
            case 49: return same(0x1B1529);
            case 50: return same(0xF7D35A);
            case 51: return same(0xF0A020);
            case 52: return same(0x1F2E3A);
            case 53: return same(0xA2834F);
            case 54: case 146: return [0x9C6E2E, 0x8A5E26];
            case 55: return same(0xB01010);
            case 56: return same(0x81A5A8);
            case 57: return same(0x63DBD5);
            case 58: return [0x8A6A3E, 0xA06E44];
            case 59: case 141: case 142: return same(d >= 7 ? (id === 59 ? 0xC8B040 : 0x5AA02A) : 0x5A9A2A);
            case 60: return [0x86603F, 0x5A3A20];
            case 61: case 62: return [0x6E6E6E, 0x8A8A8A];
            case 63: case 68: return same(0xA2834F);
            case 64: return same(0x8A6A3E);
            case 65: case 85: case 107: return same(0x9A7A48);
            case 67: case 139: return same(id === 139 && d === 1 ? 0x627A62 : 0x7A7A7A);
            case 69: return same(0x6E5A3A);
            case 70: case 77: return same(0x7D7D7D);
            case 71: return same(0xC8C8C8);
            case 72: case 143: return same(0xA2834F);
            case 73: case 74: return same(0x8E6E6E);
            case 75: return same(0x6A2010);
            case 76: return same(0xFF3020);
            case 78: case 80: return same(0xF2F8F8);
            case 79: return same(0x86AEF0);
            case 81: return [0x15781F, 0x2A8A30];
            case 82: return same(0x9EA4B0);
            case 83: return same(0x8AC060);
            case 86: return [0xC6761A, 0xB06A10];
            case 87: return same(0x6F3534);
            case 88: return same(0x54402F);
            case 89: return same(0xF9D49C);
            case 90: return same(0x6A1ACA);
            case 91: return [0xD8901A, 0xB06A10];
            case 92: return [0xE8E0D0, 0xF4F0EA];
            case 93: case 94: case 149: case 150: return same(0xA0A0A0);
            case 95: case 160: return same(WOOL[d]);
            case 96: return same(0x7E5D2D);
            case 97: return same(0x7D7D7D);
            case 98: case 109: return same([0x7A7A7A, 0x6A7A62, 0x767676, 0x7A7A7A][d & 3]);
            case 99: return same(0x8E6A4E);
            case 100: return same(0xB02020);
            case 101: return same(0x6E6E6E);
            case 102: return same(0xC0E4F0);
            case 103: return [0x8A9A2A, 0x7A8A2A];
            case 104: case 105: return same(0x6A9A2A);
            case 110: return [0x86603F, 0x6F6369];
            case 111: return same(0x208030);
            case 112: case 113: case 114: return same(0x2C1518);
            case 115: return same(0x8A1A1A);
            case 116: return [0x3A1F28, 0x9A2A2A];
            case 117: return same(0x7A7A6A);
            case 118: case 154: return same(0x3A3A3A);
            case 120: return [0x587A5A, 0x6A8A6A];
            case 121: return same(0xDDDFA5);
            case 122: return same(0x0C0910);
            case 123: return same(0x8A5A30);
            case 124: return same(0xC8963C);
            case 127: return same(0x9A6A2A);
            case 129: return same(0x7A8E80);
            case 130: return same(0x2A3A3A);
            case 131: return same(0x8A8A8A);
            case 133: return same(0x2ACB5A);
            case 134: return same(PLANKS[1]);
            case 135: return same(PLANKS[2]);
            case 136: return same(PLANKS[3]);
            case 137: return same(0xB08A6E);
            case 138: return same(0x7AE0D8);
            case 140: return same(0x7A4030);
            case 144: return same(0xC8C8B0);
            case 145: return same(0x444444);
            case 147: return same(0xF5E049);
            case 148: return same(0xDCDCDC);
            case 151: return [0x83705A, 0x6A6A5A];
            case 152: return same(0xAB1A0A);
            case 153: return same(0x7E4E48);
            case 155: case 156: return same(0xEAE6DF);
            case 159: return same(CLAY[d]);
            case 163: return same(PLANKS[4]);
            case 164: return same(PLANKS[5]);
            case 170: return [0xA88A1A, 0xB09A2A];
            case 172: return same(0x965C42);
            case 173: return same(0x121212);
            case 174: return same(0xA0B8E8);
            // The mod's own blocks, by the ids ModBlocks.java gives them: graystone, its slab, double slab and stairs,
            // the wooden door, Saradomin's and Zamorak's banners, fire that burns for show and Bandos's banners.
            case 200: return same([0x4E4E52, 0x4E5A44, 0x48484C][d & 3] || 0x4E4E52);
            case 201: case 202: case 203: return same(0x4E4E52);
            case 204: return same(0x68462A);
            case 205: return same(0x2A42A8);
            case 206: return same(0xE6E6E0);
            case 207: return same(0x1C181A);
            case 208: return same(0xA81C1A);
            case 209: return same(0xE0661A);
            case 210: return same(0x4A6A2A);
            case 211: return same(0x6A4A2A);
            // Forinthry's: charred earth, ash, scorched stone, ember rock, charred logs, mushroom stems and caps.
            case 212: return same(0x2E2622);
            case 213: return same(0x84807C);
            case 214: return same(0x3A3638);
            case 215: return same(0x5A2E1E);
            case 216: return same(0x241E1C);
            case 217: return same(0xD6CEBA);
            case 218: return same(0x602C78);
            case 175: return same([0xE6D820, 0xB060D0, 0x5E9A3A, 0x4E8A3A, 0xC21414, 0xE090C0][d & 7] || 0x5E9A3A);
            default: return same(0x8A8A8A);
        }
    }

    // Shape boxes in 1/16 block: [min, max] for the first part and optionally a second part.
    function shape(id, d) {
        const full = [[0, 0, 0], [16, 16, 16]];
        // A thin plate against a side: 0 = west (x-), 1 = north (z-), 2 = east (x+), 3 = south (z+).
        const wall = (side, t, y0, y1) => {
            switch (side & 3) {
                case 0: return [[0, y0, 0], [t, y1, 16]];
                case 1: return [[0, y0, 0], [16, y1, t]];
                case 2: return [[16 - t, y0, 0], [16, y1, 16]];
                default: return [[0, y0, 16 - t], [16, y1, 16]];
            }
        };
        const box = (a, b) => [a, b];
        switch (id) {
            case 6: return [box([4, 0, 4], [12, 12, 12])];
            case 8: case 9: case 10: case 11: return [box([0, 0, 0], [16, 14, 16])];
            case 26: return [box([0, 0, 0], [16, 9, 16])];
            case 27: case 28: case 55: case 66: case 157: case 171: return [box([0, 0, 0], [16, 1, 16])];
            case 111: return [box([0, 0, 0], [16, 1, 16])];
            case 31: return [box([3, 0, 3], [13, 10, 13])];
            case 32: return [box([3, 0, 3], [13, 9, 13])];
            case 37: case 38: return [box([5, 0, 5], [11, 10, 11])];
            case 39: case 40: return [box([5, 0, 5], [11, 6, 11])];
            case 44: case 126: case 201: return [(d & 8) ? box([0, 8, 0], [16, 16, 16]) : box([0, 0, 0], [16, 8, 16])];
            case 50: case 75: case 76: switch (d) {
                case 1: return [box([0, 3, 7], [3, 13, 9])];
                case 2: return [box([13, 3, 7], [16, 13, 9])];
                case 3: return [box([7, 3, 0], [9, 13, 3])];
                case 4: return [box([7, 3, 13], [9, 13, 16])];
                default: return [box([7, 0, 7], [9, 10, 9])];
            }
            case 51: return [box([1, 0, 1], [15, 12, 15])];
            case 53: case 67: case 108: case 109: case 114: case 128: case 134: case 135: case 136: case 156:
            case 163: case 164: case 203: {
                const upside = (d & 4) !== 0;
                const [y0, y1] = upside ? [0, 8] : [8, 16];
                const step = [
                    box([8, y0, 0], [16, y1, 16]),
                    box([0, y0, 0], [8, y1, 16]),
                    box([0, y0, 8], [16, y1, 16]),
                    box([0, y0, 0], [16, y1, 8]),
                ][d & 3];
                return [upside ? box([0, 8, 0], [16, 16, 16]) : box([0, 0, 0], [16, 8, 16]), step];
            }
            case 54: case 130: case 146: return [box([1, 0, 1], [15, 14, 15])];
            case 59: case 115: case 141: case 142: return [box([1, 0, 1], [15, (Math.min(d, 7) + 1) * 2, 15])];
            case 104: case 105: return [box([6, 0, 6], [10, (Math.min(d, 7) + 1) * 2, 10])];
            case 60: return [box([0, 0, 0], [16, 15, 16])];
            case 63: return [box([7, 0, 7], [9, 9, 9]), box([0, 9, 7], [16, 16, 9])];
            case 205: case 206: case 207: case 208: case 210: case 211: return [wall([3, 0, 1, 2][d & 3], 1, 0, 16)];
            case 209: return [box([1, 0, 1], [15, 12, 15])];
            case 64: case 71: case 204: {
                if (d & 8) {
                    return [full];
                }
                let side = d & 3;
                if (d & 4) {
                    side = (side + 1) & 3;
                }
                return [wall(side, 3, 0, 16)];
            }
            case 65: return [wall({2: 3, 3: 1, 4: 2}[d] ?? 0, 2, 0, 16)];
            case 68: return [wall({2: 3, 3: 1, 4: 2}[d] ?? 0, 2, 4, 12)];
            case 69: case 77: case 143: switch (d & 7) {
                case 1: return [box([0, 6, 5], [2, 10, 11])];
                case 2: return [box([14, 6, 5], [16, 10, 11])];
                case 3: return [box([5, 6, 0], [11, 10, 2])];
                case 4: return [box([5, 6, 14], [11, 10, 16])];
                default: return [box([5, 0, 4], [11, 3, 12])];
            }
            case 70: case 72: case 147: case 148: return [box([1, 0, 1], [15, 1, 15])];
            case 78: return [box([0, 0, 0], [16, ((d & 7) + 1) * 2, 16])];
            case 81: return [box([1, 0, 1], [15, 16, 15])];
            case 83: return [box([2, 0, 2], [14, 16, 14])];
            case 85: case 113: return [box([6, 0, 6], [10, 16, 10])];
            case 139: return [box([4, 0, 4], [12, 16, 12])];
            case 101: case 102: case 160: return [box([7, 0, 7], [9, 16, 9])];
            case 107: return [(d & 1) ? box([6, 0, 0], [10, 16, 16]) : box([0, 0, 6], [16, 16, 10])];
            case 92: return [box([1, 0, 1], [15, 8, 15])];
            case 93: case 94: case 149: case 150: return [box([0, 0, 0], [16, 2, 16])];
            case 96: {
                if (d & 4) {
                    return [wall([3, 1, 2, 0][d & 3], 3, 0, 16)];
                }
                return [(d & 8) ? box([0, 13, 0], [16, 16, 16]) : box([0, 0, 0], [16, 3, 16])];
            }
            case 116: return [box([0, 0, 0], [16, 12, 16])];
            case 117: return [box([7, 0, 7], [9, 14, 9])];
            case 120: return [box([0, 0, 0], [16, 13, 16])];
            case 122: return [box([1, 0, 1], [15, 16, 15])];
            case 127: return [box([5, 4, 5], [11, 12, 11])];
            case 131: return [box([6, 2, 0], [10, 10, 2])];
            case 140: return [box([5, 0, 5], [11, 6, 11])];
            case 144: return [box([4, 0, 4], [12, 8, 12])];
            case 145: return [box([2, 0, 2], [14, 16, 14])];
            case 151: return [box([0, 0, 0], [16, 6, 16])];
            case 154: return [box([0, 6, 0], [16, 16, 16]), box([4, 0, 4], [12, 6, 12])];
            case 175: return [box([2, 0, 2], [14, 16, 14])];
            default: return [full];
        }
    }

    // 256 x 96 RGBA, read by the shader: rows 0-15 side colour, 16-31 top colour, 32-47 first part min, 48-63
    // first part max, 64-79 second part min, 80-95 second part max, each row by metadata.
    function table() {
        const t = new Uint8Array(256 * 96 * 4);
        const put = (x, y, v) => t.set(v, (y * 256 + x) * 4);
        for (let id = 0; id < 256; id++) {
            for (let d = 0; d < 16; d++) {
                const [side, top] = colors(id, d);
                let alpha = 255;
                if (TRANSLUCENT.has(id)) {
                    alpha = {8: 165, 9: 165, 20: 90, 79: 170, 30: 120, 95: 110, 160: 110, 102: 90}[id] || 150;
                }
                put(id, d, [side >> 16 & 255, side >> 8 & 255, side & 255, alpha]);
                put(id, 16 + d, [top >> 16 & 255, top >> 8 & 255, top & 255, alpha]);
                const parts = shape(id, d);
                const a = parts[0];
                const b = parts[1] || a;
                put(id, 32 + d, [...a[0], 255]);
                put(id, 48 + d, [...a[1], 255]);
                put(id, 64 + d, [...b[0], 255]);
                put(id, 80 + d, [...b[1], 255]);
            }
        }
        return t;
    }

    // Parts each block has, by id * 16 + metadata.
    const PARTS = new Uint8Array(256 * 16);
    // Faces of each part that reach the block's edge, as bits 1 << face, by (id * 16 + metadata) * 2 + part.
    // Faces: 0 east (x+), 1 west (x-), 2 top, 3 bottom, 4 south (z+), 5 north (z-).
    const EDGES = new Uint8Array(256 * 16 * 2);
    for (let id = 0; id < 256; id++) {
        for (let d = 0; d < 16; d++) {
            const parts = shape(id, d);
            PARTS[id * 16 + d] = parts.length;
            parts.forEach(([mn, mx], part) => {
                EDGES[(id * 16 + d) * 2 + part] = (mx[0] === 16 ? 1 : 0) | (mn[0] === 0 ? 2 : 0)
                    | (mx[1] === 16 ? 4 : 0) | (mn[1] === 0 ? 8 : 0) | (mx[2] === 16 ? 16 : 0) | (mn[2] === 0 ? 32 : 0);
            });
        }
    }

    window.WotgBlocks = {
        UNKNOWN,
        table,
        // Blocks of other mods, with ids past 255, draw as the grey unknown block.
        index: (id) => (id > 255 ? UNKNOWN : id),
        isFull: (id) => FULL.has(id),
        isTranslucent: (id) => TRANSLUCENT.has(id),
        isInvisible: (id) => INVISIBLE.has(id),
        family,
        parts: (id, d) => PARTS[id * 16 + d],
        edges: (id, d, part) => EDGES[(id * 16 + d) * 2 + part],
        shape,
    };
})();
