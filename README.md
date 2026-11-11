# Painel ADM (Brumafria)

App Android do **GM Control System / Admin Buff Panel** do jogo Brumafria (repositório JOGO).
GMs e ADMs entram com a conta do jogo e aplicam buffs nos jogadores em tempo real.

- `app/src/main/assets/index.html`, `css/admin.css`, `js/admin.js`, `js/buffs.js`, `js/jogadores.js`: o painel (HTML + CSS + JS).
- Dados no Firestore do jogo: `perfis`, `buffs`, `buffLog`, `staff`, `config`. As regras de segurança ficam no repositório JOGO (`firestore.rules`).
- O APK sai sozinho no GitHub Actions a cada push (Releases → `painel-adm.apk`).

Este repositório era o app "Controle TV"; o código antigo continua no histórico do Git.
