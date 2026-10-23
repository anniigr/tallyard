/* Tallyard operations UI. Plain browser JavaScript, no build step, no external dependencies.
 * Talks to the same REST API that integrators use; the key is kept in sessionStorage only. */
(() => {
  const $ = (sel) => document.querySelector(sel);
  // The key can also be supplied as a URL fragment (#key=ty_…) for demos and screenshots; fragments
  // are never sent to the server. It is moved into sessionStorage and removed from the address bar.
  const fragmentKey = new URLSearchParams(location.hash.replace(/^#/, '')).get('key');
  if (fragmentKey) {
    sessionStorage.setItem('tallyard.key', fragmentKey);
    history.replaceState(null, '', location.pathname);
  }
  // replays: periodId → last replay report; openInvoices: ids kept expanded across re-renders
  const state = { key: sessionStorage.getItem('tallyard.key') || '', timer: null, busy: false, replays: {}, openInvoices: new Set() };

  const fmtTime = (iso) => iso ? new Date(iso).toLocaleTimeString([], { hour12: false }) : '–';
  const fmtMoney = (v, cur) => v == null ? '–' : `${Number(v).toFixed(2)} ${cur || ''}`.trim();
  const fmtQty = (v) => v == null ? '–' : Number(v).toLocaleString(undefined, { maximumFractionDigits: 2 });
  const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const short = (id) => id ? String(id).slice(0, 8) : '';

  function log(msg, cls) {
    const el = document.createElement('div');
    el.className = cls || '';
    el.textContent = `${new Date().toLocaleTimeString([], { hour12: false })}  ${msg}`;
    const box = $('#log');
    box.prepend(el);
    while (box.children.length > 40) box.lastChild.remove();
  }

  async function api(method, path, body) {
    const res = await fetch(path, {
      method,
      headers: { 'X-Api-Key': state.key, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (res.status === 204) return null;
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) {
      const err = new Error((data && (data.detail || data.title)) || `HTTP ${res.status}`);
      err.status = res.status; err.body = data;
      throw err;
    }
    return data;
  }

  async function action(label, fn) {
    try {
      const r = await fn();
      log(`${label}: ok`, 'ok');
      return r;
    } catch (e) {
      log(`${label}: ${e.message}`, 'err');
      throw e;
    }
  }

  function renderOverview(o) {
    $('#statEvents').textContent = fmtQty(o.stats.storedEvents);
    $('#statAdjust').textContent = fmtQty(o.stats.adjustments);
    $('#statRejected').textContent = fmtQty(o.stats.rejected);
    $('#statOpen').textContent = o.periods.filter((p) => p.state === 'OPEN').length;
    $('#statInvoices').textContent = o.invoices.length;
    $('#statAsOf').textContent = fmtTime(o.asOf);

    const customers = o.customers.map((c) => `
      <tr>
        <td><b>${esc(c.externalId)}</b><div class="muted">${esc(c.name)}</div></td>
        <td>${esc(c.plan || '–')}</td>
        <td>${fmtTime(c.periodEndsAt)}</td>
        <td><div class="usage">${c.usage.map((u) => `<span>${esc(u.meterCode)} <b>${fmtQty(u.quantity)}</b> <span class="muted">${esc(u.unit)}</span></span>`).join('')}</div></td>
        <td class="num">${fmtMoney(c.estimate, c.currency)}</td>
        <td class="num">${c.creditBalance == null ? '–' : fmtMoney(c.creditBalance, c.currency)}</td>
        <td>${c.openPeriodId ? `<button class="small" data-act="rate" data-id="${c.openPeriodId}">rate now</button>` : ''}</td>
      </tr>`).join('');
    $('#customers tbody').innerHTML = customers;

    const customerById = Object.fromEntries(o.customers.map((c) => [c.customerId, c.externalId]));
    $('#periods tbody').innerHTML = o.periods.map((p) => `
      <tr>
        <td><span class="badge ${p.state}">${p.state}</span></td>
        <td><code title="period ${p.id}">${esc(customerById[p.customerId] || short(p.customerId))}</code></td>
        <td class="muted">${fmtTime(p.startsAt)} → ${fmtTime(p.endsAt)}</td>
        <td>
          ${p.state === 'OPEN' ? `<button class="small" data-act="close" data-id="${p.id}">close</button>` : ''}
          ${p.state === 'OPEN' || p.state === 'CLOSING' ? `<button class="small" data-act="rate" data-id="${p.id}">rate</button>` : ''}
          ${p.state === 'RATED' ? `<button class="small" data-act="finalize" data-id="${p.id}">finalize</button>` : ''}
          ${p.state === 'RATED' || p.state === 'FINALIZED' ? `<button class="small" data-act="replay" data-id="${p.id}">replay</button>` : ''}
          <span id="replay-${p.id}">${renderReplay(state.replays[p.id])}</span>
        </td>
      </tr>`).join('');

    $('#invoices').innerHTML = o.invoices.length ? o.invoices.map((inv) => `
      <details class="invoice" data-id="${inv.id}" ${state.openInvoices.has(inv.id) ? 'open' : ''}>
        <summary>
          <span><span class="badge RATED">draft</span> <code>${short(inv.periodId)}</code> ${fmtTime(inv.createdAt)}</span>
          <span>subtotal <b>${fmtMoney(inv.subtotal, inv.currency)}</b> · credits ${fmtMoney(inv.creditsApplied, inv.currency)} · total <b>${fmtMoney(inv.total, inv.currency)}</b></span>
        </summary>
        <div class="hash">hash ${esc(inv.reproducibilityHash)}</div>
        <table><thead><tr><th>Line</th><th class="num">Quantity</th><th class="num">Amount</th><th>Breakdown</th></tr></thead><tbody>
          ${inv.lines.map((l) => `<tr><td>${esc(l.description)}</td><td class="num">${fmtQty(l.quantity)}</td><td class="num">${fmtMoney(l.amount, inv.currency)}</td><td><code>${esc(summarizeBreakdown(l.breakdownJson))}</code></td></tr>`).join('')}
        </tbody></table>
      </details>`).join('') : '<div class="muted">No invoice drafts yet — rate a period.</div>';

    $('#rejections tbody').innerHTML = o.rejections.map((r) => `
      <tr><td class="muted">${fmtTime(r.createdAt)}</td><td><code>${esc(r.reason)}</code></td><td>${esc(r.subject || '')}</td><td><code>${esc(r.eventId || '')}</code></td><td class="muted">${esc(r.detail || '')}</td></tr>`).join('')
      || '<tr><td colspan="5" class="muted">none</td></tr>';
  }

  function renderReplay(r) {
    if (!r) return '';
    return r.matches
      ? `<span class="replay-ok">✓ matches · hash ${esc(String(r.replayedHash).slice(0, 12))}… · ${r.durationMillis} ms</span>`
      : `<span class="replay-bad">✗ ${esc(r.differences.join('; '))}</span>`;
  }

  function summarizeBreakdown(json) {
    try {
      const b = JSON.parse(json);
      if (b.pricingModel === 'MINIMUM') return 'top-up';
      const tiers = (b.tiers || []).map((t) => `${fmtQty(t.units)}×${t.unitPrice}${Number(t.flatAmount) ? `+${t.flatAmount}` : ''}`).join(' | ');
      const pkg = b.packages != null ? ` pkgs=${b.packages}` : '';
      return `${b.pricingModel} q=${fmtQty(b.quantity)} incl=${fmtQty(b.includedUnits)} billable=${fmtQty(b.billableUnits)}${pkg} ${tiers}`;
    } catch { return json; }
  }

  function renderDeliveries(ds) {
    $('#deliveries tbody').innerHTML = ds.slice(0, 15).map((d) => `
      <tr><td class="muted">${fmtTime(d.createdAt)}</td><td><code>${esc(d.eventType)}</code></td><td><span class="badge ${d.state}">${d.state}</span></td><td>${d.attempts}</td><td class="muted">${d.lastStatus ?? ''} ${esc(d.lastError || '')}</td></tr>`).join('')
      || '<tr><td colspan="5" class="muted">none</td></tr>';
  }

  function renderSink(rs) {
    $('#sink tbody').innerHTML = rs.slice(0, 10).map((r) => `
      <tr><td class="muted">${fmtTime(r.receivedAt)}</td><td><code>${esc(r.eventType)}</code></td><td><span class="badge ${r.signatureValid ? 'ok' : 'bad'}">${r.signatureValid ? 'valid' : 'invalid'}</span></td><td>${esc(r.attempt || '')}</td></tr>`).join('')
      || '<tr><td colspan="4" class="muted">none</td></tr>';
  }

  async function refresh() {
    if (!state.key || state.busy) return;
    state.busy = true;
    try {
      const [overview, deliveries, sink] = await Promise.all([
        api('GET', '/v1/dashboard/overview'),
        api('GET', '/v1/webhooks/deliveries?limit=30'),
        api('GET', '/v1/webhook-sink').catch(() => []),
      ]);
      renderOverview(overview);
      renderDeliveries(deliveries);
      renderSink(sink);
      $('#status').textContent = 'connected';
      $('#status').className = 'status ok';
      $('#app').hidden = false;
    } catch (e) {
      $('#status').textContent = e.status === 401 ? 'invalid key' : `error: ${e.message}`;
      $('#status').className = 'status bad';
      if (e.status === 401) { $('#app').hidden = true; }
    } finally {
      state.busy = false;
    }
  }

  function schedule() {
    clearInterval(state.timer);
    if ($('#autorefresh').checked) state.timer = setInterval(refresh, 2000);
  }

  document.addEventListener('click', async (ev) => {
    const btn = ev.target.closest('button[data-act]');
    if (!btn) return;
    const { act, id } = btn.dataset;
    btn.disabled = true;
    try {
      if (act === 'replay') {
        const r = await action(`replay ${short(id)}`, () => api('POST', `/v1/periods/${id}/replay`));
        state.replays[id] = r;
        $(`#replay-${id}`).innerHTML = renderReplay(r);
      } else {
        await action(`${act} ${short(id)}`, () => api('POST', `/v1/periods/${id}/${act}`));
        await refresh();
      }
    } catch { /* logged */ } finally { btn.disabled = false; }
  });

  // remember which invoice drafts the operator expanded so refreshes do not collapse them
  document.addEventListener('toggle', (ev) => {
    const d = ev.target;
    if (d.matches && d.matches('details.invoice')) {
      if (d.open) state.openInvoices.add(d.dataset.id); else state.openInvoices.delete(d.dataset.id);
    }
  }, true);

  $('#entitlement').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const f = new FormData(ev.target);
    try {
      const d = await api('POST', '/v1/entitlements/check', { subject: f.get('subject'), meter: f.get('meter'), requested: Number(f.get('requested')) });
      $('#entitlementResult').textContent = `${d.decision} (${d.reason}) used=${fmtQty(d.used)} limit=${d.limit ?? '∞'} remaining=${d.remaining ?? '∞'} credit=${d.creditRemaining ?? '–'}`;
      $('#entitlementResult').style.color = d.decision === 'ALLOW' ? 'var(--ok)' : 'var(--bad)';
    } catch (e) {
      $('#entitlementResult').textContent = e.message;
      $('#entitlementResult').style.color = 'var(--bad)';
    }
  });

  $('#connect').addEventListener('click', () => {
    state.key = $('#apiKey').value.trim();
    sessionStorage.setItem('tallyard.key', state.key);
    refresh().then(schedule);
  });
  $('#autorefresh').addEventListener('change', schedule);

  if (state.key) { $('#apiKey').value = state.key; refresh().then(schedule); }
})();
