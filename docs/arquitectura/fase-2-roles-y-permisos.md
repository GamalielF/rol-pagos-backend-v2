# Fase 2: roles y permisos "plug and play"

## Qué pidió el líder y qué había

**Pedido:** segmentar el acceso por roles de forma *plug and play*. Un módulo nuevo (NÓMINA, VACACIONES) debe poder agregar sus permisos sin tocar la seguridad central, y el administrador debe poder crear roles sin un nuevo despliegue.

**Lo que había:**
- Los roles eran un `enum` de Java (`ADMIN`, `RRHH`, `EMPLEADO`). Un rol nuevo exigía cambiar código, compilar y desplegar.
- El único permiso, "ver roles ajenos", era una columna (`VER_ROLES_AJENOS`) de la tabla `ASIGNACIONES_ROL`. Un segundo permiso habría exigido otra columna.
- `SecurityConfig` conocía las rutas de **todos** los módulos. Cada módulo nuevo obligaba a editarla.

## Conceptos

### RBAC: control de acceso basado en roles
La pregunta de seguridad deja de ser "¿qué rol tiene?" y pasa a ser "¿tiene este **permiso**?". Los roles son solo paquetes de permisos.

```
USUARIO ──< USUARIO_ROLES >── ROL ──< ROL_PERMISOS >── PERMISO
   └────────< USUARIO_PERMISOS >──────────────────────────┘   (permiso suelto, sin rol)
```

- **Permiso:** una acción concreta (`ROLPAGO_GESTIONAR`). Lo define el **código**, porque el código es quien la hace cumplir.
- **Rol:** un nombre para un grupo de permisos (`RRHH`). Lo define el **administrador**, en la base de datos.
- **Permiso suelto:** se da a una persona sin crear un rol. Así quedó el antiguo "ver roles ajenos" de RRHH.

**Permisos efectivos = permisos de sus roles + permisos sueltos.** Todo usuario es `EMPLEADO`. Los administradores de arranque (`app.seguridad.administradores-iniciales`) son además `ADMIN`.

### Abierto/cerrado (la O de SOLID)
*"Abierto a la extensión, cerrado a la modificación."* Para agregar un módulo se **agrega** una clase y no se **modifica** ninguna existente. La pieza que lo permite es la interfaz `ModuloSeguridad`:

```java
public interface ModuloSeguridad {
    String modulo();                         // "VACACIONES"
    List<DefinicionPermiso> permisos();      // lo que este módulo sabe hacer
    void reglas(...AuthorizationManagerRequestMatcherRegistry reglas);   // sus rutas y qué permiso piden
}
```

Spring encuentra **todas** las clases `@Component` que la implementan y las inyecta como `List<ModuloSeguridad>`. `SecurityConfig` solo las recorre:

```java
modulos.forEach(modulo -> modulo.reglas(reglas));
```

### Responsabilidad única (la S de SOLID)
Antes, `RolesUsuarioService` resolvía los roles, los asignaba y además armaba las respuestas de la pantalla. Ahora cada clase tiene una sola razón para cambiar:

| Clase | Su única responsabilidad |
|---|---|
| `ResolutorAccesoJpa` | Calcular los roles y permisos **efectivos** de un usuario |
| `AsignacionAccesoService` | Dar y quitar roles y permisos (las filas de vínculo) |
| `GestionRolesService` | Crear roles y definir sus permisos |
| `SincronizadorCatalogoPermisos` | Registrar al arrancar los permisos que declaran los módulos |
| `AdministracionUsuariosService` | Las reglas de la pantalla "Usuarios y roles" (orquesta las anteriores) |
| `Seguridad<Modulo>` | Qué permisos tiene un módulo y qué rutas protegen |

### Inversión de dependencias (la D de SOLID)
Los filtros de seguridad (`JwtAuthFilter`, `DevAuthFilter`) dependen de la **interfaz** `ResolutorAcceso`, que vive en `common.seguridad`. No dependen de `ResolutorAccesoJpa`, que vive en el módulo `acceso`. Por eso las pruebas unitarias de los filtros no necesitan base de datos: les pasan un resolutor falso (`UsuariosDePrueba.resolutor(...)`).

