package com.wecti.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * O que a propria pessoa pode corrigir no seu cadastro (PUT
 * /usuarios/me). Existe porque muito aluno errou nome, RGM ou curso no
 * autocadastro e so o admin conseguia arrumar, um a um - e vale para os
 * dois perfis: o admin corrige o proprio cadastro pela mesma tela.
 *
 * <p>Cada perfil mexe no que e seu: <b>aluno</b> em nome, RGM e curso;
 * <b>admin</b> em nome e CPF. RGM e CPF sao o identificador academico de
 * cada um e o que confirma identidade no "esqueci minha senha", entao
 * quem depende deles precisa conseguir corrigi-los.
 *
 * <p><b>Nao tem email nem perfil, de proposito.</b> O email e o login:
 * quem digitasse errado ao "corrigir" perderia o acesso a propria conta
 * e precisaria de outra pessoa para voltar - exatamente o problema que
 * esta tela veio resolver. O perfil nunca pode vir do cliente (seria
 * escalada de privilegio: qualquer aluno viraria admin mandando um
 * JSON). Senha continua pelo fluxo de "Esqueci minha senha".
 *
 * <p>Diferente de {@link NovoUsuarioRequest}, que e a tela de Usuarios
 * do admin e mexe em campos que o dono da conta nao deve mexer sozinho
 * (email e perfil, inclusive de outras pessoas).
 *
 * @param rgm   so se aplica a aluno; ignorado para admin
 * @param cpf   so se aplica a admin; ignorado para aluno
 * @param curso texto livre, opcional - so informativo, campo de aluno
 */
public record AtualizarPerfilRequest(
        @NotBlank String nome,
        @Pattern(regexp = "^\\d{8}$", message = "RGM deve ter exatamente 8 digitos") String rgm,
        @Pattern(regexp = "^\\d{11}$", message = "CPF deve ter exatamente 11 digitos") String cpf,
        String curso) {
}
