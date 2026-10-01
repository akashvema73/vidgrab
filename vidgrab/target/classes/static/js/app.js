(() => {
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];
  const el = {
    url: $('#url'), field: $('#field'), hint: $('#hint'), fetchBtn: $('#fetchBtn'), pasteBtn: $('#pasteBtn'),
    skeleton: $('#skeleton'), result: $('#result'), thumb: $('#thumb'), dur: $('#dur'), ptag: $('#ptag'),
    title: $('#title'), by: $('#by'), dlBtn: $('#dlBtn'), chips: $$('.chip'),
    histList: $('#histList'), histEmpty: $('#histEmpty')
  };
  const HINT = 'Works with public videos, reels, shorts and posts.';
  let quality = 'best';
  let current = '';

  /* helpers */
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const detect = (u) => {
    u = u.toLowerCase();
    if (/(youtube\.com|youtu\.be)/.test(u)) return 'YouTube';
    if (/instagram\.com/.test(u)) return 'Instagram';
    if (/(facebook\.com|fb\.watch|fb\.com)/.test(u)) return 'Facebook';
    return null;
  };
  const fmtDur = (s) => {
    if (!s && s !== 0) return '';
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = Math.floor(s % 60);
    return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(x).padStart(2, '0');
  };
  const ago = (iso) => {
    const d = (Date.now() - new Date(iso).getTime()) / 1000;
    if (d < 60) return 'just now';
    if (d < 3600) return Math.floor(d / 60) + ' min ago';
    if (d < 86400) return Math.floor(d / 3600) + ' hr ago';
    return Math.floor(d / 86400) + ' d ago';
  };
  const toast = (msg, type = 'ok') => {
    const t = document.createElement('div');
    t.className = 'toast ' + type;
    t.innerHTML = '<i class="fa-solid ' + (type === 'ok' ? 'fa-circle-check' : 'fa-circle-exclamation') + '"></i><span>' + esc(msg) + '</span>';
    $('#toasts').appendChild(t);
    setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 300); }, 3800);
  };
  const setHint = (msg, err) => { el.hint.textContent = msg || HINT; el.hint.classList.toggle('err', !!err); el.field.classList.toggle('bad', !!err); };
  const loading = (btn, on) => btn.classList.toggle('loading', on);
  const api = async (path, opts) => {
    const res = await fetch(path, opts);
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || 'Something went wrong. Try again.');
    return data;
  };

  /* platform chips */
  const markPlatform = () => {
    const p = detect(el.url.value);
    el.chips.forEach((c) => c.classList.toggle('on', c.dataset.p === p));
  };
  el.chips.forEach((c) => c.addEventListener('click', () => el.url.focus()));
  el.url.addEventListener('input', () => { markPlatform(); setHint(); });
  el.url.addEventListener('paste', () => setTimeout(() => { markPlatform(); fetchInfo(); }, 60));
  el.url.addEventListener('keydown', (e) => { if (e.key === 'Enter') fetchInfo(); });

  el.pasteBtn.addEventListener('click', async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (!text) return toast('Your clipboard is empty', 'err');
      el.url.value = text.trim();
      markPlatform();
      fetchInfo();
    } catch {
      toast('Allow clipboard access or paste manually', 'err');
      el.url.focus();
    }
  });

  /* fetch info */
  async function fetchInfo() {
    const url = el.url.value.trim();
    if (!url) return setHint('Paste a video link first.', true);
    if (!detect(url)) return setHint('Only YouTube, Instagram and Facebook links are supported.', true);
    if (el.fetchBtn.classList.contains('loading')) return;
    setHint();
    el.result.classList.add('hidden');
    el.skeleton.classList.remove('hidden');
    loading(el.fetchBtn, true);
    try {
      const d = await api('/api/info', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url }) });
      current = url;
      el.title.textContent = d.title;
      el.by.textContent = [d.uploader, d.views != null ? d.views.toLocaleString() + ' views' : null].filter(Boolean).join('  \u2022  ');
      el.ptag.textContent = d.platform;
      el.dur.textContent = fmtDur(d.duration);
      el.dur.classList.toggle('hidden', !d.duration);
      el.thumb.style.visibility = d.thumbnail ? 'visible' : 'hidden';
      el.thumb.src = d.thumbnail || '';
      el.skeleton.classList.add('hidden');
      el.result.classList.remove('hidden');
      el.result.scrollIntoView({ behavior: 'smooth', block: 'center' });
    } catch (e) {
      el.skeleton.classList.add('hidden');
      setHint(e.message, true);
      toast(e.message, 'err');
    } finally {
      loading(el.fetchBtn, false);
    }
  }
  el.fetchBtn.addEventListener('click', fetchInfo);
  el.thumb.addEventListener('error', () => { el.thumb.style.visibility = 'hidden'; });

  /* quality */
  $('#quals').addEventListener('click', (e) => {
    const b = e.target.closest('.q');
    if (!b) return;
    $$('.q').forEach((x) => x.classList.remove('active'));
    b.classList.add('active');
    quality = b.dataset.q;
  });

  /* download */
  el.dlBtn.addEventListener('click', async () => {
    if (!current || el.dlBtn.classList.contains('loading')) return;
    loading(el.dlBtn, true);
    try {
      const res = await fetch('/api/download?url=' + encodeURIComponent(current) + '&quality=' + quality);
      if (!res.ok) {
        const d = await res.json().catch(() => ({}));
        throw new Error(d.error || 'Download failed. Try again.');
      }
      const cd = res.headers.get('Content-Disposition') || '';
      let name = 'video';
      const m1 = cd.match(/filename\*=UTF-8''([^;]+)/i), m2 = cd.match(/filename="?([^";]+)"?/i);
      if (m1) name = decodeURIComponent(m1[1]); else if (m2) name = m2[1];
      const blob = await res.blob();
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = name;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 4000);
      toast('Download started');
    } catch (e) {
      toast(e.message, 'err');
    } finally {
      loading(el.dlBtn, false);
      loadHistory();
      loadStats();
    }
  });

  /* history */
  async function loadHistory() {
    try {
      const list = await api('/api/history?limit=8');
      el.histEmpty.classList.toggle('hidden', list.length > 0);
      el.histList.innerHTML = list.map((h) => (
        '<li class="hi">' +
        (h.thumbnail ? '<img src="' + esc(h.thumbnail) + '" alt="" referrerpolicy="no-referrer" onerror="this.className=\'ph\';this.removeAttribute(\'src\')">' : '<div class="ph"></div>') +
        '<div class="info"><div class="t">' + esc(h.title || h.url) + '</div>' +
        '<div class="s"><span>' + esc(h.platform) + '</span><span>' + esc(h.quality === 'audio' ? 'Audio' : h.quality === 'best' ? 'Best' : h.quality + 'p') + '</span><span>' + ago(h.createdAt) + '</span>' +
        '<span class="pill ' + (h.status === 'SUCCESS' ? 'ok' : 'fail') + '">' + (h.status === 'SUCCESS' ? 'Done' : 'Failed') + '</span></div></div>' +
        '<button class="ghost again" data-url="' + esc(h.url) + '" type="button"><i class="fa-solid fa-rotate-right"></i><span>Again</span></button></li>'
      )).join('');
    } catch { /* history is optional */ }
  }
  el.histList.addEventListener('click', (e) => {
    const b = e.target.closest('.again');
    if (!b) return;
    el.url.value = b.dataset.url;
    markPlatform();
    window.scrollTo({ top: 0, behavior: 'smooth' });
    fetchInfo();
  });

  /* stats */
  function count(node, to) {
    const start = performance.now(), dur = 1100;
    const step = (t) => {
      const p = Math.min((t - start) / dur, 1);
      node.textContent = Math.round(to * (1 - Math.pow(1 - p, 3))).toLocaleString();
      if (p < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }
  async function loadStats() {
    try {
      const s = await api('/api/stats');
      count($('#sTotal'), s.total);
      count($('#sPlat'), s.platforms);
    } catch { /* keep defaults */ }
  }

  /* feedback */
  $('#fbForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = $('#fbBtn');
    if (btn.classList.contains('loading')) return;
    loading(btn, true);
    try {
      const d = await api('/api/feedback', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: $('#fbName').value, email: $('#fbEmail').value, message: $('#fbMsg').value })
      });
      toast(d.message);
      e.target.reset();
    } catch (err) {
      toast(err.message, 'err');
    } finally {
      loading(btn, false);
    }
  });

  /* scroll reveal */
  const io = new IntersectionObserver((entries) => {
    entries.forEach((en) => { if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); } });
  }, { threshold: 0.12 });
  $$('.reveal').forEach((n) => io.observe(n));

  loadHistory();
  loadStats();
})();
