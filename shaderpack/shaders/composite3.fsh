#version 120

// Bloom, last pass: the wide blur down colortex7, added to the picture, so lava, embers and eyes shine.

/* DRAWBUFFERS:0 */

#include "/lib/settings.glsl"
#include "/lib/bloom.glsl"

uniform sampler2D gcolor;
uniform sampler2D gaux4;

varying vec2 texcoord;

void main() {
    vec3 color = texture2D(gcolor, texcoord).rgb;
    vec3 bloom = blurGlow(gaux4, texcoord, vec2(0.0, 1.0), BLOOM_WIDE);
    gl_FragData[0] = vec4(color + bloom * BLOOM_STRENGTH * 1.6, 1.0);
}
