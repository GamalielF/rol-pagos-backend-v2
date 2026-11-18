package ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Un permiso que un módulo declara en su código.
 *
 * @param codigo         identificador único en MAYÚSCULAS, con el prefijo del módulo: ROLPAGO_GESTIONAR, NOMINA_CALCULAR
 * @param descripcion    para la pantalla de administración
 * @param rolesPorDefecto roles que lo reciben la PRIMERA vez que se registra (ADMIN lo recibe siempre). Después,
 *                       el administrador puede quitarlo o darlo a otros roles: la aplicación no vuelve a imponerlo.
 */
public record DefinicionPermiso(String codigo, String descripcion, Set<String> rolesPorDefecto) {

    private static final Pattern CODIGO = Pattern.compile("[A-Z][A-Z0-9_]{2,49}");

    public DefinicionPermiso {
        if (codigo == null || !CODIGO.matcher(codigo).matches()) {
            throw new IllegalArgumentException("Código de permiso inválido (MAYÚSCULAS, dígitos y _; 3 a 50): " + codigo);
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("El permiso " + codigo + " necesita una descripción");
        }
        rolesPorDefecto = rolesPorDefecto == null ? Set.of() : Set.copyOf(rolesPorDefecto);
    }

    public static DefinicionPermiso de(String codigo, String descripcion, String... rolesPorDefecto) {
        return new DefinicionPermiso(codigo, descripcion, Set.of(rolesPorDefecto));
    }
}
