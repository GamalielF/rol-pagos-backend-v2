package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Permiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Rol;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.UsuarioPermiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.UsuarioRol;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.PermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.RolPermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.RolRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.UsuarioPermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.UsuarioRolRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.BaseEntity;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.EstadoRegistro;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.RecursoNoEncontradoException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.SolicitudInvalidaException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.RolesDelSistema;
import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Las operaciones sobre el acceso de UN usuario: darle o quitarle un rol o un permiso suelto, y consultar qué tiene.
 * No sabe nada del directorio, del front ni de la auditoría: eso lo decide quien lo usa (AdministracionUsuariosService).
 * Todas las operaciones son idempotentes: dar algo que ya tiene, o quitar algo que no tiene, no cambia nada.
 */
@Service
@RequiredArgsConstructor
public class AsignacionAccesoService {

    private static final EstadoRegistro ACTIVO = EstadoRegistro.ACTIVO;

    private final RolRepository roles;
    private final PermisoRepository permisos;
    private final RolPermisoRepository rolPermisos;
    private final UsuarioRolRepository usuarioRoles;
    private final UsuarioPermisoRepository usuarioPermisos;

    /** Un rol vigente que se puede asignar (EMPLEADO no: lo es todo usuario). */
    @Transactional(readOnly = true)
    public Rol rolAsignable(String codigo) {
        String limpio = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        if (RolesDelSistema.EMPLEADO.equals(limpio)) {
            throw new SolicitudInvalidaException("EMPLEADO no se asigna: lo es todo usuario activo del directorio");
        }
        return roles.findByCodigo(limpio).filter(r -> r.getEstadoRegistro() == ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol " + limpio));
    }

    @Transactional(readOnly = true)
    public Permiso permiso(String codigo) {
        return permisos.findByCodigo(codigo).filter(p -> p.getEstadoRegistro() == ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el permiso " + codigo));
    }

    /** @return true si cambió algo (no lo tenía, o lo tenía revocado) */
    @Transactional
    public boolean asignarRol(Usuario usuario, Rol rol) {
        UsuarioRol vinculo = usuarioRoles.findByUsuarioAndRol(usuario, rol).orElseGet(() -> new UsuarioRol(usuario, rol));
        return activar(vinculo, vinculo.getId() == null, () -> usuarioRoles.save(vinculo));
    }

    /** @return true si lo tenía y se le quitó */
    @Transactional
    public boolean revocarRol(Usuario usuario, Rol rol) {
        return usuarioRoles.findByUsuarioAndRol(usuario, rol)
                .filter(v -> v.getEstadoRegistro() == ACTIVO)
                .map(v -> { v.inactivar(); usuarioRoles.save(v); return true; })
                .orElse(false);
    }

    @Transactional
    public boolean darPermiso(Usuario usuario, Permiso permiso) {
        UsuarioPermiso vinculo = usuarioPermisos.findByUsuarioAndPermiso(usuario, permiso)
                .orElseGet(() -> new UsuarioPermiso(usuario, permiso));
        return activar(vinculo, vinculo.getId() == null, () -> usuarioPermisos.save(vinculo));
    }

    @Transactional
    public boolean quitarPermiso(Usuario usuario, Permiso permiso) {
        return usuarioPermisos.findByUsuarioAndPermiso(usuario, permiso)
                .filter(v -> v.getEstadoRegistro() == ACTIVO)
                .map(v -> { v.inactivar(); usuarioPermisos.save(v); return true; })
                .orElse(false);
    }

    /** Los roles asignados vigentes de cada usuario de la lista. Las claves van en minúsculas. */
    @Transactional(readOnly = true)
    public Map<String, List<String>> rolesDe(Collection<String> usernames) {
        if (usernames.isEmpty()) {
            return Map.of();
        }
        return usuarioRoles.deUsuarios(usernames, ACTIVO).stream()
                .filter(v -> v.getRol().getEstadoRegistro() == ACTIVO)
                .collect(Collectors.groupingBy(v -> v.getUsuario().getUsernameAd().toLowerCase(Locale.ROOT),
                        Collectors.mapping(v -> v.getRol().getCodigo(), Collectors.toList())));
    }

    /** Todos los usuarios con algún rol asignado vigente (en minúsculas). */
    @Transactional(readOnly = true)
    public Set<String> usuariosConRoles() {
        return usuarioRoles.findByEstadoRegistro(ACTIVO).stream()
                .filter(v -> v.getRol().getEstadoRegistro() == ACTIVO)
                .map(v -> v.getUsuario().getUsernameAd().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    /** Quiénes de la lista tienen ese permiso como permiso suelto (en minúsculas). */
    @Transactional(readOnly = true)
    public Set<String> conPermisoSuelto(Collection<String> usernames, String permiso) {
        if (usernames.isEmpty()) {
            return Set.of();
        }
        return usuarioPermisos.usuariosConPermiso(usernames, permiso, ACTIVO).stream()
                .map(u -> u.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    /** Los roles que incluyen ese permiso. */
    @Transactional(readOnly = true)
    public Set<String> rolesConPermiso(String permiso) {
        Set<String> resultado = new HashSet<>();
        rolPermisos.findByEstadoRegistro(ACTIVO).stream()
                .filter(rp -> rp.getPermiso().getCodigo().equals(permiso))
                .forEach(rp -> resultado.add(rp.getRol().getCodigo()));
        return resultado;
    }

    /** Activa el vínculo (nuevo o revocado) y lo guarda. Si ya estaba activo no hace nada. */
    private static boolean activar(BaseEntity vinculo, boolean nuevo, Runnable guardar) {
        if (!nuevo && vinculo.getEstadoRegistro() == ACTIVO) {
            return false;
        }
        vinculo.activar();
        guardar.run();
        return true;
    }
}
