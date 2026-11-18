package ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad;

import ec.fin.coopsanjose.rol_pagos_backend_final.administracion.seguridad.SeguridadAdministracion;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.seguridad.SeguridadAuditoria;
import ec.fin.coopsanjose.rol_pagos_backend_final.directorio.seguridad.SeguridadDirectorio;
import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.seguridad.SeguridadRolPago;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Usuarios para pruebas unitarias (sin base de datos), con los permisos que la migración V12 da a cada rol del sistema.
 * RRHH incluye "ver roles ajenos", que es lo que la pantalla de administración le da por defecto.
 */
public final class UsuariosDePrueba {

    public static final Map<String, Set<String>> PERMISOS_POR_ROL = Map.of(
            RolesDelSistema.EMPLEADO, Set.of(SeguridadRolPago.CONSULTAR_PROPIO),
            RolesDelSistema.RRHH, Set.of(SeguridadRolPago.GESTIONAR, SeguridadRolPago.VER_AJENOS),
            RolesDelSistema.ADMIN, Set.of(SeguridadRolPago.CONSULTAR_PROPIO, SeguridadRolPago.VER_AJENOS,
                    SeguridadRolPago.GESTIONAR, SeguridadAdministracion.USUARIOS, SeguridadDirectorio.ADMINISTRAR,
                    SeguridadAuditoria.CONSULTAR));

    private UsuariosDePrueba() {
    }

    /** Con los roles indicados (más EMPLEADO, que lo es todo usuario) y los permisos que esos roles tienen por defecto. */
    public static UsuarioAutenticado con(String username, String cedula, String nombre, Set<String> roles) {
        Set<String> todos = new HashSet<>(roles);
        todos.add(RolesDelSistema.EMPLEADO);
        return new UsuarioAutenticado(username, cedula, nombre, todos, permisosDe(todos));
    }

    public static Set<String> permisosDe(Set<String> roles) {
        Set<String> permisos = new HashSet<>();
        roles.forEach(rol -> permisos.addAll(PERMISOS_POR_ROL.getOrDefault(rol, Set.of())));
        return permisos;
    }

    /** Un resolutor que responde con la función dada; la simulación de roles usa los permisos por defecto. */
    public static ResolutorAcceso resolutor(Function<String, ResolutorAcceso.RolesYPermisos> respuesta) {
        return new ResolutorAcceso() {
            @Override
            public RolesYPermisos resolver(String username) {
                return respuesta.apply(username);
            }

            @Override
            public Set<String> permisosDeRoles(Set<String> roles) {
                return permisosDe(roles);
            }
        };
    }

    /** Un resolutor fijo: devuelve siempre esos roles y permisos, y para la simulación usa los permisos por defecto. */
    public static ResolutorAcceso resolutor(Set<String> roles, Set<String> permisos) {
        return new ResolutorAcceso() {
            @Override
            public RolesYPermisos resolver(String username) {
                return new RolesYPermisos(roles, permisos);
            }

            @Override
            public Set<String> permisosDeRoles(Set<String> rolesPedidos) {
                return permisosDe(rolesPedidos);
            }
        };
    }
}
