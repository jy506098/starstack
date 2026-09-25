/* ===========================================
   VIP 支付页：5 秒倒计时 + "我已支付" 按钮
   =========================================== */
(function () {
    'use strict';

    const confirmBtn = document.getElementById('confirmPayBtn');
    const countdownEl = document.getElementById('countdown');
    if (!confirmBtn) return;
    const orderId = confirmBtn.dataset.orderId;

    let countdown = 5;
    confirmBtn.disabled = true;
    confirmBtn.textContent = `等待支付确认 (${countdown}s)`;
    const tick = setInterval(() => {
        countdown -= 1;
        if (countdownEl) countdownEl.textContent = String(Math.max(countdown, 0));
        if (countdown <= 0) {
            clearInterval(tick);
            confirmBtn.disabled = false;
            confirmBtn.textContent = '我已支付';
        } else {
            confirmBtn.textContent = `等待支付确认 (${countdown}s)`;
        }
    }, 1000);

    confirmBtn.addEventListener('click', async () => {
        confirmBtn.disabled = true;
        const oldText = confirmBtn.textContent;
        confirmBtn.textContent = '确认中...';
        try {
            const r = await fetch(`/vip_pay_confirm/${orderId}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
            });
            const data = await r.json();
            if (data.success && data.redirect) {
                location.href = data.redirect;
            } else {
                showFlash('error', data.msg || '开通失败');
                confirmBtn.disabled = false;
                confirmBtn.textContent = oldText;
            }
        } catch (err) {
            showFlash('error', '网络错误：' + err.message);
            confirmBtn.disabled = false;
            confirmBtn.textContent = oldText;
        }
    });

    function showFlash(type, msg) {
        const area = document.querySelector('.flash-area') || createFlashArea();
        const div = document.createElement('div');
        div.className = `flash flash-${type}`;
        div.textContent = msg;
        area.appendChild(div);
        setTimeout(() => { div.style.opacity = '0'; setTimeout(() => div.remove(), 500); }, 3000);
    }
    function createFlashArea() {
        const main = document.querySelector('main.container');
        const area = document.createElement('div');
        area.className = 'flash-area';
        if (main) main.prepend(area);
        return area;
    }
})();
