package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** La lista COMPLETA de permisos que tendrá el rol: los que no estén, se le quitan. */
public record DefinirPermisosRequest(@NotNull List<String> permisos) {
}
