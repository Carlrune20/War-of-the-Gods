#ifndef BLOOM_GLSL
#define BLOOM_GLSL

uniform float viewWidth;
uniform float viewHeight;

// The glow is blurred twice over, a narrow blur and then a wide one, each across and then down. One wide blur straight
// over the sharp glow put its taps pixels apart, so each tap picked up its own copy of a glowing block's edge and
// texture and the bloom came out in hard steps and squares; a blur's taps must sit no further apart than what it
// samples is already smooth. Both blurs widen on taller screens.
const float BLOOM_NARROW = 1.0;
const float BLOOM_WIDE = 2.0;

// One direction of a Gaussian blur over what glows, its taps the given number of pixels apart at 540 lines.
vec3 blurGlow(sampler2D glow, vec2 coord, vec2 direction, float width) {
    float spread = max(1.0, viewHeight / 540.0) * width;
    vec2 step = direction * spread / vec2(viewWidth, viewHeight);
    vec3 sum = texture2D(glow, coord).rgb * 0.1585;
    sum += (texture2D(glow, coord + step * 1.0).rgb + texture2D(glow, coord - step * 1.0).rgb) * 0.1465;
    sum += (texture2D(glow, coord + step * 2.0).rgb + texture2D(glow, coord - step * 2.0).rgb) * 0.1156;
    sum += (texture2D(glow, coord + step * 3.0).rgb + texture2D(glow, coord - step * 3.0).rgb) * 0.0778;
    sum += (texture2D(glow, coord + step * 4.0).rgb + texture2D(glow, coord - step * 4.0).rgb) * 0.0447;
    sum += (texture2D(glow, coord + step * 5.0).rgb + texture2D(glow, coord - step * 5.0).rgb) * 0.0219;
    sum += (texture2D(glow, coord + step * 6.0).rgb + texture2D(glow, coord - step * 6.0).rgb) * 0.0092;
    return sum;
}

#endif
