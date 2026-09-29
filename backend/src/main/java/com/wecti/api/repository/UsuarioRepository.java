package com.wecti.api.repository;

import com.wecti.api.domain.Perfil;
import com.wecti.api.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// JpaSpecificationExecutor: usado pra combinar os filtros dinamicos
// (perfil + busca por texto) da paginacao de GET /usuarios - ver
// UsuarioService.listar.
public interface UsuarioRepository extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

    /**
     * Busca do login e do "esqueci minha senha". Ignora maiuscula de
     * proposito: o teclado do celular maiuscula a primeira letra sozinho,
     * e o email do aluno nao muda por causa disso.
     *
     * <p>Nao basta contar com a collation do MySQL
     * ({@code utf8mb4_unicode_ci}, que ja e case-insensitive): a regra
     * ficaria escondida numa propriedade do banco, invisivel no codigo e
     * dependente de o banco ser recriado com a mesma collation. Quem
     * chama normaliza a entrada com
     * {@link com.wecti.api.security.CredenciaisDigitadas#email}; isto
     * aqui cobre as linhas <b>ja gravadas</b> com maiuscula antes de a
     * normalizacao existir.
     *
     * <p>O {@code upper()} impede o uso do indice de email, mas o custo
     * some perto do BCrypt do login (~100ms contra um scan de poucos
     * milhares de linhas).
     */
    Optional<Usuario> findByEmailIgnoreCase(String email);

    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByRgm(String rgm);
    Optional<Usuario> findByCpf(String cpf);
    List<Usuario> findByPerfil(Perfil perfil);
}
