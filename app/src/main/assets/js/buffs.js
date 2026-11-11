// Catálogo de buffs: o jogo reconhece estes mesmos tipos (forca, protecao, regen, velocidade, xp, divino).
window.BUFFS = {
  forca: { nome: 'Força Suprema', cor: '#ff3a2a', desc: 'Aumenta o dano. Aura vermelha.' },
  protecao: { nome: 'Proteção Divina', cor: '#3aa8ff', desc: 'Reduz o dano recebido. Escudo azul.' },
  regen: { nome: 'Regeneração Suprema', cor: '#3aff7a', desc: 'Vida e mana voltando. Aura verde.' },
  velocidade: { nome: 'Velocidade Suprema', cor: '#b05aff', desc: 'Anda, corre e voa mais rápido. Raios.' },
  xp: { nome: 'Experiência Divina', cor: '#ffc83a', desc: 'Ganha mais XP. Brilho dourado.' },
  divino: { nome: 'Poder Divino', cor: '#ffe8a0', desc: 'Metade de todos os outros, com imunidade opcional. Lendário.' }
};
window.DURACOES = [['5 min', 5], ['30 min', 30], ['1 hora', 60], ['24 horas', 1440], ['Permanente', 0], ['Personalizada', -1]];
window.ic = t => `<img class="ico" src="icones/gm_${t}.svg" alt="">`;
window.tempo = ate => { if (!ate) return 'permanente'; const s = Math.max(0, Math.round((ate - Date.now()) / 1000)); return s >= 86400 ? Math.floor(s / 86400) + 'd ' + Math.floor(s % 86400 / 3600) + 'h' : s >= 3600 ? Math.floor(s / 3600) + 'h ' + Math.floor(s % 3600 / 60) + 'min' : s >= 60 ? Math.floor(s / 60) + ' min' : s + ' s'; };
window.ativos = lista => (lista || []).filter(b => BUFFS[b.tipo] && (!b.ate || b.ate > Date.now()));
window.durTxt = min => !min ? 'permanente' : min >= 1440 && min % 1440 === 0 ? (min / 1440) + ' dia(s)' : min >= 60 && min % 60 === 0 ? (min / 60) + ' hora(s)' : min + ' min';
