package ec.fin.coopsanjose.rol_pagos_backend_final.administracion.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.config.AdministradoresProperties;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Rol;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.service.AsignacionAccesoService;
import ec.fin.coopsanjose.rol_pagos_backend_final.administracion.dto.response.RolAsignadoDto;
import ec.fin.coopsanjose.rol_pagos_backend_final.administracion.dto.response.UsuarioAdminDto;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.entity.TipoAccion;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.service.AuditoriaAccionesService;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.EstadoRegistro;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.ConflictoException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.RecursoNoEncontradoException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.SolicitudInvalidaException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.response.PaginaDto;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.RolesDelSistema;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.UsuarioActual;
import ec.fin.coopsanjose.rol_pagos_backend_final.directorio.entity.DirectorioAd;
import ec.fin.coopsanjose.rol_pagos_backend_final.directorio.repository.DirectorioAdRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.seguridad.SeguridadRolPago;
import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity.Usuario;
import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.service.RegistroUsuarios;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lo que hace el Admin con los roles de los usuarios: valida contra el directorio, aplica las reglas de la pantalla
 * de administración, deja la auditoría y arma las respuestas. Las operaciones de acceso en sí (dar o quitar un rol o
 * un permiso) las hace AsignacionAccesoService.
 */
@Service
@RequiredArgsConstructor
public class AdministracionUsuariosService {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{1,50}");
    private static final int TAMANO_POR_DEFECTO = 50;
    private static final int TAMANO_MAXIMO = 200;
    private static final String VER_AJENOS = SeguridadRolPago.VER_AJENOS;

    private final DirectorioAdRepository directorioRepository;
    private final AsignacionAccesoService acceso;
    private final AdministradoresProperties administradores;
    private final AuditoriaAccionesService auditoria;
    private final RegistroUsuarios usuarios;

    // ------------------------------------------------------------ consultas

