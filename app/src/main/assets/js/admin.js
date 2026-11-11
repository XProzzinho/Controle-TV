// Painel ADM: login, cargos, aplicar/remover buffs, equipe e histórico.
window.$ = id => document.getElementById(id);
const DONO = 'geansdp2@gmail.com';
firebase.initializeApp(window.FIREBASE_CONFIG);
try { localStorage.setItem('ultimo-email', localStorage.getItem('ultimo-email') || ''); } catch (e) {}
const auth = firebase.auth(), db = firebase.firestore();
let EU = null, CFG = { maxMin: 1440, maxPct: 50, perm: false }, bTipo = 'forca', bDur = 30;

function toast(t, cor) { const e = $('toast'); e.textContent = t; e.style.borderColor = cor || '#22d3ee'; e.classList.add('on'); clearTimeout(toast.t); toast.t = setTimeout(() => e.classList.remove('on'), 2800); }
function msg(id, t, erro) { const e = $(id); e.textContent = t || ''; e.className = 'msg' + (erro ? ' erro' : ''); }
const souAdm = () => EU && EU.cargo === 'adm';

// ---------- login ----------
const ERROS = { 'auth/invalid-credential': 'E-mail ou senha errados.', 'auth/wrong-password': 'Senha errada.', 'auth/user-not-found': 'Não existe conta com esse e-mail.', 'auth/invalid-email': 'E-mail inválido.', 'auth/too-many-requests': 'Muitas tentativas. Espere uns minutos ou use "Esqueci a senha".', 'auth/network-request-failed': 'Sem internet ou conexão bloqueada.', 'auth/missing-password': 'Digite a senha.', 'auth/user-disabled': 'Essa conta foi desativada.' };
const erroTxt = e => (ERROS[e.code] || 'Não deu: ' + (e.code || e.message)) + (e.code && !ERROS[e.code] ? '' : '');
$('lg-entrar').onclick = async () => { const em = $('lg-email').value.trim(), se = $('lg-senha').value; if (!em || !se) { msg('lg-msg', 'Preencha o e-mail e a senha.', true); return; } msg('lg-msg', 'Entrando...'); try { await auth.signInWithEmailAndPassword(em, se); } catch (e) { msg('lg-msg', erroTxt(e) + ' (' + (e.code || '') + ')', true); } };
$('lg-olho').onclick = () => { const i = $('lg-senha'); i.type = i.type === 'password' ? 'text' : 'password'; };
$('lg-esqueci').onclick = async () => { const em = $('lg-email').value.trim(); if (!em) { msg('lg-msg', 'Digite seu e-mail primeiro e toque de novo.', true); return; } try { await auth.sendPasswordResetEmail(em); msg('lg-msg', 'Mandei um link pra ' + em + '. Abra o e-mail (olhe no spam), crie a senha nova e entre aqui.'); } catch (e) { msg('lg-msg', erroTxt(e), true); } };
$('lg-senha').onkeydown = e => { if (e.key === 'Enter') $('lg-entrar').click(); };
$('sair').onclick = () => auth.signOut();
auth.onAuthStateChanged(async u => {
  if (!u) { EU = null; $('painel').hidden = true; $('login').hidden = false; return; }
  msg('lg-msg', 'Conferindo permissão...');
  let cargo = (u.email || '').toLowerCase() === DONO ? 'adm' : null, nome = u.displayName || u.email;
  try { const s = await db.collection('staff').doc(u.uid).get(); if (s.exists) { cargo = cargo || s.data().cargo; nome = s.data().nome || nome; } } catch (e) {}
  try { const p = await db.collection('perfis').doc(u.uid).get(); if (p.exists && p.data().nome) nome = p.data().nome; } catch (e) {}
  if (cargo !== 'adm' && cargo !== 'gm') { msg('lg-msg', 'Essa conta não é GM nem ADM. Peça pro administrador te adicionar.', true); auth.signOut(); return; }
  EU = { uid: u.uid, nome, cargo }; msg('lg-msg', '');
  $('eu-nome').textContent = nome; $('eu-cargo').textContent = cargo.toUpperCase(); $('eu-cargo').className = 'tag ' + cargo;
  $('login').hidden = true; $('painel').hidden = false;
  try { const c = await db.collection('config').doc('gm').get(); if (c.exists) CFG = Object.assign(CFG, c.data()); } catch (e) {}
  montaBuffs(); abre('jog'); J.carrega().catch(e => toast('Erro ao ler jogadores: ' + e.code, '#ef4444'));
});

