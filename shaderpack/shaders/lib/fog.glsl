#ifndef FOG_GLSL
#define FOG_GLSL

#include "/lib/map.glsl"

uniform int fogMode;
uniform int isEyeInWater;

// How much fog hides something this far away. The fog's start and end come from Minecraft, so the RTS view, which
// pushes the fog out of sight, stays clear.
float fogAmount(float dist) {
    if (fogMode == 9729) {
        return clamp((dist - gl_Fog.start) * gl_Fog.scale, 0.0, 1.0);
    }
    if (fogMode == 2048) {
        return 1.0 - clamp(exp(-gl_Fog.density * dist), 0.0, 1.0);
    }
    return 0.0;
}

vec3 mapFogColor(int map) {
    if (isEyeInWater != 0) {
        return gl_Fog.color.rgb;
    }
    if (map == MAP_FORINTHRY) {
        return FORINTHRY_HORIZON;
    }
    return gl_Fog.color.rgb;
}

vec3 applyFog(vec3 color, float dist, int map) {
    return mix(color, mapFogColor(map), fogAmount(dist));
}

#endif