    /** Usuarios ACTIVOS del directorio, con sus roles. Busca por nombre o código de usuario. */
    @Transactional(readOnly = true)
    public PaginaDto<UsuarioAdminDto> buscar(String texto, int pagina, int tamano) {
        Page<DirectorioAd> resultado = directorioRepository.buscar(EstadoRegistro.ACTIVO, patron(texto),
                PageRequest.of(Math.max(pagina, 0), tamano < 1 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO)));
        List<String> usernames = resultado.getContent().stream().map(d -> clave(d.getUsernameAd())).toList();
        Contexto contexto = contexto(usernames);
        return PaginaDto.de(resultado, d -> aDto(clave(d.getUsernameAd()), d, contexto));
    }

    /** Todos los que tienen algún rol asignado, incluidos los administradores de arranque y los dados de baja. */
    @Transactional(readOnly = true)
    public List<UsuarioAdminDto> privilegiados() {
        Set<String> usernames = new TreeSet<>(administradores.administradoresIniciales());
        usernames.addAll(acceso.usuariosConRoles());
        if (usernames.isEmpty()) {
            return List.of();
        }
        Map<String, DirectorioAd> directorio = directorioRepository.findByUsernameAdIn(usernames).stream()
                .collect(Collectors.toMap(d -> clave(d.getUsernameAd()), Function.identity(), (a, b) -> a));
        Contexto contexto = contexto(usernames);
        return usernames.stream()
                .map(u -> aDto(u, directorio.get(u), contexto))
                .sorted(Comparator.comparing((UsuarioAdminDto u) -> u.nombre() == null ? "￿" : u.nombre(),
                        String.CASE_INSENSITIVE_ORDER).thenComparing(UsuarioAdminDto::username))
                .toList();
    }

    // ------------------------------------------------------------ cambios

    /**
     * Idempotente. verRolesAjenos solo afecta a RRHH y se guarda como el permiso suelto ROLPAGO_VER_AJENOS del usuario
     * (así funcionaba la pantalla antes de existir los permisos). Por defecto, true.
     */
    @Transactional
    public UsuarioAdminDto otorgar(String usernameSolicitado, String codigoRol, Boolean verRolesAjenos) {
        Rol rol = acceso.rolAsignable(codigoRol);
        String username = normalizar(usernameSolicitado);
        DirectorioAd persona = directorioRepository.findByUsernameAd(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe en el directorio"));
        if (persona.getEstadoRegistro() != EstadoRegistro.ACTIVO) {
            throw new ConflictoException("El usuario está inactivo en el AD: no se le puede asignar un rol");
        }
        boolean esAdmin = RolesDelSistema.ADMIN.equals(rol.getCodigo());
        if (esAdmin && administradores.esAdministradorInicial(username)) {
            throw new ConflictoException("Ya es administrador de arranque (se define en la configuración)");
        }
        boolean cambia = acceso.asignarRol(persona.getUsuario(), rol);
        String detalle = "usuario=" + username + ", rol=" + rol.getCodigo();
        if (RolesDelSistema.RRHH.equals(rol.getCodigo())) {
            boolean ver = verRolesAjenos == null || verRolesAjenos;
            cambia |= ver ? acceso.darPermiso(persona.getUsuario(), acceso.permiso(VER_AJENOS))
                    : acceso.quitarPermiso(persona.getUsuario(), acceso.permiso(VER_AJENOS));
            detalle += ", verRolesAjenos=" + ver;
        }
        if (cambia) {
            auditoria.registrar(TipoAccion.USUARIO_ROL_OTORGADO, null, null, detalle);
        }
        return aDto(username, persona, contexto(List.of(username)));
    }

    @Transactional
    public UsuarioAdminDto revocar(String usernameSolicitado, String codigoRol) {
        Rol rol = acceso.rolAsignable(codigoRol);
        String username = normalizar(usernameSolicitado);
        boolean esAdmin = RolesDelSistema.ADMIN.equals(rol.getCodigo());
        if (esAdmin && administradores.esAdministradorInicial(username)) {
            throw new ConflictoException("Es administrador de arranque: solo se puede quitar desde la configuración");
        }
        if (esAdmin && UsuarioActual.obtener().map(u -> u.username().equalsIgnoreCase(username)).orElse(false)) {
            throw new ConflictoException("No puede quitarse a sí mismo el rol ADMIN");
        }
        Usuario usuario = usuarios.buscar(username).orElse(null);   // por USUARIOS: puede ya no estar en el directorio
        if (usuario == null || !acceso.revocarRol(usuario, rol)) {
            throw new RecursoNoEncontradoException("El usuario no tiene ese rol asignado");
        }
        if (RolesDelSistema.RRHH.equals(rol.getCodigo())) {
            // En la pantalla, "ver roles ajenos" es parte del rol RRHH: se va con él
            acceso.quitarPermiso(usuario, acceso.permiso(VER_AJENOS));
        }
        auditoria.registrar(TipoAccion.USUARIO_ROL_REVOCADO, null, null, "usuario=" + username + ", rol=" + rol.getCodigo());
        return aDto(username, directorioRepository.findByUsernameAd(username).orElse(null), contexto(List.of(username)));
    }

    // ------------------------------------------------------------ utilidades

    /** Lo que hace falta para armar las filas de una página, leído de una vez (y no fila por fila). */
    private record Contexto(Map<String, List<String>> roles, Set<String> verAjenosSuelto, Set<String> rolesQueVenAjenos) {
    }

    private Contexto contexto(Collection<String> usernames) {
        return new Contexto(acceso.rolesDe(usernames), acceso.conPermisoSuelto(usernames, VER_AJENOS),
                acceso.rolesConPermiso(VER_AJENOS));
    }

    private UsuarioAdminDto aDto(String username, DirectorioAd directorio, Contexto contexto) {
        boolean inicial = administradores.esAdministradorInicial(username);
        boolean verSuelto = contexto.verAjenosSuelto().contains(username);
        List<RolAsignadoDto> roles = new ArrayList<>();
        if (inicial) {
            roles.add(new RolAsignadoDto(RolesDelSistema.ADMIN, "CONFIGURACION", true));
        }
        contexto.roles().getOrDefault(username, List.of()).stream()
                .filter(rol -> !(inicial && RolesDelSistema.ADMIN.equals(rol)))
                .sorted(RolesDelSistema.ORDEN)
                .forEach(rol -> roles.add(new RolAsignadoDto(rol, "ASIGNADO",
                        contexto.rolesQueVenAjenos().contains(rol) || verSuelto)));
        return new UsuarioAdminDto(username,
                directorio == null ? null : directorio.getNombreCompleto(),
                directorio == null ? null : directorio.getCargo(),
                directorio == null ? "NO_EXISTE" : directorio.getEstadoRegistro().name(),
                roles);
    }

    private static String clave(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static String normalizar(String username) {
        String limpio = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(limpio).matches()) {
            throw new SolicitudInvalidaException("Código de usuario inválido");
        }
        return limpio;
    }

    /** Minúsculas y sin los comodines de LIKE que el usuario pudiera teclear. */
    private static String patron(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim().toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", "");
        if (limpio.isEmpty()) {
            return null;
        }
        return "%" + (limpio.length() > 50 ? limpio.substring(0, 50) : limpio) + "%";
    }
}