// ---------- abas ----------
document.querySelectorAll('.abas button').forEach(b => b.onclick = () => abre(b.dataset.a));
function abre(a) { document.querySelectorAll('.abas button').forEach(b => b.classList.toggle('on', b.dataset.a === a)); ['jog', 'buf', 'per', 'his'].forEach(k => $('a-' + k).hidden = k !== a);
  $('barra-sel').hidden = a === 'per' || a === 'his'; if (a === 'per') montaPerm(); if (a === 'his') historico(); if (a === 'buf') resumo(); }
window.abre = abre;
function barraSel() { const n = J.sel.size; $('bs-txt').textContent = n ? n + ' jogador(es) selecionado(s)' : 'Nenhum selecionado'; $('bs-ir').disabled = !n; resumo(); }
window.barraSel = barraSel;
$('bs-ir').onclick = () => abre('buf');
window.fechaFicha = () => { $('ficha').hidden = true; };
$('f-fecha').onclick = fechaFicha; $('ficha').onclick = e => { if (e.target.id === 'ficha') fechaFicha(); };
window.voltar = () => { if (!$('ficha').hidden) { fechaFicha(); return true; } if ($('a-jog').hidden && !$('painel').hidden) { abre('jog'); return true; } return false; };

// ---------- jogadores ----------
$('j-busca').oninput = () => J.desenha();
$('j-atualiza').onclick = () => J.carrega().then(() => toast('Lista atualizada'));
document.querySelectorAll('#a-jog .chips button').forEach(b => b.onclick = () => { J.filtro = b.dataset.f; document.querySelectorAll('#a-jog .chips button').forEach(x => x.classList.toggle('on', x === b)); J.desenha(); });
$('j-sel-on').onclick = () => { J.todos.filter(J.online).forEach(p => J.sel.add(p.uid)); J.desenha(); };
$('j-sel-nada').onclick = () => { J.sel.clear(); J.desenha(); };

// ---------- buffs ----------
function montaBuffs() { const G = $('b-grade'); G.innerHTML = '';
  Object.keys(BUFFS).forEach(k => { const B = BUFFS[k]; const d = document.createElement('button'); d.className = 'bcard' + (k === bTipo ? ' on' : ''); d.style.setProperty('--c', B.cor);
    d.innerHTML = `${ic(k)}<b>${B.nome}</b><small>${B.desc}</small>`; d.onclick = () => { bTipo = k; montaBuffs(); }; G.appendChild(d); });
  $('b-imune-l').hidden = bTipo !== 'divino';
  const maxP = souAdm() ? 100 : CFG.maxPct; $('b-pct').max = maxP; if (+$('b-pct').value > maxP) $('b-pct').value = maxP; $('b-pct-v').textContent = $('b-pct').value + '%';
  const D = $('b-dur'); D.innerHTML = ''; DURACOES.forEach(([t, m]) => { const bloq = !souAdm() && (m === 0 ? !CFG.perm : m > CFG.maxMin); const b = document.createElement('button'); b.textContent = t; b.disabled = bloq; b.className = m === bDur ? 'on' : ''; b.onclick = () => { bDur = m; montaBuffs(); }; D.appendChild(b); });
  $('b-min-l').hidden = bDur !== -1;
  $('b-lim').textContent = souAdm() ? 'ADM: sem limites.' : `GM: até ${durTxt(CFG.maxMin)} e ${CFG.maxPct}% de intensidade${CFG.perm ? ', pode permanente' : ''}.`;
  resumo(); }
