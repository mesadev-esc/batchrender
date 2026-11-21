#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(location = 0) in vec4 vertColor;
layout(location = 1) in vec2 localPos;
layout(location = 2) flat in vec2 rectSize;
layout(location = 3) flat in vec4 radii;
layout(location = 4) flat in float strokeWidth;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 halfSize = rectSize * 0.5;
    vec2 pos = localPos - halfSize;
    float radius = pos.x < 0.0 ? (pos.y < 0.0 ? radii.x : radii.w) : (pos.y < 0.0 ? radii.y : radii.z);
    vec2 q = abs(pos) - halfSize + radius;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(dist), 0.0001);
    float alpha = 1.0 - smoothstep(-aa, 0.0, dist);
    if (strokeWidth > 0.0) alpha -= 1.0 - smoothstep(-aa, 0.0, dist + strokeWidth);
    if (alpha <= 0.0) discard;
    vec4 color = vertColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * alpha);
}
