package ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Identidad del usuario de la petición: la arma el filtro de autenticación (JWT o, en desarrollo, las cabeceras).
 * Roles y permisos son CÓDIGOS (texto) porque viven en la base de datos y pueden crecer sin recompilar.
 */
public record UsuarioAutenticado(String username, String cedula, String nombre, Set<String> roles, Set<String> permisos) {

    public UsuarioAutenticado {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("El username es obligatorio");
        }
        roles = (roles == null) ? Set.of() : Set.copyOf(roles);              // copia inmutable
        permisos = (permisos == null) ? Set.of() : Set.copyOf(permisos);
    }

    public boolean tieneRol(String rol) {
        return roles.contains(rol);
    }

    public boolean tienePermiso(String permiso) {
        return permisos.contains(permiso);
    }

    /** Lo que Spring Security compara: ROLE_ADMIN para hasRole('ADMIN') y el código tal cual para hasAuthority(...). */
    public List<SimpleGrantedAuthority> autoridades() {
        return Stream.concat(roles.stream().map(rol -> RolesDelSistema.PREFIJO_AUTORIDAD + rol), permisos.stream())
                .sorted()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
