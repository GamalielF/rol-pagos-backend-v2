package ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad;

import java.util.HashSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * SOLO DESARROLLO. Identifica al usuario por X-Dev-Usuario. Con X-Dev-Roles simula los roles indicados (y recibe los
 * permisos que esos roles tienen en la base); X-Dev-Permisos agrega permisos sueltos. SIN X-Dev-Roles usa los roles
 * y permisos reales del usuario.
 * SecurityConfig lo registra únicamente si app.seguridad.dev-auth-habilitado=true (perfil dev).
 */
@Slf4j
public class DevAuthFilter extends OncePerRequestFilter {

    public static final String CABECERA_USUARIO = "X-Dev-Usuario";
    public static final String CABECERA_ROLES = "X-Dev-Roles";
    public static final String CABECERA_PERMISOS = "X-Dev-Permisos";

    private final DirectorioUsuarios directorio;
    private final ResolutorAcceso resolutorAcceso;

    public DevAuthFilter(DirectorioUsuarios directorio, ResolutorAcceso resolutorAcceso) {
        this.directorio = directorio;
        this.resolutorAcceso = resolutorAcceso;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String solicitado = limpiar(request.getHeader(CABECERA_USUARIO));

        if (solicitado != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<PerfilDirectorio> perfil = directorio.buscarPorUsername(solicitado);

            if (perfil.isPresent() && !perfil.get().activo()) {
                // Deshabilitado en el AD: sin sesión (la petición sigue anónima y recibe 401)
                log.warn("Usuario inactivo en el directorio: se rechaza la sesión de desarrollo");
            } else {
                String username = perfil.map(PerfilDirectorio::username).orElse(solicitado);
                String cedula = perfil.map(PerfilDirectorio::cedula).orElse(null);
                String nombre = perfil.map(PerfilDirectorio::nombre).orElse(null);
                String cabeceraRoles = limpiar(request.getHeader(CABECERA_ROLES));

                UsuarioAutenticado usuario;
                if (cabeceraRoles != null) {                                                             // simulación
                    Set<String> roles = leerCodigos(cabeceraRoles);
                    roles.add(RolesDelSistema.EMPLEADO);           // como en la resolución real: todo usuario es EMPLEADO
                    Set<String> permisos = new HashSet<>(resolutorAcceso.permisosDeRoles(roles));
                    permisos.addAll(leerCodigos(limpiar(request.getHeader(CABECERA_PERMISOS))));
                    usuario = new UsuarioAutenticado(username, cedula, nombre, roles, permisos);
                } else {
                    ResolutorAcceso.RolesYPermisos reales = resolutorAcceso.resolver(username);             // acceso real
                    usuario = new UsuarioAutenticado(username, cedula, nombre, reales.roles(), reales.permisos());
                }
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(usuario, null, usuario.autoridades()));
            }
        }
        chain.doFilter(request, response);
    }

    /** Códigos separados por coma, en mayúsculas. Un código que no existe en la base simplemente no da nada. */
    private static Set<String> leerCodigos(String cabecera) {
        Set<String> codigos = new HashSet<>();
        if (cabecera == null) {
            return codigos;
        }
        for (String texto : cabecera.split(",")) {
            String codigo = texto.trim().toUpperCase(Locale.ROOT);
            if (!codigo.isEmpty()) {
                codigos.add(codigo);
            }
        }
        return codigos;
    }

    private String limpiar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}