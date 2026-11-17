# Fase 1: los usuarios tienen un ID secuencial

## Qué pidió el líder y qué encontramos

Las 8 tablas ya tenían ID secuencial (`BIGINT AUTO_INCREMENT`). El problema estaba en **cómo se referían a los usuarios**: cinco tablas guardaban el código del AD como texto (`USERNAME_AD = 'fsepa'`), sin ninguna clave foránea.

| Tabla | Antes (texto) | Ahora (ID) |
|---|---|---|
| `DIRECTORIO_AD` | `USERNAME_AD` | `USUARIO_ID` → `USUARIOS.ID` (1 a 1) |
| `ASIGNACIONES_ROL` | `USERNAME_AD` | `USUARIO_ID` → `USUARIOS.ID` |
| `ACCESOS_ROLES_PAGO` | `USERNAME_AD` | `USUARIO_ID` → `USUARIOS.ID` |
| `ACCIONES_ROLES_PAGO` | `USERNAME_AD` | `USUARIO_ID` → `USUARIOS.ID` |
| `NOTIFICACIONES_ROLES_PAGO` | `USERNAME_DESTINO` | `USUARIO_DESTINO_ID` → `USUARIOS.ID` (opcional) |
| `PERIODOS` | `PUBLICADO_POR` | `PUBLICADO_POR_ID` → `USUARIOS.ID` (opcional) |

`CREADO_POR` y `MODIFICADO_POR` siguen como texto, y es a propósito: son la huella de auditoría de **cada** tabla, incluida `USUARIOS`. Si fueran claves foráneas a `USUARIOS`, crear un usuario exigiría que ya existiera otro.

## Conceptos

### Clave natural y clave sustituta
- **Natural:** un dato del negocio usado como identificador (`fsepa`, la cédula). Puede cambiar, ocupa más y la base no verifica que exista.
- **Sustituta:** un número sin significado (`USUARIO_ID = 42`). Nunca cambia y, con una `FOREIGN KEY`, la base **impide** referenciar a alguien que no existe.

Con la clave sustituta, si TI renombra una cuenta del AD basta con actualizar **una** fila de `USUARIOS`: toda la historia la sigue.

### Por qué una tabla `USUARIOS` y no usar `DIRECTORIO_AD`
`DIRECTORIO_AD` es una **copia del AD**: se llena al sincronizar. Pero el sistema también conoce usuarios que no están ahí, como los administradores de arranque antes de la primera sincronización, o alguien a quien el autenticador aceptó y que todavía no se sincronizó. Por eso la identidad vive en su propia tabla, y `DIRECTORIO_AD` la referencia. Es **SRP aplicado a datos**: una tabla para *quién es* y otra para *qué dice el AD de él*. Los módulos futuros (NÓMINA, VACACIONES) referenciarán `USUARIOS.ID`.

### Los huecos en los ID son normales
MySQL no reutiliza un ID aunque la transacción que lo pidió se revierta. Si ves 1, 2, 5, 9… no se perdió nada: un ID es secuencial (siempre crece), no consecutivo.

## Las piezas nuevas (módulo `usuario`)

| Clase | Responsabilidad |
|---|---|
| `Usuario` (entidad) | La fila de `USUARIOS`: ID heredado de `BaseEntity` y el código del AD, único |
| `UsuarioRepository` | Acceso a la tabla. `insertarSiNoExiste` (`INSERT … ON DUPLICATE KEY`) y `buscarConfirmado` (lectura con bloqueo) |
| `RegistroUsuarios` (interfaz) | El contrato que usan los demás módulos: `obtenerOCrear`, `obtenerOCrearTodos`, `buscar`, `sistema` |
| `RegistroUsuariosJpa` | La implementación con JPA y MySQL |

**Inversión de dependencias (la D de SOLID).** Auditoría, notificaciones y directorio dependen de la interfaz `RegistroUsuarios`, no de `RegistroUsuariosJpa`. Si mañana los usuarios vinieran de otro lado, cambia solo la implementación.

### Crear un usuario sin errores ante peticiones simultáneas
Dos peticiones pueden ver a la vez a un usuario nuevo. Con un `save()` normal, la segunda chocaría con la clave única y **toda su transacción fallaría**. Por eso:
1. `INSERT … ON DUPLICATE KEY UPDATE ID = ID`: si ya existe, MySQL no hace nada y no lanza error.
2. `buscarConfirmado` con `@Lock(PESSIMISTIC_READ)`, que genera `SELECT … FOR SHARE`.

