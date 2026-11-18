package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** codigo: MAYÚSCULAS, dígitos y _ (por ejemplo JEFE_NOMINA). No se puede cambiar después. */
public record CrearRolRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,29}", message = "MAYÚSCULAS, dígitos y _; de 3 a 30 caracteres")
        String codigo,
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 255) String descripcion,
        @NotNull List<String> permisos) {
}
