#version 120

// Bloom, first of four passes: the narrow blur across what glows (colortex4), into colortex5.

/* DRAWBUFFERS:5 */

#include "/lib/bloom.glsl"

uniform sampler2D gaux1;

varying vec2 texcoord;

void main() {
    gl_FragData[0] = vec4(blurGlow(gaux1, texcoord, vec2(1.0, 0.0), BLOOM_NARROW), 1.0);
}
