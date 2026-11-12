// Aba Itens: o Administrador escolhe qualquer item do jogo e dá pros jogadores selecionados.
// Grava em presentes/{uid}; no jogo, chega na Caixa de Mensagens do jogador e ele toca em Resgatar.
(function () {
  const CATS = [['arma', '⚔️ Armas'], ['fant', '🧥 Conjuntos'], ['sob', '🛡️ Peças'], ['cosm', '🪽 Asas'], ['mont', '🐾 Montarias'], ['pet', '🐉 Pets'], ['crist', '💎 Cristais'], ['item', '🍖 Itens'], ['moedas', '💰 Moedas'], ['xp', '⭐ XP'], ['titulo', '🏷️ Títulos']];
  let cat = 'arma', sel = null, soEquipe = false, STAFF = null;
  async function carregaEquipe() { if (STAFF) return; STAFF = {}; try { const q = await firebase.firestore().collection('staff').get(); q.forEach(d => { STAFF[d.id] = d.data().cargo || 'gm'; }); } catch (e) { toast('Não deu pra ler a equipe: ' + (e.code || e.message), '#ef4444'); } }
  const esc = s => String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  function lista() { if (cat === 'moedas') return [['moedas', 'Moedas', '', 'Escolha a quantidade']]; if (cat === 'xp') return [['xp', 'Experiência (XP)', '', 'Escolha a quantidade']]; if (cat === 'crist') return (window.CATALOGO.item || []).filter(i => i[0].startsWith('cristal_')).map(i => [i[0], i[1], i[2], i[0] === 'cristal_baixo' ? 'Aprimora +1 a +5' : i[0] === 'cristal_alto' ? 'Aprimora +6 a +10' : 'Aprimora +11 a +15']); return (window.CATALOGO[cat] || []); }
  function jogDesenha() { const B = document.getElementById('i-jog'), q = document.getElementById('i-jbusca').value.trim().toLowerCase(); B.innerHTML = '';
    const F = document.getElementById('i-equipe'); F.classList.toggle('on', soEquipe); F.textContent = soEquipe ? '⭐ Mostrando só a equipe (ADM/GM)' : '⭐ Mostrar só a equipe (ADM/GM)';
    const L = J.todos.filter(p => (!q || (p.nome || '').toLowerCase().includes(q)) && (!soEquipe || (STAFF && STAFF[p.uid]))).slice(0, 60);
    if (!L.length) { B.innerHTML = '<small style="opacity:.6">Nenhum jogador encontrado.</small>'; return; }
    L.forEach(p => { const b = document.createElement('button'); b.className = J.sel.has(p.uid) ? 'on' : ''; b.innerHTML = `<i class="${J.online(p) ? 'on' : ''}"></i>${J.sel.has(p.uid) ? '✓ ' : ''}${esc(p.nome || 'Jogador')}${STAFF && STAFF[p.uid] ? ` <em class="cg ${STAFF[p.uid]}">${STAFF[p.uid].toUpperCase()}</em>` : ''}`;
      b.onclick = () => { J.sel.has(p.uid) ? J.sel.delete(p.uid) : J.sel.add(p.uid); if (window.barraSel) barraSel(); if (J.desenha) J.desenha(); jogDesenha(); itensResumo(); }; B.appendChild(b); }); }
  window.itensDesenha = function () { jogDesenha(); const C = document.getElementById('i-cats'); C.innerHTML = '';
    CATS.forEach(([k, t]) => { const b = document.createElement('button'); b.textContent = t; b.className = k === cat ? 'on' : ''; b.onclick = () => { cat = k; sel = null; itensDesenha(); }; C.appendChild(b); });
    const q = document.getElementById('i-busca').value.trim().toLowerCase(); const G = document.getElementById('i-grade'); G.innerHTML = '';
    lista().filter(it => !q || it[1].toLowerCase().includes(q)).forEach(it => { const d = document.createElement('button'); d.className = 'icard' + (sel && sel[0] === it[0] ? ' on' : '');
      d.innerHTML = (it[2] ? `<img src="${it[2]}" alt="">` : `<span class="em">${cat === 'moedas' ? '💰' : cat === 'xp' ? '⭐' : cat === 'titulo' ? '🏷️' : '🎁'}</span>`) + `<b>${esc(it[1])}</b><small>${esc(it[3] || '')}</small>`;
      d.onclick = async () => { sel = it; if (/^placa_|^esp_(adm|gm)$/.test(it[0]) && !soEquipe) { await carregaEquipe(); soEquipe = true; toast('Arma de equipe: mostrando só os ADM/GM.', '#22d3ee'); } itensDesenha(); }; G.appendChild(d); });
    const qt = cat === 'item' || cat === 'crist' || cat === 'moedas' || cat === 'xp'; document.getElementById('i-qtd-l').hidden = !qt;
    if (qt && cat !== 'item' && cat !== 'crist' && +document.getElementById('i-qtd').value < 100) document.getElementById('i-qtd').value = cat === 'moedas' ? 1000 : 5000;
    itensResumo(); };
  window.itensResumo = function () { const n = J.sel.size; document.getElementById('i-resumo').innerHTML = !sel ? 'Escolha um item.' : !n ? 'Escolha o jogador lá em cima (passo 1).' : `📩 <b>${esc(sel[1])}</b>${document.getElementById('i-qtd-l').hidden ? '' : ' x' + (+document.getElementById('i-qtd').value || 1)} → <b>${n === 1 ? esc(J.nome([...J.sel][0])) : n + ' jogadores'}</b><br><small>Chega na Caixa de Mensagens do jogador</small>`; document.getElementById('i-dar').disabled = !sel || !n; };
  document.getElementById('i-busca').oninput = itensDesenha; document.getElementById('i-jbusca').oninput = jogDesenha; document.getElementById('i-equipe').onclick = async () => { if (!soEquipe) await carregaEquipe(); soEquipe = !soEquipe; jogDesenha(); }; document.getElementById('i-qtd').oninput = itensResumo;
  document.getElementById('i-dar').onclick = async () => { if (!sel || !J.sel.size) return; const db = firebase.firestore(), FV = firebase.firestore.FieldValue; const n = Math.max(1, Math.round(+document.getElementById('i-qtd').value || 1));
    const item = { t: cat === 'crist' ? 'item' : cat, k: sel[0], n, porNome: (window.EU_NOME || 'Administrador'), msg: document.getElementById('i-msg').value.trim().slice(0, 140), criado: Date.now() }; let ok = 0; document.getElementById('i-dar').disabled = true;
    for (const uid of J.sel) { try { await db.collection('presentes').doc(uid).set({ lista: FV.arrayUnion(Object.assign({ id: Math.random().toString(36).slice(2, 12) }, item)) }, { merge: true });
        await db.collection('buffLog').add({ acao: 'item', alvo: uid, alvoNome: J.nome(uid), por: (firebase.auth().currentUser || {}).uid || '', porNome: item.porNome, cargo: window.EU_CARGO || '', itemNome: sel[1] + (n > 1 ? ' x' + n : ''), criado: Date.now() }); ok++; }
      catch (e) { toast('Erro em ' + J.nome(uid) + ': ' + (e.code || e.message), '#ef4444'); } }
    if (ok) { toast('📩 ' + sel[1] + ' enviado por mensagem pra ' + ok + ' jogador(es)!', '#22c55e'); document.getElementById('i-msg').value = ''; } itensResumo(); };
})();
