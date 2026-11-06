package com.geanalves.controletv

/** Mesmas cores do Início TV: claro com botões brancos, ou escuro pela lua. */
object Cores {
    var escuro = false

    // Texto principal e secundário
    val tinta: Int get() = if (escuro) 0xFFF2F2F7.toInt() else 0xFF1C1C1E.toInt()
    val cinza: Int get() = if (escuro) 0xFF9A9AA0.toInt() else 0xFF7C7C82.toInt()

    /** Cor do texto/ícone em cima de algo pintado com [tinta] (botão apertado). */
    val papel: Int get() = if (escuro) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()

    // Fundo da tela (degradê de cima para baixo)
    val fundoTopo: Int get() = if (escuro) 0xFF1E1E24.toInt() else 0xFFFFFFFF.toInt()
    val fundoBase: Int get() = if (escuro) 0xFF0B0B0F.toInt() else 0xFFF1F1F4.toInt()

    /** Botões e barras (branco no claro). */
    val janela: Int get() = if (escuro) 0xFF2C2C2E.toInt() else 0xFFFFFFFF.toInt()

    /** Botões dentro das barras e janelas. */
    val campo: Int get() = if (escuro) 0xFF3A3A3C.toInt() else 0xFFF2F2F7.toInt()

    val sutil: Int get() = if (escuro) 0x1FFFFFFF else 0x1A000000

    val verde = 0xFF34C759.toInt()
    val vermelho = 0xFFFF3B30.toInt()
}
