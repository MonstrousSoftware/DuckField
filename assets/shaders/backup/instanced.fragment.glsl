#version 300 es


#ifdef GL_ES
precision mediump float;
#endif

// very basic instanced rendering shader, no lighting, no shadows, etc.


out vec4 FragColor;


void main () {

    FragColor = vec4(0,0,1,1);
}
