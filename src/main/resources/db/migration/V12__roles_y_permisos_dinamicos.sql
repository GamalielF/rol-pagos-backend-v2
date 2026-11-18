-- =====================================================================================================
-- V12. Roles y permisos en la base de datos (control de acceso basado en roles, RBAC).
--
--   PERMISO          lo que se puede hacer (ROLPAGO_GESTIONAR). Lo DECLARA cada módulo en su código; al
--                    arrancar, la aplicación registra aquí los que falten.
--   ROL              un conjunto de permisos con nombre (RRHH). Los del sistema vienen de esta migración;
--                    el administrador puede crear otros sin tocar el código.
--   ROL_PERMISOS     qué permisos tiene cada rol.
--   USUARIO_ROLES    qué roles tiene cada usuario (EMPLEADO no se guarda: lo es todo usuario).
--   USUARIO_PERMISOS permisos sueltos dados a un usuario, además de los de sus roles.
--
-- Nada se borra: quitar un rol o un permiso deja la fila INACTIVA (queda la historia de quién lo tuvo).
-- Los datos de ASIGNACIONES_ROL se trasladan y luego esa tabla se elimina.
-- =====================================================================================================

CREATE TABLE ROLES (
                       ID              BIGINT       NOT NULL AUTO_INCREMENT,
                       CODIGO          VARCHAR(30)  NOT NULL,
                       NOMBRE          VARCHAR(100) NOT NULL,
                       DESCRIPCION     VARCHAR(255) NULL,
                       DEL_SISTEMA     INT          NOT NULL DEFAULT 0,
                       ESTADO_REGISTRO VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
                       CREADO_POR      VARCHAR(100) NOT NULL,
                       CREADO_EN       DATETIME(6)  NOT NULL,
                       MODIFICADO_POR  VARCHAR(100) NOT NULL,
                       MODIFICADO_EN   DATETIME(6)  NOT NULL,
                       VERSION         BIGINT       NOT NULL DEFAULT 0,
                       CONSTRAINT PK_ROLES PRIMARY KEY (ID),
                       CONSTRAINT UK_ROLES_CODIGO UNIQUE (CODIGO),
                       CONSTRAINT CK_ROLES_DEL_SISTEMA CHECK (DEL_SISTEMA IN (0, 1)),
                       CONSTRAINT CK_ROLES_ESTADO_REGISTRO CHECK (ESTADO_REGISTRO IN ('ACTIVO', 'INACTIVO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Roles: conjuntos de permisos con nombre';

CREATE TABLE PERMISOS (
                          ID              BIGINT       NOT NULL AUTO_INCREMENT,
                          CODIGO          VARCHAR(50)  NOT NULL,
                          MODULO          VARCHAR(30)  NOT NULL,
                          DESCRIPCION     VARCHAR(255) NOT NULL,
                          ESTADO_REGISTRO VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
                          CREADO_POR      VARCHAR(100) NOT NULL,
                          CREADO_EN       DATETIME(6)  NOT NULL,
                          MODIFICADO_POR  VARCHAR(100) NOT NULL,
                          MODIFICADO_EN   DATETIME(6)  NOT NULL,
                          VERSION         BIGINT       NOT NULL DEFAULT 0,
                          CONSTRAINT PK_PERMISOS PRIMARY KEY (ID),
                          CONSTRAINT UK_PERMISOS_CODIGO UNIQUE (CODIGO),
                          CONSTRAINT CK_PERMISOS_ESTADO_REGISTRO CHECK (ESTADO_REGISTRO IN ('ACTIVO', 'INACTIVO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Catalogo de permisos que declaran los modulos';

CREATE TABLE ROL_PERMISOS (
                              ID              BIGINT       NOT NULL AUTO_INCREMENT,
                              ROL_ID          BIGINT       NOT NULL,
                              PERMISO_ID      BIGINT       NOT NULL,
                              ESTADO_REGISTRO VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
                              CREADO_POR      VARCHAR(100) NOT NULL,
                              CREADO_EN       DATETIME(6)  NOT NULL,
                              MODIFICADO_POR  VARCHAR(100) NOT NULL,
                              MODIFICADO_EN   DATETIME(6)  NOT NULL,
                              VERSION         BIGINT       NOT NULL DEFAULT 0,
                              CONSTRAINT PK_ROL_PERMISOS PRIMARY KEY (ID),
                              CONSTRAINT UK_ROL_PERMISOS UNIQUE (ROL_ID, PERMISO_ID),
                              CONSTRAINT FK_ROL_PERMISOS_ROL FOREIGN KEY (ROL_ID) REFERENCES ROLES (ID),
                              CONSTRAINT FK_ROL_PERMISOS_PERMISO FOREIGN KEY (PERMISO_ID) REFERENCES PERMISOS (ID),
                              CONSTRAINT CK_ROL_PERMISOS_ESTADO_REGISTRO CHECK (ESTADO_REGISTRO IN ('ACTIVO', 'INACTIVO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Permisos de cada rol';

CREATE TABLE USUARIO_ROLES (
                               ID              BIGINT       NOT NULL AUTO_INCREMENT,
                               USUARIO_ID      BIGINT       NOT NULL,
                               ROL_ID          BIGINT       NOT NULL,
                               ESTADO_REGISTRO VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
                               CREADO_POR      VARCHAR(100) NOT NULL,
                               CREADO_EN       DATETIME(6)  NOT NULL,
                               MODIFICADO_POR  VARCHAR(100) NOT NULL,
                               MODIFICADO_EN   DATETIME(6)  NOT NULL,
                               VERSION         BIGINT       NOT NULL DEFAULT 0,
                               CONSTRAINT PK_USUARIO_ROLES PRIMARY KEY (ID),
                               CONSTRAINT UK_USUARIO_ROLES UNIQUE (USUARIO_ID, ROL_ID),
                               CONSTRAINT FK_USUARIO_ROLES_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIOS (ID),
                               CONSTRAINT FK_USUARIO_ROLES_ROL FOREIGN KEY (ROL_ID) REFERENCES ROLES (ID),
                               CONSTRAINT CK_USUARIO_ROLES_ESTADO_REGISTRO CHECK (ESTADO_REGISTRO IN ('ACTIVO', 'INACTIVO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Roles de cada usuario; INACTIVO = revocado';

CREATE TABLE USUARIO_PERMISOS (
                                  ID              BIGINT       NOT NULL AUTO_INCREMENT,
                                  USUARIO_ID      BIGINT       NOT NULL,
                                  PERMISO_ID      BIGINT       NOT NULL,
                                  ESTADO_REGISTRO VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
                                  CREADO_POR      VARCHAR(100) NOT NULL,
                                  CREADO_EN       DATETIME(6)  NOT NULL,
                                  MODIFICADO_POR  VARCHAR(100) NOT NULL,
                                  MODIFICADO_EN   DATETIME(6)  NOT NULL,
                                  VERSION         BIGINT       NOT NULL DEFAULT 0,
                                  CONSTRAINT PK_USUARIO_PERMISOS PRIMARY KEY (ID),
                                  CONSTRAINT UK_USUARIO_PERMISOS UNIQUE (USUARIO_ID, PERMISO_ID),
                                  CONSTRAINT FK_USUARIO_PERMISOS_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIOS (ID),
                                  CONSTRAINT FK_USUARIO_PERMISOS_PERMISO FOREIGN KEY (PERMISO_ID) REFERENCES PERMISOS (ID),
                                  CONSTRAINT CK_USUARIO_PERMISOS_ESTADO_REGISTRO CHECK (ESTADO_REGISTRO IN ('ACTIVO', 'INACTIVO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Permisos sueltos de un usuario, ademas de los de sus roles';

-- Los roles del sistema: no se pueden borrar ni renombrar.
INSERT INTO ROLES (CODIGO, NOMBRE, DESCRIPCION, DEL_SISTEMA, CREADO_POR, CREADO_EN, MODIFICADO_POR, MODIFICADO_EN) VALUES
    ('ADMIN',    'Administrador',      'Tiene todos los permisos, incluidos los de los modulos futuros', 1, 'SISTEMA', NOW(6), 'SISTEMA', NOW(6)),
    ('RRHH',     'Recursos Humanos',   'Gestiona los roles de pago',                                     1, 'SISTEMA', NOW(6), 'SISTEMA', NOW(6)),
    ('EMPLEADO', 'Empleado',           'Todo usuario activo del directorio; no se asigna',               1, 'SISTEMA', NOW(6), 'SISTEMA', NOW(6));

-- Los permisos que declaran hoy los módulos. Los que se agreguen después los registra la aplicación al arrancar.
INSERT INTO PERMISOS (CODIGO, MODULO, DESCRIPCION, CREADO_POR, CREADO_EN, MODIFICADO_POR, MODIFICADO_EN) VALUES
    ('ROLPAGO_CONSULTAR_PROPIO', 'ROLPAGO',       'Consultar su propio rol de pago',                                 'SISTEMA', NOW(6), 'SISTEMA', NOW(6)),
    ('ROLPAGO_VER_AJENOS',       'ROLPAGO',       'Abrir el rol de pago de otra persona (indicando el motivo)',      'SISTEMA', NOW(6), 'SISTEMA', NOW(6)),
    ('ROLPAGO_GESTIONAR',        'ROLPAGO',       'Cargar, corregir y publicar roles de pago, y gestionar los avisos', 'SISTEMA', NOW(6), 'SISTEMA', NOW(6)),
    ('ADMIN_USUARIOS',           'ADMINISTRACION','Dar y quitar roles, y definir roles y permisos',                  'SISTEMA', NOW(6), 'SISTEMA', NOW(6)),
    ('ADMIN_DIRECTORIO',         'DIRECTORIO',    'Sincronizar y consultar el directorio del AD',                    'SISTEMA', NOW(6), 'SISTEMA', NOW(6)),
    ('ADMIN_AUDITORIA',          'AUDITORIA',     'Consultar y exportar la auditoria',                               'SISTEMA', NOW(6), 'SISTEMA', NOW(6));

-- Permisos por rol: ADMIN todos; RRHH gestiona (ver roles ajenos se da aparte, como hasta ahora); EMPLEADO consulta el suyo.
INSERT INTO ROL_PERMISOS (ROL_ID, PERMISO_ID, CREADO_POR, CREADO_EN, MODIFICADO_POR, MODIFICADO_EN)
SELECT r.ID, p.ID, 'SISTEMA', NOW(6), 'SISTEMA', NOW(6)
FROM ROLES r JOIN PERMISOS p
WHERE r.CODIGO = 'ADMIN'
   OR (r.CODIGO = 'RRHH' AND p.CODIGO = 'ROLPAGO_GESTIONAR')
   OR (r.CODIGO = 'EMPLEADO' AND p.CODIGO = 'ROLPAGO_CONSULTAR_PROPIO');

-- Traslado de las asignaciones existentes: mismo usuario, mismo estado (ACTIVO o revocado) y misma auditoría.
INSERT INTO USUARIO_ROLES (USUARIO_ID, ROL_ID, ESTADO_REGISTRO, CREADO_POR, CREADO_EN, MODIFICADO_POR, MODIFICADO_EN, VERSION)
SELECT a.USUARIO_ID, r.ID, a.ESTADO_REGISTRO, a.CREADO_POR, a.CREADO_EN, a.MODIFICADO_POR, a.MODIFICADO_EN, a.VERSION
FROM ASIGNACIONES_ROL a JOIN ROLES r ON r.CODIGO = a.ROL;

-- El antiguo "RRHH que ve roles ajenos" pasa a ser un permiso suelto del usuario (el rol ADMIN ya lo incluye).
INSERT INTO USUARIO_PERMISOS (USUARIO_ID, PERMISO_ID, ESTADO_REGISTRO, CREADO_POR, CREADO_EN, MODIFICADO_POR, MODIFICADO_EN)
SELECT a.USUARIO_ID, p.ID, a.ESTADO_REGISTRO, a.CREADO_POR, a.CREADO_EN, a.MODIFICADO_POR, a.MODIFICADO_EN
FROM ASIGNACIONES_ROL a JOIN PERMISOS p ON p.CODIGO = 'ROLPAGO_VER_AJENOS'
WHERE a.ROL = 'RRHH' AND a.VER_ROLES_AJENOS = 1;

DROP TABLE ASIGNACIONES_ROL;

-- Auditoría: dos acciones nuevas. Se llaman ROL_ACCESO_* porque en este sistema "rol" también es el rol de pago.
ALTER TABLE ACCIONES_ROLES_PAGO DROP CHECK CK_ACCIONES_ROLES_PAGO_TIPO;
ALTER TABLE ACCIONES_ROLES_PAGO ADD CONSTRAINT CK_ACCIONES_ROLES_PAGO_TIPO CHECK (TIPO_ACCION IN
    ('CARGA_LOTE', 'ROL_REEMPLAZADO', 'ROL_INACTIVADO', 'ROL_ACTIVADO', 'PERIODO_PUBLICADO', 'REPORTE_EXPORTADO',
     'NOTIFICACIONES_GENERADAS', 'NOTIFICACIONES_REINTENTADAS', 'NOTIFICACION_REENVIADA',
     'USUARIO_ROL_OTORGADO', 'USUARIO_ROL_REVOCADO', 'CARGA_CORRECCION', 'ROL_AGREGADO',
     'ROL_ACCESO_CREADO', 'ROL_ACCESO_PERMISOS_DEFINIDOS'));
