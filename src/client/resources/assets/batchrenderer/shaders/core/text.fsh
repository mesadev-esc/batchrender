#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec4 vertColor;
layout(location = 0) out vec4 fragColor;

void main() {
    float dist = texture(Sampler0, texCoord).r;
    float width = fwidth(dist) * 0.7;
    float alpha = smoothstep(0.5 - width, 0.5 + width, dist);
    if (alpha < 0.1) discard;
    fragColor = vec4(vertColor.rgb, vertColor.a * alpha) * ColorModulator;
}