## Cómo funciona una petición

1. El filtro (`JwtAuthFilter` en producción) valida el token y llama a `resolutor.resolver("fsepa")`.
2. Recibe `RolesYPermisos(roles=[ADMIN, EMPLEADO], permisos=[ROLPAGO_GESTIONAR, ...])`.
3. Crea un `UsuarioAutenticado` y se lo da a Spring Security con sus **autoridades**: `ROLE_ADMIN`, `ROLE_EMPLEADO`, `ROLPAGO_GESTIONAR`, …
   - El prefijo `ROLE_` es una convención de Spring: `hasRole("ADMIN")` busca `ROLE_ADMIN`, y `hasAuthority("X")` busca `X` tal cual.
4. Las reglas se evalúan **en orden**, y gana la primera que coincide:
   1. `/actuator/health` y `/error`: libres.
   2. Las reglas de cada módulo: `/rrhh/**` → `hasAuthority("ROLPAGO_GESTIONAR")`, etc.
   3. Red de seguridad: cualquier otro `/admin/**` → `hasRole("ADMIN")`.
   4. Todo lo demás: basta con iniciar sesión.

Los permisos se leen **en cada petición**. Si el Admin quita un rol, el cambio rige desde la siguiente petición, sin cerrar sesión.

## El catálogo se sincroniza solo

`SincronizadorCatalogoPermisos` es un `ApplicationRunner`, es decir, código que Spring ejecuta una vez, cuando la aplicación ya arrancó. Hace tres cosas:

1. **Verifica** que dos módulos no declaren el mismo código. Si lo hacen, la aplicación **no arranca**: es preferible un error claro a un permiso ambiguo.
2. **Crea** los permisos nuevos y se los da a `ADMIN` y a sus *roles por defecto*.
3. **Actualiza** la descripción de los que ya existían, pero **nunca** vuelve a imponer a quién se le dan. Si el administrador le quitó un permiso a `EMPLEADO`, el siguiente arranque lo respeta.

### Agregar el módulo VACACIONES (ejemplo)

```java
@Component
public class SeguridadVacaciones implements ModuloSeguridad {

    public static final String SOLICITAR = "VACACIONES_SOLICITAR";
    public static final String APROBAR = "VACACIONES_APROBAR";

    @Override
    public String modulo() {
        return "VACACIONES";
    }

    @Override
    public List<DefinicionPermiso> permisos() {
        return List.of(
                DefinicionPermiso.de(SOLICITAR, "Solicitar vacaciones", RolesDelSistema.EMPLEADO),
                DefinicionPermiso.de(APROBAR, "Aprobar solicitudes de vacaciones", RolesDelSistema.RRHH));
    }

    @Override
    public void reglas(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry reglas) {
        reglas.requestMatchers("/vacaciones/aprobaciones/**").hasAuthority(APROBAR)
              .requestMatchers("/vacaciones/**").hasAuthority(SOLICITAR);
    }
}
```

Eso es **todo** lo que hace falta en seguridad: sin migración, sin tocar `SecurityConfig`. Al arrancar, los permisos aparecen en `GET /admin/permisos` y el administrador ya puede asignarlos. La prueba `SincronizadorCatalogoPermisosIT` lo demuestra con un módulo de mentira.

**Ojo con el orden:** dentro de un módulo va primero la ruta **más específica** (`/vacaciones/aprobaciones/**` antes que `/vacaciones/**`), porque gana la primera regla que coincide.

## La migración `V12__roles_y_permisos_dinamicos.sql`

1. Crea `ROLES`, `PERMISOS`, `ROL_PERMISOS`, `USUARIO_ROLES` y `USUARIO_PERMISOS`, con claves foráneas y las columnas de auditoría de `BaseEntity`.
2. Siembra los 3 roles del sistema (`DEL_SISTEMA = 1`) y los 6 permisos actuales.
3. Da permisos a los roles: `ADMIN` todos; `RRHH` gestionar roles de pago; `EMPLEADO` consultar el propio.
4. **Pasa los datos:** cada fila de `ASIGNACIONES_ROL` pasa a `USUARIO_ROLES`, con su mismo estado. Cada RRHH que tenía `VER_ROLES_AJENOS = 1` recibe el permiso suelto `ROLPAGO_VER_AJENOS`.
5. Borra `ASIGNACIONES_ROL`.

