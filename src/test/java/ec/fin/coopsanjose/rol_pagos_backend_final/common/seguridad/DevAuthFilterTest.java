package ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DevAuthFilterTest {

    private final Map<String, PerfilDirectorio> directorio = new HashMap<>();
    private final DevAuthFilter filtro = new DevAuthFilter(
            username -> Optional.ofNullable(directorio.get(username)),
            UsuariosDePrueba.resolutor(username -> new ResolutorAcceso.RolesYPermisos(Set.of(RolesDelSistema.EMPLEADO), Set.of())));

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    private Authentication ejecutar(MockHttpServletRequest peticion) throws Exception {
        filtro.doFilter(peticion, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private MockHttpServletRequest como(String usuario, String roles) {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader(DevAuthFilter.CABECERA_USUARIO, usuario);
        if (roles != null) {
            peticion.addHeader(DevAuthFilter.CABECERA_ROLES, roles);
        }
        return peticion;
    }

    private PerfilDirectorio perfil(boolean activo) {
        return new PerfilDirectorio("jperez", "1712345675", "PEREZ LOPEZ JUAN", "jperez@coopsanjose.fin.ec", activo);
    }

    @Test
    void sinCabeceraNoAutentica() throws Exception {
        assertThat(ejecutar(new MockHttpServletRequest())).isNull();
    }

    @Test
    void laCedulaYElNombreSalenDelDirectorioNuncaDeLaPeticion() throws Exception {
        directorio.put("jperez", perfil(true));
        MockHttpServletRequest peticion = como("jperez", "rrhh, empleado");
        peticion.addHeader("X-Dev-Cedula", "1709876542");   // intento de suplantación: se ignora

        Authentication auth = ejecutar(peticion);

        UsuarioAutenticado usuario = (UsuarioAutenticado) auth.getPrincipal();
        assertThat(usuario.cedula()).isEqualTo("1712345675");
        assertThat(usuario.nombre()).isEqualTo("PEREZ LOPEZ JUAN");
        List<String> autoridades = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(autoridades).contains("ROLE_RRHH", "ROLE_EMPLEADO", "ROLPAGO_GESTIONAR", "ROLPAGO_CONSULTAR_PROPIO");
    }

    @Test
    void unUsuarioInactivoEnElAdNoSeAutentica() throws Exception {
        directorio.put("jperez", perfil(false));

        assertThat(ejecutar(como("jperez", "EMPLEADO"))).isNull();
    }

    @Test
    void unUsuarioDesconocidoSeAutenticaSinCedula() throws Exception {
        UsuarioAutenticado usuario = (UsuarioAutenticado) ejecutar(como("nuevo", "EMPLEADO")).getPrincipal();

        assertThat(usuario.username()).isEqualTo("nuevo");
        assertThat(usuario.cedula()).isNull();
    }

    @Test
    void usaElCodigoDeUsuarioCanonicoDelDirectorio() throws Exception {
        directorio.put("JPEREZ", perfil(true));

        UsuarioAutenticado usuario = (UsuarioAutenticado) ejecutar(como("JPEREZ", "EMPLEADO")).getPrincipal();

        assertThat(usuario.username()).isEqualTo("jperez");
    }

    @Test
    void sinRolesAsumeEmpleado() throws Exception {
        UsuarioAutenticado usuario = (UsuarioAutenticado) ejecutar(como("jperez", null)).getPrincipal();

        assertThat(usuario.roles()).containsExactly(RolesDelSistema.EMPLEADO);
    }

    @Test
    void rolDesconocidoNuncaEscalaPrivilegios() throws Exception {
        UsuarioAutenticado usuario = (UsuarioAutenticado) ejecutar(como("jperez", "SUPERADMIN")).getPrincipal();

        assertThat(usuario.tieneRol(RolesDelSistema.ADMIN)).isFalse();
        // un rol que no existe no da ningún permiso: solo los de EMPLEADO
        assertThat(usuario.roles()).doesNotContain(RolesDelSistema.ADMIN);
        assertThat(usuario.permisos()).containsExactly("ROLPAGO_CONSULTAR_PROPIO");
    }

    @Test
    void laSimulacionDaLosPermisosDelRolYLosSueltosDeLaCabecera() throws Exception {
        MockHttpServletRequest peticion = como("jperez", "RRHH");
        peticion.addHeader(DevAuthFilter.CABECERA_PERMISOS, " rolpago_ver_ajenos , ");

        UsuarioAutenticado usuario = (UsuarioAutenticado) ejecutar(peticion).getPrincipal();

        assertThat(usuario.permisos()).containsExactlyInAnyOrder("ROLPAGO_CONSULTAR_PROPIO", "ROLPAGO_GESTIONAR",
                "ROLPAGO_VER_AJENOS");
    }

    @Test
    void sinCabeceraDeRolesLaCabeceraDePermisosSeIgnora() throws Exception {
        MockHttpServletRequest peticion = como("jperez", null);
        peticion.addHeader(DevAuthFilter.CABECERA_PERMISOS, "ADMIN_USUARIOS");

        UsuarioAutenticado usuario = (UsuarioAutenticado) ejecutar(peticion).getPrincipal();

        assertThat(usuario.tienePermiso("ADMIN_USUARIOS")).isFalse();
    }
}