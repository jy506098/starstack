// Mouse-trail effect (canvas-rendered particles following the cursor).
// Honors user config from window.STARSTACK_MOUSE_CONFIG (set by template).
const COLORS = {
    cyan: ['#00ffff', '#7fffff', '#bfffff'],
    rainbow: ['#ff4dd2', '#7c4dff', '#00ffff', '#4dff88', '#ffd700'],
    random: ['#ff4dd2', '#7c4dff', '#00ffff', '#4dff88', '#ffd700', '#ff8800'],
};
function pickColor(mode) {
    const pool = COLORS[mode] ?? COLORS.cyan;
    return pool[Math.floor(Math.random() * pool.length)];
}
export function initMouseTrail() {
    const canvas = document.getElementById('mouseTrailCanvas');
    if (!canvas)
        return;
    const cfg = window.STARSTACK_MOUSE_CONFIG ?? { enabled: true, color_mode: 'cyan', shape: 'circle' };
    if (!cfg.enabled) {
        canvas.classList.add('hidden');
        return;
    }
    canvas.classList.remove('hidden');
    const ctx = canvas.getContext('2d');
    if (!ctx)
        return;
    const resize = () => {
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
    };
    resize();
    window.addEventListener('resize', resize);
    const particles = [];
    let lastX = 0;
    let lastY = 0;
    let mouseInside = false;
    document.addEventListener('mousemove', (e) => {
        lastX = e.clientX;
        lastY = e.clientY;
        mouseInside = true;
        particles.push({
            x: lastX,
            y: lastY,
            vx: (Math.random() - 0.5) * 2,
            vy: (Math.random() - 0.5) * 2,
            life: 1.0,
            color: pickColor(cfg.color_mode),
            size: 4 + Math.random() * 4,
        });
    });
    document.addEventListener('mouseleave', () => { mouseInside = false; });
    const draw = () => {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        for (let i = particles.length - 1; i >= 0; i--) {
            const p = particles[i];
            p.life -= 0.02;
            p.x += p.vx;
            p.y += p.vy;
            if (p.life <= 0) {
                particles.splice(i, 1);
                continue;
            }
            ctx.globalAlpha = p.life;
            ctx.fillStyle = p.color;
            ctx.shadowColor = p.color;
            ctx.shadowBlur = 8;
            const s = p.size * p.life;
            if (cfg.shape === 'square') {
                ctx.fillRect(p.x - s / 2, p.y - s / 2, s, s);
            }
            else if (cfg.shape === 'star') {
                drawStar(ctx, p.x, p.y, 5, s, s / 2);
            }
            else {
                ctx.beginPath();
                ctx.arc(p.x, p.y, s / 2, 0, Math.PI * 2);
                ctx.fill();
            }
        }
        ctx.globalAlpha = 1.0;
        ctx.shadowBlur = 0;
        requestAnimationFrame(draw);
    };
    requestAnimationFrame(draw);
    // silence unused-var warning
    void lastX;
    void lastY;
    void mouseInside;
}
function drawStar(ctx, x, y, spikes, outer, inner) {
    let rot = Math.PI / 2 * 3;
    const step = Math.PI / spikes;
    ctx.beginPath();
    ctx.moveTo(x, y - outer);
    for (let i = 0; i < spikes; i++) {
        ctx.lineTo(x + Math.cos(rot) * outer, y + Math.sin(rot) * outer);
        rot += step;
        ctx.lineTo(x + Math.cos(rot) * inner, y + Math.sin(rot) * inner);
        rot += step;
    }
    ctx.lineTo(x, y - outer);
    ctx.closePath();
    ctx.fill();
}
//# sourceMappingURL=index.js.map