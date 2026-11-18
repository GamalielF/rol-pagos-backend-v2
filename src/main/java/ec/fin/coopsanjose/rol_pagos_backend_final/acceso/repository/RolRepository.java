package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Rol;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.repository.BaseRepository;

import java.util.Optional;

public interface RolRepository extends BaseRepository<Rol> {

    Optional<Rol> findByCodigo(String codigo);
}
