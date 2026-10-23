#version 120

// Lights and colours the far terrain as the pack does the world's blocks (shaderpack/shaders/lib/light.glsl and
// gbuffers_fragment.glsl), and writes what glows to colortex4 for the pack's bloom. Keep the two in step.

// The light of each fog of war cell, with the light level in alpha.
uniform sampler2D light;
// Minecraft's day count, which carries the map (shaderpack/shaders/lib/map.glsl).
uniform int worldDay;

varying vec4 color;
varying vec2 lightCoord;

void main() {
    vec4 cell = texture2D(light, lightCoord);
    vec3 light = cell.rgb;
    float level = cell.a;
    int day = worldDay - 65536;
    if (day >= 16 && day < 32) {
        // Forinthry: open ground is lit a dim ember red by the glowing ash overhead.
        light = max(light, level * vec3(0.5, 0.25, 0.18));
    }
    // The share of the cell that glows, in the colour's alpha; fog of war hides it where nothing is seen.
    float glow = color.a * level;
    gl_FragData[0] = vec4(color.rgb * max(light, vec3(glow)), 1.0);
    gl_FragData[1] = vec4(color.rgb * glow, 1.0);
}
