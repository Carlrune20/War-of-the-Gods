#version 120

// Each map's colours, a gentle tone curve and the vignette. Everything in the world passes through here, the RTS
// view's far terrain included; the GUI is drawn after, untouched.

#include "/lib/settings.glsl"
#include "/lib/map.glsl"

uniform sampler2D gcolor;
uniform float aspectRatio;

varying vec2 texcoord;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 saturation(vec3 c, float amount) {
    return max(vec3(0.0), mix(vec3(luma(c)), c, amount));
}

// Misthalin: kingdom green and gold, lively by day; by night a cool blue with a little lift in the shadows.
vec3 gradeMisthalin(vec3 c) {
    float day = daylight();
    c = saturation(c, mix(0.95, 1.15, day));
    c *= mix(vec3(0.92, 0.97, 1.08), vec3(1.04, 1.01, 0.95), day);
    c += (1.0 - day) * vec3(0.004, 0.008, 0.016);
    return c;
}

// Forinthry: drained of colour but for the reds and oranges of fire, with crimson shadows under the smoke.
vec3 gradeForinthry(vec3 c) {
    float l = luma(c);
    float warm = clamp((c.r - max(c.g, c.b)) * 3.0, 0.0, 1.0);
    c = mix(vec3(l), c, mix(0.55, 1.15, warm));
    c *= vec3(1.1, 0.92, 0.85);
    c += (1.0 - smoothstep(0.0, 0.25, l)) * vec3(0.012, 0.002, 0.0);
    return c;
}

// A soft shoulder so bright bloom rolls off instead of clipping.
vec3 toneCurve(vec3 c) {
    vec3 bright = max(c - 0.8, 0.0);
    return min(c, 0.8) + bright / (1.0 + bright * 5.0);
}

void main() {
    vec3 c = texture2D(gcolor, texcoord).rgb;
    int map = currentMap();
    float vignette = 0.25;

#ifdef COLOR_GRADING
    if (map == MAP_MISTHALIN) {
        c = gradeMisthalin(c);
    } else if (map == MAP_FORINTHRY) {
        c = gradeForinthry(c);
        vignette = 0.5;
    }
#endif

    c = toneCurve(c);

    vec2 centered = (texcoord - 0.5) * vec2(aspectRatio, 1.0);
    c *= 1.0 - smoothstep(0.35, 1.1, length(centered)) * vignette * VIGNETTE;

    gl_FragColor = vec4(c, 1.0);
}
