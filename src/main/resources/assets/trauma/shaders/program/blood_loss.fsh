#version 150

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Time;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

// Random math
float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

// Custom 2D noise
float noise(vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    float a = rand(i);
    float b = rand(i + vec2(1.0, 0.0));
    float c = rand(i + vec2(0.0, 1.0));
    float d = rand(i + vec2(1.0, 1.0));

    vec2 u = f*f*(3.0-2.0*f);
    return mix(a, b, u.x) + (c - a)* u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

void main() {
    vec4 texColor = texture(DiffuseSampler, texCoord);
    
    // 1. Aspect-ratio fixed coordinates for the vignette
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 pos = texCoord - vec2(0.5);
    pos.x *= aspect;
    
    // 2. Dynamic pulsating vignette
    float dist = length(pos);
    float pulse = sin(Time * 5.0) * 0.1 + 0.9;
    
    // Outer vignette
    float vignetteThreshold = 0.5 * pulse;
    float vignette = smoothstep(0.3, 0.9, dist / vignetteThreshold);
    
    vec3 red = vec3(0.7, 0.0, 0.0);
    float factor = clamp(vignette, 0.0, 1.0) * Intensity * 2.5;

    // 3. Procedural blood splatters sliding down
    // Distort dropping coordinates slightly to make them look fluid
    vec2 dropPos = texCoord;
    dropPos.x *= 8.0 * aspect; // scale to make squares, then wider
    dropPos.y = (dropPos.y - Time * 0.3) * 6.0;
    
    // Use layered noise for blobs
    float n = noise(dropPos);
    float blobs = smoothstep(0.85, 0.95, n); // sharp drops
    
    // Multiply by a mask so the center of the screen stays mostly clean 
    // And to make it look like splatters instead of uniform rain
    float splatterMask = noise(vec2(texCoord.x * 2.0, texCoord.y * 2.0));
    blobs *= smoothstep(0.4, 1.0, splatterMask);
    
    // Bleed effect mask (focus mainly on edges)
    float bloodSplatter = blobs * smoothstep(0.1, 0.6, dist) * Intensity * 2.0;

    // Output final color
    vec3 finalColor = mix(texColor.rgb, red, clamp(factor + bloodSplatter, 0.0, 1.0));
    
    fragColor = vec4(finalColor, texColor.a);
}
