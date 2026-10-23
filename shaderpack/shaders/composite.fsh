#version 120

// Bloom, first half: blurs what glows (colortex4) across into colortex5.

/* DRAWBUFFERS:5 */

#include "/lib/bloom.glsl"

uniform sampler2D gaux1;

varying vec2 texcoord;

void main() {
    gl_FragData[0] = vec4(blurGlow(gaux1, texcoord, vec2(1.0, 0.0)), 1.0);
}
