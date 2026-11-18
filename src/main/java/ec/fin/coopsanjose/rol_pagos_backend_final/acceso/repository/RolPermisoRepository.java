package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Permiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Rol;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.EstadoRegistro;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.RolPermiso;

public interface RolPermisoRepository extends BaseRepository<RolPermiso> {

    Optional<RolPermiso> findByRolAndPermiso(Rol rol, Permiso permiso);

    List<RolPermiso> findByRol(Rol rol);

    List<RolPermiso> findByEstadoRegistro(EstadoRegistro estado);

    /** Los permisos que dan esos roles. Cuentan solo si el rol, el permiso y el vínculo están activos. */
    @Query("select distinct p.codigo from RolPermiso rp join rp.rol r join rp.permiso p "
            + "where r.codigo in :roles and rp.estadoRegistro = :activo and r.estadoRegistro = :activo "
            + "and p.estadoRegistro = :activo")
    List<String> codigosDePermisos(@Param("roles") Collection<String> roles, @Param("activo") EstadoRegistro activo);
}
