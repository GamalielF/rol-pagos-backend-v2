package ec.fin.coopsanjose.rol_pagos_backend_final.administracion.controller;

import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.entity.TipoAccion;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.repository.AccionRolPagoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.SoporteRolesPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Crear un rol con permisos, dárselo a alguien y comprobar que con eso, y sin tocar código, entra donde debe. */
class AdminRolesControllerIT extends SoporteRolesPago {

    @Autowired private WebApplicationContext contexto;
    @Autowired private AccionRolPagoRepository acciones;
    private MockMvc mockMvc;

    @BeforeEach
    void escenario() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        persona("zz.pedro", CEDULA_JUAN, NOMBRE_JUAN, true);
    }

    private static RequestPostProcessor real(String usuario) {
        return peticion -> {
            peticion.addHeader("X-Dev-Usuario", usuario);
            return peticion;
        };
    }

    private static RequestPostProcessor simulada(String usuario, String roles) {
        return peticion -> {
            peticion.addHeader("X-Dev-Usuario", usuario);
            peticion.addHeader("X-Dev-Roles", roles);
            return peticion;
        };
    }

    private void crearAuditor() throws Exception {
        mockMvc.perform(post("/admin/roles").with(real("fsepa")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"ZZ_AUDITOR\",\"nombre\":\"Auditor interno\",\"permisos\":[\"ADMIN_AUDITORIA\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.codigo").value("ZZ_AUDITOR"))
                .andExpect(jsonPath("$.content.delSistema").value(false))
                .andExpect(jsonPath("$.content.permisos[0]").value("ADMIN_AUDITORIA"));
    }

    @Test
    void elAdminVeElCatalogoYLosRolesDelSistema() throws Exception {
        mockMvc.perform(get("/admin/permisos").with(real("fsepa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].codigo", hasItems("ROLPAGO_GESTIONAR", "ADMIN_USUARIOS")));
        mockMvc.perform(get("/admin/roles").with(real("fsepa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codigo").value("ADMIN"))
                .andExpect(jsonPath("$.content[*].codigo", hasItems("ADMIN", "RRHH", "EMPLEADO")));
    }

    @Test
    void sinElPermisoDeAdministrarUsuariosNoSeTocanLosRoles() throws Exception {
        mockMvc.perform(get("/admin/roles").with(simulada("zz.pedro", "RRHH"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/roles").with(simulada("zz.pedro", "RRHH")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"ZZ_X\",\"nombre\":\"x\",\"permisos\":[]}")).andExpect(status().isForbidden());
    }

    @Test
    void unRolNuevoDaAccesoSoloALoQueSusPermisosCubren() throws Exception {
        crearAuditor();
        mockMvc.perform(get("/admin/auditoria/acciones").with(real("zz.pedro"))).andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/usuarios/zz.pedro/roles/ZZ_AUDITOR/otorgar").with(real("fsepa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.roles[*].rol", hasItem("ZZ_AUDITOR")));

        mockMvc.perform(get("/admin/auditoria/acciones").with(real("zz.pedro"))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/usuarios").with(real("zz.pedro"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/directorio/sincronizar").with(real("zz.pedro"))).andExpect(status().isForbidden());
        assertThat(acciones.findAll()).anySatisfy(a -> {
            assertThat(a.getTipoAccion()).isEqualTo(TipoAccion.ROL_ACCESO_CREADO);
            assertThat(a.getDetalle()).isEqualTo("rol=ZZ_AUDITOR, permisos=[ADMIN_AUDITORIA]");
        });
    }

    @Test
    void cambiarLosPermisosDeUnRolSurteEfectoEnLaSiguientePeticion() throws Exception {
        crearAuditor();
        mockMvc.perform(post("/admin/usuarios/zz.pedro/roles/ZZ_AUDITOR/otorgar").with(real("fsepa"))).andExpect(status().isOk());

        mockMvc.perform(post("/admin/roles/ZZ_AUDITOR/permisos").with(real("fsepa")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permisos\":[\"ADMIN_DIRECTORIO\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.permisos[0]").value("ADMIN_DIRECTORIO"));

        mockMvc.perform(get("/admin/auditoria/acciones").with(real("zz.pedro"))).andExpect(status().isForbidden());
    }

    @Test
    void reglasDeValidacion() throws Exception {
        crearAuditor();
        // código repetido
        mockMvc.perform(post("/admin/roles").with(real("fsepa")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"ZZ_AUDITOR\",\"nombre\":\"Otro\",\"permisos\":[]}")).andExpect(status().isConflict());
        // código con formato inválido
        mockMvc.perform(post("/admin/roles").with(real("fsepa")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"zz auditor\",\"nombre\":\"Otro\",\"permisos\":[]}")).andExpect(status().isBadRequest());
        // permiso que no existe
        mockMvc.perform(post("/admin/roles").with(real("fsepa")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"ZZ_OTRO\",\"nombre\":\"Otro\",\"permisos\":[\"NO_EXISTE\"]}")).andExpect(status().isBadRequest());
        // ADMIN no se edita y un rol desconocido da 404
        mockMvc.perform(post("/admin/roles/ADMIN/permisos").with(real("fsepa")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"permisos\":[]}")).andExpect(status().isConflict());
        mockMvc.perform(post("/admin/roles/ZZ_NADA/permisos").with(real("fsepa")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"permisos\":[]}")).andExpect(status().isNotFound());
    }
}
