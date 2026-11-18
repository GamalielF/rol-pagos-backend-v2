package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.request.CrearRolRequest;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.response.PermisoDto;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.response.RolDto;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Permiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Rol;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.RolPermiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.PermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.RolPermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.RolRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.entity.TipoAccion;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.service.AuditoriaAccionesService;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.EstadoRegistro;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.ConflictoException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.RecursoNoEncontradoException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.SolicitudInvalidaException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.RolesDelSistema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Definir los roles: crearlos y decidir qué permisos tiene cada uno. Cada cambio queda en la auditoría. */
@Service
@RequiredArgsConstructor
public class GestionRolesService {

    private static final EstadoRegistro ACTIVO = EstadoRegistro.ACTIVO;

    private final RolRepository roles;
    private final PermisoRepository permisos;
    private final RolPermisoRepository rolPermisos;
    private final AuditoriaAccionesService auditoria;

    /** El catálogo: lo que declaran los módulos, agrupado por módulo. */
    @Transactional(readOnly = true)
    public List<PermisoDto> permisos() {
        return permisos.findAllByEstadoRegistro(ACTIVO).stream()
                .sorted(Comparator.comparing(Permiso::getModulo).thenComparing(Permiso::getCodigo))
                .map(p -> new PermisoDto(p.getCodigo(), p.getModulo(), p.getDescripcion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RolDto> roles() {
        Map<Long, Set<String>> permisosPorRol = new HashMap<>();
        rolPermisos.findByEstadoRegistro(ACTIVO).stream()
                .filter(rp -> rp.getPermiso().getEstadoRegistro() == ACTIVO)
                .forEach(rp -> permisosPorRol.computeIfAbsent(rp.getRol().getId(), id -> new TreeSet<>())
                        .add(rp.getPermiso().getCodigo()));
        return roles.findAllByEstadoRegistro(ACTIVO).stream()
                .sorted(Comparator.comparing(Rol::getCodigo, RolesDelSistema.ORDEN))
                .map(r -> aDto(r, permisosPorRol.getOrDefault(r.getId(), Set.of())))
                .toList();
    }

    @Transactional
    public RolDto crear(CrearRolRequest solicitud) {
        String codigo = solicitud.codigo().trim().toUpperCase(Locale.ROOT);
        if (roles.findByCodigo(codigo).isPresent()) {
            throw new ConflictoException("Ya existe un rol con el código " + codigo);
        }
        List<Permiso> elegidos = buscarPermisos(solicitud.permisos());
        Rol rol = new Rol();
        rol.setCodigo(codigo);
        rol.setNombre(solicitud.nombre().trim());
        rol.setDescripcion(solicitud.descripcion() == null || solicitud.descripcion().isBlank() ? null : solicitud.descripcion().trim());
        roles.save(rol);
        elegidos.forEach(p -> rolPermisos.save(new RolPermiso(rol, p)));
        Set<String> codigos = elegidos.stream().map(Permiso::getCodigo).collect(Collectors.toCollection(TreeSet::new));
        auditoria.registrar(TipoAccion.ROL_ACCESO_CREADO, null, null, "rol=" + codigo + ", permisos=" + codigos);
        return aDto(rol, codigos);
    }

    /** Reemplaza la lista de permisos del rol. ADMIN no se edita: siempre los tiene todos. */
    @Transactional
    public RolDto definirPermisos(String codigoRol, List<String> codigosPermisos) {
        String codigo = codigoRol == null ? "" : codigoRol.trim().toUpperCase(Locale.ROOT);
        Rol rol = roles.findByCodigo(codigo).filter(r -> r.getEstadoRegistro() == ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el rol " + codigo));
        if (RolesDelSistema.ADMIN.equals(rol.getCodigo())) {
            throw new ConflictoException("El rol ADMIN tiene siempre todos los permisos: no se edita");
        }
        List<Permiso> elegidos = buscarPermisos(codigosPermisos);
        Set<Long> idsElegidos = elegidos.stream().map(Permiso::getId).collect(Collectors.toSet());
        Map<Long, RolPermiso> actuales = rolPermisos.findByRol(rol).stream()
                .collect(Collectors.toMap(rp -> rp.getPermiso().getId(), rp -> rp));
        actuales.values().forEach(rp -> {
            boolean debeTenerlo = idsElegidos.contains(rp.getPermiso().getId());
            if (debeTenerlo != (rp.getEstadoRegistro() == ACTIVO)) {
                if (debeTenerlo) {
                    rp.activar();
                } else {
                    rp.inactivar();
                }
                rolPermisos.save(rp);
            }
        });
        elegidos.stream().filter(p -> !actuales.containsKey(p.getId()))
                .forEach(p -> rolPermisos.save(new RolPermiso(rol, p)));
        Set<String> codigos = elegidos.stream().map(Permiso::getCodigo).collect(Collectors.toCollection(TreeSet::new));
        auditoria.registrar(TipoAccion.ROL_ACCESO_PERMISOS_DEFINIDOS, null, null, "rol=" + codigo + ", permisos=" + codigos);
        return aDto(rol, codigos);
    }

    /** Todos deben existir: un código mal escrito es un error, no se ignora en silencio. */
    private List<Permiso> buscarPermisos(List<String> codigos) {
        Set<String> pedidos = codigos.stream().filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toCollection(TreeSet::new));
        List<Permiso> encontrados = permisos.findByCodigoIn(pedidos).stream()
                .filter(p -> p.getEstadoRegistro() == ACTIVO).toList();
        if (encontrados.size() != pedidos.size()) {
            Set<String> faltan = new TreeSet<>(pedidos);
            encontrados.forEach(p -> faltan.remove(p.getCodigo()));
            throw new SolicitudInvalidaException("Permisos que no existen: " + faltan);
        }
        return encontrados;
    }

    private static RolDto aDto(Rol rol, Set<String> permisos) {
        return new RolDto(rol.getCodigo(), rol.getNombre(), rol.getDescripcion(), rol.esDelSistema(), List.copyOf(permisos));
    }
}
