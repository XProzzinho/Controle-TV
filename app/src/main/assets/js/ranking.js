// ===== Ranking de Poder: mostra o top 10 e deixa o ADM forçar a atualização do ranking e das estátuas do pódio (config/ranking) =====
(function () {
  const db = () => firebase.firestore(); let cfg = null, ouvindo = false;
  const hm = ms => { const d = new Date(ms - 3 * 3600 * 1000); return String(d.getUTCDate()).padStart(2, '0') + '/' + String(d.getUTCMonth() + 1).padStart(2, '0') + ' às ' + String(d.getUTCHours()).padStart(2, '0') + ':' + String(d.getUTCMinutes()).padStart(2, '0'); };
  const esc = t => String(t == null ? '' : t).replace(/[<>&]/g, '');
  function status() { const el = $('r-status'); if (!el) return; el.className = 'nst ' + (cfg && cfg.forcar ? 'on' : 'off');
    el.innerHTML = cfg && cfg.forcar ? `<b>🔄 Última atualização manual: ${hm(cfg.forcar)}</b><small>${cfg.porNome ? 'Por ' + esc(cfg.porNome) + ' · ' : ''}${cfg.todosN ? cfg.todosN + ' jogadores no cálculo · ' : ''}também atualiza à meia-noite</small>` : '<b>🕛 Atualiza sozinho à meia-noite</b><small>Nenhuma atualização manual ainda</small>';
    $('r-atual').disabled = !(typeof souAdm === 'function' && souAdm()); }
  async function lista() { const el = $('r-lista'); try { const q = await db().collection('perfis').orderBy('poderDia', 'desc').limit(10).get(); const med = ['👑', '🥈', '🥉'];
      el.innerHTML = q.empty ? 'Ninguém no ranking ainda.' : q.docs.map((d, i) => { const x = d.data(); return `<div class="rk"><span>${med[i] || (i + 1) + 'º'}</span><b>${esc(x.nomeDia || x.nome || 'Jogador')}</b><small>Nv ${(x.nvDia || x.nv) | 0}</small><i>⚡ ${((x.poderDia || 0) | 0).toLocaleString('pt-BR')}</i></div>`; }).join('');
    } catch (e) { el.textContent = '⚠️ ' + (e.code || e.message); } }
  function ouve() { if (ouvindo) return; ouvindo = true; db().collection('config').doc('ranking').onSnapshot(d => { cfg = d.exists ? d.data() : null; status(); }, e => { $('r-status').textContent = '⚠️ ' + (e.code || e.message); }); }
  // mesma conta do jogo (src/ranking.js poderDe): arma, atributos, roupa, nível e divino
  const ARMA_DANO = {"bastao": 10, "espada": 22, "besta": 30, "glacial": 38, "dragao": 72, "esp_suprema": 48, "esp_corrompida": 52, "esp_celestial": 56, "esp_dragao_real": 64, "esp_executora": 78, "esp_adm": 999, "placa_adm": 999, "placa_gm": 999, "esp_gm": 999, "gl_eclipse": 62, "gl_abismo": 68, "gl_ceifador": 74, "gl_sangrenta": 80, "gl_prata": 90, "memorial": 66, "punho": 6, "espadao": 26, "laminas": 13, "cajado": 22, "arco": 18, "escudo": 14, "runica": 18, "chakram": 15, "arcana": 16, "sombrio": 25}, FANT_DEF = {"comum": 0, "celeste": 25, "mago_gelo": 32, "mago_fogo": 34, "mago_arcano": 30, "mago_natureza": 38, "mago_divino": 45};
  function poderDe(A) { A = A || {}; const at = A.attr || {}; const dano = (ARMA_DANO[A.arma] || 10) * (1 + (at.for || 0) * 0.006), vida = 115 + (at.vit || 0) * 2, mana = 115 + (at.int || 0) * 2, def = Math.min(85, (FANT_DEF[A.fantasia] || 0) + (at.def || 0) * 0.08);
    return Math.round(dano * 15 + vida * 3 + mana * 1.5 + def * 30 + (A.nv || 1) * 45 + (A.divino ? 900 : 0)); }
  const hojeBR = () => new Date(Date.now() - 3 * 3600e3).toISOString().slice(0, 10);
  async function recalculaTodos() { const q = await db().collection('jogadores').get(); let b = db().batch(), n = 0, tot = 0;
    for (const d of q.docs) { const x = d.data(); let A = null; try { A = typeof x.arvore === 'string' ? JSON.parse(x.arvore) : x.arvore; } catch (e) {} if (!A || !A.nv) continue;
      const pd = poderDe(A); b.set(db().collection('perfis').doc(d.id), { poder: pd, poderDia: pd, nomeDia: String((A.visual && A.visual.nome) || x.nome || 'Jogador').slice(0, 20), nvDia: A.nv | 0, naDia: A.nacao || '', dia: hojeBR() }, { merge: true }); n++; tot++; if (n >= 400) { await b.commit(); b = db().batch(); n = 0; } }
    if (n) await b.commit(); return tot; }
  $('r-atual').onclick = async () => { if (!(typeof souAdm === 'function' && souAdm())) return toast('Só o ADM pode atualizar o ranking.', '#ef4444');
    try { toast('⏳ Calculando o poder de todos os jogadores...', '#22d3ee'); const tot = await recalculaTodos(); const agora = Date.now(); await db().collection('config').doc('ranking').set({ forcar: agora, todosDia: hojeBR(), todosEm: agora + 1, todosN: tot, por: EU ? EU.uid : '', porNome: EU ? EU.nome || '' : '' }, { merge: true }); try { log('ranking', '', {}); } catch (e) {}
      toast('🏆 Ranking atualizado com ' + tot + ' jogadores (online e offline)!', '#22c55e'); lista(); setTimeout(lista, 8000); } catch (e) { toast('Erro: ' + (e.code || e.message) + (e.code === 'permission-denied' ? ' (cole as regras novas no Firebase)' : ''), '#ef4444'); } };
  window.rnkMonta = () => { ouve(); status(); lista(); };
})();
