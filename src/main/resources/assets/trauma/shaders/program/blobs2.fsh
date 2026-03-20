#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform float Radius;
uniform float Time;

out vec4 fragColor;

void main(){
    vec4 c  = texture(DiffuseSampler, texCoord);
    vec4 maxVal = c;
    
    // Use Radius directly (pulsation is now controlled via Java)
    float currentRadius = Radius;
    
    // Constant loop bound needed for Some old GL specs
    for(float u = 0.0; u <= 15.0; u += 1.0) {
        if (u > currentRadius) break;
        for(float v = 0.0; v <= 15.0; v += 1.0) {
            if (v > currentRadius) break;
            
            float weight = (((sqrt(u * u + v * v) / (currentRadius)) > 1.0) ? 0.0 : 1.0);

            vec4 s0 = texture(DiffuseSampler, texCoord + vec2(-u * oneTexel.x, -v * oneTexel.y));
            vec4 s1 = texture(DiffuseSampler, texCoord + vec2( u * oneTexel.x,  v * oneTexel.y));
            vec4 s2 = texture(DiffuseSampler, texCoord + vec2(-u * oneTexel.x,  v * oneTexel.y));
            vec4 s3 = texture(DiffuseSampler, texCoord + vec2( u * oneTexel.x, -v * oneTexel.y));

            vec4 o0 = max(s0, s1);
            vec4 o1 = max(s2, s3);
            vec4 tempMax = max(o0, o1);
            maxVal = mix(maxVal, max(maxVal, tempMax), weight);
        }
    }

    fragColor = vec4(maxVal.rgb, 1.0);
}
