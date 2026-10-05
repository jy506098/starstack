// mouse-trail/index.ts — particle trail that follows the mouse.
// Reads config from /get_mouse_config JSON.
const DEFAULT = { enabled: true, color_mode: 'rainbow', shape: 'circle' };
function rand(min, max) {
    return Math.random() * (max - min) + min;
}
function pickColor(config, hue) {
    if (config.color_mode === 'cyan')
        return `rgba(102, 252, 241, ${rand(0.4, 0.9)})`;
    if (config.color_mode === 'random')
        return `hsl(${rand(0, 360)}, 80%, 60%)`;
    return `hsl(${hue}, 90%, 60%)`;
}
function drawShape(ctx, x, y, size, color, shape) {
    ctx.fillStyle = color;
    if (shape === 'square') {
        ctx.fillRect(x - size / 2, y - size / 2, size, size);
    }
    else if (shape === 'star') {
        ctx.beginPath();
        for (let i = 0; i < 5; i++) {
            const a = (i * 2 * Math.PI / 5) - Math.PI / 2;
            const r = i % 2 === 0 ? size : size / 2;
            ctx.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
        }
        ctx.closePath();
        ctx.fill();
    }
    else {
        ctx.beginPath();
        ctx.arc(x, y, size / 2, 0, Math.PI * 2);
        ctx.fill();
    }
}
export function initMouseTrail(canvas, initConfig) {
    const ctx = canvas.getContext('2d');
    if (!ctx)
        return;
    let config = { ...DEFAULT, ...(initConfig ?? {}) };
    let hue = 0;
    function resize() {
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
    }
    resize();
    window.addEventListener('resize', resize);
    const particles = [];
    const MAX = 60;
    window.addEventListener('mousemove', (e) => {
        if (!config.enabled)
            return;
        particles.push({
            x: e.clientX,
            y: e.clientY,
            size: rand(8, 18),
            life: 1,
            color: pickColor(config, hue),
        });
        if (particles.length > MAX)
            particles.shift();
    });
    function render() {
        if (!ctx)
            return;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        hue = (hue + 2) % 360;
        for (let i = particles.length - 1; i >= 0; i--) {
            const p = particles[i];
            p.life -= 0.03;
            if (p.life <= 0) {
                particles.splice(i, 1);
                continue;
            }
            if (!ctx)
                return;
            ctx.globalAlpha = p.life;
            drawShape(ctx, p.x, p.y, p.size * p.life, p.color, config.shape);
        }
        ctx.globalAlpha = 1;
        requestAnimationFrame(render);
    }
    render();
    window.updateMouseConfig = (newCfg) => {
        config = { ...config, ...newCfg };
    };
}
//# sourceMappingURL=index.js.map