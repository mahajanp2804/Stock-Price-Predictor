'use strict';

// ------------------------------------------------------------------
// State and API
// ------------------------------------------------------------------

const state = {
  token: sessionStorage.getItem('token'),
  watchlist: [],
  selected: null,        // symbol shown in the chart
  period: '3M',
};

const $ = (id) => document.getElementById(id);
const money = (v) => v == null ? '—' : '$' + Number(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const pct = (v) => v == null ? 'n/a' : (v >= 0 ? '+' : '') + Number(v).toFixed(2) + '%';

/** Calls the JSON API; throws Error(message) for non-2xx responses (401 returns to login). */
async function api(path, options = {}) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}) };
  if (state.token) headers.Authorization = 'Bearer ' + state.token;
  const res = await fetch(path, { ...options, headers, body: options.body ? JSON.stringify(options.body) : undefined });
  if (res.status === 401 && !path.startsWith('/api/auth/')) { showLogin('Your session has ended. Please log in again.'); throw new Error('Please log in'); }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const err = new Error((data && (data.error || data.message)) || res.statusText);
    err.data = data;
    throw err;
  }
  return data;
}

function setMsg(el, text, kind) {
  el.textContent = text || '';
  el.className = 'msg' + (kind ? ' ' + kind : '');
}

/** Creates an element with optional text and class. All API text goes through textContent. */
function el(tag, text, className) {
  const e = document.createElement(tag);
  if (text != null) e.textContent = text;
  if (className) e.className = className;
  return e;
}

function row(cells) {
  const tr = document.createElement('tr');
  for (const c of cells) {
    const td = el('td', c.text, c.cls);
    tr.appendChild(td);
  }
  return tr;
}

function emptyRow(tbody, cols, text) {
  tbody.replaceChildren();
  const td = el('td', text, 'empty');
  td.colSpan = cols;
  const tr = document.createElement('tr');
  tr.appendChild(td);
  tbody.appendChild(tr);
}

// ------------------------------------------------------------------
// Login / register
// ------------------------------------------------------------------

function showLogin(message) {
  state.token = null;
  state.watchlist = [];          // don't show one user's data to the next
  state.selected = null;
  sessionStorage.removeItem('token');
  $('app-view').hidden = true;
  $('auth-view').hidden = false;
  setMsg($('auth-msg'), message || '', message ? 'error' : '');
}

async function login(username, password) {
  const res = await api('/api/auth/login', { method: 'POST', body: { username, password } });
  state.token = res.token;
  sessionStorage.setItem('token', res.token);
  await startApp();
}

$('auth-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const btn = $('login-btn');
  btn.disabled = true;
  setMsg($('auth-msg'), 'Signing in…');
  try { await login($('auth-user').value.trim(), $('auth-pass').value); }
  catch (err) { setMsg($('auth-msg'), err.message, 'error'); }
  finally { btn.disabled = false; }
});

$('register-btn').addEventListener('click', async () => {
  const username = $('auth-user').value.trim(), password = $('auth-pass').value;
  if (!username || password.length < 6) { setMsg($('auth-msg'), 'Enter a username and a password of at least 6 characters.', 'error'); return; }
  setMsg($('auth-msg'), 'Creating account…');
  try {
    await api('/api/auth/register', { method: 'POST', body: { username, password } });
    await login(username, password);
  } catch (err) { setMsg($('auth-msg'), err.message, 'error'); }
});

$('logout-btn').addEventListener('click', async () => {
  try { await api('/api/auth/logout', { method: 'POST' }); } catch (_) { /* already logged out */ }
  showLogin();
});

// ------------------------------------------------------------------
// App shell
// ------------------------------------------------------------------

async function startApp() {
  for (const id of ['watch-msg', 'trade-msg', 'predict-msg']) setMsg($(id), '');
  for (const id of ['portfolio-table', 'tx-table', 'watch-table']) $(id).querySelector('tbody').replaceChildren();
  $('predict-out').hidden = true;
  $('search-results').hidden = true;
  $('auth-view').hidden = true;
  $('app-view').hidden = false;
  await Promise.all([refreshMe(), loadWatchlist()]);
}

async function refreshMe() {
  const me = await api('/api/me');
  $('who-name').textContent = me.username;
  $('who-balance').textContent = money(me.balance);
}

