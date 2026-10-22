#version 120

// Bloom, second half: blurs colortex5 down and adds it to the picture, so lava, embers and eyes shine.

/* DRAWBUFFERS:0 */

#include "/lib/settings.glsl"
#include "/lib/bloom.glsl"

uniform sampler2D gcolor;
uniform sampler2D gaux2;

varying vec2 texcoord;

void main() {
    vec3 color = texture2D(gcolor, texcoord).rgb;
    vec3 bloom = blurGlow(gaux2, texcoord, vec2(0.0, 1.0));
    gl_FragData[0] = vec4(color + bloom * BLOOM_STRENGTH * 1.6, 1.0);
}
