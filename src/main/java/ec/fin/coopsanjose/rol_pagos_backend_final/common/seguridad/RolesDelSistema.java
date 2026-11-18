package ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad;

import java.util.Comparator;
import java.util.List;

/**
 * Los tres roles que existen siempre (los crea la migración V12 y no se pueden borrar). Los demás roles los crea el
 * administrador en la base de datos, por eso aquí solo hay constantes y no un enum: el código no debe conocer la
 * lista completa de roles, solo los permisos.
 */
public final class RolesDelSistema {

    /** Tiene todos los permisos, incluidos los que registren los módulos futuros. */
    public static final String ADMIN = "ADMIN";
    public static final String RRHH = "RRHH";
    /** Todo usuario activo lo es: no se asigna ni se guarda. */
    public static final String EMPLEADO = "EMPLEADO";

    /** Spring Security distingue los roles de los permisos con este prefijo: hasRole('ADMIN') busca 'ROLE_ADMIN'. */
    public static final String PREFIJO_AUTORIDAD = "ROLE_";

    /** ADMIN, RRHH y EMPLEADO primero (del más al menos privilegiado); los roles creados después, en orden alfabético. */
    public static final Comparator<String> ORDEN = Comparator
            .comparingInt((String rol) -> {
                int posicion = List.of(ADMIN, RRHH, EMPLEADO).indexOf(rol);
                return posicion < 0 ? Integer.MAX_VALUE : posicion;
            })
            .thenComparing(Comparator.naturalOrder());

    private RolesDelSistema() {
    }
}