document.querySelectorAll('.tabs button').forEach((b) => b.addEventListener('click', () => {
  document.querySelectorAll('.tabs button').forEach((x) => x.classList.toggle('active', x === b));
  document.querySelectorAll('.tab').forEach((t) => { t.hidden = t.id !== 'tab-' + b.dataset.tab; });
  if (b.dataset.tab === 'portfolio') loadPortfolio();
  if (b.dataset.tab === 'transactions') loadTransactions();
}));

// ------------------------------------------------------------------
// Watchlist, search, chart
// ------------------------------------------------------------------

async function loadWatchlist() {
  const tbody = $('watch-table').querySelector('tbody');
  if (!state.watchlist.length) emptyRow(tbody, 6, 'Loading prices…');
  try {
    state.watchlist = await api('/api/watchlist');
  } catch (err) { setMsg($('watch-msg'), err.message, 'error'); return; }
  renderWatchlist();
  fillSymbolSelects();
  if (!state.watchlist.some((w) => w.symbol === state.selected)) {
    const first = state.watchlist.find((w) => w.available);
    selectStock(first ? first.symbol : null);
  }
}

function renderWatchlist() {
  const tbody = $('watch-table').querySelector('tbody');
  if (!state.watchlist.length) { emptyRow(tbody, 6, 'Your watchlist is empty. Search above to add a stock.'); return; }
  tbody.replaceChildren();
  for (const w of state.watchlist) {
    const tr = row([
      { text: w.symbol }, { text: w.name },
      { text: money(w.price), cls: 'num' },
      { text: pct(w.change30d), cls: 'num ' + (w.change30d == null ? '' : w.change30d >= 0 ? 'gain' : 'loss') },
      { text: !w.available ? 'No data' : w.liveAt ? 'Live ' + new Date(w.liveAt).toLocaleTimeString() : 'Last close' },
    ]);
    const td = document.createElement('td');
    const remove = el('button', 'Remove', 'link');
    remove.addEventListener('click', (e) => { e.stopPropagation(); removeStock(w.symbol); });
    td.appendChild(remove);
    tr.appendChild(td);
    tr.classList.toggle('selected', w.symbol === state.selected);
    if (w.available) tr.addEventListener('click', () => selectStock(w.symbol));
    tbody.appendChild(tr);
  }
}

async function removeStock(symbol) {
  setMsg($('watch-msg'), 'Removing ' + symbol + '…');
  try {
    await api('/api/watchlist/' + encodeURIComponent(symbol), { method: 'DELETE' });
    setMsg($('watch-msg'), symbol + ' removed.', 'ok');
    await loadWatchlist();
  } catch (err) { setMsg($('watch-msg'), err.message, 'error'); }
}

$('search-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const q = $('search-q').value.trim();
  if (!q) return;
  const list = $('search-results');
  setMsg($('watch-msg'), 'Searching…');
  try {
    const results = await api('/api/search?q=' + encodeURIComponent(q));
    list.replaceChildren();
    for (const r of results) {
      const li = document.createElement('li');
      const label = el('span');
      label.append(el('span', r.symbol, 'sym'), document.createTextNode(r.companyName));
      const add = el('button', 'Add');
      add.addEventListener('click', () => addStock(r.symbol, r.companyName, add));
      li.append(label, add);
      list.appendChild(li);
    }
    list.hidden = results.length === 0;
    setMsg($('watch-msg'), results.length ? '' : 'No US stocks or ETFs match "' + q + '".');
  } catch (err) { setMsg($('watch-msg'), err.message, 'error'); }
});

async function addStock(symbol, name, button) {
  button.disabled = true;
  setMsg($('watch-msg'), 'Adding ' + symbol + ' and loading its prices…');
  try {
    await api('/api/watchlist', { method: 'POST', body: { symbol, name } });
    $('search-results').hidden = true;
    $('search-q').value = '';
    setMsg($('watch-msg'), symbol + ' added.', 'ok');
    state.selected = symbol;
    await loadWatchlist();
    selectStock(symbol);
  } catch (err) { setMsg($('watch-msg'), err.message, 'error'); button.disabled = false; }
}

