/* ===========================================
   设置页：头像、密码、鼠标配置
   =========================================== */

(function () {
    'use strict';

    // ---------- 头像预览 ----------
    function renderAvatar() {
        const preview = document.getElementById('avatarPreview');
        const valueEl = document.getElementById('avatarValue');
        if (!preview || !valueEl) return;
        const v = valueEl.textContent.trim();

        if (v.startsWith('/static/')) {
            preview.style.background = `url(${v}) center/cover no-repeat`;
            preview.textContent = '';
        } else {
            // 默认（包括 default:none、空值）
            preview.style.background = "url('/static/avatars/default.svg') center/cover no-repeat, #0f1535";
            preview.textContent = '';
        }
    }
    renderAvatar();

    // ---------- 头像上传 ----------
    const uploadForm = document.getElementById('avatarUploadForm');
    if (uploadForm) {
        uploadForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const fd = new FormData(uploadForm);
            try {
                const r = await fetch('/settings/avatar', { method: 'POST', body: fd });
                const data = await r.json();
                if (data.success) {
                    document.getElementById('avatarValue').textContent = data.avatar_url;
                    renderAvatar();
                    showFlash('success', '头像上传成功');
                } else {
                    showFlash('error', data.msg || '上传失败');
                }
            } catch (err) {
                showFlash('error', '网络错误：' + err.message);
            }
        });
    }

    // ---------- 恢复默认头像 ----------
    const resetForm = document.getElementById('avatarResetForm');
    if (resetForm) {
        resetForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            try {
                const r = await fetch('/settings/avatar', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: 'action=reset',
                });
                const data = await r.json();
                if (data.success) {
                    document.getElementById('avatarValue').textContent = 'default:none';
                    renderAvatar();
                    showFlash('success', '已恢复默认头像');
                } else {
                    showFlash('error', data.msg || '重置失败');
                }
            } catch (err) {
                showFlash('error', '网络错误：' + err.message);
            }
        });
    }

    // ---------- 鼠标配置 ----------
    fetch('/get_mouse_config')
        .then(r => r.json())
        .then(c => {
            if (c && !c.error) {
                if (document.getElementById('mouseEnabled')) document.getElementById('mouseEnabled').checked = !!c.enabled;
                if (document.getElementById('colorMode')) document.getElementById('colorMode').value = c.color_mode;
                if (document.getElementById('shape')) document.getElementById('shape').value = c.shape;
                if (window.updateMouseConfig) window.updateMouseConfig(c);
            }
        }).catch(() => {});

    const saveBtn = document.getElementById('saveMouseConfig');
    if (saveBtn) {
        saveBtn.addEventListener('click', async () => {
            const cfg = {
                enabled: document.getElementById('mouseEnabled').checked,
                color_mode: document.getElementById('colorMode').value,
                shape: document.getElementById('shape').value,
            };
            try {
                const r = await fetch('/save_mouse_config', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(cfg),
                });
                const data = await r.json();
                if (data.success) {
                    if (window.updateMouseConfig) window.updateMouseConfig(cfg);
                    showFlash('success', '鼠标配置已保存');
                } else {
                    showFlash('error', data.msg || '保存失败');
                }
            } catch (err) {
                showFlash('error', '网络错误：' + err.message);
            }
        });
    }

    // ---------- 复制 MC 服务器地址 ----------
    const copyMcBtn = document.getElementById('copyMcServerBtn');
    if (copyMcBtn) {
        copyMcBtn.addEventListener('click', async () => {
            const host = (document.getElementById('mcServerHost') || {}).textContent || '';
            const port = (document.getElementById('mcServerPort') || {}).textContent || '';
            const addr = `${host}:${port}`;
            const original = copyMcBtn.textContent;
            try {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    await navigator.clipboard.writeText(addr);
                } else {
                    const ta = document.createElement('textarea');
                    ta.value = addr;
                    document.body.appendChild(ta);
                    ta.select();
                    document.execCommand('copy');
                    ta.remove();
                }
                copyMcBtn.textContent = '✓ 已复制';
            } catch (err) {
                copyMcBtn.textContent = '复制失败';
            }
            setTimeout(() => { copyMcBtn.textContent = original; }, 2000);
        });
    }

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
        main.prepend(area);
        return area;
    }
})();