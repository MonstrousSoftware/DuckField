#ifdef GL_ES
precision mediump float;
#endif

uniform vec3 u_color;

void main() {
    vec4 col = vec4(u_color, 1.0);
    //col.g = 0.5;
    gl_FragColor = col;
}