El paso 2 lo agregó una prueba que falló. En MySQL (aislamiento `REPEATABLE READ`) cada transacción lee desde una *foto* tomada en su primera lectura, y el usuario que acababa de crear la otra petición no aparecía en ella. Una lectura con bloqueo lee lo último confirmado. La prueba `dosPeticionesSimultaneasNoDuplicanNiFallan` se repite 20 veces; sin el bloqueo fallan las 20.

## Cambios en las entidades

```java
@ManyToOne(fetch = FetchType.EAGER, optional = false)
@JoinColumn(name = "USUARIO_ID", nullable = false)
private Usuario usuario;

public String getUsernameAd() {               // atajo: el resto del código no cambió
    return usuario == null ? null : usuario.getUsernameAd();
}
```

- **`@ManyToOne`**: muchos accesos pertenecen a un usuario. En `DIRECTORIO_AD` es `@OneToOne`: una cuenta, un usuario.
- **`@JoinColumn`**: la columna que guarda el ID.
- **`EAGER`**: el usuario se carga junto con la fila. Así no hay errores `LazyInitializationException`, porque el proyecto usa `open-in-view: false`.
- **`default_batch_fetch_size: 100`** (en `application.yaml`): al leer 50 accesos, sus usuarios se traen en **una** consulta y no en 50. Ese es el problema "N+1".

## Repositorios: mismos métodos, consultas explícitas

Spring Data arma la consulta a partir del nombre del método (`findByUsernameAd`). Como `usernameAd` ya no es una columna de esas tablas, se escribe la consulta con `@Query` y se **conserva el nombre**, para que ningún servicio cambie:

```java
@Query("select a from AsignacionRol a join a.usuario u where u.usernameAd = :username and a.rol = :rol")
Optional<AsignacionRol> findByUsernameAdAndRol(@Param("username") String usernameAd, @Param("rol") Rol rol);
```

## La migración `V11__usuarios_con_id_secuencial.sql`

Regla de oro de Flyway: **una migración aplicada nunca se edita**; siempre se agrega otra. Orden seguro, tabla por tabla:

1. Crear `USUARIOS` y el usuario `SISTEMA` (ID 1, para los procesos automáticos).
2. Insertar **todos** los códigos que ya existen en cualquier tabla; `GROUP BY` evita duplicados. La intercalación de MySQL no distingue mayúsculas, así que `FSEPA` y `fsepa` quedan como uno solo.
3. Por cada tabla: columna nueva **opcional** → llenarla con un `UPDATE … JOIN` → hacerla obligatoria → `FOREIGN KEY` → borrar la columna de texto.

Se probó con datos que cubren los casos difíciles (mayúsculas distintas, un administrador que no está en el directorio, un usuario dado de baja, el actor `SISTEMA`): no se perdió ninguna fila, y la base ahora **rechaza** un usuario inexistente y un usuario duplicado.

## Pruebas

| | Antes | Después |
|---|---|---|
| `mvnw test` ejecutaba | 265 (solo unitarias) | **560** (unitarias y de integración) |
| Fallaban | 4 que nadie veía | **0** |

- **Maven no ejecutaba las pruebas `*IT`**: su configuración por defecto solo incluye `*Test` y `*Tests`. Se agregó el plugin `maven-surefire-plugin` con las tres terminaciones.
- **3 pruebas fallaban** porque el generador de PDF de prueba producía bytes distintos en cada llamada: PDFBox escribe un `/ID` nuevo cada vez que guarda. Se fijó con `setDocumentId(1L)`. El código del sistema estaba bien.
- **1 prueba estaba desactualizada**: esperaba que un período publicado no se pudiera tocar, pero desde la corrección de períodos se puede, indicando un motivo. Ahora prueba esa regla.
- **7 pruebas nuevas** para `RegistroUsuarios`, una de ellas repetida 20 veces.

## Antes de desplegar a test

1. **Respaldo de la base de test** (en el servidor). Primero confirma que el contenedor tiene esas variables con `docker exec test-rrhh-roll-administration-db env | grep ^MYSQL_` (no muestres la salida a nadie: incluye contraseñas):
   ```bash
   docker exec test-rrhh-roll-administration-db sh -c 'mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --single-transaction --no-tablespaces "$MYSQL_DATABASE"' > respaldo-antes-de-v11-$(date +%Y%m%d-%H%M).sql
   ```
2. Desplegar. Flyway aplica la `V11` al arrancar; en el registro debe aparecer `Successfully applied 1 migration … now at version v11`.
3. Comprobar: `SELECT COUNT(*) FROM USUARIOS;` debe ser mayor que 1, y las pantallas de Usuarios y roles y de Auditoría deben verse igual que antes.

**La API no cambió**: el front sigue recibiendo los códigos de usuario como texto.
