package com.wecti.api.domain;

/**
 * Quem usa o sistema.
 *
 * <ul>
 *   <li><b>ADMIN</b> - cadastra eventos e usuarios, gera os QR de
 *       check-in, acompanha presenca, lanca pontos de gincana.</li>
 *   <li><b>PROFESSOR</b> - so consulta: a lista de usuarios e as notas
 *       distribuidas, que ele exporta para lancar no diario. Nao cria,
 *       nao edita, nao exclui nada.</li>
 *   <li><b>ALUNO</b> - se autocadastra, participa, pontua e converte a
 *       pontuacao em nota.</li>
 * </ul>
 *
 * <p><b>Sobre o PROFESSOR voltar.</b> Este enum ja teve PROFESSOR, que
 * foi removido quando o professor (stakeholder) confirmou que nao
 * precisava dele. O comentario que ficava aqui dizia "nao reintroduzir
 * sem confirmar de novo" - e em novembro de 2026 ele confirmou de novo,
 * agora no sentido inverso: pediu "login como professor com acesso a
 * usuarios e notas apenas", para a equipe docente baixar as notas sem
 * depender do admin.
 *
 * <p>O acesso dele e <b>somente leitura</b>, decisao tomada junto com o
 * pedido: excluir aluno apaga inscricoes, presencas e certificados em
 * cascata e sem desfazer (ver UsuarioService.excluir), e nao ha motivo
 * para esse poder estar na conta de quem so precisa conferir RGM e
 * baixar um CSV.
 */
public enum Perfil {
    ADMIN,
    PROFESSOR,
    ALUNO
}
