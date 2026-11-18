package ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad;

import java.util.Set;

/**
 * Calcula a qué tiene acceso un usuario. Lo usan los filtros de autenticación en CADA petición, así que quitar un
 * rol o un permiso tiene efecto inmediato (no hay que esperar a que venza el token).
 */
public interface ResolutorAcceso {

    /** Códigos de roles y de permisos efectivos. */
    record RolesYPermisos(Set<String> roles, Set<String> permisos) {
        public RolesYPermisos {
            roles = Set.copyOf(roles);
            permisos = Set.copyOf(permisos);
        }
    }

    /** Siempre incluye EMPLEADO: todo usuario activo del AD lo es. */
    RolesYPermisos resolver(String username);

    /** Los permisos que dan esos roles (sin mirar a ningún usuario). Para simular roles en desarrollo. */
    Set<String> permisosDeRoles(Set<String> roles);
}
