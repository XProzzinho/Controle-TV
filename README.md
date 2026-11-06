# Controle TV

Controle remoto da TV pelo celular, com as mesmas cores do **Início TV** (claro, ou escuro pela lua).

Funciona pelo Wi-Fi usando a depuração ADB da TV — serve para a TV TCL (Android TV) e para a Fire TV Stick, sem instalar nada na TV.

- Setas e OK (segurar repete), Voltar, Início, Menu, Play/Pause
- Volume +/−, mudo e botão de ligar/desligar
- Digitar na TV pelo teclado do celular
- Atalhos para abrir apps (Netflix, YouTube, Prime Video, Globoplay…) e botão **+** para colocar outros
- Procura a TV sozinho na rede

## Como usar
1. Na TV, ligue a depuração (Fire TV: *Opções do desenvolvedor › Depuração ADB*; TCL: *Opções do desenvolvedor › Depuração USB*).
2. Instale o APK da última versão em **Releases**.
3. Abra o app, toque na sua TV e aceite o aviso que aparece na tela da TV.

Cada envio para `main` gera um APK novo automaticamente (GitHub Actions).
