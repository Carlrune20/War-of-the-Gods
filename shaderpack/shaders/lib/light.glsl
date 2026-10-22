#ifndef LIGHT_GLSL
#define LIGHT_GLSL

#include "/lib/settings.glsl"
#include "/lib/map.glsl"

uniform sampler2D lightmap;

// Block light in x and sky light in y, each from 0 to 1.
vec2 lightLevels(vec2 lmcoord) {
    return clamp((lmcoord - 0.03125) * 1.06667, 0.0, 1.0);
}

// The light falling on a surface. It always starts from Minecraft's own lightmap, so night vision, the brightness
// setting and the RTS view's fog of war, which lights the ground by what a team has seen, all work as without
// shaders; each map then colours it.
vec3 worldLight(vec2 lmcoord, int map) {
    vec3 light = texture2D(lightmap, lmcoord).rgb;
    vec2 level = lightLevels(lmcoord);
    if (map == MAP_FORINTHRY) {
        // Always midnight: open ground is lit a dim ember red by the glowing ash overhead, and fire burns hot.
        light = max(light, level.y * FORINTHRY_GLOW * vec3(0.5, 0.25, 0.18));
        light *= mix(vec3(1.0), vec3(1.15, 0.9, 0.75), level.x * 0.7);
    } else if (map == MAP_MISTHALIN) {
        // Warm sunlight by day, cool blue moonlight by night, and torches a soft gold.
        float day = daylight();
        vec3 sky = mix(vec3(0.8, 0.9, 1.15), vec3(1.06, 1.0, 0.9), day);
        float torch = level.x * (1.0 - level.y * day);
        light *= mix(mix(vec3(1.0), sky, level.y), vec3(1.12, 0.96, 0.78), torch * 0.6);
    }
    return light;
}

#endif