function fillSymbolSelects() {
  for (const id of ['trade-symbol', 'predict-symbol']) {
    const select = $(id), current = select.value;
    select.replaceChildren();
    for (const w of state.watchlist.filter((x) => x.available)) {
      const o = el('option', w.symbol + ' — ' + w.name);
      o.value = w.symbol;
      select.appendChild(o);
    }
    if (current) select.value = current;
  }
}

document.querySelectorAll('#period-buttons button').forEach((b) => b.addEventListener('click', () => {
  document.querySelectorAll('#period-buttons button').forEach((x) => x.classList.toggle('active', x === b));
  state.period = b.dataset.period;
  drawChart();
}));
$('show-forecast').addEventListener('change', drawChart);

function selectStock(symbol) {
  state.selected = symbol;
  document.querySelectorAll('#watch-table tbody tr').forEach((tr) => tr.classList.toggle('selected', tr.firstChild && tr.firstChild.textContent === symbol));
  drawChart();
}

async function drawChart() {
  const box = $('chart'), symbol = state.selected;
  if (!symbol) { box.replaceChildren(el('div', 'Select a stock to see its chart.', 'empty')); $('chart-title').textContent = 'Chart'; return; }
  try {
    const [history, forecast] = await Promise.all([
      api('/api/stocks/' + encodeURIComponent(symbol) + '/history?period=' + state.period),
      $('show-forecast').checked ? api('/api/stocks/' + encodeURIComponent(symbol) + '/forecast?days=10').catch(() => null) : null,
    ]);
    if (symbol !== state.selected) return;   // another stock was picked meanwhile
    renderChart(box, symbol, history, forecast);
  } catch (err) { box.replaceChildren(el('div', err.message, 'empty')); }
}

/** Line chart of closes (SVG), with hover readout and optional forecast band. */
function renderChart(box, symbol, history, forecast) {
  const w = state.watchlist.find((x) => x.symbol === symbol);
  const first = history[0].close, last = history[history.length - 1].close;
  const change = (last - first) / first * 100;
  const title = $('chart-title');
  title.replaceChildren(document.createTextNode(symbol + ' ' + (w ? w.name : '') + '  ' + money(last) + '  '),
    el('span', pct(change) + ' over ' + (state.period === 'ALL' ? 'all data' : state.period), change >= 0 ? 'gain' : 'loss'));

  const fc = forecast ? forecast.forecast : [];
  const width = box.clientWidth || 800, height = box.clientHeight || 300, L = 60, R = 12, T = 12, B = 26;
  const slots = history.length + fc.length;
  const x = (i) => L + (slots <= 1 ? 0 : i * (width - L - R) / (slots - 1));
  let lo = Math.min(...history.map((p) => p.close)), hi = Math.max(...history.map((p) => p.close));
  for (const f of fc) { lo = Math.min(lo, f.low); hi = Math.max(hi, f.high); }
  const ticks = niceTicks(lo, hi, 5), yMin = ticks[0], yMax = ticks[ticks.length - 1];
  const y = (v) => T + (yMax - v) / (yMax - yMin) * (height - T - B);

  const NS = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  const add = (tag, attrs, text) => {
    const n = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
    if (text != null) n.textContent = text;
    svg.appendChild(n);
    return n;
  };

  for (const t of ticks) {
    add('line', { x1: L, x2: width - R, y1: y(t), y2: y(t), class: 'grid' });
    add('text', { x: L - 8, y: y(t) + 4, 'text-anchor': 'end', class: 'axis' }, '$' + (yMax - yMin < 10 ? t.toFixed(2) : Math.round(t).toLocaleString()));
  }
  const labels = Math.min(6, history.length);
  for (let k = 0; k < labels; k++) {
    const i = labels === 1 ? 0 : Math.round(k * (history.length - 1) / (labels - 1));
    const d = new Date(history[i].date + 'T00:00:00');
    const text = d.toLocaleDateString('en-US', state.period === '1Y' || state.period === 'ALL' ? { month: 'short', year: '2-digit' } : { month: 'short', day: 'numeric' });
    add('text', { x: x(i), y: height - 6, 'text-anchor': k === 0 ? 'start' : k === labels - 1 ? 'end' : 'middle', class: 'axis' }, text);
  }

  const color = getComputedStyle(document.documentElement).getPropertyValue(change >= 0 ? '--gain' : '--loss').trim();
  if (fc.length) {
    const s = history.length - 1;
    const band = [`M${x(s)},${y(last)}`, ...fc.map((f, d) => `L${x(s + 1 + d)},${y(f.high)}`), ...fc.slice().reverse().map((f, d) => `L${x(s + fc.length - d)},${y(f.low)}`), 'Z'].join(' ');
    add('path', { d: band, fill: 'var(--band)' });
    add('path', { d: `M${x(s)},${y(last)} ` + fc.map((f, d) => `L${x(s + 1 + d)},${y(f.price)}`).join(' '), fill: 'none', stroke: 'var(--forecast)', 'stroke-width': 1.5, 'stroke-dasharray': '5 4' });
  }
  const line = history.map((p, i) => `${i ? 'L' : 'M'}${x(i)},${y(p.close)}`).join(' ');
  add('path', { d: `${line} L${x(history.length - 1)},${height - B} L${x(0)},${height - B} Z`, fill: color, 'fill-opacity': 0.1 });
  add('path', { d: line, fill: 'none', stroke: color, 'stroke-width': 1.8, 'stroke-linejoin': 'round' });

  const cursor = add('line', { y1: T, y2: height - B, stroke: 'var(--muted)', 'stroke-width': 1, visibility: 'hidden' });
  const dot = add('circle', { r: 4, fill: color, visibility: 'hidden' });
  const tip = el('div', '', 'tip');
  tip.hidden = true;
  svg.addEventListener('mousemove', (e) => {
    const rect = svg.getBoundingClientRect();
    const px = (e.clientX - rect.left) * width / rect.width;
    const i = Math.round((px - L) * (slots - 1) / (width - L - R));
    if (i < 0 || i >= history.length) { cursor.setAttribute('visibility', 'hidden'); dot.setAttribute('visibility', 'hidden'); tip.hidden = true; return; }
    const p = history[i];
    cursor.setAttribute('x1', x(i)); cursor.setAttribute('x2', x(i)); cursor.setAttribute('visibility', 'visible');
    dot.setAttribute('cx', x(i)); dot.setAttribute('cy', y(p.close)); dot.setAttribute('visibility', 'visible');
    tip.textContent = p.date + '  ' + money(p.close);
    tip.hidden = false;
    tip.style.left = Math.min(x(i) * rect.width / width + 10, rect.width - 150) + 'px';
  });
  svg.addEventListener('mouseleave', () => { cursor.setAttribute('visibility', 'hidden'); dot.setAttribute('visibility', 'hidden'); tip.hidden = true; });

  box.replaceChildren(svg, tip);
  if (forecast) box.appendChild(el('div', 'Forecast: ' + forecast.forecastModel + ', 80% range', 'note small muted'));
}

