package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Permiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.EstadoRegistro;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.repository.BaseRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity.Usuario;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.UsuarioPermiso;

public interface UsuarioPermisoRepository extends BaseRepository<UsuarioPermiso> {

    Optional<UsuarioPermiso> findByUsuarioAndPermiso(Usuario usuario, Permiso permiso);

    @Query("select p.codigo from UsuarioPermiso up join up.usuario u join up.permiso p "
            + "where u.usernameAd = :username and up.estadoRegistro = :activo and p.estadoRegistro = :activo")
    List<String> codigosDePermisos(@Param("username") String username, @Param("activo") EstadoRegistro activo);

    /** Quiénes de esa lista tienen ese permiso suelto vigente. */
    @Query("select u.usernameAd from UsuarioPermiso up join up.usuario u join up.permiso p "
            + "where u.usernameAd in :usernames and p.codigo = :permiso and up.estadoRegistro = :activo")
    List<String> usuariosConPermiso(@Param("usernames") Collection<String> usernames, @Param("permiso") String permiso,
                                    @Param("activo") EstadoRegistro activo);
}
