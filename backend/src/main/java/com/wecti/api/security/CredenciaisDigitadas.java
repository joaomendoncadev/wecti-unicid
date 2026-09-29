package com.wecti.api.security;

import java.util.Locale;

/**
 * Limpa o que a pessoa <b>digitou</b> antes de comparar com o que esta
 * guardado. Nao valida nada: so tira a sujeira de digitacao que nunca
 * fez parte do dado.
 *
 * <p><b>Por que existe.</b> No 2o dia da WECTI 2026 (29/09), depois de
 * corrigido o bloqueio por IP, ainda sobraram alunos travados no
 * "esqueci minha senha". A comparacao de identidade era
 * {@code rgmGuardado.equals(rgmDigitado)} - byte a byte. Um espaco
 * invisivel que o teclado do celular cola no fim do numero bastava para
 * devolver "Dados nao conferem", e o aluno, que tinha digitado o RGM
 * certo, nao tinha como adivinhar o que estava errado. Do lado do login,
 * o mesmo valia para o email.
 *
 * <p><b>O que NAO passa por aqui: a senha.</b> Espaco e maiuscula sao
 * caracteres validos de senha, e normalizar reduziria de verdade o
 * espaco de busca de quem tentasse adivinhar. Senha continua comparada
 * exatamente como foi digitada.
 */
public final class CredenciaisDigitadas {

    private CredenciaisDigitadas() {
    }

    /**
     * Email para busca: sem espaco nas pontas e em minusculo.
     *
     * <p>O MySQL de producao ja perdoa maiuscula sozinho (as tabelas sao
     * {@code utf8mb4_unicode_ci}), mas depender disso deixaria a regra
     * escondida numa propriedade do banco - e o H2 dos testes, que e
     * case-sensitive, provaria o contrario. Melhor a aplicacao garantir.
     */
    public static String email(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * RGM ou CPF para conferencia de identidade: so os digitos.
     *
     * <p>Os dois sao numericos e ficam guardados so com digitos (RGM
     * {@code ^\d{8}$}, CPF {@code ^\d{11}$}), entao comparar digito a
     * digito e exatamente tao rigoroso quanto comparar a string crua -
     * nao afrouxa a verificacao, so para de reprovar quem digitou
     * "123.456.789-01" ou colou um espaco junto do numero.
     */
    public static String identificador(String identificador) {
        return identificador == null ? "" : identificador.replaceAll("\\D", "");
    }
}