$('b-pct').oninput = () => { $('b-pct-v').textContent = $('b-pct').value + '%'; resumo(); };
$('b-min').oninput = resumo;
function minutos() { return bDur === -1 ? Math.max(1, Math.round(+$('b-min').value || 0)) : bDur; }
function resumo() { const n = J.sel.size, B = BUFFS[bTipo]; $('b-resumo').innerHTML = n ? `${ic(bTipo)} <b>${B.nome}</b> ${$('b-pct').value}% · ${durTxt(minutos())} → <b>${n}</b> jogador(es): ${[...J.sel].slice(0, 4).map(J.nome).map(J.esc).join(', ')}${n > 4 ? '…' : ''}` : 'Selecione jogadores na aba <b>Jogadores</b>.'; $('b-aplicar').disabled = !n; $('b-remover').disabled = !n; }
function log(acao, alvo, extra) { return db.collection('buffLog').add(Object.assign({ acao, alvo, alvoNome: J.nome(alvo), por: EU.uid, porNome: EU.nome, cargo: EU.cargo, criado: Date.now() }, extra || {})); }
$('b-aplicar').onclick = async () => { const min = minutos(), pct = +$('b-pct').value;
  if (!souAdm() && ((min === 0 && !CFG.perm) || min > CFG.maxMin || pct > CFG.maxPct)) { toast('Passa do limite do GM.', '#ef4444'); return; }
  if (bDur === -1 && !(+$('b-min').value > 0)) { toast('Digite os minutos.', '#ef4444'); return; }
  const item = { tipo: bTipo, pct, ate: min ? Date.now() + min * 60000 : 0, imune: bTipo === 'divino' && $('b-imune').checked, por: EU.uid, porNome: EU.nome, criado: Date.now() };
  $('b-aplicar').disabled = true; let ok = 0;
  for (const uid of J.sel) { try { const ref = db.collection('buffs').doc(uid);
      await db.runTransaction(async t => { const d = await t.get(ref); const L = ativos(d.exists ? d.data().lista : []).filter(b => b.tipo !== item.tipo); L.push(Object.assign({ id: Math.random().toString(36).slice(2, 10) }, item)); t.set(ref, { lista: L, nome: J.nome(uid), mudou: Date.now() }); });
      J.buffs[uid] = (J.buffs[uid] || []).filter(b => b.tipo !== item.tipo).concat([item]); await log('aplicar', uid, { tipo: item.tipo, pct, min }); ok++; } catch (e) { toast('Erro em ' + J.nome(uid) + ': ' + (e.code || e.message), '#ef4444'); } }
  if (ok) toast(BUFFS[item.tipo].nome + ' aplicado em ' + ok + ' jogador(es)!', BUFFS[item.tipo].cor); J.carrega(); resumo(); };
async function removeBuffs(uids, id) { let ok = 0;
  for (const uid of uids) { try { const ref = db.collection('buffs').doc(uid); let tirou = null;
      await db.runTransaction(async t => { const d = await t.get(ref); const L0 = ativos(d.exists ? d.data().lista : []); tirou = id ? L0.find(b => b.id === id) : null; const L = id ? L0.filter(b => b.id !== id) : []; t.set(ref, { lista: L, nome: J.nome(uid), mudou: Date.now() }); });
      await log(id ? 'remover' : 'limpar', uid, tirou ? { tipo: tirou.tipo } : {}); ok++; } catch (e) { toast('Erro: ' + (e.code || e.message), '#ef4444'); } }
  if (ok) toast(id ? 'Buff removido.' : 'Buffs removidos de ' + ok + ' jogador(es).', '#f59e0b'); await J.carrega(); }
window.removeBuffs = removeBuffs;
$('b-remover').onclick = () => { if (confirm('Remover TODOS os buffs de ' + J.sel.size + ' jogador(es)?')) removeBuffs([...J.sel]); };

