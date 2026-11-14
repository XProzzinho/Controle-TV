// ===== Ranking de Poder: mostra o top 10 e deixa o ADM forçar a atualização do ranking e das estátuas do pódio (config/ranking) =====
(function () {
  const db = () => firebase.firestore(); let cfg = null, ouvindo = false;
  const hm = ms => { const d = new Date(ms - 3 * 3600 * 1000); return String(d.getUTCDate()).padStart(2, '0') + '/' + String(d.getUTCMonth() + 1).padStart(2, '0') + ' às ' + String(d.getUTCHours()).padStart(2, '0') + ':' + String(d.getUTCMinutes()).padStart(2, '0'); };
  const esc = t => String(t == null ? '' : t).replace(/[<>&]/g, '');
  function status() { const el = $('r-status'); if (!el) return; el.className = 'nst ' + (cfg && cfg.forcar ? 'on' : 'off');
    el.innerHTML = cfg && cfg.forcar ? `<b>🔄 Última atualização manual: ${hm(cfg.forcar)}</b><small>${cfg.porNome ? 'Por ' + esc(cfg.porNome) + ' · ' : ''}também atualiza sozinho à meia-noite</small>` : '<b>🕛 Atualiza sozinho à meia-noite</b><small>Nenhuma atualização manual ainda</small>';
    $('r-atual').disabled = !(typeof souAdm === 'function' && souAdm()); }
  async function lista() { const el = $('r-lista'); try { const q = await db().collection('perfis').orderBy('poderDia', 'desc').limit(10).get(); const med = ['👑', '🥈', '🥉'];
      el.innerHTML = q.empty ? 'Ninguém no ranking ainda.' : q.docs.map((d, i) => { const x = d.data(); return `<div class="rk"><span>${med[i] || (i + 1) + 'º'}</span><b>${esc(x.nomeDia || x.nome || 'Jogador')}</b><small>Nv ${(x.nvDia || x.nv) | 0}</small><i>⚡ ${((x.poderDia || 0) | 0).toLocaleString('pt-BR')}</i></div>`; }).join('');
    } catch (e) { el.textContent = '⚠️ ' + (e.code || e.message); } }
  function ouve() { if (ouvindo) return; ouvindo = true; db().collection('config').doc('ranking').onSnapshot(d => { cfg = d.exists ? d.data() : null; status(); }, e => { $('r-status').textContent = '⚠️ ' + (e.code || e.message); }); }
  $('r-atual').onclick = async () => { if (!(typeof souAdm === 'function' && souAdm())) return toast('Só o ADM pode atualizar o ranking.', '#ef4444');
    try { await db().collection('config').doc('ranking').set({ forcar: Date.now(), por: EU ? EU.uid : '', porNome: EU ? EU.nome || '' : '' }, { merge: true }); try { log('ranking', '', {}); } catch (e) {}
      toast('🔄 Ranking atualizando! Quem estiver online manda o poder e o visual agora.', '#22c55e'); setTimeout(lista, 6000); setTimeout(lista, 15000); } catch (e) { toast('Erro: ' + (e.code || e.message), '#ef4444'); } };
  window.rnkMonta = () => { ouve(); status(); lista(); };
})();
