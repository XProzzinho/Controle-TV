// Aba Itens: o Administrador escolhe qualquer item do jogo e dá pros jogadores selecionados.
// Grava em presentes/{uid}; o jogo entrega na hora (ou quando o jogador entrar).
(function () {
  const CATS = [['arma', '⚔️ Armas'], ['fant', '🧥 Conjuntos'], ['sob', '🛡️ Peças'], ['cosm', '🪽 Asas'], ['mont', '🐾 Montarias'], ['pet', '🐉 Pets'], ['item', '🍖 Itens'], ['moedas', '💰 Moedas'], ['xp', '⭐ XP'], ['titulo', '🏷️ Títulos']];
  let cat = 'arma', sel = null;
  const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  function lista() { if (cat === 'moedas') return [['moedas', 'Moedas', '', 'Escolha a quantidade']]; if (cat === 'xp') return [['xp', 'Experiência (XP)', '', 'Escolha a quantidade']]; return (window.CATALOGO[cat] || []); }
  window.itensDesenha = function () { const C = document.getElementById('i-cats'); C.innerHTML = '';
    CATS.forEach(([k, t]) => { const b = document.createElement('button'); b.textContent = t; b.className = k === cat ? 'on' : ''; b.onclick = () => { cat = k; sel = null; itensDesenha(); }; C.appendChild(b); });
    const q = document.getElementById('i-busca').value.trim().toLowerCase(); const G = document.getElementById('i-grade'); G.innerHTML = '';
    lista().filter(it => !q || it[1].toLowerCase().includes(q)).forEach(it => { const d = document.createElement('button'); d.className = 'icard' + (sel && sel[0] === it[0] ? ' on' : '');
      d.innerHTML = (it[2] ? `<img src="${it[2]}" alt="">` : `<span class="em">${cat === 'moedas' ? '💰' : cat === 'xp' ? '⭐' : cat === 'titulo' ? '🏷️' : '🎁'}</span>`) + `<b>${esc(it[1])}</b><small>${esc(it[3] || '')}</small>`;
      d.onclick = () => { sel = it; itensDesenha(); }; G.appendChild(d); });
    const qt = cat === 'item' || cat === 'moedas' || cat === 'xp'; document.getElementById('i-qtd-l').hidden = !qt;
    if (qt && cat !== 'item' && +document.getElementById('i-qtd').value < 100) document.getElementById('i-qtd').value = cat === 'moedas' ? 1000 : 5000;
    itensResumo(); };
  window.itensResumo = function () { const n = J.sel.size; document.getElementById('i-resumo').innerHTML = !sel ? 'Escolha um item.' : !n ? 'Selecione jogadores na aba <b>Jogadores</b>.' : `🎁 <b>${esc(sel[1])}</b>${document.getElementById('i-qtd-l').hidden ? '' : ' x' + (+document.getElementById('i-qtd').value || 1)} → <b>${n}</b> jogador(es)`; document.getElementById('i-dar').disabled = !sel || !n; };
  document.getElementById('i-busca').oninput = itensDesenha; document.getElementById('i-qtd').oninput = itensResumo;
  document.getElementById('i-dar').onclick = async () => { if (!sel || !J.sel.size) return; const db = firebase.firestore(), FV = firebase.firestore.FieldValue; const n = Math.max(1, Math.round(+document.getElementById('i-qtd').value || 1));
    const item = { t: cat, k: sel[0], n, porNome: (window.EU_NOME || 'Administrador'), criado: Date.now() }; let ok = 0; document.getElementById('i-dar').disabled = true;
    for (const uid of J.sel) { try { await db.collection('presentes').doc(uid).set({ lista: FV.arrayUnion(Object.assign({ id: Math.random().toString(36).slice(2, 12) }, item)) }, { merge: true });
        await db.collection('buffLog').add({ acao: 'item', alvo: uid, alvoNome: J.nome(uid), por: firebase.auth().currentUser.uid, porNome: item.porNome, cargo: window.EU_CARGO || '', itemNome: sel[1] + (n > 1 ? ' x' + n : ''), criado: Date.now() }); ok++; }
      catch (e) { toast('Erro em ' + J.nome(uid) + ': ' + (e.code || e.message), '#ef4444'); } }
    if (ok) toast('🎁 ' + sel[1] + ' enviado pra ' + ok + ' jogador(es)!', '#22c55e'); itensResumo(); };
})();
