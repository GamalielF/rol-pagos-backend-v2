package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una fila del catálogo de permisos. La crea el sincronizador a partir de lo que declara cada módulo. */
@Entity
@Table(name = "PERMISOS", uniqueConstraints = @UniqueConstraint(name = "UK_PERMISOS_CODIGO", columnNames = "CODIGO"))
@Getter
@Setter
@NoArgsConstructor
public class Permiso extends BaseEntity {

    @Column(name = "CODIGO", nullable = false, length = 50, updatable = false)
    private String codigo;

    @Column(name = "MODULO", nullable = false, length = 30)
    private String modulo;

    @Column(name = "DESCRIPCION", nullable = false, length = 255)
    private String descripcion;
}
