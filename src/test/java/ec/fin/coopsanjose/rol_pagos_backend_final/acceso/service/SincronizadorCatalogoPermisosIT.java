package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Permiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.RolPermiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.PermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.RolPermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.RolRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.EstadoRegistro;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.ResolutorAcceso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.RolesDelSistema;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.DefinicionPermiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.ModuloSeguridad;
import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.SoporteRolesPago;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El "plug and play": un módulo nuevo (aquí, un VACACIONES de mentira) declara sus permisos y, al arrancar, quedan
 * registrados y repartidos sin escribir ninguna migración.
 */
class SincronizadorCatalogoPermisosIT extends SoporteRolesPago {

    private static final DefinicionPermiso SOLICITAR = DefinicionPermiso.de("ZZVAC_SOLICITAR",
            "Solicitar vacaciones", RolesDelSistema.EMPLEADO);
    private static final DefinicionPermiso APROBAR = DefinicionPermiso.de("ZZVAC_APROBAR",
            "Aprobar vacaciones", RolesDelSistema.RRHH);

    @Autowired private List<ModuloSeguridad> modulosReales;
    @Autowired private PermisoRepository permisos;
    @Autowired private RolRepository roles;
    @Autowired private RolPermisoRepository rolPermisos;
    @Autowired private ResolutorAcceso resolutor;

    private static ModuloSeguridad modulo(String nombre, DefinicionPermiso... declarados) {
        return new ModuloSeguridad() {
            @Override
            public String modulo() {
                return nombre;
            }

            @Override
            public List<DefinicionPermiso> permisos() {
                return List.of(declarados);
            }

            @Override
            public void reglas(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry reglas) {
                // en esta prueba no hay rutas
            }
        };
    }

    private void sincronizar(ModuloSeguridad... modulos) {
        new SincronizadorCatalogoPermisos(List.of(modulos), permisos, roles, rolPermisos).run(null);
        em.flush();
        em.clear();
    }

    private List<String> rolesCon(String permiso) {
        return rolPermisos.findByEstadoRegistro(EstadoRegistro.ACTIVO).stream()
                .filter(rp -> rp.getPermiso().getCodigo().equals(permiso))
                .map(rp -> rp.getRol().getCodigo()).sorted().toList();
    }

    @Test
    void alArrancarTodosLosPermisosDeLosModulosEstanEnLaBase() {
        List<String> declarados = modulosReales.stream().flatMap(m -> m.permisos().stream())
                .map(DefinicionPermiso::codigo).toList();

        assertThat(declarados).contains("ROLPAGO_GESTIONAR", "ADMIN_USUARIOS", "ADMIN_AUDITORIA");
        assertThat(permisos.findByCodigoIn(declarados)).extracting(Permiso::getCodigo)
                .containsExactlyInAnyOrderElementsOf(declarados);
    }

    @Test
    void unModuloNuevoRegistraSusPermisosYLosDaAAdminYASusRolesPorDefecto() {
        sincronizar(modulo("ZZVACACIONES", SOLICITAR, APROBAR));

        assertThat(permisos.findByCodigo("ZZVAC_SOLICITAR")).get()
                .satisfies(p -> assertThat(p.getModulo()).isEqualTo("ZZVACACIONES"));
        assertThat(rolesCon("ZZVAC_SOLICITAR")).containsExactly("ADMIN", "EMPLEADO");
        assertThat(rolesCon("ZZVAC_APROBAR")).containsExactly("ADMIN", "RRHH");
        // y ya forma parte de lo que tiene cualquier usuario
        assertThat(resolutor.resolver("zz.cualquiera").permisos()).contains("ZZVAC_SOLICITAR").doesNotContain("ZZVAC_APROBAR");
    }

    @Test
    void volverASincronizarNoDuplicaNiDevuelveLoQueElAdministradorQuito() {
        sincronizar(modulo("ZZVACACIONES", SOLICITAR));
        RolPermiso deEmpleado = rolPermisos.findByEstadoRegistro(EstadoRegistro.ACTIVO).stream()
                .filter(rp -> rp.getPermiso().getCodigo().equals("ZZVAC_SOLICITAR")
                        && rp.getRol().getCodigo().equals(RolesDelSistema.EMPLEADO))
                .findFirst().orElseThrow();
        deEmpleado.inactivar();   // el administrador decidió que EMPLEADO no lo tenga
        rolPermisos.save(deEmpleado);
        em.flush();

        sincronizar(modulo("ZZVACACIONES", DefinicionPermiso.de("ZZVAC_SOLICITAR", "Pedir vacaciones", RolesDelSistema.EMPLEADO)));

        assertThat(rolesCon("ZZVAC_SOLICITAR")).containsExactly("ADMIN");
        assertThat(permisos.findByCodigo("ZZVAC_SOLICITAR")).get()
                .satisfies(p -> assertThat(p.getDescripcion()).isEqualTo("Pedir vacaciones"));
    }

    @Test
    void dosModulosConElMismoCodigoImpidenArrancar() {
        assertThatThrownBy(() -> sincronizar(modulo("ZZUNO", SOLICITAR), modulo("ZZDOS", SOLICITAR)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ZZVAC_SOLICITAR")
                .hasMessageContaining("ZZUNO").hasMessageContaining("ZZDOS");
    }

    @Test
    void unRolPorDefectoQueNoExisteSeOmiteSinFallar() {
        sincronizar(modulo("ZZVACACIONES", DefinicionPermiso.de("ZZVAC_FIRMAR", "Firmar", "ZZ_NO_EXISTE")));

        assertThat(rolesCon("ZZVAC_FIRMAR")).containsExactly("ADMIN");
    }
}
