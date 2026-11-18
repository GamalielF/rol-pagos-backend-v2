package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un rol de ACCESO (no confundir con el rol de pago): un conjunto de permisos con nombre. */
@Entity
@Table(name = "ROLES", uniqueConstraints = @UniqueConstraint(name = "UK_ROLES_CODIGO", columnNames = "CODIGO"))
@Getter
@Setter
@NoArgsConstructor
public class Rol extends BaseEntity {

    @Column(name = "CODIGO", nullable = false, length = 30, updatable = false)
    private String codigo;

    @Column(name = "NOMBRE", nullable = false, length = 100)
    private String nombre;

    @Column(name = "DESCRIPCION", length = 255)
    private String descripcion;

    /** 1 = ADMIN, RRHH o EMPLEADO: los crea la migración y no se borran ni se renombran. */
    @Column(name = "DEL_SISTEMA", nullable = false)
    private int delSistema;

    public boolean esDelSistema() {
        return delSistema == 1;
    }
}
