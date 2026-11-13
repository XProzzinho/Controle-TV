// ===== Batalha de Nação: o ADM inicia/encerra (config/batalha, o jogo lê esse documento) =====
(function () {
  let N_CFG = null, N_MIN = 20, ouvindo = false;
  const br = ms => { const d = new Date(ms - 3 * 3600 * 1000); return { h: d.getUTCHours(), m: d.getUTCMinutes() }; };
  const hm = ms => { const d = new Date(ms - 3 * 3600 * 1000); return String(d.getUTCHours()).padStart(2, '0') + ':' + String(d.getUTCMinutes()).padStart(2, '0'); };
  function estado() { const now = Date.now(); if (N_CFG && N_CFG.ate > now) return { on: true, extra: true, ate: N_CFG.ate };
    if (N_CFG && N_CFG.parou && now - N_CFG.parou < 25 * 60 * 1000) return { on: false, parada: true };
    const b = br(now); if ((b.h === 12 || b.h === 20) && b.m < 20) return { on: true, extra: false, ate: now + (20 - b.m) * 60 * 1000 - new Date(now).getUTCSeconds() * 1000 }; return { on: false }; }
  function prox() { const b = br(Date.now()); return b.h < 12 ? '12:00' : b.h < 20 ? '20:00' : '12:00 de amanhã'; }
  function desenha() { const S = estado(), el = $('n-status'); if (!el) return; const pode = typeof souAdm === 'function' && souAdm();
    el.className = 'nst ' + (S.on ? 'on' : 'off');
    el.innerHTML = S.on ? `<b>⚔️ BATALHA ACONTECENDO</b><small>${S.extra ? 'Batalha extra (iniciada pelo ADM' + (N_CFG && N_CFG.por ? ': ' + String(N_CFG.por).replace(/</g, '') : '') + ')' : 'Horário automático'} · termina às ${hm(S.ate)}</small>` : `<b>😴 Nenhuma batalha agora</b><small>${S.parada ? 'Encerrada pelo ADM · ' : ''}Próxima automática: ${prox()}</small>`;
    $('n-ini').disabled = !pode || S.on; $('n-fim').disabled = !pode || !S.on; $('n-info').textContent = pode ? '' : 'Só o ADM pode iniciar ou encerrar a batalha.'; }
  function ouve() { if (ouvindo) return; ouvindo = true; db.collection('config').doc('batalha').onSnapshot(d => { N_CFG = d.exists ? d.data() : null; desenha(); }, e => { $('n-info').textContent = '⚠️ ' + (e.code || e.message); }); setInterval(desenha, 5000); }
  window.nacMonta = () => { ouve(); desenha(); };
  document.querySelectorAll('#n-dur button').forEach(b => b.onclick = () => { N_MIN = +b.dataset.m; document.querySelectorAll('#n-dur button').forEach(x => x.classList.toggle('on', x === b)); });
  $('n-ini').onclick = async () => { const now = Date.now(); try { await db.collection('config').doc('batalha').set({ ini: now, ate: now + N_MIN * 60 * 1000, por: (window.EU_NOME || 'ADM') }); toast('⚔️ Batalha de Nação iniciada por ' + N_MIN + ' min!', '#22c55e'); try { log('nacao', '', { min: N_MIN }); } catch (e) {} } catch (e) { toast('Erro: ' + (e.code || e.message), '#ef4444'); } };
  $('n-fim').onclick = async () => { if (!confirm('Encerrar a Batalha de Nação agora? Todos voltam pra Praça.')) return; try { await db.collection('config').doc('batalha').set({ ate: 0, parou: Date.now() }); toast('🛑 Batalha encerrada.', '#f59e0b'); } catch (e) { toast('Erro: ' + (e.code || e.message), '#ef4444'); } };
  // esconde a aba de quem não é ADM
  setInterval(() => { const b = $('ab-nac'); if (b) b.hidden = !(typeof souAdm === 'function' && souAdm()); }, 1500);
})();