/** Round-number axis ticks covering [min, max]. */
function niceTicks(min, max, count) {
  if (max <= min) { max = min + 1; min -= 1; }
  const rough = (max - min) / Math.max(1, count - 1);
  const mag = Math.pow(10, Math.floor(Math.log10(rough)));
  const f = rough / mag;
  const step = (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * mag;
  const ticks = [];
  for (let t = Math.floor(min / step) * step; t <= Math.ceil(max / step) * step + step / 2; t += step) ticks.push(t);
  return ticks;
}

window.addEventListener('resize', () => { clearTimeout(window.__rz); window.__rz = setTimeout(drawChart, 200); });

// ------------------------------------------------------------------
// Trade, portfolio, transactions
// ------------------------------------------------------------------

$('trade-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const side = e.submitter ? e.submitter.dataset.side : 'BUY';
  const buttons = $('trade-form').querySelectorAll('button');
  buttons.forEach((b) => { b.disabled = true; });
  setMsg($('trade-msg'), 'Getting the latest price…');
  try {
    const res = await api('/api/trades', { method: 'POST', body: { symbol: $('trade-symbol').value, side, quantity: Number($('trade-qty').value) } });
    setMsg($('trade-msg'), res.message + (res.livePrice ? '' : ' (live quote unavailable — used last known price)'), 'ok');
    $('who-balance').textContent = money(res.balance);
  } catch (err) {
    setMsg($('trade-msg'), err.message, 'error');
  } finally {
    buttons.forEach((b) => { b.disabled = false; });
  }
});

