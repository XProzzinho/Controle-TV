// Jogadores: lista, busca, seleção e ficha. Os dados vêm de perfis/{uid} (o jogo atualiza a cada minuto) e buffs/{uid}.
window.J = { todos: [], buffs: {}, sel: new Set(), filtro: 'todos' };
J.online = p => p.visto && Date.now() - p.visto < 3 * 60 * 1000;
J.carrega = async () => {
  const db = firebase.firestore();
  const [ps, bs] = await Promise.all([db.collection('perfis').limit(1000).get(), db.collection('buffs').limit(1000).get()]);
  J.buffs = {}; bs.forEach(d => { J.buffs[d.id] = d.data().lista || []; });
  J.todos = []; ps.forEach(d => J.todos.push(Object.assign({ uid: d.id }, d.data())));
  J.todos.sort((a, b) => (J.online(b) - J.online(a)) || (b.visto || 0) - (a.visto || 0));
  J.desenha();
};
J.esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
J.visiveis = () => { const q = $('j-busca').value.trim().toLowerCase();
  return J.todos.filter(p => (!q || (p.nome || '').toLowerCase().includes(q) || (p.codigo || '').toLowerCase() === q || p.uid.toLowerCase().startsWith(q))
    && (J.filtro !== 'online' || J.online(p)) && (J.filtro !== 'buff' || ativos(J.buffs[p.uid]).length)); };
J.desenha = () => { const L = $('j-lista'), V = J.visiveis(); L.innerHTML = '';
  $('j-conta').textContent = J.todos.filter(J.online).length + ' online · ' + J.todos.length + ' no total';
  if (!V.length) L.innerHTML = '<p class="vazio">Nenhum jogador encontrado.</p>';
  V.forEach(p => { const A = ativos(J.buffs[p.uid]), on = J.online(p), s = J.sel.has(p.uid); const d = document.createElement('div'); d.className = 'jog' + (s ? ' sel' : '');
    d.innerHTML = `<button class="ck">${s ? '✓' : ''}</button><div class="av" style="--c:${on ? '#22c55e' : '#475569'}">${J.esc((p.nome || '?')[0].toUpperCase())}</div>
      <div class="tx"><b>${J.esc(p.nome || 'Jogador')}</b><small>ID ${J.esc(p.codigo || p.uid.slice(0, 6).toUpperCase())} · Nv ${p.nv || 1}${p.classe ? ' · ' + J.esc(p.classe) : ''}</small><small class="${on ? 'on' : ''}">${on ? '● online agora' : p.visto ? 'visto há ' + tempo(Date.now() * 2 - p.visto).replace('permanente', '') : 'nunca visto no painel'}</small></div>
      <div class="bf">${A.map(b => ic(b.tipo)).join('')}</div>`;
    d.querySelector('.ck').onclick = e => { e.stopPropagation(); J.alterna(p.uid); };
    d.onclick = () => J.ficha(p.uid); L.appendChild(d); });
  barraSel(); };
J.alterna = uid => { J.sel.has(uid) ? J.sel.delete(uid) : J.sel.add(uid); J.desenha(); };
J.nome = uid => { const p = J.todos.find(x => x.uid === uid); return p ? p.nome || 'Jogador' : uid.slice(0, 6); };
J.ficha = uid => { const p = J.todos.find(x => x.uid === uid); if (!p) return; const A = ativos(J.buffs[uid]); const F = $('f-corpo');
  F.innerHTML = `<div class="fh"><div class="av grande" style="--c:${J.online(p) ? '#22c55e' : '#475569'}">${J.esc((p.nome || '?')[0].toUpperCase())}</div><div><h2>${J.esc(p.nome || 'Jogador')}</h2>
    <small>ID ${J.esc(p.codigo || '')} · Nv ${p.nv || 1}${p.classe ? ' · ' + J.esc(p.classe) : ''}${p.titulo ? ' · título ' + J.esc(p.titulo) : ''}</small></div></div>
    <h3>Buffs ativos (${A.length})</h3><div class="lista">${A.length ? A.map(b => `<div class="bfl" style="--c:${BUFFS[b.tipo].cor}">${ic(b.tipo)}<div class="tx"><b>${BUFFS[b.tipo].nome} · ${b.pct}%${b.imune ? ' · imune' : ''}</b><small>Acaba: ${tempo(b.ate)} · dado por ${J.esc(b.porNome || '?')}</small></div><button class="bt mini perigo" data-id="${b.id}">Remover</button></div>`).join('') : '<p class="vazio">Sem buffs agora.</p>'}</div>
    <div class="acoes"><button id="f-sel" class="bt">${J.sel.has(uid) ? 'Tirar da seleção' : 'Selecionar'}</button><button id="f-buff" class="bt pri">Dar buff só pra ele</button>${A.length ? '<button id="f-tudo" class="bt perigo">Remover todos</button>' : ''}</div>`;
  F.querySelectorAll('[data-id]').forEach(b => b.onclick = async () => { await removeBuffs([uid], b.dataset.id); J.ficha(uid); });
  $('f-sel').onclick = () => { J.alterna(uid); J.ficha(uid); };
  $('f-buff').onclick = () => { J.sel = new Set([uid]); fechaFicha(); J.desenha(); abre('buf'); };
  if ($('f-tudo')) $('f-tudo').onclick = async () => { await removeBuffs([uid]); J.ficha(uid); };
  $('ficha').hidden = false; };
