// ===== Batalha de Nação: o ADM inicia/encerra, escolhe o prêmio e pode rodar uma simulação 10x10 (config/batalha, o jogo lê esse documento) =====
(function () {
  let N_CFG = null, N_MIN = 20, N_MODO = 'real', N_PREMIO = 'voo', N_VALOR = 1000, N_AUTO = 'voo', ouvindo = false, autoMexeu = false;
  const PTXT = pr => !pr ? '🪽 Direito de voar' : pr.tipo === 'moedas' ? '💰 ' + (pr.valor || 1000) + ' moedas' : pr.tipo === 'arma' ? '🏅 Arma Memorial' : '🪽 Direito de voar';
  const br = ms => { const d = new Date(ms - 3 * 3600 * 1000); return { h: d.getUTCHours(), m: d.getUTCMinutes() }; };
  const hm = ms => { const d = new Date(ms - 3 * 3600 * 1000); return String(d.getUTCHours()).padStart(2, '0') + ':' + String(d.getUTCMinutes()).padStart(2, '0'); };
  function estado() { const now = Date.now(); if (N_CFG && N_CFG.ate > now) return { on: true, extra: true, sim: !!N_CFG.sim, ate: N_CFG.ate, premio: N_CFG.premio };
    if (N_CFG && N_CFG.parou && !N_CFG.sim && now - N_CFG.parou < 25 * 60 * 1000) return { on: false, parada: true };
    const b = br(now); if ((b.h === 12 || b.h === 20) && b.m < 20) return { on: true, extra: false, ate: now + (20 - b.m) * 60 * 1000 - new Date(now).getUTCSeconds() * 1000, premio: N_CFG && N_CFG.premioAuto }; return { on: false }; }
  function prox() { const b = br(Date.now()); return b.h < 12 ? '12:00' : b.h < 20 ? '20:00' : '12:00 de amanhã'; }
  function desenha() { const S = estado(), el = $('n-status'); if (!el) return; const pode = typeof souAdm === 'function' && souAdm();
    el.className = 'nst ' + (S.on ? 'on' : 'off');
    el.innerHTML = S.on ? `<b>${S.sim ? '🧪 SIMULAÇÃO 10 x 10 RODANDO' : '⚔️ BATALHA ACONTECENDO'}</b><small>${S.sim ? 'Só aparece no jogo de quem é ADM' : S.extra ? 'Batalha extra (iniciada pelo ADM' + (N_CFG && N_CFG.por ? ': ' + String(N_CFG.por).replace(/</g, '') : '') + ')' : 'Horário automático'} · termina às ${hm(S.ate)}<br>Prêmio: ${PTXT(S.premio)}</small>` : `<b>😴 Nenhuma batalha agora</b><small>${S.parada ? 'Encerrada pelo ADM · ' : ''}Próxima automática: ${prox()} · Prêmio: ${PTXT(N_CFG && N_CFG.premioAuto)}</small>`;
    $('n-ini').disabled = !pode || S.on; $('n-fim').disabled = !pode || !S.on; $('n-auto-salva').disabled = !pode;
    $('n-ini').textContent = N_MODO === 'sim' ? '🧪 Iniciar simulação 10 x 10 agora' : '🟢 Iniciar Batalha de Nação agora';
    if (!autoMexeu && N_CFG && N_CFG.premioAuto) { N_AUTO = N_CFG.premioAuto.tipo || 'voo'; marca('#n-auto', N_AUTO); }
    if (!pode) $('n-info').textContent = 'Só o ADM pode iniciar ou encerrar a batalha.'; }
  function marca(sel, v) { document.querySelectorAll(sel + ' button').forEach(x => x.classList.toggle('on', (x.dataset.v || x.dataset.m) === String(v))); }
  function ouve() { if (ouvindo) return; ouvindo = true; db.collection('config').doc('batalha').onSnapshot(d => { N_CFG = d.exists ? d.data() : null; desenha(); }, e => { $('n-info').textContent = '⚠️ ' + (e.code || e.message); }); setInterval(desenha, 5000); }
  window.nacMonta = () => { ouve(); desenha(); };
  const premio = () => N_PREMIO === 'moedas' ? { tipo: 'moedas', valor: N_VALOR } : { tipo: N_PREMIO };
  document.querySelectorAll('#n-dur button').forEach(b => b.onclick = () => { N_MIN = +b.dataset.m; marca('#n-dur', N_MIN); });
  document.querySelectorAll('#n-modo button').forEach(b => b.onclick = () => { N_MODO = b.dataset.v; marca('#n-modo', N_MODO); $('n-modo-info').textContent = N_MODO === 'sim' ? 'Teste: 20 jogadores de mentira (10 🔴 x 10 🔵) aparecem na cidade do seu jogo, aceitam e vão lutar. Os outros jogadores não veem nada. Abra o jogo na conta de ADM.' : 'Todos os jogadores online recebem o convite.'; if (N_MODO === 'sim' && N_MIN > 10) { N_MIN = 3; marca('#n-dur', 3); } desenha(); });
  document.querySelectorAll('#n-premio button').forEach(b => b.onclick = () => { N_PREMIO = b.dataset.v; marca('#n-premio', N_PREMIO); $('n-valor').hidden = N_PREMIO !== 'moedas'; });
  document.querySelectorAll('#n-valor button').forEach(b => b.onclick = () => { N_VALOR = +b.dataset.v; marca('#n-valor', N_VALOR); });
  document.querySelectorAll('#n-auto button').forEach(b => b.onclick = () => { N_AUTO = b.dataset.v; autoMexeu = true; marca('#n-auto', N_AUTO); });
  $('n-ini').onclick = async () => { const now = Date.now(), sim = N_MODO === 'sim', pr = premio(); try { await db.collection('config').doc('batalha').set({ ini: now, ate: now + N_MIN * 60 * 1000, por: (window.EU_NOME || 'ADM'), sim, premio: pr, parou: 0 }, { merge: true }); toast((sim ? '🧪 Simulação 10 x 10 iniciada por ' : '⚔️ Batalha de Nação iniciada por ') + N_MIN + ' min! Prêmio: ' + PTXT(pr), '#22c55e'); try { log('nacao', '', { min: N_MIN, sim, premio: pr.tipo }); } catch (e) {} } catch (e) { toast('Erro: ' + (e.code || e.message), '#ef4444'); } };
  $('n-fim').onclick = async () => { if (!confirm('Encerrar agora? Todos voltam pra Praça e o resultado aparece.')) return; try { await db.collection('config').doc('batalha').set({ ate: 0, parou: Date.now() }, { merge: true }); toast('🛑 Batalha encerrada.', '#f59e0b'); } catch (e) { toast('Erro: ' + (e.code || e.message), '#ef4444'); } };
  $('n-auto-salva').onclick = async () => { const pr = N_AUTO === 'moedas' ? { tipo: 'moedas', valor: 1000 } : { tipo: N_AUTO }; try { await db.collection('config').doc('batalha').set({ premioAuto: pr }, { merge: true }); autoMexeu = false; toast('💾 Prêmio das batalhas automáticas: ' + PTXT(pr), '#22c55e'); } catch (e) { toast('Erro: ' + (e.code || e.message), '#ef4444'); } };
  // esconde a aba de quem não é ADM
  setInterval(() => { const b = $('ab-nac'); if (b) b.hidden = !(typeof souAdm === 'function' && souAdm()); }, 1500);
})();
