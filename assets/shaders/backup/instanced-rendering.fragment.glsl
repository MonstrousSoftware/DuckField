


precision mediump float;


// very basic instanced rendering shader, no lighting, no shadows, etc.


uniform sampler2D u_diffuseTexture;

in vec2 v_diffuseUV;

out vec4 FragColor;


void main () {

    FragColor = texture(u_diffuseTexture, v_diffuseUV);
}
