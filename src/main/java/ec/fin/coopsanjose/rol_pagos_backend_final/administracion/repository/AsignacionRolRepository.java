package ec.fin.coopsanjose.rol_pagos_backend_final.administracion.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import ec.fin.coopsanjose.rol_pagos_backend_final.administracion.entity.AsignacionRol;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.EstadoRegistro;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.repository.BaseRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.Rol;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AsignacionRolRepository extends BaseRepository<AsignacionRol> {

    @Query("select a from AsignacionRol a join a.usuario u where u.usernameAd = :username and a.rol = :rol")
    Optional<AsignacionRol> findByUsernameAdAndRol(@Param("username") String usernameAd, @Param("rol") Rol rol);

    @Query("select a from AsignacionRol a join a.usuario u where u.usernameAd = :username and a.estadoRegistro = :estado")
    List<AsignacionRol> findByUsernameAdAndEstadoRegistro(@Param("username") String usernameAd,
                                                          @Param("estado") EstadoRegistro estadoRegistro);

    @Query("select a from AsignacionRol a join a.usuario u where u.usernameAd in :usernames and a.estadoRegistro = :estado")
    List<AsignacionRol> findByUsernameAdInAndEstadoRegistro(@Param("usernames") Collection<String> usernames,
                                                            @Param("estado") EstadoRegistro estadoRegistro);

    List<AsignacionRol> findByEstadoRegistro(EstadoRegistro estadoRegistro);
}