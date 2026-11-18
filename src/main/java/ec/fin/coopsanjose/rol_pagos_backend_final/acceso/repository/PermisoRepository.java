package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Permiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.repository.BaseRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PermisoRepository extends BaseRepository<Permiso> {

    Optional<Permiso> findByCodigo(String codigo);

    List<Permiso> findByCodigoIn(Collection<String> codigos);
}
