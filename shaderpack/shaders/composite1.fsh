#version 120

// Bloom, second pass: the narrow blur down colortex5, into colortex6.

/* DRAWBUFFERS:6 */

#include "/lib/bloom.glsl"

uniform sampler2D gaux2;

varying vec2 texcoord;

void main() {
    gl_FragData[0] = vec4(blurGlow(gaux2, texcoord, vec2(0.0, 1.0), BLOOM_NARROW), 1.0);
}
