package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.response;

import java.util.List;

/** delSistema: ADMIN, RRHH y EMPLEADO, que no se borran ni se renombran. permisos: códigos, ordenados. */
public record RolDto(String codigo, String nombre, String descripcion, boolean delSistema, List<String> permisos) {
}
