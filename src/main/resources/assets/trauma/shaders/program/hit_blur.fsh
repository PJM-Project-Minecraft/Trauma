#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform float Radius;

out vec4 fragColor;

void main(){
    vec4 color = vec4(0.0);
    float totalWeight = 0.0;
    
    // Convert to int for looping
    int intRadius = int(Radius);
    
    if (intRadius <= 0) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }

    // Box blur with simple falloff
    for(int u = -15; u <= 15; u++) {
        if (u > intRadius) break;
        if (u < -intRadius) continue;
        
        for(int v = -15; v <= 15; v++) {
            if (v > intRadius) break;
            if (v < -intRadius) continue;
            
            float dist = sqrt(float(u * u + v * v));
            if (dist > Radius) continue;
            
            float weight = max(0.0, 1.0 - (dist / Radius));
            
            vec4 samp = texture(DiffuseSampler, texCoord + vec2(float(u) * oneTexel.x, float(v) * oneTexel.y));
            color += samp * weight;
            totalWeight += weight;
        }
    }

    if (totalWeight > 0.0) {
        fragColor = vec4((color / totalWeight).rgb, 1.0);
    } else {
        fragColor = texture(DiffuseSampler, texCoord);
    }
}
