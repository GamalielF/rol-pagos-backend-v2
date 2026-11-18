package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.config.AdministradoresProperties;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.RolPermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.UsuarioPermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.UsuarioRolRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.EstadoRegistro;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.ResolutorAcceso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.RolesDelSistema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Roles efectivos   = EMPLEADO + ADMIN (si es administrador de arranque) + sus roles asignados vigentes.
 * Permisos efectivos = los de esos roles + sus permisos sueltos vigentes.
 */
@Service
@RequiredArgsConstructor
public class ResolutorAccesoJpa implements ResolutorAcceso {

    private static final EstadoRegistro ACTIVO = EstadoRegistro.ACTIVO;

    private final UsuarioRolRepository usuarioRoles;
    private final UsuarioPermisoRepository usuarioPermisos;
    private final RolPermisoRepository rolPermisos;
    private final AdministradoresProperties administradores;

    @Override
    @Transactional(readOnly = true)
    public RolesYPermisos resolver(String username) {
        Set<String> roles = new HashSet<>(Set.of(RolesDelSistema.EMPLEADO));
        if (username == null || username.isBlank()) {
            return new RolesYPermisos(roles, permisosDeRoles(roles));
        }
        String usuario = username.trim().toLowerCase(Locale.ROOT);
        if (administradores.esAdministradorInicial(usuario)) {
            roles.add(RolesDelSistema.ADMIN);
        }
        roles.addAll(usuarioRoles.codigosDeRoles(usuario, ACTIVO));
        Set<String> permisos = new HashSet<>(permisosDeRoles(roles));
        permisos.addAll(usuarioPermisos.codigosDePermisos(usuario, ACTIVO));
        return new RolesYPermisos(roles, permisos);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> permisosDeRoles(Set<String> roles) {
        return roles.isEmpty() ? Set.of() : Set.copyOf(rolPermisos.codigosDePermisos(roles, ACTIVO));
    }
}
