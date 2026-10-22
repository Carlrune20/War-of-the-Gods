#ifndef MAP_GLSL
#define MAP_GLSL

// Which map is being played. The mod sends the game dimension's time with the map in its day count
// (WorldProviderRts.getWorldTime): day 65536 + 16 * map + 0 to 15, so the moon still goes through its phases.
// Anywhere else, such as the lobby, the day is never that high and the map is MAP_NONE.

#define MAP_NONE -1
#define MAP_MISTHALIN 0
#define MAP_FORINTHRY 1

uniform int worldDay;
uniform float sunAngle;

int currentMap() {
    int day = worldDay - 65536;
    if (day < 0 || day >= 256) {
        return MAP_NONE;
    }
    return day / 16;
}

// 1 in full daylight, 0 at night, easing through sunrise and sunset.
float daylight() {
    return smoothstep(-0.08, 0.12, sin(sunAngle * 6.2831853));
}

// Forinthry's sky, which its fog fades into: an ember glow low down under smoke that is nearly black overhead.
const vec3 FORINTHRY_HORIZON = vec3(0.13, 0.05, 0.035);
const vec3 FORINTHRY_ZENITH = vec3(0.016, 0.009, 0.013);

#endif
