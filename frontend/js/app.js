// ── State ──────────────────────────────────────────────────────────────
const state = {
  q: '',
  beverageType: '',
  status: '',
  page: 0,
  size: 24,
  loading: false,
};

// ── API ────────────────────────────────────────────────────────────────
const API = '/api';

async function fetchColas() {
  const params = new URLSearchParams({ q: state.q, page: state.page, size: state.size });
  if (state.beverageType) params.set('beverageType', state.beverageType);
  if (state.status) params.set('status', state.status);

  const res = await fetch(`${API}/colas?${params}`);
  if (!res.ok) throw new Error(`Search failed (${res.status})`);
  return res.json();
}

async function fetchBeverageTypes() {
  const res = await fetch(`${API}/colas/filters/beverage-types`);
  return res.ok ? res.json() : [];
}

async function fetchDetail(ttbId) {
  const res = await fetch(`${API}/colas/${encodeURIComponent(ttbId)}`);
  return res.ok ? res.json() : null;
}

async function triggerIngest() {
  const res = await fetch(`${API}/admin/ingest`, { method: 'POST' });
  if (!res.ok) throw new Error(`Ingest failed (${res.status})`);
  return res.json();
}

// ── Render ─────────────────────────────────────────────────────────────
function renderCard(cola) {
  const card = document.createElement('article');
  card.className = 'cola-card';
  card.setAttribute('tabindex', '0');
  card.setAttribute('role', 'button');
  card.setAttribute('aria-label', `View details for ${cola.brandName || cola.ttbId}`);

  const imgWrap = document.createElement('div');
  imgWrap.className = 'label-img-wrap';

  const img = document.createElement('img');
  img.className = 'label-img';
  img.alt = cola.brandName || 'Label image';
  img.loading = 'lazy';
  img.src = cola.labelImageUrl;
  img.onerror = () => { img.classList.add('img-error'); img.src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg"/>'; };

  imgWrap.appendChild(img);

  const statusClass = (() => {
    const s = (cola.status || '').toLowerCase();
    if (s === 'approved') return 'status--approved';
    if (s === 'deleted') return 'status--deleted';
    return 'status--other';
  })();

  const info = document.createElement('div');
  info.className = 'card-info';
  info.innerHTML = `
    <span class="brand-name">${esc(cola.brandName || '—')}</span>
    ${cola.fancifulName ? `<span class="fanciful">${esc(cola.fancifulName)}</span>` : ''}
    ${cola.classType ? `<span class="class-type">${esc(cola.classType)}</span>` : ''}
    <span class="applicant">${esc(cola.applicantName || '')}</span>
    <span class="date">${cola.approvalDate || ''}</span>
    ${cola.status ? `<span class="status ${statusClass}">${esc(cola.status)}</span>` : ''}
  `;

  card.appendChild(imgWrap);
  card.appendChild(info);

  const openDetail = () => openModal(cola.ttbId);
  card.addEventListener('click', openDetail);
  card.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') openDetail(); });

  return card;
}

function renderGrid(data) {
  const grid = document.getElementById('cardGrid');
  grid.innerHTML = '';

  if (!data.content.length) {
    grid.innerHTML = '<p class="empty-state">No labels found. Try a different search.</p>';
    document.getElementById('resultsMeta').textContent = '';
    return;
  }

  const frag = document.createDocumentFragment();
  data.content.forEach(c => frag.appendChild(renderCard(c)));
  grid.appendChild(frag);

  document.getElementById('resultsMeta').textContent =
    `${data.totalElements.toLocaleString()} result${data.totalElements !== 1 ? 's' : ''}` +
    (data.totalPages > 1 ? `  ·  page ${data.page + 1} of ${data.totalPages}` : '');
}

function renderPagination(data) {
  const pg = document.getElementById('pagination');
  pg.innerHTML = '';
  if (data.totalPages <= 1) return;

  if (data.page > 0) pg.appendChild(mkBtn('← Previous', () => changePage(data.page - 1)));

  const info = document.createElement('span');
  info.textContent = `${data.page + 1} / ${data.totalPages}`;
  pg.appendChild(info);

  if (data.hasNext) pg.appendChild(mkBtn('Next →', () => changePage(data.page + 1)));
}

function mkBtn(label, handler) {
  const b = document.createElement('button');
  b.textContent = label;
  b.addEventListener('click', handler);
  return b;
}

async function openModal(ttbId) {
  const detail = await fetchDetail(ttbId);
  if (!detail) return;

  const body = document.getElementById('modalBody');
  body.innerHTML = `
    <img class="modal-label-img" src="${detail.labelImageUrl}"
         alt="${esc(detail.brandName || '')}"
         onerror="this.classList.add('img-error')">
    <dl>
      <dt>TTB ID</dt>       <dd>${esc(detail.ttbId)}</dd>
      <dt>Brand</dt>        <dd>${esc(detail.brandName || '—')}</dd>
      <dt>Fanciful Name</dt><dd>${esc(detail.fancifulName || '—')}</dd>
      <dt>Class / Type</dt> <dd>${esc(detail.classType || '—')}</dd>
      <dt>Permit Holder</dt><dd>${esc(detail.applicantName || '—')}</dd>
      <dt>Approval Date</dt><dd>${detail.approvalDate || '—'}</dd>
      <dt>Status</dt>       <dd>${esc(detail.status || '—')}</dd>
      <dt>Beverage Type</dt><dd>${esc(detail.beverageType || '—')}</dd>
    </dl>
  `;
  document.getElementById('modal').classList.remove('hidden');
  document.getElementById('modalClose').focus();
}

function closeModal() {
  document.getElementById('modal').classList.add('hidden');
}

function esc(str) {
  return String(str).replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

// ── Search control ─────────────────────────────────────────────────────
async function doSearch() {
  if (state.loading) return;
  state.loading = true;
  const grid = document.getElementById('cardGrid');
  grid.style.opacity = '0.5';

  try {
    const data = await fetchColas();
    renderGrid(data);
    renderPagination(data);
  } catch (e) {
    grid.innerHTML = `<p class="error-state">Search error: ${esc(e.message)}</p>`;
  } finally {
    state.loading = false;
    grid.style.opacity = '1';
  }
}

function changePage(newPage) {
  state.page = newPage;
  doSearch();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function debounce(fn, ms) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}

// ── Bootstrap ──────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {

  // Populate beverage type dropdown
  try {
    const types = await fetchBeverageTypes();
    const sel = document.getElementById('beverageFilter');
    types.forEach(t => {
      const opt = document.createElement('option');
      opt.value = t;
      opt.textContent = t;
      sel.appendChild(opt);
    });
  } catch (_) { /* non-fatal */ }

  // Search input — debounced 350ms
  document.getElementById('searchInput').addEventListener('input', debounce(e => {
    state.q = e.target.value.trim();
    state.page = 0;
    doSearch();
  }, 350));

  // Beverage type filter
  document.getElementById('beverageFilter').addEventListener('change', e => {
    state.beverageType = e.target.value;
    state.page = 0;
    doSearch();
  });

  // Sync button
  const ingestBtn = document.getElementById('ingestBtn');
  ingestBtn.addEventListener('click', async () => {
    ingestBtn.disabled = true;
    ingestBtn.textContent = 'Syncing…';
    try {
      const result = await triggerIngest();
      alert(`Sync complete: ${result.recordsProcessed.toLocaleString()} records ingested.`);
      state.page = 0;
      doSearch();
    } catch (e) {
      alert(`Sync failed: ${e.message}`);
    } finally {
      ingestBtn.disabled = false;
      ingestBtn.textContent = 'Sync Data';
    }
  });

  // Modal close
  document.getElementById('modalClose').addEventListener('click', closeModal);
  document.querySelector('.modal-backdrop').addEventListener('click', closeModal);
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });

  // Initial search
  doSearch();
});
