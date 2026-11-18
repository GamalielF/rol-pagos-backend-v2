package ec.fin.coopsanjose.rol_pagos_backend_final.administracion.controller;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.request.CrearRolRequest;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.request.DefinirPermisosRequest;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.response.PermisoDto;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.response.RolDto;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.service.GestionRolesService;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Definir roles y sus permisos. Lo protege el permiso ADMIN_USUARIOS (SeguridadAdministracion). Solo GET y POST, por CORS. */
@RestController
@RequiredArgsConstructor
public class AdminRolesController {

    private final GestionRolesService servicio;

    @GetMapping("/admin/permisos")
    public ApiResponse<List<PermisoDto>> permisos() {
        return ApiResponse.ok("Permisos disponibles", servicio.permisos());
    }

    @GetMapping("/admin/roles")
    public ApiResponse<List<RolDto>> roles() {
        return ApiResponse.ok("Roles", servicio.roles());
    }

    @PostMapping("/admin/roles")
    public ApiResponse<RolDto> crear(@Valid @RequestBody CrearRolRequest solicitud) {
        return ApiResponse.ok("Rol creado", servicio.crear(solicitud));
    }

    @PostMapping("/admin/roles/{codigo}/permisos")
    public ApiResponse<RolDto> definirPermisos(@PathVariable String codigo, @Valid @RequestBody DefinirPermisosRequest solicitud) {
        return ApiResponse.ok("Permisos del rol actualizados", servicio.definirPermisos(codigo, solicitud.permisos()));
    }
}
