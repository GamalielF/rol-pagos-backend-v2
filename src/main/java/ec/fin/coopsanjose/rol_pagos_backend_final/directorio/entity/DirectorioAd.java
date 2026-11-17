package ec.fin.coopsanjose.rol_pagos_backend_final.directorio.entity;

import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity.Usuario;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Un usuario del AD, identificado por su CÓDIGO DE USUARIO. La cédula es un atributo y puede repetirse. */
@Entity
@Table(name = "DIRECTORIO_AD",
        uniqueConstraints = @UniqueConstraint(name = "UK_DIRECTORIO_AD_USUARIO", columnNames = "USUARIO_ID"),
        indexes = @Index(name = "IDX_DIRECTORIO_AD_CEDULA", columnList = "CEDULA"))
@Getter
@Setter
@NoArgsConstructor
public class DirectorioAd extends BaseEntity {

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "USUARIO_ID", nullable = false)
    private Usuario usuario;

    @Column(name = "CEDULA", nullable = false, length = 10)
    private String cedula;

    @Column(name = "NOMBRE_COMPLETO", length = 150)
    private String nombreCompleto;

    @Column(name = "CARGO", length = 255)
    private String cargo;

    @Column(name = "EMAIL", length = 120)
    private String email;

    @Column(name = "SECUENCIA_OFICINA")
    private Integer secuenciaOficina;

    @Column(name = "FECHA_CREACION_AD")
    private LocalDate fechaCreacionAd;

    /** Código del AD del usuario de esta cuenta del AD. Atajo para no recorrer la relación en cada uso. */
    public String getUsernameAd() {
        return usuario == null ? null : usuario.getUsernameAd();
    }
}
