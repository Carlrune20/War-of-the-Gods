#version 120

// Bloom, third pass: the wide blur across colortex6, into colortex7.

/* DRAWBUFFERS:7 */

#include "/lib/bloom.glsl"

uniform sampler2D gaux3;

varying vec2 texcoord;

void main() {
    gl_FragData[0] = vec4(blurGlow(gaux3, texcoord, vec2(1.0, 0.0), BLOOM_WIDE), 1.0);
}
