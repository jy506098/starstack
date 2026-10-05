/* ===========================================
   积分充值：套餐购买 + 微信支付倒计时
   =========================================== */

(function () {
    'use strict';

    // ---------- 套餐购买按钮 ----------
    document.querySelectorAll('.recharge-buy-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const pkgKey = btn.dataset.config || btn.dataset.packageKey;
            if (!pkgKey) return;
            btn.disabled = true;
            const original = btn.textContent;
            btn.textContent = '下单中...';
            try {
                const r = await fetch('/buy_recharge', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ package_key: pkgKey }),
                });
                const data = await r.json();
                if (data.success && data.redirect) {
                    window.location.href = data.redirect;
                } else {
                    alert(data.msg || '下单失败');
                    btn.disabled = false;
                    btn.textContent = original;
                }
            } catch (err) {
                alert('网络错误：' + err.message);
                btn.disabled = false;
                btn.textContent = original;
            }
        });
    });

    // ---------- 微信支付倒计时 ----------
    const confirmBtn = document.getElementById('confirmPayBtn');
    const countdownEl = document.getElementById('countdown');
    if (confirmBtn && countdownEl) {
        const orderId = confirmBtn.dataset.orderId;
        let seconds = 5;
        confirmBtn.disabled = true;

        const tick = setInterval(() => {
            seconds -= 1;
            countdownEl.textContent = seconds;
            confirmBtn.textContent = `等待支付确认 (${seconds}s)`;
            if (seconds <= 0) {
                clearInterval(tick);
                confirmBtn.disabled = false;
                confirmBtn.textContent = '✓ 我已支付';
                confirmBtn.classList.remove('btn-vip');
                confirmBtn.classList.add('btn-primary');
                confirmBtn.addEventListener('click', async () => {
                    confirmBtn.disabled = true;
                    confirmBtn.textContent = '确认中...';
                    try {
                        const r = await fetch(`/recharge_pay_confirm/${orderId}`, { method: 'POST' });
                        const data = await r.json();
                        if (data.success && data.redirect) {
                            window.location.href = data.redirect;
                        } else {
                            alert(data.msg || '确认失败');
                            confirmBtn.disabled = false;
                            confirmBtn.textContent = '✓ 我已支付';
                        }
                    } catch (err) {
                        alert('网络错误：' + err.message);
                        confirmBtn.disabled = false;
                        confirmBtn.textContent = '✓ 我已支付';
                    }
                });
            }
        }, 1000);
    }
})();