async function loadPortfolio() {
  const tbody = $('portfolio-table').querySelector('tbody');
  try {
    const rows = await api('/api/portfolio');
    if (!rows.length) { emptyRow(tbody, 7, 'No holdings yet. Buy something on the Trade tab.'); return; }
    tbody.replaceChildren(...rows.map((p) => row([
      { text: p.stockName }, { text: String(p.quantity), cls: 'num' },
      { text: money(p.avgPrice), cls: 'num' }, { text: money(p.currentPrice), cls: 'num' },
      { text: money(p.marketValue), cls: 'num' },
      { text: (p.unrealizedPnL >= 0 ? '+' : '-') + money(Math.abs(p.unrealizedPnL)), cls: 'num ' + (p.unrealizedPnL >= 0 ? 'gain' : 'loss') },
      { text: pct(p.pnlPercent), cls: 'num ' + (p.pnlPercent >= 0 ? 'gain' : 'loss') },
    ])));
  } catch (err) { emptyRow(tbody, 7, err.message); }
}

async function loadTransactions() {
  const tbody = $('tx-table').querySelector('tbody');
  try {
    const rows = await api('/api/transactions');
    if (!rows.length) { emptyRow(tbody, 6, 'No transactions yet.'); return; }
    tbody.replaceChildren(...rows.map((t) => row([
      { text: t.timestamp.replace('T', ' ').slice(0, 16) }, { text: t.type, cls: t.type === 'BUY' ? 'gain' : 'loss' },
      { text: t.stockName }, { text: String(t.quantity), cls: 'num' },
      { text: money(t.price), cls: 'num' }, { text: money(t.total), cls: 'num' },
    ])));
  } catch (err) { emptyRow(tbody, 6, err.message); }
}

$('portfolio-refresh').addEventListener('click', loadPortfolio);
$('tx-refresh').addEventListener('click', loadTransactions);

// ------------------------------------------------------------------
// Predict
// ------------------------------------------------------------------

$('predict-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const symbol = $('predict-symbol').value, days = Number($('predict-days').value), model = $('predict-model').value;
  setMsg($('predict-msg'), 'Backtesting…');
  $('predict-out').hidden = true;
  try {
    const f = await api(`/api/stocks/${encodeURIComponent(symbol)}/forecast?days=${days}` + (model ? '&model=' + encodeURIComponent(model) : ''));
    setMsg($('predict-msg'), '');
    $('predict-tested').textContent = `${f.symbol} (${f.name}): replayed ${f.models[0].samples} past forecasts made ${f.testedFrom} to ${f.testedTo}, `
      + `each using only the prices known that day, scoring the price ${f.days} trading day(s) later.`;
    $('models-table').querySelector('tbody').replaceChildren(...f.models.map((m) => row([
      { text: m.name },
      { text: money(m.averageError) + ' (' + m.averageErrorPercent.toFixed(2) + '%)', cls: 'num' },
      { text: m.directionAccuracy == null ? 'n/a' : Math.round(m.directionAccuracy * 100) + '%', cls: 'num' },
      { text: m.vsTodaysPrice == null ? '(baseline)' : Math.abs(m.vsTodaysPrice * 100).toFixed(1) + '% ' + (m.vsTodaysPrice >= 0 ? 'better' : 'worse'), cls: 'num ' + (m.vsTodaysPrice == null ? '' : m.vsTodaysPrice >= 0 ? 'gain' : 'loss') },
    ])));
    $('predict-verdict').textContent = f.bestModel === "Today's price"
      ? 'No model beat simply assuming the price stays where it is. Up/down calls near 50% are no better than a coin flip.'
      : 'Most accurate: ' + f.bestModel + '.';
    $('forecast-title').textContent = 'Forecast: ' + f.forecastModel;
    $('forecast-note').textContent = `From the ${f.lastDate} close of ${money(f.lastClose)}. The range is where 80% of this model's past outcomes landed.`;
    $('forecast-table').querySelector('tbody').replaceChildren(...f.forecast.map((d) => row([
      { text: 'Day +' + d.day }, { text: money(d.price), cls: 'num' }, { text: money(d.low) + ' – ' + money(d.high), cls: 'num' },
    ])));
    $('predict-out').hidden = false;
  } catch (err) { setMsg($('predict-msg'), err.message, 'error'); }
});

// ------------------------------------------------------------------
// Start
// ------------------------------------------------------------------

if (state.token) startApp().catch(() => showLogin());
else showLogin();
