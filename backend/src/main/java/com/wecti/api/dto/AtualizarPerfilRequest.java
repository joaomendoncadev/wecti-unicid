package com.wecti.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * O que a propria pessoa pode corrigir no seu cadastro (PUT
 * /usuarios/me). Existe porque muito aluno errou nome, RGM ou curso no
 * autocadastro e so o admin conseguia arrumar, um a um.
 *
 * <p><b>Nao tem email nem perfil, de proposito.</b> O email e o login:
 * quem digitasse errado ao "corrigir" perderia o acesso a propria conta
 * e precisaria do admin de novo - exatamente o problema que esta tela
 * veio resolver. O perfil nunca pode vir do cliente (seria escalada de
 * privilegio: qualquer aluno viraria admin mandando um JSON). Senha
 * continua pelo fluxo de "Esqueci minha senha", que ja confirma
 * identidade com email + RGM.
 *
 * <p>Diferente de {@link NovoUsuarioRequest}, que e a tela do admin e
 * mexe em campos que o dono da conta nao deve mexer sozinho.
 *
 * @param rgm   so se aplica a aluno; ignorado para admin, cujo
 *              identificador e o CPF (e o CPF nao entra aqui porque e o
 *              que valida o "esqueci minha senha" dele)
 * @param curso texto livre, opcional - so informativo
 */
public record AtualizarPerfilRequest(
        @NotBlank String nome,
        @Pattern(regexp = "^\\d{8}$", message = "RGM deve ter exatamente 8 digitos") String rgm,
        String curso) {
}
