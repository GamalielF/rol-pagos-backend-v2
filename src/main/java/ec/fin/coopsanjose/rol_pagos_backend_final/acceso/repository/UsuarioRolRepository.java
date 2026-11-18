package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Rol;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.EstadoRegistro;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.repository.BaseRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity.Usuario;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.UsuarioRol;

public interface UsuarioRolRepository extends BaseRepository<UsuarioRol> {

    Optional<UsuarioRol> findByUsuarioAndRol(Usuario usuario, Rol rol);

    /** Los códigos de los roles vigentes de un usuario (un rol desactivado deja de contar para todos). */
    @Query("select r.codigo from UsuarioRol ur join ur.usuario u join ur.rol r "
            + "where u.usernameAd = :username and ur.estadoRegistro = :activo and r.estadoRegistro = :activo")
    List<String> codigosDeRoles(@Param("username") String username, @Param("activo") EstadoRegistro activo);

    @Query("select ur from UsuarioRol ur join ur.usuario u where u.usernameAd in :usernames and ur.estadoRegistro = :estado")
    List<UsuarioRol> deUsuarios(@Param("usernames") Collection<String> usernames, @Param("estado") EstadoRegistro estado);

    List<UsuarioRol> findByEstadoRegistro(EstadoRegistro estado);
}