¿Por qué la migración siembra los permisos, si el sincronizador también los crea? Porque el **paso 4 los necesita**, y las migraciones corren antes de que arranque Spring.

`DEL_SISTEMA` es `INT` con `CHECK (0, 1)`, igual que las demás banderas del proyecto. Con `TINYINT`, Hibernate (`ddl-auto: validate`) rechaza arrancar porque la entidad usa `int`. Esa validación detectó el error en las pruebas.

## API nueva (todo bajo el permiso `ADMIN_USUARIOS`)

| Método y ruta | Qué hace |
|---|---|
| `GET /admin/permisos` | El catálogo, agrupado por módulo |
| `GET /admin/roles` | Los roles con sus permisos (primero los del sistema) |
| `POST /admin/roles` | Crea un rol: `{"codigo":"AUDITOR","nombre":"Auditor interno","permisos":["ADMIN_AUDITORIA"]}` |
| `POST /admin/roles/{codigo}/permisos` | Reemplaza sus permisos: `{"permisos":["ADMIN_AUDITORIA","ROLPAGO_VER_AJENOS"]}` |

`POST /admin/usuarios/{u}/roles/{rol}/otorgar` ahora acepta **cualquier** rol existente, incluidos los creados por el administrador. `EMPLEADO` sigue sin asignarse, porque lo es todo usuario.

Reglas: `ADMIN` no se edita (409), porque siempre tiene todos los permisos. Un código repetido da 409, y un permiso inexistente da 400. Crear un rol y cambiar sus permisos queda en la auditoría (`ROL_ACCESO_CREADO`, `ROL_ACCESO_PERMISOS_DEFINIDOS`).

**Compatibilidad con el front actual:** las respuestas de `/admin/usuarios` y `/perfil` conservan su forma, con dos detalles:
- El permiso se llamaba `VER_ROLES_AJENOS` y ahora es `ROLPAGO_VER_AJENOS`, porque cada permiso lleva el prefijo de su módulo. El front lo busca con el nombre anterior en `/perfil`, así que `PerfilService` envía **los dos** (constante `NOMBRE_ANTERIOR_VER_AJENOS`, marcada `@Deprecated`). Cuando el front use el nombre nuevo, se borra.
- Otorgar un rol que no existe ahora responde **404**, no 400: el rol se busca en la base, como cualquier recurso.

**Un cambio de regla, a propósito:** antes, ver roles ajenos exigía *el rol RRHH* **y** el permiso. Ahora basta **el permiso**, y así un rol creado por el Admin (un "auditor", por ejemplo) puede tenerlo. La ruta `/rrhh/**` sigue exigiendo `ROLPAGO_GESTIONAR`.

## Modo de desarrollo

`DevAuthFilter` sigue aceptando `X-Dev-Roles: RRHH`, y ahora da los **permisos que ese rol tiene en la base**. Hay una cabecera nueva, `X-Dev-Permisos`, para simular permisos sueltos:

```
X-Dev-Usuario: zz.pedro
X-Dev-Roles: RRHH
X-Dev-Permisos: ROLPAGO_VER_AJENOS
```

Antes, simular `RRHH` daba también "ver roles ajenos". Ahora no, porque en la base ese permiso es suelto y no viene con el rol. Para simularlo se agrega la cabecera. `X-Dev-Permisos` solo se lee junto con `X-Dev-Roles`: sin simulación, mandan los permisos reales.

## Pruebas

- **Adaptadas:** las pruebas que usaban el `enum Rol` ahora usan `RolesDelSistema` y el ayudante `UsuariosDePrueba`, que reproduce los permisos que la V12 da a cada rol.
- **Nuevas:**
  - `ResolutorAccesoJpaIT`: roles y permisos efectivos, permisos sueltos, revocaciones y un rol creado en tiempo de ejecución.
  - `SincronizadorCatalogoPermisosIT`: el *plug and play*.
  - `AdminRolesControllerIT`: de punta a punta, crear un rol, dárselo a alguien y comprobar que entra solo donde sus permisos lo permiten.
