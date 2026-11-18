package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Qué permisos tiene cada rol. INACTIVO = el administrador se lo quitó. */
@Entity
@Table(name = "ROL_PERMISOS", uniqueConstraints = @UniqueConstraint(name = "UK_ROL_PERMISOS", columnNames = {"ROL_ID", "PERMISO_ID"}))
@Getter
@Setter
@NoArgsConstructor
public class RolPermiso extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ROL_ID", nullable = false, updatable = false)
    private Rol rol;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "PERMISO_ID", nullable = false, updatable = false)
    private Permiso permiso;

    public RolPermiso(Rol rol, Permiso permiso) {
        this.rol = rol;
        this.permiso = permiso;
    }
}