// ---------- permissões ----------
async function montaPerm() { $('p-adm').hidden = !souAdm(); $('p-gm').hidden = souAdm();
  if (!souAdm()) { $('p-gm-lim').textContent = `Seus limites: até ${durTxt(CFG.maxMin)}, ${CFG.maxPct}% de intensidade${CFG.perm ? ', pode permanente' : ', sem permanente'}.`; return; }
  $('c-min').value = CFG.maxMin; $('c-pct').value = CFG.maxPct; $('c-perm').checked = !!CFG.perm;
  const L = $('p-lista'); L.innerHTML = '<p class="vazio">Carregando...</p>';
  try { const s = await db.collection('staff').get(); L.innerHTML = `<div class="jog"><div class="av" style="--c:#22d3ee">★</div><div class="tx"><b>Dono (${DONO.split('@')[0].slice(0, 4)}…)</b><small>ADM fixo · não dá pra remover</small></div></div>`;
    s.forEach(d => { const x = d.data(); const e = document.createElement('div'); e.className = 'jog'; e.innerHTML = `<div class="av" style="--c:${x.cargo === 'adm' ? '#f59e0b' : '#22d3ee'}">${J.esc((x.nome || '?')[0])}</div><div class="tx"><b>${J.esc(x.nome || d.id)}</b><small>${x.cargo.toUpperCase()} · ID ${J.esc(x.codigo || '')}</small></div><button class="bt mini perigo">Remover</button>`;
      e.querySelector('button').onclick = async () => { if (!confirm('Tirar ' + (x.nome || '') + ' da equipe?')) return; await db.collection('staff').doc(d.id).delete(); await log('equipe-sai', d.id, { cargo: x.cargo }); montaPerm(); }; L.appendChild(e); });
  } catch (e) { L.innerHTML = '<p class="vazio">Erro: ' + (e.code || e.message) + '</p>'; } }
$('p-add').onclick = async () => { const cod = $('p-cod').value.trim().toUpperCase(); if (cod.length < 6) { msg('p-msg', 'O código tem 6 letras/números.', true); return; }
  msg('p-msg', 'Procurando...'); try { const q = await db.collection('perfis').where('codigo', '==', cod).limit(1).get(); if (q.empty) { msg('p-msg', 'Nenhum jogador com esse código.', true); return; }
    const d = q.docs[0], cargo = $('p-cargo').value; await db.collection('staff').doc(d.id).set({ cargo, nome: d.data().nome || '', codigo: cod, por: EU.uid, criado: Date.now() }); await log('equipe-entra', d.id, { cargo });
    msg('p-msg', (d.data().nome || cod) + ' agora é ' + cargo.toUpperCase() + '.'); $('p-cod').value = ''; montaPerm(); } catch (e) { msg('p-msg', 'Erro: ' + (e.code || e.message), true); } };
$('c-salva').onclick = async () => { CFG = { maxMin: Math.max(5, +$('c-min').value || 60), maxPct: Math.min(100, Math.max(10, +$('c-pct').value || 50)), perm: $('c-perm').checked };
  try { await db.collection('config').doc('gm').set(CFG); toast('Limites salvos.'); } catch (e) { toast('Erro: ' + (e.code || e.message), '#ef4444'); } };

// ---------- histórico ----------
const ACAO = { aplicar: 'aplicou', remover: 'removeu', limpar: 'removeu todos os buffs de', 'equipe-entra': 'colocou na equipe', 'equipe-sai': 'tirou da equipe' };
async function historico() { const L = $('h-lista'); L.innerHTML = '<p class="vazio">Carregando...</p>';
  try { const s = await db.collection('buffLog').orderBy('criado', 'desc').limit(100).get(); L.innerHTML = s.empty ? '<p class="vazio">Nada registrado ainda.</p>' : '';
    s.forEach(d => { const x = d.data(), B = BUFFS[x.tipo]; const e = document.createElement('div'); e.className = 'hist'; const q = new Date(x.criado);
      e.innerHTML = `${B ? ic(x.tipo) : '<span class="ico">🛡️</span>'}<div class="tx"><b>${J.esc(x.porNome)} <em>(${(x.cargo || '').toUpperCase()})</em> ${ACAO[x.acao] || x.acao} ${B && x.acao !== 'limpar' ? '<i style="color:' + B.cor + '">' + B.nome + '</i> ' + (x.acao === 'aplicar' ? (x.pct + '% · ' + durTxt(x.min)) + ' em ' : 'de ') : x.cargo && x.acao.startsWith('equipe') ? '' : ''}${J.esc(x.alvoNome || '')}</b><small>${q.toLocaleDateString('pt-BR')} ${q.toLocaleTimeString('pt-BR').slice(0, 5)}</small></div>`; L.appendChild(e); });
  } catch (e) { L.innerHTML = '<p class="vazio">Erro: ' + (e.code || e.message) + '</p>'; } }
$('h-atualiza').onclick = historico;
setInterval(() => { if (EU && !$('a-jog').hidden && $('ficha').hidden) J.desenha(); }, 30000);
