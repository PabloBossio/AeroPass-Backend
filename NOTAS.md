# Notas — Aprendiendo Spring Boot con el proyecto Aerolinea API

Este archivo es un registro vivo de dudas, preguntas y conceptos que fueron surgiendo durante el desarrollo del proyecto. La idea es que sirva como referencia rápida propia — no memorices el código, quedate con el "para qué sirve esto" de cada entrada, y buscá la sintaxis exacta cuando la necesites (documentación oficial, un proyecto anterior tuyo, o una IA).

Se va a ir actualizando a medida que surjan nuevas dudas durante el desarrollo.

---

## Fundamentos de Spring Boot

**¿Qué es Spring Boot?**
No es "otro framework": es Spring Framework (que resuelve Inversión de Control / Inyección de Dependencias) + autoconfiguración (Spring Boot decide sensatamente cómo configurar cosas según lo que detecta en el classpath) + servidor embebido (Tomcat) + "starters" (paquetes de dependencias pre-armadas y compatibles entre sí, ej: `spring-boot-starter-web`).

**Inversión de Control / Inyección de Dependencias (IoC/DI)**
En vez de que una clase cree sus propias dependencias con `new`, se las inyectan desde afuera. Spring arma un contenedor (`ApplicationContext`) que escanea las clases marcadas como "beans" y las conecta entre sí automáticamente.

**Anotaciones "estereotipo"**: `@Component` (genérica), y sus especializaciones `@Service` (lógica de negocio), `@Repository` (acceso a datos), `@RestController` (capa HTTP). Todas le dicen a Spring "gestioná vos el ciclo de vida de esta clase". Las escanea `@ComponentScan`, que viene incluido dentro de `@SpringBootApplication`.

**Constructor injection** (recomendado sobre inyectar con `@Autowired` en el atributo): las dependencias se piden como parámetros del constructor. Hace las dependencias explícitas y facilita testear con mocks más adelante.

**Estructura de paquetes usada en el proyecto**: `controller`, `service`, `repository`, `model`, `dto`, `mapper`, `exception` — cada uno con una única responsabilidad.

---

## Persistencia (JPA / Hibernate / MySQL)

- JPA es la especificación estándar de Java para mapear objetos a tablas; Hibernate es la implementación que usa Spring Boot.
- `spring.jpa.hibernate.ddl-auto=update`: Hibernate crea/actualiza tablas según las entidades. Cómodo para aprender/prototipar; en producción se reemplaza por migraciones versionadas (Flyway/Liquibase).
- `BigDecimal` para valores monetarios, nunca `double`/`float` (errores de redondeo binario).
- `@GeneratedValue(strategy = GenerationType.IDENTITY)`: delega la generación del ID en el auto-incremento nativo de MySQL.
- `@Enumerated(EnumType.STRING)`: guarda el enum como texto, no como número ordinal — si reordenás el enum en el futuro, los datos existentes no se corrompen.
- Diseño de estados como un enum pensado como ciclo de vida real (`PROGRAMADO → EN_VUELO → FINALIZADO`, con `DEMORADO`/`CANCELADO` como alternativas) — pensar el dominio antes de escribir código.

**Spring Data JPA — derived query methods**: interfaces que extienden `JpaRepository<Entidad, TipoId>` ya traen `save()`, `findById()`, `findAll()`, etc. sin implementación. Además, métodos con nombres como `findByOrigenAndDestino(...)` se traducen automáticamente a una consulta JPQL, parseando el nombre del método.

**Bug real: `ddl-auto=update` es solo aditivo.** Al sacar un campo de una entidad (ej. `aerolinea` de `Vuelo`), Hibernate **nunca borra la columna vieja** de la tabla real — solo agrega columnas nuevas, nunca quita ni renombra. Si esa columna vieja era `NOT NULL`, los futuros `INSERT` (que ya no la mandan) fallan con un error de MySQL tipo `Field 'x' doesn't have a default value`. Hay que borrar la columna a mano (`ALTER TABLE tabla DROP COLUMN columna;` en phpMyAdmin/SQL). Además, filas viejas no reciben valores para columnas nuevas (quedan en `NULL` aunque la entidad diga `nullable = false`), lo que puede romper después al mapear esas filas viejas a un DTO (`NullPointerException` si el código asume que una relación nunca es null). Esta es una de las razones reales por las que en producción se usan migraciones versionadas (Flyway/Liquibase) en vez de `ddl-auto`.

**Relaciones JPA (`@ManyToOne`)**: `@JoinColumn(name = "...")` define la columna FK en la tabla. `fetch = FetchType.LAZY` (recomendado explícito, ya que el default de `@ManyToOne` en la especificación JPA es EAGER) hace que Hibernate no traiga la entidad relacionada hasta que se accede a ella con el getter. Esto funciona sin problema dentro de una misma request gracias a `spring.jpa.open-in-view=true` (default), que mantiene la sesión de Hibernate abierta durante toda la duración de la request — si se desactiva (recomendado en proyectos grandes, para forzar fetch explícito), acceder a una relación LAZY fuera de la transacción tira `LazyInitializationException`.

**Un service puede inyectar más de un repositorio.** Es normal cuando una regla de negocio involucra más de una entidad (ej. `VueloService` necesita `AvionRepository` para validar que `asientosDisponibles` no supere la `capacidad` del avión asignado).

---

## Seguridad (contraseñas, antes de llegar al módulo de JWT)

- **`spring-security-crypto`** es un artefacto separado del starter completo de Spring Security (`spring-boot-starter-security`). Da acceso a `BCryptPasswordEncoder` **sin** disparar la autoconfiguración que bloquea todos los endpoints con login automático (esa autoconfiguración depende de `spring-security-web`/`spring-security-config`, no de `spring-security-crypto`). Útil para encriptar contraseñas desde ya, antes de meterse con JWT.
- `passwordEncoder.encode(texto)` genera un hash irreversible (formato `$2a$10$...`); no se "desencripta", solo se compara con `passwordEncoder.matches(textoPlano, hash)` (esto se usa en el login, más adelante).
- **Nunca** devolver el password (ni el hash) en un DTO de respuesta — es exactamente el tipo de fuga que el patrón DTO existe para evitar.
- Igual que se fuerza `estado = PROGRAMADO` al crear un `Vuelo`, se fuerza `rol = USUARIO` al registrar un `Usuario` — nunca confiar en un rol que venga del cliente en el registro público. Crear un `ADMIN` es una operación aparte (a definir más adelante, probablemente restringida a otro admin ya autenticado).

**`@Configuration` + `@Bean`**: para registrar como bean de Spring una clase que **no es tuya** (no la podés anotar con `@Component`/`@Service`, como `BCryptPasswordEncoder` de una librería externa). Se escribe una clase `@Configuration` con métodos `@Bean` — cada método devuelve un objeto que Spring gestiona y que después se puede inyectar por constructor en cualquier otra clase, igual que un bean propio.

**Nuevas validaciones de Bean Validation**: `@Email` (formato de email válido) y `@Size(min = ..., max = ...)` (longitud de un String) — mismo mecanismo ya conocido, reglas distintas.

---

## Lombok

- `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`: para **entidades** (`@Entity`).
- `@Data`: solo para **DTOs**, nunca (o con mucho cuidado) para entidades JPA. Motivo: `@Data` genera `equals`/`hashCode`/`toString` con todos los campos, lo cual es riesgoso en entidades por los proxies de Hibernate (lazy loading) y por relaciones bidireccionales (`toString()` recursivo → `StackOverflowError`).
- `@Builder`: genera el patrón Builder para construir objetos de forma legible y nombrada (`Vuelo.builder().origen("BUE")...build()`), evitando constructores con muchos parámetros posicionales ambiguos.
- `@NoArgsConstructor` es obligatorio en toda entidad JPA porque Hibernate lo usa por reflection para instanciarlas.
- Recordar habilitar "Annotation Processing" en IntelliJ (Settings → Build, Execution, Deployment → Compiler → Annotation Processors) para que Lombok funcione bien en el IDE.

---

## Capa de servicio

- Ahí vive la lógica de negocio (validaciones que dependen del dominio, no solo del formato). La entidad describe la forma de los datos; el controller traduce HTTP; el servicio decide "esto tiene sentido de negocio o no".
- `@Transactional`: marca que un método corre dentro de una transacción de base de datos — si algo falla a mitad de camino (relevante cuando una operación toca más de una tabla, ej. reservas), Hibernate hace rollback de todo.
- Mantener validaciones de negocio en el servicio aunque "ya estén" validadas en el DTO de entrada: es defensa en profundidad — el servicio puede ser invocado desde otros lugares además del controller (tests, batch jobs, otros servicios) que no pasan por la validación del DTO.

---

## DTOs y API REST

- **Nunca exponer la entidad JPA directamente en la API.** Motivos: control de qué puede setear el cliente (ej. no debería poder mandar `estado` al crear un vuelo), evitar problemas de serialización con relaciones/lazy loading, y desacoplar el contrato público de la API del esquema interno de base de datos.
- Patrón DTO + Mapper: clases de request/response separadas de la entidad, con una clase `Mapper` (métodos estáticos, sin anotaciones de Spring — no todo necesita ser un bean) que traduce entre ambas.
- `@Valid` en el parámetro del controller dispara las validaciones de Bean Validation del DTO.
- `@PathVariable` (parte de la URL, identifica un recurso específico) vs `@RequestParam` (query param, para filtros/búsquedas opcionales).
- `ResponseEntity<T>` permite controlar el código de estado HTTP explícitamente: `201 Created` al crear un recurso (no `200 OK`, que es para lecturas), `404 Not Found` cuando se busca por id y no existe.
- Encadenar `Optional.map(...).map(...).orElse(...)` es el estilo funcional para manejar ausencia de valor sin `if/else` ni riesgo de `NullPointerException`.

**Validación cruzada entre campos (cross-field)**: las anotaciones estándar (`@NotNull`, `@Positive`, etc.) validan un campo aislado. Para reglas que comparan dos campos entre sí (ej: fechaLlegada posterior a fechaSalida), se define una anotación propia a **nivel de clase** (`@Target(ElementType.TYPE)`) con su propio `ConstraintValidator`.

**El patrón DTO/Mapper no es rígido.** `Reserva` no tiene un `toEntity` en su mapper porque el service ya recibe los ids (`usuarioId`, `vueloId`) directo y arma la entidad buscando las relaciones reales — no hace falta forzar el mismo mapeo 1:1 que usan `Vuelo`/`Avion` si no aporta nada en ese caso.

---

## Reservas: transacciones multi-entidad y snapshots

- **Snapshot histórico**: `Reserva.precioPagado` copia el precio del vuelo *al momento de reservar*, en vez de leer `vuelo.getPrecio()` dinámicamente. El precio de un vuelo puede cambiar después; lo que alguien pagó en el pasado es un hecho histórico que no debe cambiar con él. A veces duplicar un dato a propósito (en vez de todo "normalizado") es el modelado correcto — es una fotografía de un momento, no una referencia viva.
- **Acá `@Transactional` importa de verdad**: `crearReserva` escribe en dos tablas (descuenta `asientosDisponibles` del vuelo Y crea la reserva). Sin la transacción, un fallo a mitad de camino dejaría un asiento "perdido" sin ninguna reserva que lo explique.
- **Race condition detectada y resuelta con bloqueo pesimista**: si dos requests reservan el último asiento de un vuelo *al mismo tiempo*, ambas pueden leer "queda 1" antes de que cualquiera escriba el descuento, y las dos tendrían éxito — sobreventa. Solución aplicada: un `SELECT ... FOR UPDATE` que bloquea la fila del vuelo hasta que termina la transacción; cualquier otra transacción que quiera tocar esa misma fila queda esperando en la cola (se ve clarísimo probándolo: la segunda request en Postman directamente no responde hasta que la primera libera el lock). Alternativa que no se implementó acá pero existe: bloqueo optimista (`@Version`), que en vez de bloquear detecta el conflicto recién al momento de escribir y falla la segunda transacción para que se reintente — mejor para baja contención, pero requiere manejar el reintento.

**Bug real: `@Lock` sobre un método heredado no funciona.** No se puede poner `@Lock` directamente sobre `findById` (viene ya implementado por `JpaRepository`) — hace falta declarar un método propio con `@Query` y recién ahí colgarle `@Lock`.

**Bug real: MariaDB (XAMPP) no es 100% compatible con la sintaxis de bloqueo que genera Hibernate para "MySQL".** Con `@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre una consulta JPQL, Hibernate generó `SELECT ... FOR UPDATE OF v1_0` — sintaxis que MySQL 8+ soporta pero que MariaDB (a pesar de ser de la misma familia) nunca implementó, y tira un error de sintaxis SQL (1064). Intentar arreglarlo fijando un dialecto específico de MariaDB (`hibernate-community-dialects` + `spring.jpa.properties.hibernate.dialect=...`) llevó a un `ClassNotFoundException` (la clase no estaba disponible en esa versión/configuración). La solución que sí funcionó: escribir la consulta como **SQL nativo** en vez de JPQL, con `nativeQuery = true` — así Hibernate no traduce nada y ejecuta exactamente el SQL que escribiste (`FOR UPDATE`, sin el `OF`, que MariaDB sí entiende):
```java
@Query(value = "SELECT * FROM vuelos WHERE id = :id FOR UPDATE", nativeQuery = true)
Optional<Vuelo> buscarPorIdConBloqueo(@Param("id") Long id);
```
Ojo de sintaxis: con más de un atributo en `@Query` (`value` y `nativeQuery`), Java exige nombrar **todos** los atributos explícitamente — no alcanza con poner la cadena SQL sola entre paréntesis.
- Derived query methods pueden "atravesar" relaciones: `findByUsuarioId(Long usuarioId)` en `ReservaRepository` filtra por el `id` del `Usuario` relacionado (vía `@ManyToOne`), generando el JOIN automáticamente.
- `PUT` para acciones de cambio de estado sobre un recurso existente (ej. `PUT /api/reservas/{id}/cancelar`), a diferencia de `POST` que crea un recurso nuevo. (Un purista de REST diría que `PATCH` es más preciso para modificaciones parciales — matiz fino, `PUT` es aceptado en la práctica.)

---

## Manejo global de excepciones

- `@RestControllerAdvice` + `@ExceptionHandler`: intercepta cualquier excepción que se escape de los controllers, en un solo lugar centralizado, en vez de try/catch repetido en cada método.
- Spring elige el `@ExceptionHandler` más específico automáticamente (no importa el orden de los métodos en la clase).
- Excepciones propias (ej. `ReglaDeNegocioException`) en vez de usar `IllegalArgumentException` genérica, para poder distinguir tipos de error y mapearlos a códigos HTTP con sentido.
- **Seguridad**: nunca devolver el stack trace ni el mensaje real de una excepción inesperada al cliente. Dos líneas de defensa:
  1. Tu propio `@RestControllerAdvice` construye la respuesta de error a mano (nunca incluye trace porque nunca lo escribís ahí).
  2. `server.error.include-stacktrace=never` en `application.properties`, como red de seguridad para casos que ni tu `@RestControllerAdvice` llega a interceptar (ej. errores en filtros de bajo nivel, antes de llegar al controller).
- Para errores realmente inesperados (`Exception` genérica): loguear el detalle real del lado del servidor (`log.error(...)`) pero devolver al cliente un mensaje genérico.

**Bug real encontrado: `FieldError` vs `ObjectError`.** Al validar, Spring separa los errores en dos tipos: `FieldError` (atado a un campo puntual, ej. `@Positive` en `precio`) y `ObjectError` (atado a la clase entera, ej. una validación cruzada como `@FechasValidas` con `@Target(TYPE)`). `getFieldErrors()` en el `BindingResult` **solo** trae los `FieldError` — si armás la lista de detalles con eso, los errores de validaciones a nivel de clase quedan invisibles (lista vacía) aunque el request sí se rechace. Solución: usar `getAllErrors()` (trae ambos tipos, ya que `FieldError` es subclase de `ObjectError`) y distinguir con `instanceof FieldError fieldError` para saber si mostrar el nombre del campo o no.

**Regla general: cada tipo de excepción tiene su código HTTP semánticamente correcto, no todo es `400` o `500`.** Cualquier excepción sin un `@ExceptionHandler` específico cae en el genérico (`Exception` → `500`), aunque la causa real sea un error del cliente. Handlers agregados a medida que se fueron encontrando estos casos, todos con el mismo esqueleto (armar `ErrorResponseDto`, elegir el status correcto):
- `RecursoNoEncontradoException` (propia) → `404 Not Found` — ej. buscar un `Avion`/`Usuario`/`Vuelo` por un id que no existe.
- `HttpMessageNotReadableException` → `400 Bad Request` — JSON mal formado (ej. mandar un array `[ ]` en vez de un objeto `{ }`).
- `MethodArgumentTypeMismatchException` → `400 Bad Request` — un `@PathVariable`/`@RequestParam` con un tipo incorrecto (ej. mandar texto donde se espera un `Long`). Trae `ex.getValue()` y `ex.getName()` para armar un mensaje útil.
- `HttpRequestMethodNotSupportedException` → `405 Method Not Allowed` — pedir un verbo HTTP (GET/POST/PUT/DELETE) que esa ruta puntual no soporta. Trae `ex.getMethod()`.

---

## Herramientas / flujo de trabajo

- **Postman**: cuidado con el dropdown de método (GET por defecto) — si no lo cambiás a POST/PUT/DELETE explícitamente, tu request pega contra el endpoint equivocado y puede darte una respuesta "válida" (200 OK) que no es la que esperabas.
- HikariCP: pool de conexiones a la base de datos que usa Spring Boot por defecto (reutiliza conexiones en vez de abrir una nueva por request).
- **Windows: puerto ocupado después de cortar una ejecución a mitad de camino.** Si se detiene el proceso "a la fuerza" (o queda colgado) puede dejar un `java.exe` viejo escuchando en el puerto de Tomcat (8080), y el siguiente intento de levantar la app falla con "puerto en uso". Diagnóstico: `netstat -ano | findstr :8080` muestra el PID que lo tiene tomado. Si `netstat`/`taskkill` no se reconocen como comando (problema de PATH), usar la ruta completa: `C:\Windows\System32\netstat.exe` / `C:\Windows\System32\taskkill.exe /PID <pid> /F`. Alternativa sin línea de comandos: buscar el proceso en el Administrador de Tareas y finalizarlo ahí.

---

## Seguridad: Spring Security + JWT completo

- Apenas agregás la dependencia `spring-boot-starter-security` al `pom.xml`, Spring Boot autoconfigura un bloqueo total: **todos** los endpoints piden autenticación (login básico con un usuario generado, `user`, y una contraseña random impresa en consola). Hace falta un bean `SecurityFilterChain` propio para reemplazar ese comportamiento por defecto con reglas explícitas.
- **`csrf(csrf -> csrf.disable())`**: CSRF (Cross-Site Request Forgery) es un ataque que explota que el *browser* manda automáticamente las cookies de sesión en cualquier request, incluso a sitios de terceros. Ese modelo de ataque no aplica a una API stateless donde el cliente tiene que adjuntar manualmente el header `Authorization: Bearer <token>` — por eso es estándar (no una vulnerabilidad) desactivar CSRF en APIs REST con JWT.
- **`SessionCreationPolicy.STATELESS`**: le dice a Spring Security que nunca cree ni consulte una `HttpSession`. Cada request tiene que probar quién es por sí solo (con el JWT); el servidor no "recuerda" nada entre requests. Esto es lo que hace posible escalar horizontalmente sin sincronizar sesiones entre instancias.
- **`UserDetailsService`** (interfaz de Spring Security): un solo método, `loadUserByUsername(String username)`. Es el punto donde Spring Security te pregunta "¿quién es este usuario y qué permisos tiene?" — vos lo implementás (`UsuarioDetailsService`) buscando en tu propio `UsuarioRepository` y devolviendo un objeto `UserDetails` (acá se usó el `User.builder()` que trae Spring Security, con username=email, password=hash, y las `authorities`).
- **`AuthenticationManager`**: el componente que efectivamente *ejecuta* la autenticación (compara credenciales recibidas contra las reales, usando el `UserDetailsService` y el `PasswordEncoder` por detrás). Spring Boot no lo expone como bean por defecto; hay que sacarlo explícitamente de `AuthenticationConfiguration.getAuthenticationManager()` en un método `@Bean` propio para poder inyectarlo (ej. en `AuthController`).
- **Convención `ROLE_`**: `hasRole("ADMIN")` en las reglas de autorización busca, por detrás, una authority que se llame literalmente `"ROLE_ADMIN"` (agrega el prefijo solo). Por eso `UsuarioDetailsService` arma la authority como `"ROLE_" + usuario.getRol().name()` — si te olvidás el prefijo al construirla a mano, `hasRole(...)` nunca la va a matchear.
- **JWT (JSON Web Token)**: un token con 3 partes separadas por puntos (header.payload.signature), donde el payload lleva "claims" (acá: `sub` = email, `rol`, `iat` = fecha de emisión, `exp` = fecha de expiración) codificados en Base64 (no encriptados — cualquiera puede decodificarlos, ej. en jwt.io; lo que garantiza que no fueron alterados es la firma, verificable solo con la clave secreta del servidor). Librería usada: `io.jsonwebtoken` (jjwt), que a diferencia de los starters de Spring **sí** necesita versión explícita en el `pom.xml` porque no está gestionada por el BOM de Spring Boot.
- **`OncePerRequestFilter`**: clase base de Spring para escribir un filtro de servlet que se garantiza ejecutar una sola vez por request (evita duplicados en ciertos escenarios de forwards internos de Servlet). Ahí vive `JwtAuthenticationFilter`: lee el header `Authorization`, valida el JWT, y si es válido, carga el usuario y lo autentica manualmente.
- **`SecurityContextHolder`**: el lugar (en el hilo actual) donde Spring Security guarda "quién es el usuario autenticado en este request ahora mismo". Los filtros lo leen/escriben; las reglas de autorización (`hasRole`, `authenticated()`) lo consultan al final de la cadena.
- **Dato importante sobre de dónde sale el rol real**: la autorización de cada request **no** se basa en el claim `rol` que viene *dentro* del JWT, sino en lo que devuelve `UsuarioDetailsService.loadUserByUsername(email)` en ese momento — es decir, una consulta fresca a la base de datos. El JWT solo se usa para decir *quién sos* (el email); qué permisos tenés se recalcula siempre desde la fuente de verdad (la tabla `usuarios`). Ventaja de este diseño: si le cambiás el rol a un usuario en la base, el cambio aplica inmediatamente en su próximo request, sin esperar a que expire o se regenere su token viejo.
- **`addFilterBefore(miFiltro, UsernamePasswordAuthenticationFilter.class)`**: inserta un filtro propio en un punto específico de la cadena de filtros que arma Spring Security (que ya trae ~15 filtros propios por defecto). Acá se usa para que `JwtAuthenticationFilter` corra temprano, antes de que la cadena llegue a la parte de autorización final.
- **`requestMatchers(...)` se evalúan en orden, gana el primero que matchea.** Por eso las reglas más específicas (ej. `GET /api/vuelos/**` público) tienen que ir *antes* que una regla más genérica que cubra la misma ruta (ej. `POST/PUT/DELETE /api/vuelos/**` solo `ADMIN`), y la más genérica de todas (`anyRequest().authenticated()`) siempre al final, como default de cierre.
- **`permitAll()` vs `authenticated()` vs `hasRole("X")`**: sin autenticación / cualquier usuario autenticado sin importar el rol / usuario autenticado y además con ese rol específico. Se combinan por ruta y por verbo HTTP (`HttpMethod.GET`, `POST`, etc.) según la regla de negocio de cada recurso.

**Bug real: faltaba `@PostMapping("/login")` en el método del controller.** `@RequestMapping("/api/auth")` a nivel de clase solo define el *prefijo* de ruta — no alcanza por sí solo para exponer un endpoint. Sin una anotación de verbo HTTP (`@PostMapping`, `@GetMapping`, etc.) en el método, Spring nunca registra ese método como handler de nada. El síntoma fue confuso: no dio un error de compilación ni un `404` típico, sino `NoResourceFoundException` (`500` con nuestro handler genérico) — porque al no encontrar ningún controller que matchee la ruta, Spring Boot cae al manejador de recursos estáticos por defecto (el mismo que serviría un `index.html`), que tampoco encuentra nada y tira esa excepción.

**Bug real (case-sensitivity en claims de JWT):** `generarToken` guarda el claim como `.claim("rol", rol)` (minúscula), pero `extraerRol` lo buscaba con `claims.get("Rol", String.class)` (mayúscula). Los nombres de claims de un JWT son case-sensitive — con esa diferencia, `extraerRol` siempre devuelve `null`. No rompió nada todavía porque ese método no se usa en el filtro (la autorización usa el rol fresco desde la base, no el del token), pero quedó anotado para el día que sí se necesite leer ese claim.

**Detalle a tener en cuenta (no arreglado todavía, anotado como mejora futura):** `JwtAuthenticationFilter` no tiene `try/catch` alrededor del parseo del token. Si llega un token malformado/corrupto, `Jwts.parser()...parseSignedClaims(token)` tira una excepción sin capturar. Como este filtro corre *antes* que `ExceptionTranslationFilter` en la cadena de Spring Security (el componente que normalmente traduce esas excepciones a respuestas `401`/`403` prolijas), una excepción ahí no se traduce — se propaga como error crudo del servidor. Mejora pendiente: envolver ese parseo en un `try/catch` y tratar un token inválido simplemente como "no autenticado" en vez de dejar que explote.

**Postman — el campo de token enmascarado no siempre se puede copiar completo.** La pestaña Authorization → Bearer Token oculta el valor por ser un dato sensible, y en algunos casos el copiado (`Ctrl+A` + `Ctrl+C`) desde ese campo enmascarado no trae el string completo. Más confiable: copiar el token directo desde el **body de la respuesta JSON del login** (ahí no hay ningún enmascarado, es texto plano).

---

## Testing (1): JUnit 5 + Mockito — tests de servicio

- **Unit test vs integration test**: un unit test mockea todas las dependencias (`@Mock`) y no levanta contexto de Spring — corre en milisegundos. Un integration test (`@SpringBootTest`) levanta el `ApplicationContext` completo y habla con la base real — corre en segundos. Se vio la diferencia concreta en los tiempos de los propios logs de Maven (6+ segundos para el test de contexto, contra 0.02–0.3 segundos para los tests de servicio). Regla práctica: la mayoría de la lógica de negocio se cubre con unit tests (rápidos, baratos); los integration tests son para verificar que las piezas realmente se conectan bien entre sí, no para repetir cada caso de negocio.
- **`@ExtendWith(MockitoExtension.class)`**: le dice a JUnit 5 que procese las anotaciones de Mockito (`@Mock`, `@InjectMocks`) en la clase de test.
- **`@Mock`**: crea un doble de prueba de una dependencia (repositorio, otro service, incluso interfaces de terceros como `PasswordEncoder` — Mockito no distingue, funciona igual). **`@InjectMocks`**: crea una instancia real de la clase bajo test e inyecta automáticamente los `@Mock` declarados arriba (por constructor, si existe uno).
- **`when(mock.metodo(args)).thenReturn(valor)`**: define qué devuelve el mock ante una llamada puntual. **`.thenThrow(new Excepcion(...))`**: para simular que la dependencia falla. **`.thenAnswer(inv -> inv.getArgument(0))`**: necesario cuando el objeto que se guarda se **construye dentro** del método bajo test (ej. `Reserva.builder()...build()` armado adentro de `crearReserva`) — como el test no tiene una referencia a ese objeto de antemano, no puede hacer `thenReturn(esaInstancia)`; en cambio, le dice al mock "devolvé el mismo argumento que te pasaron", simulando lo que hace un `save()` real de JPA.
- **`verify(mock, times(n))`** / **`verify(mock, never())`**: confirma que un método del mock se llamó (o no se llamó) una cantidad de veces determinada — útil para probar "fail-fast" (ej. verificar que `passwordEncoder.encode(...)` nunca se llama si el email ya existe, porque el método debería cortar antes).
- **`any(Clase.class)`**: matcher de Mockito para "no me importa el valor exacto, cualquier instancia de este tipo sirve" — necesario junto con `thenAnswer` en el mismo escenario de arriba.
- **Mutación visible sobre la misma referencia**: como Java pasa objetos por referencia, si el método bajo test mutza un objeto que el test también tiene referenciado (ej. `vuelo.setAsientosDisponibles(...)` dentro del service), el test puede verificar el resultado leyendo directamente esa misma variable después de llamar al service — no hace falta un mock adicional para "capturar" el cambio.
- Para escenarios con muchas variantes de un mismo objeto de prueba (ej. un vuelo válido, uno con fecha inválida, uno con precio inválido), usar **métodos helper privados** (`avionValido()`, `vueloValido()`) en vez de `@BeforeEach` — cada llamada arma una instancia nueva e inmutable, evitando que un test folle modifique sin querer el estado que usa otro test.

---

## Testing (2): tests de controller con `@WebMvcTest` + `MockMvc`

- **`@WebMvcTest(Controller.class)`**: a diferencia de `@SpringBootTest`, levanta solo la "capa web" — el controller indicado más la infraestructura de MVC (`@RestControllerAdvice`, filtros, conversores, validación) — sin tocar la base de datos. Mucho más rápido que un integration test completo, pero sigue probando el comportamiento HTTP real (status codes, JSON, seguridad), a diferencia de un unit test de service que no sabe nada de HTTP.
- **Bug real / concepto clave: el escaneo de `@WebMvcTest` es angosto.** Solo incluye clases con estereotipos reconocidos como "capa web": `@Controller`, `@ControllerAdvice`, `Filter`, `Converter`, `HandlerInterceptor`, `WebMvcConfigurer`, etc. Un `@Service` normal (como `AvionService`) o un `@Component`/`@Configuration` cualquiera (como `JwtUtil`, `UsuarioDetailsService`, o la propia clase `SecurityConfig` con su `@Bean SecurityFilterChain`) **no entran** en ese escaneo aunque estén en el classpath. Esto causó dos síntomas distintos con la misma raíz:
  - `UnsatisfiedDependencyException` al no encontrar `JwtUtil`/`UsuarioDetailsService` (dependencias de `JwtAuthenticationFilter`, que sí es un `Filter` y por eso sí entra al escaneo) → solución: declararlos como `@MockitoBean` en el test, aunque el test no los use directamente.
  - Spring Boot cayendo a su **seguridad de fallback por defecto** (HTTP Basic con usuario/contraseña generados, visible en el log como `Using generated security password: ...`) porque no encontraba la `SecurityFilterChain` real (`SecurityConfig` no es un estereotipo reconocido) → solución: `@Import(SecurityConfig.class)` en la clase de test, que fuerza a incluir esa configuración puntual en el contexto reducido.
- **`@MockitoBean`** (reemplaza al `@MockBean` viejo, deprecado en Spring Boot 4/Framework 6.2): reemplaza un bean del contexto por un mock de Mockito. Funciona tanto para beans "faltantes" (como los de arriba) como para **reemplazar un bean real ya definido** (ej. el `AuthenticationManager` que define `SecurityConfig` vía `@Bean`) cuando el test necesita controlar exactamente qué devuelve, en vez de ejercitar el flujo real completo.
- **`MockMvc`**: simula peticiones HTTP en memoria, sin levantar un puerto real. `mockMvc.perform(get(...)/post(...)/put(...)).andExpect(status()....).andExpect(jsonPath("$.campo").value(...))`.
- **`@WithMockUser(roles = "ADMIN")`**: simula un usuario autenticado con determinado rol para ese test puntual, sin pasar por login/JWT real.
- **Bug real / lección importante: `@WithAnonymousUser` explícito, no "ausencia de anotación".** Para testear el caso "sin autenticación", omitir `@WithMockUser` no alcanza — en cierto orden de ejecución de tests (JUnit no garantiza el orden declarado) apareció una fuga real del contexto de seguridad de un test anterior autenticado como ADMIN, dejando pasar una petición que debía ser anónima. La forma correcta y explícita es anotar `@WithAnonymousUser`, que fuerza un contexto anónimo real para ese test sin depender de lo que haya pasado antes.
- **Los datos de prueba tienen que respetar las invariantes del dominio.** Si un mapper arma una respuesta completa navegando relaciones (`ReservaResponseDTO` incluye `Vuelo`, que a su vez incluye `Avion`), el objeto de prueba tiene que traer esa cadena completa armada (`Vuelo.builder()...avion(avionValido())...build()`) — si falta un eslabón, explota con `NullPointerException` en tiempo de test, no porque el código de producción esté mal, sino porque el dato de prueba no refleja una invariante real del negocio (un vuelo siempre tiene avión asignado).
- **`jsonPath(...).value(...)` es sensible al tipo, no solo al valor.** Comparar un campo numérico (`BigDecimal`, `Integer`) contra un `String` entre comillas (`.value("500.00")`) **siempre falla**, aunque el número "se vea igual" — el JSON se deserializa como `Double`/número, y `Double` nunca es `.equals()` a un `String`. Los literales numéricos van sin comillas (`.value(500.00)`); las comillas solo para campos que son realmente texto (ej. un enum serializado por nombre, `"CONFIRMADA"`).

---

## Spring Boot 4 (correcciones sobre información desactualizada)

Spring Boot 4 se lanzó en octubre de 2025, después del corte de conocimiento de la IA que ayudó en este proyecto — varias respuestas iniciales fueron corregidas tras verificar contra documentación oficial actual:

- **Modularización de starters**: `spring-boot-starter-web` pasó a llamarse `spring-boot-starter-webmvc`. Cada starter "principal" ahora tiene un starter de test compañero (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-validation-test`) que ya trae `spring-boot-starter-test` transitivamente — declararlo aparte es redundante, no un error.
- **Jackson 3**: el paquete pasó de `com.fasterxml.jackson` a `tools.jackson`. El bean autoconfigurado por Spring Boot para JSON ya no es `ObjectMapper` sino `tools.jackson.databind.json.JsonMapper` (config inmutable basada en builder).
- **`spring-boot-starter-security-test`**: hace falta agregarlo explícitamente (no viene incluido en los otros starters de test) para que `@WithMockUser`/`@WithAnonymousUser` funcionen de forma confiable dentro de un `@WebMvcTest`.
- **`@WebMvcTest`** cambió de paquete: ahora es `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (antes `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`).

---

## Herramientas / flujo de trabajo (testing)

- **IntelliJ + JUnit Platform de Spring Boot 4**: el runner interno de tests de IntelliJ puede chocar en versión con las librerías de JUnit Platform que trae Spring Boot 4.1 (`NoSuchMethodError: MethodSelector.getMethodParameterTypes()`). Se soluciona activando "Delegate IDE build/run actions to Maven" (Settings → Build, Execution, Deployment → Build Tools → Maven → Runner) y recargando Maven — así IntelliJ ejecuta los tests vía Maven en vez de su propio motor. Este ajuste no siempre se respeta en atajos de "correr un solo método" (clic en el ícono verde); para eso, mejor crear una configuración de ejecución de tipo Maven con `-Dtest=Clase#metodo`.
- **Bug recurrente: imports estáticos que IntelliJ autocompleta hacia la clase equivocada.** Pasó varias veces con `post`/`get`/`status`/`jsonPath`: el autocompletado a veces ofrece clases de testing **reactivo** (`MockServerHttpRequest`, para WebFlux) o de testing **del lado cliente** (`MockRestRequestMatchers`, para `RestTemplate`) en vez de las de `MockMvc` (testing del lado servidor con Spring MVC tradicional, que es lo que usa este proyecto). Antídoto: usar wildcards explícitos y verificar el paquete completo antes de aceptar una sugerencia — siempre `org.springframework.test.web.servlet.*`:
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
```

---

## Documentación con Swagger / OpenAPI

- **`springdoc-openapi`** (para Spring Boot 4: `springdoc-openapi-starter-webmvc-ui`, versión 3.0.3) genera documentación interactiva de la API **automáticamente** a partir de lo que ya existe: escanea los `@RestController`/`@GetMapping`/`@PostMapping`/etc. y los campos/validaciones de los DTOs, sin escribir nada extra como baseline. Expone dos cosas por defecto: la UI interactiva en `/swagger-ui.html` y el spec crudo en JSON en `/v3/api-docs`.
- **Bug real: Swagger UI tapado por la regla de seguridad por defecto.** `anyRequest().authenticated()` (la regla de cierre de `SecurityConfig`) bloquea con 403 cualquier ruta nueva que no esté explícitamente permitida — y `/swagger-ui.html` es una ruta nueva para Spring Security, aunque la haya "creado" una librería. Solución: agregar una regla `permitAll()` explícita, **antes** de la regla de cierre (mismo principio de "orden importa, lo más específico primero" que ya se usa en el resto de `SecurityConfig`), para las **tres** rutas necesarias (la página HTML, sus assets JS/CSS, y el JSON que esos assets van a pedir):
```java
.requestMatchers(
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**"
).permitAll()
```
- **JWT Bearer auth dentro de Swagger UI**: para poder probar endpoints protegidos desde el botón "Try it out" (que si no, siempre da 403 por no mandar ningún token), hace falta un bean `OpenAPI` propio que declare un esquema de seguridad. Las clases (`OpenAPI`, `Info`, `Components`, `SecurityRequirement`, `SecurityScheme`) vienen de `io.swagger.v3.oas.models.*` — una librería de modelo aparte (`swagger-core`), independiente de la versión de Spring Boot:
```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Aerolinea API")
                        .description("API REST para la gestión de vuelos, reservas, aviones y usuarios de una aerolínea. Autenticación vía JWT.")
                        .version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
```
  `.info(...)` pone el título/descripción que aparecen arriba de todo en Swagger UI. `.addSecurityItem(...)` aplica el requisito de seguridad globalmente a todas las operaciones documentadas. `.components().addSecuritySchemes(...)` registra el esquema reutilizable, lo que hace aparecer el botón "Authorize" con un campo para pegar el token crudo (Swagger UI antepone el prefijo "Bearer " solo).
- **Bug real (recurrencia del mismo bug-family de siempre): faltaba `@Bean` sobre `customOpenAPI()`.** Sin esa anotación, el método sigue siendo Java válido — no da error de compilación ni en el arranque — pero Spring nunca lo registra como bean, así que el esquema de seguridad nunca se agrega al `OpenAPI` real, y el botón "Authorize"/`securitySchemes` simplemente no aparece en `/v3/api-docs`, sin ningún error visible en consola. Mismo patrón que ya pasó antes con `@PostMapping`/`@GetMapping`/`@Test` faltantes: **una anotación de Spring "faltante" nunca es un error de compilación — es Java válido que Spring ignora en silencio, y el síntoma aparece después, indirecto.** Lección: ante un comportamiento "no pasa nada, ni funciona ni tira error", sospechar primero de una anotación faltante antes que de un bug de lógica.
- **Bug real de compilación: confundir una variable local con una clase.** `.type(securitySchemeName.Type.HTTP)` — `securitySchemeName` es un `String` (variable local), no la clase `SecurityScheme`, así que no tiene un `.Type` anidado. Corrección: `.type(SecurityScheme.Type.HTTP)`, usando la clase. Típicamente lo sugiere mal el autocompletado del IDE por tener un nombre parecido cerca.
- **`@Tag(name = ..., description = ...)`** (`io.swagger.v3.oas.annotations.tags.Tag`), a nivel de clase sobre cada controller: le pone nombre/descripción legible al grupo de endpoints en Swagger UI (por defecto sale un nombre feo tipo `avion-controller`, autogenerado del nombre de la clase).
- **`@Operation(summary = ..., description = ...)`** y **`@ApiResponses({ @ApiResponse(responseCode = ..., description = ...), ... })`** (`io.swagger.v3.oas.annotations.Operation` / `.responses.ApiResponse(s)`), a nivel de método: documentan qué hace cada endpoint y qué códigos de estado HTTP puede devolver.
- **Principio clave — precisión documental, no copiar/pegar el mismo bloque en todos lados.** Los códigos de estado documentados en `@ApiResponses` tienen que salir de revisar el código real, no de una plantilla genérica:
  - La regla real de `SecurityConfig` para esa ruta+verbo puntual (`permitAll` → no hace falta documentar 403; `hasRole`/`authenticated` → sí).
  - Las excepciones reales que puede tirar el service subyacente, cruzadas contra `GlobalExceptionHandler` (`RecursoNoEncontradoException` → 404, `ReglaDeNegocioException` → 400, validación de `@Valid` → 400).
  - Documentar un código que en la práctica **nunca puede pasar** (ej. un 404 en un endpoint cuyo service nunca valida existencia) es peor que no documentarlo — engaña a quien consuma la API sobre qué manejo de errores necesita hacer. Un test ya existente y pasando (ej. uno que prueba explícitamente un caso de 404) es la mejor prueba de que un código sí es real y hay que documentarlo.
- **Sintaxis de array de anotaciones**: dentro de `@ApiResponses({ ... })` cada `@ApiResponse` es un elemento de un array de anotaciones — necesitan coma entre ellos, igual que un array normal. Una **coma final después del último elemento es válida** (no es un bug), pero una **coma faltante entre dos elementos es error de compilación**.
- **Login (`/api/auth/login`) con manejo de credenciales inválidas**: como `authenticationManager.authenticate(...)` se llama manualmente dentro del controller (no dentro de la cadena de filtros de seguridad), una excepción ahí no la traduce automáticamente el mecanismo estándar de Spring Security — hay que capturarla explícitamente. Se resolvió agregando un `@ExceptionHandler(BadCredentialsException.class)` más en el `GlobalExceptionHandler` ya existente (devolviendo 401), en vez de un `try/catch` local en el controller — mantiene el mismo patrón centralizado que ya se usaba para el resto de las excepciones del proyecto.
- **Limpieza de código de aprendizaje inicial**: se eliminó `HelloController`/`HelloService`, el típico endpoint de prueba ("hola mundo") armado al empezar a aprender Spring Boot. Una vez que la API real tiene su propia documentación prolija con Swagger, dejar ese tipo de endpoints vestigiales sin relación con el dominio solo ensucia la doc (aparecería como un grupo suelto sin `@Tag`) y no aporta nada — se confirma con la suite de tests completa que nada dependía de esas clases antes de borrarlas.

---

## Frontend en React (consumiendo la API)

A diferencia de todo el backend (que siempre escribiste vos, yo solo explicaba), el frontend lo armé yo directamente — el objetivo acá no era que aprendas a programar en React línea por línea, sino que entiendas la arquitectura general y cómo se conecta con tu propia API. Igual, algunos conceptos valen la pena quedar anotados:

- **CORS (Cross-Origin Resource Sharing)**: el navegador bloquea por defecto que JavaScript corriendo en un origen (`http://localhost:5173`, el frontend) llame a una API en otro origen (`http://localhost:8080`, el backend) — distinto puerto ya cuenta como "otro origen". El servidor tiene que declarar explícitamente qué orígenes permite. En Spring Security se hace con un bean `CorsConfigurationSource` (orígenes, métodos y headers permitidos) conectado a la cadena de filtros con `.cors(cors -> cors.configurationSource(...))`.
- **Bug real / gap de diseño encontrado al conectar el frontend**: `LoginResponseDTO` no devolvía el `id` del usuario — solo `token`, `email`, `rol`. Sin el `id`, el frontend no tenía forma de armar una reserva (`POST /api/reservas` necesita `usuarioId`), y no había ningún endpoint público para que un usuario común consultara su propio id (`GET /api/usuarios/**` es solo `ADMIN`). Se resolvió agregando el campo `id` a `LoginResponseDTO`. Buen ejemplo de cómo construir un cliente real para tu API expone huecos de diseño que no se notan probando solo con Postman/Swagger a mano.
- **Vite**: la herramienta estándar actual para arrancar un proyecto de React (reemplaza a `create-react-app`, que React oficialmente discontinuó en 2025). Sirve el proyecto en desarrollo con recarga instantánea y arma el build de producción.
- **Componentes + JSX**: React arma la UI como funciones de JavaScript que devuelven "JSX" (HTML mezclado con JS). Cada página (`LoginPage`, `VuelosPage`, `MisReservasPage`) es un componente.
- **`useState`/`useEffect`** (hooks de React): `useState` guarda un valor que, al cambiar, hace que el componente se vuelva a renderizar (ej. la lista de vuelos, un mensaje de error). `useEffect` ejecuta código al montarse el componente — se usa para pedir los datos a la API apenas se abre la página.
- **Context API (`AuthContext`)**: el equivalente en React a un estado "global" — evita tener que pasar manualmente "quién está logueado" de componente en componente. Cualquier página puede preguntar `useAuth()` y saber el usuario actual.
- **`react-router-dom`**: define las rutas de una SPA (Single Page Application) sin recargar la página completa en cada navegación. `ProtectedRoute` es el equivalente, del lado del cliente, a las reglas de `SecurityConfig`: si no hay usuario logueado, redirige a `/login` antes de mostrar una página protegida — aunque ojo, **esto no reemplaza la seguridad real del backend**, es solo una mejora de experiencia de usuario (la protección de verdad la sigue haciendo Spring Security del lado del servidor).
- **`axios` + interceptor**: librería para llamadas HTTP. Un interceptor de request agrega automáticamente el header `Authorization: Bearer <token>` a cada llamada saliente si hay un token guardado, sin repetir esa lógica en cada función.
- **`localStorage` para guardar el JWT**: simple y estándar para un proyecto de aprendizaje corriendo en el navegador del propio usuario. En un proyecto con mayores exigencias de seguridad se prefiere una cookie `httpOnly` (no accesible desde JavaScript, protege mejor contra robo de token vía XSS).
- **Bug real (de manejo de terminal, no de código)**: `npm install` tirando `ENOENT ... Could not read package.json` porque la terminal estaba parada en una carpeta distinta a la del proyecto (`C:\Users\Usuario` en vez de la carpeta donde estaba el `package.json`). `npm install`/`npm run dev` siempre corren en base a la carpeta actual de la terminal — hay que `cd` hasta la carpeta correcta primero.
- **Aviso de `npm` sobre `allow-scripts` (esbuild)**: versiones recientes de npm no corren automáticamente el script `postinstall` de algunos paquetes por seguridad. En este caso no hizo falta intervenir — Vite arrancó bien igual.

---

## Troubleshooting de Windows: instalación de Docker Desktop / WSL2

Esta parte no tiene que ver con Spring Boot, pero vale la pena documentarla porque fue un troubleshooting real en capas, con el mismo método de siempre (diagnosticar con evidencia real antes de asumir nada):

- **Síntoma inicial**: comandos básicos de Windows (`netstat`, `taskkill`, `wsl`, `msiexec`, `dism`) no se reconocían como comando, pero funcionaban con `.\` adelante o con ruta completa.
- **Causa raíz encontrada**: la variable de entorno `Path` **del sistema** (no la de usuario) tenía sobrescritas las rutas núcleo de Windows (`C:\Windows\system32`, `C:\Windows`, etc.) — el valor arrancaba directamente con una ruta de Oracle Java, señal de que algún instalador viejo (un bug conocido de instaladores de Java antiguos) reemplazó el `Path` completo en vez de agregarle su ruta al final. Se diagnosticó viendo el valor completo con `[System.Environment]::GetEnvironmentVariable('Path','Machine')`, y se solucionó agregando de nuevo las rutas núcleo desde Variables de entorno → Variables del sistema → Path → Editar.
- **Segundo hallazgo**: el servicio de Windows Update (`wuauserv`) estaba **deshabilitado**, y encima `mmc.exe` (la consola que usa `services.msc` para administrar servicios) estaba **bloqueado por una política** ("un administrador bloqueó esta aplicación"). Ambas cosas, sumadas al `Path` roto, apuntan a que el service técnico que reinstaló Windows (~5 meses atrás, por un cambio de disco) usó una copia con activación no oficial — este tipo de herramientas suele deshabilitar Windows Update a propósito (para que no se detecte/revierta la activación) y bloquear las consolas de administración para que no se pueda revertir fácil.
- **Salida práctica sin tener que "curar" Windows Update por completo**: `wsl --update --web-download` baja el kernel de WSL2 directo de internet, sin pasar por Windows Update ni por Microsoft Store — esquivó todo el problema de raíz para lograr el objetivo puntual (tener WSL2 funcionando para poder correr Docker Desktop).
- **Lección general**: cuando varios comandos de sistema fallan "no se reconoce" al mismo tiempo, sospechar de el `PATH` antes que de cada comando individual. Y cuando un servicio de Windows no arranca, el error de `net start`/`net stop` (código de error + mensaje en español) suele decir exactamente cuál es el problema real (deshabilitado, sin permisos, etc.) — leerlo con atención antes de probar cosas al azar.

---

## Docker: dockerizar MySQL y el backend

- **Imagen vs contenedor**: una imagen es la "plantilla" (código + dependencias + sistema de archivos empaquetados); un contenedor es una instancia corriendo de esa imagen — como la relación entre una clase y un objeto.
- **`docker-compose.yml`**: un archivo que describe uno o más "servicios" (contenedores) y cómo se relacionan entre sí (puertos, variables de entorno, volúmenes, dependencias de arranque). Con `docker compose up -d` se levanta todo junto de una vez, en segundo plano (`-d` = detached).
- **Volúmenes con nombre** (`volumes: mysql_data:/var/lib/mysql`): Docker administra dónde vive físicamente ese volumen; los datos sobreviven aunque se borre y recree el contenedor. Sin volumen, perder el contenedor significa perder los datos.
- **Redes internas de Docker Compose / resolución por nombre de servicio**: los contenedores de un mismo `docker-compose.yml` se pueden llamar entre sí usando el **nombre del servicio** como si fuera un hostname (ej. `PMA_HOST: mysql`, o `SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/...`) — Docker arma esa resolución de nombres solo. Importante: `localhost` **adentro** de un contenedor se refiere a sí mismo, nunca a otro contenedor.
- **Variables de entorno pisan `application.properties`**: Spring Boot mapea automáticamente variables de entorno en MAYÚSCULAS_CON_GUION_BAJO (ej. `SPRING_DATASOURCE_URL`) a la propiedad equivalente (`spring.datasource.url`), sin tocar el archivo. Así el mismo proyecto corre con `localhost` desde IntelliJ y con el nombre del servicio Docker cuando está containerizado, sin duplicar configuración.
- **`healthcheck` + `depends_on: condition: service_healthy`**: `depends_on` sin más solo espera a que el contenedor **arranque**, no a que el servicio esté realmente listo para recibir conexiones — un `healthcheck` (ej. `mysqladmin ping`) verifica el estado real, y `condition: service_healthy` hace que otro servicio (el backend) espere ese chequeo antes de arrancar. Esto evita el bug real que sí sufrimos manualmente una vez (ver abajo).
- **Bug real: la primera vez que un contenedor de MySQL arranca con un volumen vacío, se reinicia internamente.** El propio proceso de inicialización (crear la base, el usuario, aplicar el `MYSQL_DATABASE`) hace que el servidor arranque, se reinicie, y recién ahí quede definitivamente arriba. Si algo intenta conectarse justo en esa ventana, falla — por eso el `healthcheck` de arriba es la solución real (no un simple `sleep`).
- **`Dockerfile` multi-stage**: una primera etapa (`FROM eclipse-temurin:21-jdk AS build`) tiene todo lo necesario para compilar (JDK completo + Maven Wrapper), y una segunda etapa final (`FROM eclipse-temurin:21-jre`) solo copia el `.jar` ya compilado (`COPY --from=build`) sin arrastrar herramientas de build a la imagen que se corre. Copiar primero `pom.xml` y bajar dependencias, y recién después copiar `src`, aprovecha el cacheo de capas de Docker (cambiar solo el código no obliga a re-bajar todas las dependencias).
- **Bug real: `Public Key Retrieval is not allowed`.** Error de MySQL 8 (no de credenciales ni de red): el método de autenticación por defecto (`caching_sha2_password`) necesita intercambiar una clave pública con el cliente, y el driver no lo permite por defecto sin conexión cifrada. Se agrega `allowPublicKeyRetrieval=true` a la URL JDBC (seguro para desarrollo local sin TLS real). El error aparece enterrado varias líneas dentro de un stack trace larguísimo — el mensaje de más arriba (`Unable to determine Dialect without JDBC metadata`) es solo el síntoma genérico de "no pude conectarme", hay que bajar hasta encontrar la causa real (`Caused by:` más profundo).
- Con esto, XAMPP queda completamente reemplazado: MySQL, phpMyAdmin y el propio backend corren los tres en contenedores Docker, coordinados por un solo `docker-compose.yml`.

---

## Deploy en producción: Aiven (MySQL) + Render (backend) + Vercel (frontend)

Con esto el proyecto pasó de "corre en mi máquina" a estar accesible en internet, con tres proveedores distintos coordinados entre sí. Mismo método de siempre: cada pieza se probó de forma aislada antes de conectar la siguiente, y cada bug se diagnosticó con evidencia real antes de suponer nada.

- **Elección de proveedores**: Aiven (MySQL, capa gratuita real sin tarjeta, se auto-apaga por inactividad pero se reactiva solo), Render (backend Dockerizado, capa free duerme a los 15 min sin tráfico), Vercel (frontend, estándar de la industria para SPAs). Se descartó Railway porque su capa gratuita dejó de ser de uso continuo (solo un crédito inicial de prueba).

**Bug real, el más importante de todo este módulo: `Access denied` que en realidad no era ni de red ni de credenciales.** Al probar la conexión a Aiven desde `application.properties`, aparecía `java.sql.SQLException: Access denied for user 'avnadmin'@'<ip>' (using password: YES)`. La primera sospecha (razonable) fue un firewall/IP allowlist de Aiven — pero un bloqueo de red nunca llega a generar ese mensaje puntual, porque lo emite el propio servidor MySQL *después* de aceptar la conexión y evaluar credenciales; un firewall real da timeout o "connection refused", nunca "access denied... using password". Para descartar variables una por una, se probó la conexión **fuera de Spring por completo**, con un cliente de mysql aislado en un contenedor descartable:
```
docker run --rm -it mysql:8.0 mysql -h <host> -P <puerto> -u avnadmin -p <db>
```
Esa conexión anduvo perfecto — confirmando que las credenciales y la red estaban bien, y que el problema estaba en cómo Java leía el archivo. La causa real: al pegar la URL de Aiven en `application.properties`, quedó **pegada sin salto de línea justo después del valor de la contraseña** (`spring.datasource.password=abc123spring.datasource.url=jdbc:...`). En un archivo `.properties`, una "línea" sigue siendo la misma hasta el próximo salto de línea real — así que la contraseña que Java estaba mandando en realidad era la contraseña real + toda la URL pegada atrás. Lección: ante cualquier "access denied" con contraseña que "debería ser correcta", conviene aislar la conexión de la capa de la aplicación (un cliente de base de datos directo) antes de sospechar de la infraestructura de red.

- **Verificar sin poder ver la contraseña en pantalla**: el cliente de `mysql` oculta la contraseña por completo al tipearla (ni siquiera asteriscos) — comportamiento normal, no significa que la terminal "no deja escribir". En Windows, si el prompt interactivo de contraseña realmente no acepta ningún input (ni con Enter), alternativa práctica: pasar la contraseña directo en el comando, sin prompt interactivo: `-pLA_CONTRASEÑA` (pegado, sin espacio) o vía variable de entorno `-e MYSQL_PWD=...` (evita que quede en el historial de la shell).
- **Riesgo real de dejar una sesión de `mysql>` abierta sin salir**: si escribís un comando de la shell (`docker compose stop`) *dentro* del prompt `mysql>` por error (por ejemplo, en la misma ventana de terminal que quedó abierta de una prueba anterior), mysql lo interpreta como el inicio de una sentencia SQL sin terminar y se queda esperando (`->`). Se sale con `\c` (cancela la sentencia a medio escribir) y después `exit;` (cierra el cliente).

- **`server.port=${PORT:8080}` en `application.properties`**: Render (como la mayoría de plataformas de hosting) le inyecta a tu contenedor una variable de entorno `PORT` (en Render, `10000` por defecto) y espera que la aplicación escuche exactamente ahí — si tu app sigue fija en el 8080 sin leer esa variable, Render nunca detecta que el servicio levantó ("no open ports detected"), aunque adentro del contenedor todo esté funcionando bien. La sintaxis `${PORT:8080}` le dice a Spring "usá la variable de entorno `PORT` si existe, si no, `8080` por defecto" — así el mismo `application.properties` sirve para local (sin esa variable) y para Render (que sí la define).
- **Variables de entorno en Render/Vercel = el mismo mecanismo que `docker-compose.yml`, en la nube.** Así como local las variables de entorno del compose pisaban `application.properties` sin tocar el archivo, en Render se configuran `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD` en el panel del servicio (apuntando a Aiven) — el mismo código corre distinto según el entorno, sin ningún `if` ni archivo separado por ambiente.
- **`docker-compose.yml` no se usa para nada en Render.** Cada servicio de Render corresponde a un solo contenedor/imagen — Render solo lee el `Dockerfile`. El `mysql` y `phpmyadmin` del compose quedan afuera de este esquema: Aiven reemplaza a MySQL, y phpMyAdmin era una herramienta de desarrollo local que no tiene sentido en producción.
- **Permisos de la GitHub App al conectar Render/Vercel**: si el repo no aparece en la lista al crear el servicio, no es un bug — la app de GitHub que usa Render/Vercel solo puede ver los repos que le diste acceso explícitamente al instalarla. Se soluciona yendo a la configuración de la instalación de esa app en GitHub (`github.com/apps/<app>/installations/new` o el link que ofrezca la propia plataforma) y agregando el repo faltante a la lista de "Repository access".
- **Variables de entorno de Vite (`VITE_...`) se "hornean" en el momento del build, no en tiempo de ejecución** — a diferencia de las variables de Spring Boot, que se leen cada vez que arranca el proceso. Por eso `VITE_API_URL` no se edita en el `.env` local para producción: se configura directo en el panel de Vercel, y ese valor queda fijo dentro del bundle de JS generado en cada deploy.

- **CORS en producción — un origen nuevo por cada dominio real.** El `CorsConfigurationSource` del backend tenía permitido solo `http://localhost:5173`. Al desplegar el frontend en Vercel, con una URL nueva (`https://algo.vercel.app`), el navegador bloqueó los pedidos con el error clásico `has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present`. Se soluciona agregando la URL real de producción a la lista de `setAllowedOrigins(...)`, junto (no en reemplazo) de la de `localhost`, para poder seguir desarrollando local sin problemas.

- **El problema de "quién crea al primer admin"**: no existe forma de crear un usuario `ADMIN` vía la API pública, porque esa misma API exige ser `ADMIN` para crearlos (y está bien que sea así — nunca se debe confiar en un rol que mande el cliente al registrarse). Con una base de datos nueva en Aiven, sin ningún admin todavía, se resuelve **por fuera de la API**: conectarse directo a la base (mismo cliente de mysql aislado de antes) y promover a mano al primer usuario con una consulta SQL (`UPDATE usuarios SET rol = 'ADMIN' WHERE email = '...'`). Es una operación de bootstrap manual, puntual, que se hace una sola vez por entorno.
- **Dato importante después de promover a un usuario a mano**: el JWT que ya tenías de ese usuario queda "viejo" — como el rol se graba dentro del token en el momento del login (claim `rol`), cambiar la base no actualiza tokens ya emitidos. Hay que volver a loguearse para conseguir un token nuevo que refleje el rol actualizado (aunque, como está anotado más arriba, la *autorización real* de cada request en este proyecto se recalcula siempre fresca contra la base vía `UsuarioDetailsService`, no contra el claim del JWT — el token viejo en este proyecto puntual seguiría funcionando bien igual; loguearse de nuevo es más que nada para tener un `LoginResponseDTO` consistente con la realidad).

- **Higiene de secretos con git**: durante las pruebas de conexión a Aiven se cambió temporalmente `application.properties` para apuntar a producción con la contraseña real en texto plano — antes de continuar, se confirmó explícitamente que ese cambio **nunca se subió a git** (no se hizo commit de esa versión). Si se llega a commitear una contraseña real por error, no alcanza con borrarla del archivo después: git guarda el historial completo, así que además hay que rotar/resetear esa contraseña en el proveedor (Aiven, en este caso) para invalidar la que quedó expuesta.

---

## Completando el backend para el panel de administración

Antes de arrancar el rediseño completo del frontend (pensado como carta de presentación para el CV), se auditó Swagger contra lo que iba a necesitar un panel de ADMIN completo y aparecieron 5 endpoints faltantes. Proceso elegido a propósito para este módulo: implementar los 5 antes de tocar un solo commit, y recién ahí probar todo junto — más cómodo que ir commiteando de a uno.

**Los 5 endpoints agregados:**
- `PUT /api/usuarios/{id}/rol` — cambiar el rol de un usuario (para que un ADMIN pueda promover a otro usuario). Nuevo DTO chico y dedicado (`CambioRolDTO`, un solo campo `rol` con `@NotNull`) en vez de reusar un DTO de edición de usuario más grande — cuando una operación cambia *un solo campo puntual* de un recurso, un DTO angosto es más claro que forzar el DTO genérico de edición completa.
- `PUT /api/vuelos/{id}` — editar un vuelo existente.
- `DELETE /api/vuelos/{id}` — eliminar un vuelo, con una regla de negocio nueva: no se puede borrar un vuelo si tiene reservas asociadas (se valida con un nuevo derived query method, `existsByVueloId`, antes de intentar el `delete`).
- `PUT /api/aviones/{id}` — editar un avión, con dos reglas de negocio nuevas: no permitir dejarlo con la misma matrícula que otro avión ya existente (`existsByMatriculaAndIdNot`, que excluye el propio id para no chocar consigo mismo al editar), y no permitir reducir la `capacidad` por debajo de los `asientosDisponibles` ya comprometidos en cualquiera de sus vuelos existentes (recorriendo `findByAvionId` y comparando).
- `GET /api/reservas` — listar todas las reservas del sistema (antes solo existía `findByUsuarioId`, pensado para que un usuario vea las suyas — este nuevo endpoint es la vista de ADMIN sobre *todas*).

**Reglas de seguridad para los endpoints nuevos**: de los 5, solo 2 necesitaron un cambio real en `SecurityConfig` (`PUT /api/usuarios/**` y `GET /api/reservas`, ambos agregados como `hasRole("ADMIN")` antes de la regla más genérica que ya cubría esa ruta) — los otros 3 ya caían dentro de reglas `hasRole("ADMIN")` que ya existían de antes (`PUT/DELETE /api/vuelos/**` y todo `/api/aviones/**`). Buen recordatorio de que agregar un endpoint nuevo no siempre implica tocar la seguridad — a veces ya está cubierto por una regla más amplia que ya existía.

**DRY aplicado a `VueloService`**: la validación de reglas de negocio de un vuelo (asientos ≤ capacidad del avión, fecha de llegada posterior a la de salida, precio > 0) estaba solo dentro de `crearVuelo`. Al agregar `editarVuelo`, en vez de copiar y pegar esas mismas tres validaciones, se extrajeron a un método privado compartido (`validarDatosVuelo(Vuelo vuelo, Avion avion)`), llamado desde ambos métodos. Señal general: la necesidad de reusar código entre dos métodos parecidos es la oportunidad de extraer el código compartido, no de duplicarlo.

**Bug real, el más importante de este módulo: `setAvion` con el objeto equivocado.** `editarVuelo` recibe el nuevo estado del vuelo ya mapeado desde el DTO (`datosNuevos = VueloMapper.toEntity(request)`) más el `avionId` suelto por separado. La línea `vueloExistente.setAvion(datosNuevos.getAvion())` compilaba perfecto pero estaba mal: `VueloMapper.toEntity(...)` nunca arma un objeto `Avion` real dentro del DTO (el mapper solo conoce el `avionId`, un `Long` suelto, no la entidad completa) — así que `datosNuevos.getAvion()` siempre devolvía `null`, y guardar un vuelo con `avion = null` rompía la restricción `NOT NULL` de la columna `avion_id` en MySQL (`Column 'avion_id' cannot be null`, `ErrorCode: 1048`). La corrección: usar la variable `avion` que el propio método ya había buscado un par de líneas antes vía `avionRepository.findById(avionId)` — `vueloExistente.setAvion(avion)`. Mismo patrón de bug que "faltaba un @Bean/anotación": código sintácticamente válido que hace algo distinto de lo que parece a simple vista — acá el error no era de sintaxis sino de *qué objeto* se estaba leyendo.

**Lección de proceso para la próxima vez (propuesta durante este mismo módulo): probar en local *antes* de hacer commit, y mucho antes de hacer deploy.** Este bug se encontró recién probando contra Render ya desplegado — hubo que: diagnosticar vía logs de Render (truncados, hay que scrollear para encontrar el `Caused by:` real), corregir el código, y recién ahí redeployar para confirmar. Si se hubiera levantado la API local primero (mismo Docker Compose de MySQL + backend corrido desde IntelliJ) y probado ahí, el error habría aparecido con el stack trace completo en la propia consola al instante, sin gastar un ciclo entero de deploy en la nube para descubrirlo. Character adicional real de este intento: la primera vez que se probó local después de arreglar el código, dio el mismo error porque **la aplicación no se había reiniciado** — un cambio en el código de Java no se aplica solo, hay que parar el proceso viejo (que sigue corriendo con el `.class` compilado anterior) y volver a correrlo. Y ahí apareció el clásico "puerto 8080 ya en uso" (ver la entrada ya existente sobre esto en la sección de Herramientas) porque el proceso viejo no había liberado el puerto todavía.

**Separación de commits por tipo de cambio**: los 5 endpoints (funcionalidad nueva) se commitearon con `feat:`, y la corrección del bug de `setAvion` (encontrado después, en pruebas) se commiteó aparte con `fix:` — mantiene el historial de git legible, cada commit contando una sola historia coherente en vez de mezclar "agrego algo nuevo" con "arreglo algo que rompí sin querer" en el mismo commit.

**Cierre del módulo: tests para los 5 endpoints nuevos, en las dos capas.** Antes de pasar al frontend, se completó la cobertura de tests que había quedado pendiente — buena práctica general: cerrar cada módulo de backend con su red de tests antes de construir algo nuevo encima, en vez de acumular deuda de testing.
- **Capa de servicio (`@ExtendWith(MockitoExtension.class)`, sin Spring ni base real)**: casos de éxito, recurso inexistente (`RecursoNoEncontradoException`) y cada regla de negocio nueva (vuelo con reservas al eliminar, matrícula duplicada y capacidad insuficiente al editar avión). El test de `editarVuelo` con éxito quedó armado a propósito para funcionar como **test de regresión** del bug del `setAvion`: usa `thenAnswer(inv -> inv.getArgument(0))` (en vez de `thenReturn` con un objeto fijo) para poder inspeccionar el objeto *después* de que el método lo mutó, y compara explícitamente `resultado.getAvion()` contra el avión esperado — si alguien reintrodujera `datosNuevos.getAvion()` en el futuro, este test fallaría inmediatamente en vez de que el bug reaparezca recién en producción.
- **Capa de controller (`@WebMvcTest` + `MockMvc` + `@Import(SecurityConfig.class)`)**: acá el foco no es repetir la lógica de negocio (ya cubierta en el service), sino confirmar el **comportamiento HTTP y la seguridad real** de cada endpoint — `403` sin autenticación, `403` con rol insuficiente (`@WithMockUser(roles = "USUARIO")` contra una ruta `hasRole("ADMIN")`), y el código correcto para cada excepción mapeada por `GlobalExceptionHandler`. Se le prestó atención especial a `PUT /api/usuarios/{id}/rol` y `GET /api/reservas`, los dos únicos endpoints de este módulo que requirieron un cambio real en `SecurityConfig` (los otros tres ya caían bajo reglas `hasRole("ADMIN")` preexistentes).
- **Método `void` + Mockito**: para simular que un método sin retorno (`eliminarVuelo`) tira una excepción, la sintaxis es al revés de la habitual — no se puede encadenar `when(mock.metodo()).thenThrow(...)` porque no hay nada que "cuando" capture de un `void`; se usa `doThrow(new Excepcion(...)).when(mock).metodo(argumentos)`.
- **Bug real (de tipeo, no de lógica): `@WithMockUser(roles = "ADMN")`.** Un test de `eliminarVuelo` con éxito fallaba con `403` en vez de `204`, a pesar de que el resto de los tests con `ADMIN` en la misma clase pasaban bien — la primera sospecha razonable hubiera sido algo más profundo (fuga de `SecurityContext` entre tests, ya documentada como un bug real anterior en este mismo archivo), pero la causa fue mucho más simple: al rol le faltaba la "I" (`"ADMN"` en vez de `"ADMIN"`). `@WithMockUser` arma la authority como `"ROLE_" + rol`, así que `"ROLE_ADMN"` nunca matcheaba contra lo que pedía `hasRole("ADMIN")` (`"ROLE_ADMIN"`). Lección reforzada: ante un fallo raro, revisar primero lo más simple (un typo en el propio test) antes de asumir un bug de framework.

---

## Rediseño completo del frontend (Tailwind CSS + panel de administración)

A diferencia del backend (siempre escrito por vos), esta parte la construí yo directamente de punta a punta, como quedó acordado — pero con diseño colaborativo: antes de tocar código, armamos mockups estáticos en HTML/CSS puro con 2-3 propuestas de paleta/estética, y entre los dos fuimos combinando lo que más te gustaba de cada una (paleta y estructura de la propuesta "clean moderna", logo circular con ícono de avión de otra, botones tipo píldora y tipografía extra bold de otra, fondo con patrón de puntos de otra) hasta llegar a un sistema de diseño único, con su versión en modo oscuro incluida.

- **Bug real en los mockups iniciales**: la primera versión usaba el CDN de Tailwind alojado en `cdnjs.cloudflare.com`, que sirve el paquete ya compilado (pensado para un proceso de build), **no** el script de "Play CDN" que compila las clases al vuelo en el navegador. Resultado: ninguna clase se aplicaba y la página se veía como HTML sin estilos. Se solucionó escribiendo el CSS a mano para esos mockups de comparación (nada de Tailwind ahí, total era descartable), y usando Tailwind "de verdad" recién en el proyecto real, vía su CLI/PostCSS (que sí compila correctamente porque procesa los archivos fuente, no depende de un script de navegador).
- **Preferencia de diseño explícita, reforzada dos veces**: reemplazar íconos por SVGs de línea (estilo Feather/Heroicons) en vez de emojis, "por más profesional" — aplicado de forma consistente en todo el proyecto real (logo del avión, toggle de modo oscuro, sidebar del admin, etc.), no solo donde se pidió inicialmente.

**Restricción real de este entorno de trabajo (no del proyecto en sí): sin acceso a los registries de npm ni permiso de push al repo de GitHub del frontend.** Un `npm install` normal fallaba con `403 Forbidden` incluso para paquetes ya publicados y estables (`tailwindcss`, `react`, hasta un simple `npm view`), y un intento de `git push` fue rechazado explícitamente por el proxy de git del entorno ("repository not in this session's authorized repository set"). Esto llevó a un flujo de trabajo distinto al del backend: en vez de pegar código en el chat para que lo escribas vos, o hacer push directo, escribí el proyecto completo en una copia clonada del repo y lo entregué empaquetado en `.zip` por tandas (Tailwind + sistema de diseño → Perfil + andamiaje admin + dashboard → CRUD completo del panel), cada una para que la copiaras sobre tu carpeta local, corrieras `npm install && npm run dev` vos (con acceso normal a internet) y confirmaras que compilaba antes de seguir con la siguiente — mismo principio de "probar antes de seguir" que ya habíamos aprendido con el backend, aplicado acá porque en este entorno no hay forma de levantar Vite ni instalar nada para verificarlo de antemano.

- **Bug real, encontrado en la primera tanda: dependencia circular en Tailwind (`@apply` sobre una clase que se referencia a sí misma sin darse cuenta).** En `tailwind.config.js` se agregó un `backgroundSize` personalizado con la clave `dots` (pensado para generar la utilidad `bg-dots`, el tamaño del patrón de puntos). Pero en `index.css` ya existía una clase de componente propia llamada también `.bg-dots` (el fondo con patrón completo), que hacía `@apply bg-dots-light bg-dots dark:bg-dots-dark`. Como la clave `dots` del config genera automáticamente una utilidad literalmente llamada `bg-dots`, el `@apply bg-dots` de adentro de `.bg-dots` terminaba refiriéndose a sí misma — Tailwind lo detecta y tira `You cannot @apply the 'bg-dots' utility here because it creates a circular dependency`. Solución: renombrar la clave del config a `dots-pattern` para que no choque con el nombre de la clase de componente. Lección: al definir utilidades personalizadas en Tailwind, cuidado con reusar un nombre que ya existe como clase propia en `@layer components` — el motor de Tailwind no distingue "tu" clase de una utilidad generada, ambas viven en el mismo espacio de nombres de clases CSS.
- **Dark mode con estrategia `darkMode: 'class'`**: en vez de depender solo de la preferencia del sistema operativo (`prefers-color-scheme`), Tailwind permite alternar manualmente agregando la clase `dark` al `<html>`. Se guarda la preferencia en `localStorage` (clave `theme`) vía un `ThemeContext` propio.
- **"Anti-flash" de tema**: sin cuidado extra, la página carga primero en modo claro (mientras React todavía no montó) y recién después `ThemeContext` corrige a oscuro si correspondía — un parpadeo visible e incómodo. Se resuelve con un `<script>` chiquito e inline en el `<head>` del `index.html` (no en un archivo JS aparte, para que se ejecute *antes* de que el navegador pinte nada), que lee `localStorage`/la preferencia del sistema y agrega la clase `dark` al `<html>` de una, antes de que React exista.
- **`ProtectedRoute` extendido con una prop `soloAdmin`**: mismo componente de antes, ahora además puede exigir un rol específico y no solo "estar logueado". Remarcado explícitamente en el código como lo que es: una mejora de experiencia de cliente (evita el parpadeo de ver una página que de todos modos va a fallar al pedir datos), **no** una medida de seguridad real — esa la sigue haciendo Spring Security del lado del servidor, y un usuario que fuerce la URL a mano no puede leer ni modificar nada que el backend no le permita.
- **Rutas anidadas de React Router (`<Outlet />`)**: el panel de admin (`/admin`, `/admin/vuelos`, `/admin/aviones`, `/admin/usuarios`, `/admin/reservas`) se armó con una ruta padre (`AdminLayout`, que dibuja el sidebar) y rutas hijas que se renderizan donde el layout pone `<Outlet />` — así el sidebar no se re-renderiza al navegar entre secciones del panel.
- **Gap real de diseño encontrado al construir el Perfil de usuario**: `GET /api/usuarios/{id}` es `hasRole("ADMIN")` en `SecurityConfig` — por diseño, ya que ese endpoint expone datos de cualquier usuario. Pero eso significa que un usuario común no tiene forma de pedir sus *propios* datos completos (como el `nombre`, que no viaja en el `LoginResponseDTO`). La página de Perfil quedó armada con lo que sí está disponible del lado del cliente (email, rol, id, guardados desde el login) más un resumen de reservas propias — **queda pendiente para mañana** un endpoint nuevo tipo `GET /api/usuarios/me` (identificando al usuario por el JWT, no por un id en la URL) si se quiere mostrar el nombre real en el perfil.
- **Mismo bug de "rol viejo" que ya habíamos documentado, pero ahora del lado del frontend**: después de promover un usuario a ADMIN a mano en la base (mismo procedimiento de bootstrap ya conocido), el navbar seguía mostrando "Usuario". Causa: el objeto `user` en `AuthContext` se lee de `localStorage`, que solo se actualiza en el momento del login — ni recargar la página (F5) alcanza, porque eso solo vuelve a leer el `localStorage` viejo, no vuelve a pedir el login. Hace falta cerrar sesión y loguearse de nuevo para que el frontend pida un `LoginResponseDTO` fresco con el rol actualizado.
- **Decisiones de scope tomadas contra el backend real, no contra lo que "debería" tener una app de este tipo**: no hay botón de eliminar avión en el panel porque el backend no tiene ese endpoint (solo crear/editar/listar) — no se inventó uno en el frontend que después no tendría con qué hablar. Al revés, cancelar una reserva desde el panel de reservas de ADMIN funciona para *cualquier* reserva del sistema (no solo las propias) porque así está permitido en el backend (`ReservaService.cancelarReserva` no valida dueño) — coherencia entre lo que el frontend ofrece y lo que la API realmente permite, en las dos direcciones.
- **Gráficos del dashboard sin ninguna librería externa**: dada la restricción de npm de este entorno, las barras de "vuelos por estado" / "reservas por estado" del dashboard son un componente `BarList` propio hecho con `div`s y anchos porcentuales de Tailwind, no un `<canvas>` ni una librería tipo Recharts — decisión pragmática, pero también una que vale la pena revisar a futuro si se quiere algo más rico visualmente.

---

## Cierre del rediseño: endpoint `GET /api/usuarios/me`

Cierre del gap de diseño que había quedado anotado en el módulo anterior: la página de Perfil no podía mostrar el `nombre` real del usuario porque `GET /api/usuarios/{id}` es `hasRole("ADMIN")` por diseño, y el `LoginResponseDTO` nunca trajo el nombre.

**Backend (escrito por vos, como siempre):**
- `UsuarioService.buscarPorEmail(String email)`: nuevo método, mismo patrón que `buscarPorId` pero buscando por email (`usuarioRepository.findByEmail(...).orElseThrow(...)`).
- `UsuarioController.obtenerMiPerfil(Authentication authentication)`: nuevo `GET /api/usuarios/me`. La clave es el parámetro `Authentication` — Spring Security lo inyecta automáticamente con el principal autenticado, y `authentication.getName()` devuelve el username (en este proyecto, el email, porque así se diseñó `UsuarioDetailsService`). Con eso alcanza para buscar "quién soy" sin ningún id en la URL — a diferencia de `GET /api/usuarios/{id}`, acá no hay forma de que un usuario pida los datos de otro, porque el id nunca lo manda el cliente.
- **Orden de reglas en `SecurityConfig`, otra vez importa**: hubo que agregar `.requestMatchers(HttpMethod.GET, "/api/usuarios/me").authenticated()` **antes** de la regla ya existente `.requestMatchers(HttpMethod.GET, "/api/usuarios/**").hasRole("ADMIN")` — si hubiera quedado después, la regla más genérica (que matchea `/me` porque `/**` incluye cualquier sufijo) hubiera ganado primero y bloqueado a cualquier usuario no-ADMIN. Mismo principio ya documentado varias veces en este archivo: reglas específicas antes que las genéricas que las contienen.
- Tests en las dos capas, incluido uno pensado explícitamente como regresión: `obtenerMiPerfil_conUsuarioAutenticado_deberiaDevolver200` usa `@WithMockUser(username = "...", roles = "USUARIO")` (no ADMIN) a propósito, para que si algún día se rompe el orden de las reglas de `SecurityConfig` de nuevo, este test lo detecte inmediatamente. Suite completa cerró en 92 tests, 0 failures, 0 errors.
- Verificado en tres niveles antes de dar el módulo por cerrado: tests automatizados (92/92) → Swagger local con un usuario `USUARIO` real → Swagger de producción (Render) con otro usuario real, ambos devolviendo 200 con los datos correctos.

**Bug real / hallazgo de infraestructura, no de código: un contenedor Docker propio compitiendo por el puerto 8080.** Al reiniciar el backend local para que tomara el endpoint nuevo, el puerto 8080 seguía "ocupado" incluso después de matar el proceso Java de IntelliJ con `taskkill`. La causa no era otro `java.exe` colgado (como el bug ya documentado antes) sino un contenedor Docker separado, `aerolinea-backend` (una imagen dockerizada de la propia API, de una prueba de días atrás), mapeado a `0.0.0.0:8080->8080/tcp` y corriendo en paralelo. Como el contenedor tiene el puerto tomado a nivel de Docker (`docker-proxy`), ningún `taskkill` sobre un proceso Java local lo libera. Diagnóstico real: `docker ps` y mirar la columna `PORTS` de cada contenedor, no asumir que el conflicto siempre es un proceso suelto de Windows. Solución puntual: `docker stop aerolinea-backend`, dejando corriendo solo `aerolinea-mysql` (necesaria) y `aerolinea-phpmyadmin` (en el 8081, sin conflicto). Lección: cuando el puerto 8080 "no se libera" a pesar de matar el proceso esperado, revisar también `docker ps` — puede haber más de una cosa escuchando ahí, y no todas son procesos de Windows.

**Frontend (escrito por mí, como el resto del rediseño):** se agregó `obtenerMiPerfil()` en `src/api/usuarios.js` (`GET /api/usuarios/me`) y se actualizó `PerfilPage.jsx` para pedir el perfil real al montar la página y mostrar el `nombre` como título principal (con el email debajo, más chico) en vez de mostrar el email como título — con fallback silencioso al email si la petición fallara, para que la página nunca se rompa por esto.

---

## Deploy del frontend rediseñado a Vercel

El proyecto de Vercel ya existía (conectado desde el primer deploy, antes del rediseño) — solo hizo falta pushear los cambios nuevos para que se redesplegara solo (auto-deploy en cada push a la rama conectada).

- **URL de producción vs. URL de preview de Vercel.** Cada deploy de Vercel genera dos URLs distintas: una de **producción**, fija y corta (ej. `https://aero-pass-frontend.vercel.app`, sin cambiar nunca entre deploys), y una de **preview**, única por deploy, con un hash en el medio (ej. `https://aero-pass-frontend-1jtaiez8b-pablo-8cd0.vercel.app`). Entrar por la URL de preview equivocada hizo aparecer un error de CORS que en realidad no era un bug: el backend nunca tuvo (ni necesitaba tener) esa URL de preview en su whitelist, porque cambia en cada deploy — no tendría sentido perseguirla. La URL correcta para usar/compartir siempre es la de producción, que además ya estaba en la whitelist de CORS del backend desde el deploy original (antes del rediseño), por eso funcionó sin tocar nada del backend.
- **Verificación completa en producción**: login, Perfil (con nombre real vía `/me`), Dashboard admin con estadísticas reales, CRUD de Vuelos, y el toggle de modo claro/oscuro — todo probado directo contra la URL de producción, con datos reales del backend en Render + Aiven.

---

## Preparando el proyecto para mostrarlo a terceros: usuario de demo + README

Con el deploy verificado (puntos 12 y 13 ya cerrados), el último paso antes de actualizar el CV fue dejar el proyecto listo para que cualquiera pueda entrar a probarlo sin depender de que vos estés presente ni tengas que explicar nada por chat.

- **Usuario de demo con rol ADMIN, promovido desde el propio panel, no por SQL.** En vez de repetir el procedimiento de bootstrap manual (`UPDATE usuarios SET rol = 'ADMIN' ...` directo en Aiven), se usó el flujo que el propio sistema ya ofrece: registrar el usuario de demo normal (rol `USUARIO` por defecto) y promoverlo desde `Panel admin → Usuarios → Hacer admin`, ya logueado con una cuenta admin existente. Más simple, y además es una forma más de confirmar que esa funcionalidad del panel funciona de punta a punta en producción.
- **README.md en los dos repos**, pensados para que un reclutador o entrevistador técnico entienda el proyecto en la primera pantalla sin tener que clonar nada: qué es, link a la demo en vivo, credenciales de un usuario de prueba (rol ADMIN) para ver el panel completo, lista de funcionalidades, stack técnico, cómo correrlo en local, y cómo está desplegado (Aiven + Render + Vercel). El del frontend lo armé directo (como el resto del rediseño); el del backend te pasé el contenido para que lo agregaras vos a tu repo, mismo criterio que con el resto del código de ese proyecto.
- **Aviso del "cold start" de Render en el propio README**: la capa gratuita duerme el contenedor tras 15 minutos sin tráfico, y el primer request después de eso tarda entre 30 y 50 segundos en responder. Documentarlo explícitamente evita que alguien evaluando el proyecto piense que está roto cuando en realidad solo se está despertando.
- **Contraseña de un usuario de demo en texto plano dentro de un README público**: normalmente sería una mala práctica de seguridad, pero en este caso puntual es intencional y aceptable — es una cuenta armada específicamente para que cualquiera la use libremente, sin datos reales ni sensibles detrás.
- **CV actualizado en paralelo a este cierre**: se armaron dos versiones (español para Argentina, inglés para el exterior), formato ATS-friendly (una sola columna, sin fotos ni íconos decorativos, texto real seleccionable), con la sección de AeroPass ampliada para reflejar todo lo construido desde el rediseño (seguridad JWT, tests, Docker, deploy multi-proveedor) y el trabajo de frontend descripto honestamente como "dirigido e integrado con asistencia de IA" — decisión tomada a propósito para no sobre-representar habilidades de React en una entrevista técnica.

---

## Repaso final: fortalezas y áreas a reforzar

Antes de sumar features nuevas, hicimos un repaso activo (preguntas y respuestas, no solo relectura pasiva) de los conceptos centrales de todo el proyecto — arquitectura en capas y DTOs, testing (unit vs. controller), transacciones/concurrencia en reservas, manejo centralizado de excepciones, seguridad con JWT, y Docker/deploy.

**Resultado por bloque:**
- DTOs y arquitectura en capas: sólido. Buena identificación de filtración de datos y de la necesidad de formas distintas de un mismo recurso; se sumó como refuerzo el control sobre qué puede escribir el cliente (un DTO de request angosto es una restricción "gratis") y el desacople entre el modelo interno y el contrato público de la API.
- Testing (unit vs. controller): sólido, con un matiz importante reforzado — no es que "un método puede fallar y romper el sistema", es que cada tipo de test cubre una capa que el otro literalmente no puede ver (un unit test nunca pasa por Spring Security ni serializa JSON; un test de controller no repite la lógica de negocio, confirma HTTP/seguridad real).
- Concurrencia y bloqueo pesimista (reservas): sólido, explicado correctamente sin ayuda.
- Manejo de excepciones (`@RestControllerAdvice`): sólido una vez explicado — el punto clave es evitar `try/catch` repetido y garantizar un formato de error consistente en toda la API sin poder "olvidarse" de un caso nuevo.
- **Seguridad / JWT: el bloque más difícil, como era esperable.** Costó separar qué pregunta puntual se estaba respondiendo (tendencia a mezclar conceptos relacionados — duración del token, firma asimétrica, CSRF — en una sola respuesta en vez de contestar la pregunta exacta que se hizo; buena habilidad a entrenar de cara a entrevistas reales). Temas cubiertos en profundidad: el problema de revocación de JWT (no hay estado del lado del servidor, por eso tokens de corta duración + refresh token es el patrón estándar), la relación directa entre dónde se guarda el token (`localStorage` vs. cookie `httpOnly`) y si hace falta CSRF o no (son decisiones acopladas, no independientes), HS256/HS384 (simétrico, un solo backend — este proyecto usa HS384, corregido más abajo tras revisar un token real) vs. RS256 (asimétrico, útil cuando quien emite el token y quien lo verifica son servicios distintos), y un gap real identificado en el proyecto actual: no hay rate limiting en `/api/auth/login`, dejando la puerta abierta a fuerza bruta de contraseñas.
- Docker y deploy (multi-stage build, networking de Docker Compose por nombre de servicio, `${PORT:8080}` para Render): sólido, sin correcciones — las tres respuestas fueron correctas y completas.

**Conclusión**: la seguridad es el área a reforzar con más profundidad, y quedó anotado como plan explícito (no para ahora) armar un segundo proyecto más adelante que vuelva a tocar JWT/autenticación desde cero, ya con el contexto de esta primera implementación — la seguridad es un tema que recién termina de fijarse en una segunda vuelta.

---

## Nuevas features (1): paginación y filtrado server-side en `GET /api/vuelos`

Primera de las seis features nuevas del roadmap. Alcance acordado de antemano en tres pasos: paginación básica primero (probada en Swagger), recién después filtrado, y por último la integración en el frontend — cada paso probado antes de pasar al siguiente, mismo método de siempre.

**Backend (escrito por vos, como siempre):**
- `VueloRepository.findAll(Pageable)` ya viene gratis por extender `JpaRepository` (que a su vez extiende `PagingAndSortingRepository`) — no hizo falta declarar nada para la paginación básica.
- **Nunca exponer `Page<T>` de Spring directamente en la respuesta de la API** — mismo principio que "nunca exponer la entidad JPA": se envuelve en un DTO propio. Se creó `PageResponseDTO<T>` (genérico: `contenido`, `paginaActual`, `tamanoPagina`, `totalElementos`, `totalPaginas`, `esUltima`) más un `PageMapper.toPageResponseDTO(Page<T>)` estático, mismo patrón Mapper ya usado en todo el proyecto.
- `Page<T>.map(Function)` transforma el contenido (`Page<Vuelo>` → `Page<VueloResponseDto>`) preservando toda la metadata de paginación — no hace falta reconstruir el `Page` a mano.
- `@PageableDefault(size = 10, sort = "fechaSalida")` en el parámetro del controller define los valores por defecto cuando el cliente no manda `page`/`size`/`sort` — Spring resuelve el `Pageable` solo a partir de los query params, sin parseo manual.
- **Filtrado**: se evaluaron dos enfoques — JPA Specifications (más escalable, pero introduce la Criteria API, una herramienta nueva) vs. una query condicional con `@Query` y parámetros opcionales (`(:origen IS NULL OR v.origen = :origen) AND ...`). Para 3 filtros fijos (origen/destino/estado) se eligió la segunda: resuelve el caso real sin sumar una API nueva a mitad de la feature. Specifications queda anotado como concepto a conocer si el filtrado crece mucho más adelante. No hizo falta `countQuery` aparte: al ser una consulta simple sin joins, Spring Data JPA la deriva sola para la paginación.
- Los tres filtros llegan como `@RequestParam(required = false)` sueltos en el controller (no un DTO de filtro aparte, por ser solo 3 campos) — con `null` cuando no se mandan, cae en la rama `IS NULL` de la query y devuelve todo sin filtrar, igual que antes de esta feature.
- Suite completa: 95/95 tests (93 previos + 2 nuevos para el filtrado, cubriendo con y sin filtros en service y controller).

**Bug real, tercera aparición del mismo bug-family de "autocompletado de IntelliJ elige la clase equivocada por nombre repetido":**
- Primera vez en esta feature: `java.awt.print.Pageable` (AWT, nada que ver) en vez de `org.springframework.data.domain.Pageable`.
- Segunda vez, en el test del controller: `org.springdoc.core.converters.models.Pageable` (el modelo interno de Springdoc para generar el schema de Swagger) en vez del de Spring Data — mismo síntoma, otra librería. El popup de autocompletado de IntelliJ mostró hasta 4 clases candidatas con el mismo nombre simple `Pageable` (sumando además `Pageable in DataWebProperties`, una clase de configuración de Spring Boot con nombre parecido pero no relacionada); la única correcta para este uso es siempre la de `org.springframework.data.domain`. Lección ya anotada varias veces en este archivo, reforzada una vez más: mirar siempre el paquete completo antes de aceptar una sugerencia de autocompletado.

**Bug real (no de código, de la propia UI de Swagger): serialización rota del parámetro `sort` en el editor JSON.** Al editar el objeto `pageable` vía el panel "Edit Value" de Swagger UI (que arma el `Pageable` completo como un bloque JSON), el campo `sort` (un array, porque `Pageable` admite ordenar por más de una propiedad a la vez) se mandó en la URL final como el string literal `["fechaSalida"]`, corchetes y comillas incluidos, en vez de convertirlo al formato de query string correcto. Síntoma: `InvalidDataAccessApiUsageException: Sort expression '["fechaSalida"]: ASC' must only contain property references...` — Spring Data JPA intentando ordenar por una "propiedad" que literalmente incluía corchetes. No era un bug del código: se confirmó pasando la misma request por `curl` con `sort=fechaSalida` en texto plano (sin corchetes), que respondió 200 correctamente. Lección: para probar parámetros tipo array de `Pageable` en Swagger, mejor usar los campos individuales si la UI los expone así, o testear por `curl`/navegador directo en vez de confiar en el editor JSON de "Edit Value".

**Verificación manual en Swagger, 5 casos antes de dar el backend por cerrado**: sin filtros, un filtro solo, dos combinados, los tres juntos, y el caso límite (una combinación que no matchea ningún vuelo real) — devolvió `contenido: []`, `totalElementos: 0`, `totalPaginas: 0`, `esUltima: true`, sin romperse.

**Criterio para decidir qué otras entidades vale la pena paginar (no solo "se puede", sino "tiene sentido").** Surgió como pregunta genuina durante la feature: ¿por qué paginar `Vuelo` y no `Avion` también? La regla aplicada: paginar cuando la colección **crece sin límite con el uso normal del sistema** y el endpoint la devuelve **toda de una** (ej. `Vuelo`, que acumula histórico indefinidamente). No tiene sentido cuando la colección es chica y básicamente estática (`Avion`, la flota física de la aerolínea — no crece al ritmo de los vuelos). Candidatas identificadas para el mismo tratamiento más adelante, no planificadas todavía: `Usuario` y `Reserva` (ambas crecen con el uso real del sistema, igual que `Vuelo`).

**Trade-off real encontrado al pensar el impacto en el resto del sistema: paginar rompe el conteo agregado del panel admin.** Tanto `DashboardPage.jsx` (estadísticas: totales, ocupación promedio, ingresos) como `VuelosAdminPage.jsx` (la tabla completa de gestión) dependían de que `listarVuelos()` devolviera **todos** los vuelos de una. Solución pragmática acordada explícitamente como no-ideal: pedir una página grande (`{ size: 1000 }`) desde esas dos vistas en vez de sumarles paginación también. La solución "correcta" (un endpoint de agregación server-side aparte, que calcule los totales sin traer todos los registros) queda documentada acá como pendiente, no implementada — se prioriza avanzar con el resto del roadmap antes de perfeccionar este punto.

**Frontend (escrito por mí, como el resto del proyecto):**
- `listarVuelos()` en `src/api/vuelos.js` pasó de no recibir argumentos a `{ page, size, sort, origen, destino, estado }` (todos opcionales, con defaults) — axios omite solo los parámetros en `undefined`, así que no filtrar es simplemente no pasar esos campos.
- Se dio de baja `buscarVuelosPorRuta()` (pegaba contra `GET /api/vuelos/buscar`, un endpoint separado): quedó redundante una vez que `GET /api/vuelos` mismo soporta filtrar por origen/destino (y ahora también estado). El endpoint viejo del backend no hace falta borrarlo si sigue existiendo — solo quedó sin ningún consumidor del lado del frontend.
- `VuelosPage.jsx` (vista pública) sumó filtro por estado (dropdown con los 5 valores del enum, reutilizando las mismas etiquetas ya definidas en `Badge.jsx`), botón "Limpiar filtros", contador de resultados, y controles de paginación (Anterior/Siguiente + "Página X de Y", visibles solo si hay más de una página).
- `DashboardPage.jsx` y `VuelosAdminPage.jsx`: mismo fix pragmático del lado del backend, adaptado al frontend — piden `{ size: 1000 }` y leen `.contenido` en vez de la respuesta directa.
- Verificado de punta a punta con datos reales: se armó un admin en la base local (mismas credenciales que el de producción, para no gestionar dos sets distintos — registrado normal y promovido a mano vía phpMyAdmin, con el re-login obligatorio después para que el JWT refleje el rol nuevo) y se confirmó visualmente tanto el Dashboard como el listado de Vuelos del panel admin mostrando los datos correctos, más los filtros nuevos funcionando en la vista pública.
- Build de producción (`npm run build`) verificado sin errores antes de entregar el código.

**Detalle menor de infraestructura, mismo tipo de troubleshooting ya documentado antes con Docker**: al querer entrar a phpMyAdmin local se probó primero `localhost/phpmyadmin`, que dio `ERR_CONNECTION_REFUSED` — la URL real dependía del puerto mapeado en `docker-compose.yml` para ese contenedor (`8081`, visible en la columna `PORTS` de `docker ps`, no el 80 por defecto), y el contenedor oficial de phpMyAdmin sirve todo desde la raíz, no bajo una ruta `/phpmyadmin`. Mismo método de siempre: `docker ps` para confirmar puerto real en vez de asumir uno.

---

## Nuevas features (2): Spring Boot Actuator (observabilidad)

Segunda de las seis features del roadmap — la más chica y rápida, elegida a propósito para tomar impulso antes de las más grandes.

**Concepto**: Actuator expone automáticamente información operacional de la app vía HTTP (¿está viva?, ¿qué versión es?, métricas de JVM/HTTP) — el tipo de endpoint que en un trabajo real consulta una herramienta de monitoreo o el propio panel del proveedor de hosting, no una persona a mano.

**Corrección importante sobre algo que te dije mal al arrancar este módulo**: afirmé que Actuator tenía una capa de seguridad separada e independiente de `SecurityConfig`. **Es falso** — se comprobó en el momento, con `/actuator/health` e `/actuator/info` devolviendo 403 apenas se agregó la dependencia, sin haber tocado `SecurityConfig` todavía. La razón real: como `spring-security` ya está en el classpath del proyecto, **absolutamente todos** los endpoints (los tuyos y los que trae cualquier librería, Actuator incluido) pasan por tu cadena de filtros — nada queda "afuera" de Spring Security por default. Como no había ninguna regla explícita para `/actuator/**`, cayó en la regla de cierre `anyRequest().authenticated()` ya existente. Mismo principio ya documentado desde el primer día en este archivo ("apenas agregás `spring-boot-starter-security`, todos los endpoints piden autenticación por defecto"), reforzado acá con un caso nuevo.

**Backend (escrito por vos, como siempre):**
- Dependencia `spring-boot-starter-actuator`. Por defecto, solo `/actuator/health` queda expuesto por HTTP — el resto están "activos" pero no accesibles hasta habilitarlos a propósito (medida de seguridad: agregar la dependencia sola no debería filtrar información sensible sin que lo decidas). Se habilitaron `health`, `info`, `metrics` vía `management.endpoints.web.exposure.include=health,info,metrics`.
- `management.endpoint.health.show-details=when-authorized` + `management.endpoint.health.roles=ADMIN`: un request sin autenticar a `/health` solo ve `{"status":"UP"}`; autenticado con rol `ADMIN` ve el detalle completo (`components`, con el estado real de cada pieza).
- **`DataSourceHealthIndicator` es automático**: sin escribir ninguna línea de código, Actuator detectó el `DataSource` configurado y probó la conexión real a MySQL, reportando `db: UP` con el motor detectado — buen ejemplo de la autoconfiguración de Spring Boot funcionando "gratis" cuando ya tenés las piezas correctas en el proyecto.
- **Reglas nuevas en `SecurityConfig`**, agregadas antes de la regla de cierre (mismo principio de siempre — específico antes que genérico, ya que si `hasRole("ADMIN")` sobre `/actuator/**` quedara antes que la regla de `/actuator/health`+`/actuator/info`, la más amplia ganaría primero):
```java
.requestMatchers("/actuator/health", "/actuator/info").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```
- **Bug real: `/actuator/info` devolvía `{}` vacío pese a tener `info.app.*` en `application.properties`.** El `EnvironmentInfoContributor` (el que lee justamente esas propiedades) viene **desactivado por defecto** desde hace varias versiones de Spring Boot — hay que habilitarlo a mano con `management.info.env.enabled=true`. Sin esa línea, las propiedades `info.*` quedan escritas pero nadie las lee para armar la respuesta.
- **Testing distinto al resto del proyecto, a propósito**: acá no sirve `@WebMvcTest` (su escaneo angosto no levanta `SecurityConfig` ni la autoconfiguración de Actuator sin trabajo extra, como ya está documentado más arriba) — corresponde `@SpringBootTest` completo + `@AutoConfigureMockMvc`, que levanta el contexto real (con la base real) para probar la seguridad de punta a punta. 4 tests nuevos (`health` público sin detalle, `metrics` sin auth → 403, `metrics`/`health` con `ADMIN` → 200 y detalle completo). Suite completa: 99/99.
- **Corrección menor de precisión, encontrada de pasada revisando un JWT real**: el `alg` del header del token es `HS384`, no `HS256` como quedó anotado en la sección de seguridad/JWT de este archivo — mismo algoritmo simétrico, versión más fuerte; se corrige acá para que quede exacto.

**Verificado en cuatro escenarios antes de cerrar el módulo**: local sin auth (`health`/`info` públicos, `metrics` → 403), local con token `ADMIN` (`metrics` → 200, `health` con detalle completo), producción con los mismos dos casos, y la suite automatizada.

**Falso alarma en producción, buena lección de troubleshooting reforzada**: el login en producción (`POST /api/auth/login`, ruta `permitAll()`) daba 403 — que en un login debería ser imposible salvo que algo esté mal configurado, ya que ni siquiera debería llegar a evaluar autorización. Se descartó paso a paso: los logs de Render confirmaban el deploy `Live` sin errores; el `SecurityConfig` pegado coincidía exactamente con lo esperado; los headers de la respuesta (los mismos que agrega `HeaderWriterFilter` de Spring Security en cada respuesta) confirmaban que la request sí llegaba a la app, no era un bloqueo de infraestructura de Render/Cloudflare. La causa real, mucho más simple: la request de Postman había quedado con un **Bearer Token viejo cargado en la pestaña Authorization** (heredado de haber probado `/actuator/metrics` justo antes en la misma sesión de Postman) — nada que ver con el backend. Mismo método ya aplicado una vez con el bug de Aiven: **aislar la herramienta de prueba** (en ese caso, un cliente de mysql aparte; acá, probar el mismo request directo desde Swagger UI) para confirmar que el problema estaba del lado del cliente de prueba, no del servidor.

**Producción — Health Check Path de Render**: se configuró `/actuator/health` como Health Check Path en el dashboard del servicio (Settings), reemplazando la ausencia de configuración explícita. Ahora Render usa ese endpoint (liviano, sin autenticación) para decidir si el contenedor está realmente sano, no solo si "arrancó" — el mismo propósito con el que se diseñó ese endpoint para quedar público.

---

## Nuevas features (3): CI/CD con GitHub Actions

Tercera feature del roadmap, hecha con poco tiempo disponible (media hora), así que el módulo se resolvió en pasos chicos y muy incrementales — cada error se corrigió antes de seguir, en vez de acumular cambios sin probar.

**Concepto**: un workflow de GitHub Actions corre automáticamente en cada push/PR a `main` — primero corre los tests (**CI**), y si pasan, dispara el deploy a Render (**CD**), en vez de que Render despliegue sin importar si algo está roto.

**Archivo**: `.github/workflows/ci.yml` (dos jobs, `test` y `deploy`, el segundo con `needs: test`). El job `test` levanta un MySQL descartable como *service container* — necesario porque, a diferencia de los tests de servicio/controller (mockeados, sin base), la suite tiene algunos `@SpringBootTest` completos (el de contexto, `ActuatorSecurityTest`) que sí necesitan una base real para levantar el `ApplicationContext`.

**Cuatro bugs reales en el camino, todos de infraestructura/configuración, no de código Java — buen recordatorio de que CI/CD tiene su propia categoría de errores, distinta a la de programar:**
- **`.git/workflows/` en vez de `.github/workflows/`.** Nombres casi idénticos a simple vista, pero `.git` es la base de datos interna de Git (nada de lo que se ponga ahí se trata como parte normal del repo); `.github` es una carpeta convencional donde GitHub busca configuración del repositorio, entre otras cosas los workflows. GitHub Actions no encuentra nada si el archivo queda en el lugar equivocado.
- **Push rechazado: `refusing to allow a Personal Access Token to create or update workflow ... without workflow scope`.** GitHub trata los archivos de `.github/workflows/` como sensibles (pueden ejecutar código arbitrario en su infraestructura) y exige que el token usado para autenticar el push tenga el scope `workflow` habilitado explícitamente, además del `repo` normal — hubo que regenerar el token con ese scope y volver a autenticar (Windows: borrar la credencial vieja en el Administrador de credenciales para forzar el re-login).
- **`./mvnw: Permission denied` (exit code 126) en el runner de GitHub Actions.** El Maven Wrapper se commiteó originalmente desde Windows, que no maneja el bit de ejecución de Unix de la misma forma — así que el archivo llegó al repo sin ese permiso marcado. En Windows nunca se nota (`mvnw.cmd`/asociación de archivos lo tapa), pero el runner de Actions es Ubuntu y sí lo exige. Solución: un paso `run: chmod +x mvnw` antes de correr los tests en el workflow (no hace falta arreglar el bit en el repo en sí, alcanza con corregirlo en cada corrida del CI).
- **(Agregado al sumar Stripe) 5 tests fallando en CI, invisibles en local: `STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET` no existían como GitHub Actions secret.** El runner de Actions es una máquina completamente aparte de la PC local — no hereda ni las variables de entorno de usuario de Windows ni nada de Docker. Mismo síntoma de siempre (`PlaceholderResolutionException`) pero en un tercer entorno distinto a los dos ya conocidos (IntelliJ/Maven fork local, Docker Compose). Solución: agregar ambos valores como **GitHub repo Secrets** (`Settings → Secrets and variables → Actions`) y referenciarlos en el `env:` del step de tests del workflow con `${{ secrets.STRIPE_SECRET_KEY }}` / `${{ secrets.STRIPE_WEBHOOK_SECRET }}` — mismo mecanismo ya usado para `RENDER_DEPLOY_HOOK_URL` más arriba. **Lección más general, confirmada tres veces con Stripe**: una misma variable de entorno "ya configurada" no viaja sola entre entornos — hay que setearla por separado en cada uno de los lugares donde corre código: Windows (IntelliJ/Maven), Docker (`docker-compose.yml` → `environment:`), GitHub Actions (repo Secrets), y Render (Environment del servicio) son cuatro mecanismos independientes, cada uno con su propia forma de declarar variables.

**Diseño de la parte de CD**: se decidió no depender del auto-deploy nativo de Render (que ignora si los tests pasan o no) — en cambio, se desactivó ("Auto-Deploy: Off") y se agregó un job `deploy` en el mismo workflow que dispara un **Deploy Hook** de Render (una URL única de disparo) vía `curl -X POST`, condicionado con `needs: test` (no corre si `test` falla) y `if: github.ref == 'refs/heads/main' && github.event_name == 'push'` (para que un PR no dispare un deploy). La URL del hook se guardó como **GitHub Actions secret** (`RENDER_DEPLOY_HOOK_URL`), nunca pegada directo en el yaml — cualquiera con esa URL puede disparar un deploy del servicio, así que es un dato sensible como cualquier credencial.

**Verificación, parcial por el tiempo disponible**: los dos jobs (`test`, `deploy`) corrieron en verde en GitHub Actions, y los logs de la aplicación en Render confirmaron "servicio en vivo" — quedó pendiente solo confirmar que el badge de estado del deploy en el dashboard de Render se puso en "Live" (parecía ir con un pequeño delay respecto al log real de la app, normal, no una falla). A confirmar/cerrar del todo la próxima vez que se retome este tema.

---

## Nuevas features (4): Caché con Redis en `GET /api/vuelos/{id}`

Cuarta feature del roadmap. Elegida por sobre pagos/notificaciones para este momento del proyecto porque tocaba un concepto nuevo (caching) sin depender de servicios externos de terceros (a diferencia de Stripe/MercadoPago o un proveedor de email).

**Concepto**: cachear la respuesta de un endpoint de lectura frecuente para no pegarle a la base de datos en cada request — Redis guarda el resultado ya calculado en memoria, con una expiración (TTL), y Spring se encarga de leer/escribir ese caché de forma transparente vía anotaciones.

**Backend (escrito por vos, como siempre):**
- Dependencias nuevas: `spring-boot-starter-data-redis` (cliente Redis para Spring) + `spring-boot-starter-cache` (abstracción de caché de Spring, independiente del proveedor).
- `docker-compose.yml`: nuevo servicio `redis` (imagen `redis:7-alpine`, puerto `6379`), sin volumen — a diferencia de MySQL, perder el caché al reiniciar el contenedor no es un problema real (es información derivable, no la fuente de verdad).
- `application.properties`: `spring.data.redis.host=localhost` / `spring.data.redis.port=6379`.
- **`@EnableCaching` + `CacheConfig`** (clase `@Configuration` nueva): define un bean `RedisCacheConfiguration` con TTL de 10 minutos (`entryTtl(Duration.ofMinutes(10))`), serialización JSON en vez del serializador Java por defecto (más liviano y legible directamente en `redis-cli`), y `disableCachingNullValues()` — a propósito, para que una búsqueda por un id inexistente **nunca** quede cacheada como "no existe" para siempre; cada intento contra un id que no existe sigue golpeando la base real.
- **Decisión de diseño clave: cachear el método que devuelve DTO (`buscarPorIdCacheado`), nunca el que devuelve la entidad (`buscarPorId`).** `buscarPorId` (que devuelve `Optional<Vuelo>`) lo siguen usando internamente `editarVuelo`/`eliminarVuelo` para mutar el vuelo — cachear ahí arriesgaría trabajar sobre una entidad vieja/detached de una edición anterior. El método nuevo, `buscarPorIdCacheado`, llama a `buscarPorId` y mapea a `VueloResponseDto` (inmutable, ya desacoplado de Hibernate) — ese es el que se cachea:
```java
@Cacheable(cacheNames = "vuelo", key = "#id")
public VueloResponseDto buscarPorIdCacheado(Long id) {
    return buscarPorId(id)
            .map(VueloMapper::toResponseDto)
            .orElse(null);
}
```
- **`@CacheEvict(cacheNames = "vuelo", key = "#id")`** agregado sobre `editar` y `eliminar` en el controller — invalida la entrada cacheada de ese vuelo puntual apenas se edita o borra, para que la próxima lectura traiga datos frescos en vez de servir la versión vieja.
- `VueloController.buscarPorId` pasó de llamar al service (`Optional<Vuelo>`) a llamar directo a `buscarPorIdCacheado` (`VueloResponseDto` o `null`), sin pasar por `Optional` en el controller — coherente con que el mapeo ya lo hace el service ahora.

**Concepto nuevo, importante para el futuro: el "self-invocation problem" de Spring AOP.** `@Cacheable`/`@Transactional`/etc. funcionan mediante un *proxy* que Spring genera alrededor del bean real — cuando un método llama a otro método **de la misma clase** vía `this.algo()` (implícito, sin que se note en el código), esa llamada no pasa por el proxy, así que la anotación se ignora en silencio, sin ningún error. Por eso `buscarPorIdCacheado` se puso en `VueloService` (invocado desde `VueloController`, un bean distinto — llamada real cruzada a través del proxy) y no como un método privado dentro del propio controller. Mismo tipo de "falla silenciosa por mecanismo de Spring", ya documentado antes en este archivo con las anotaciones de mapeo HTTP faltantes — la diferencia es que acá la causa no es una anotación faltante, sino *dónde* vive el método.

**Bug real de knowledge-cutoff (mismo patrón ya visto con Spring Boot 4/Jackson 3): `GenericJackson2JsonRedisSerializer` deprecado/removido en esta versión del proyecto.** La sugerencia inicial (basada en versiones anteriores de Spring Data Redis) no compilaba — IntelliJ marcaba la clase como deprecada/inexistente. El reemplazo real, confirmado vía autocompletado del IDE: `GenericJacksonJsonRedisSerializer`, que ahora pide explícitamente un `ObjectMapper` inyectado por constructor (ya no arma uno con `new` por dentro solo). Y ese `ObjectMapper` tiene que ser el de **Jackson 3** (`tools.jackson.databind.ObjectMapper`, no el viejo `com.fasterxml.jackson.databind.ObjectMapper`), consistente con el resto de la migración de Jackson 3 ya documentada en la sección de Spring Boot 4 de este archivo. Config final:
```java
@Configuration
@EnableCaching
public class CacheConfig {
    private final ObjectMapper objectMapper;

    public CacheConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJacksonJsonRedisSerializer(objectMapper)))
                .disableCachingNullValues();
    }
}
```

**Bug real de test, mismo patrón que "el mock no está stubbeado devuelve `null`/default":** al cambiar `buscarPorId` del controller para llamar a `buscarPorIdCacheado` en vez de `buscarPorId` del service, los dos tests existentes de ese endpoint seguían mockeando el método viejo (`vueloService.buscarPorId(...)`) — como el controller ya no lo llama, el mock de `buscarPorIdCacheado` quedaba sin stubbear y Mockito le devolvía `null` por default, dando 404 en vez de 200. Se corrigieron los dos tests para mockear `buscarPorIdCacheado` directo, devolviendo un `VueloResponseDto`/`null` en vez de un `Optional<Vuelo>`. Confirmado: 99/99.

**Verificación manual con evidencia real, en tres pasos:**
1. `GET /api/vuelos/2` en Swagger → 200 con el DTO completo.
2. `docker exec -it aerolinea-redis redis-cli` → `KEYS *` mostró `vuelo::2`, y `GET "vuelo::2"` devolvió el JSON completo ya serializado (con Jackson 3 vía la config de arriba) — confirmando que la escritura en caché funcionó y con el formato esperado. (Nota al margen: `redis-cli` mostró "Cancún" con los bytes UTF-8 escapados tipo `\xc3\xba` en vez de la tilde legible — comportamiento normal de cómo `redis-cli` imprime bytes crudos, no indica ningún problema de codificación real.)
3. **`PUT /api/vuelos/2`** (cambiando `precio` a `700.00`) para probar `@CacheEvict` — primer intento dio **403 Forbidden**, con el `curl` reproducido mostrando que faltaba el header `Authorization` por completo. Causa: sesión de Swagger sin loguearse como ADMIN (el botón "Authorize" con un token fresco de `/api/auth/login`), no un bug del código nuevo — coherente con que `SecurityConfig` exige `hasRole("ADMIN")` para `PUT /api/vuelos/**`. Una vez autenticado correctamente, el `PUT` funcionó, y:
   - `KEYS *` inmediatamente después mostró `(empty array)` — confirmando que `@CacheEvict` borró la entrada apenas se editó el vuelo.
   - Un `GET /api/vuelos/2` posterior repobló el caché, y `GET "vuelo::2"` en `redis-cli` mostró el JSON **ya con `precio: 700.00`** — confirmando que lo que se recachea es el dato fresco, no el viejo.

Con esto, el ciclo completo (`@Cacheable` puebla → `@CacheEvict` invalida → siguiente lectura repuebla con datos frescos) quedó verificado con evidencia real de Redis, no solo asumido por leer el código.

### Cierre real del módulo: resiliencia ante Redis caído (el bug que encontró el propio CI)

Después de dar el módulo por cerrado y pushear, **el propio pipeline de CI/CD encontró un gap real que se nos había pasado** — la mejor prueba posible de por qué vale la pena tener esa red de seguridad, más allá de repetir manualmente los mismos casos una y otra vez.

**El bug de fondo: `@Cacheable` no atrapa errores de conexión por defecto.** El comportamiento estándar de Spring (`SimpleCacheErrorHandler`) relanza cualquier excepción que ocurra al leer/escribir/invalidar el caché — así que un Redis inalcanzable no se traduce en "seguir sin caché", sino en romper el endpoint entero con un 500. Esto se descubrió porque el job `test` del CI falló: `.github/workflows/ci.yml` nunca tuvo un *service container* de Redis (solo MySQL), así que al correr ahí, `ActuatorSecurityTest` encontró un `/actuator/health` devolviendo `503` en vez de `200` — porque Actuator agrega el estado de **todos** los indicadores de salud (incluido el de Redis, autodetectado apenas se agregó la dependencia), y si uno solo está `DOWN`, el conjunto entero queda `DOWN`.

**Dato importante para tranquilidad, reforzando una lección ya documentada sobre CD**: como el job `deploy` tiene `needs: test`, el código roto **nunca llegó a producción** — el pipeline hizo exactamente lo que se diseñó para hacer.

**Dos correcciones, no una — cada una resuelve un problema distinto:**
1. **`RedisCacheErrorHandler`** (`config/RedisCacheErrorHandler.java`), implementando `org.springframework.cache.interceptor.CacheErrorHandler` con sus cuatro métodos (`handleCacheGetError`/`PutError`/`EvictError`/`ClearError`), todos limitándose a loguear un `log.warn(...)` en vez de relanzar la excepción. Se conecta reemplazando el manejador default a través de `CacheConfig implements CachingConfigurer` y su método `errorHandler()`. Con esto, si Redis está caído, el método cacheado simplemente sigue de largo hasta la base — el caché deja de ser un punto único de falla para un endpoint que funcionaba bien antes de agregarlo.
2. **`management.health.redis.enabled=false`**: decisión de diseño explícita, documentada con un comentario en el propio `application.properties`, de que Redis (un caché de lectura opcional, no una dependencia crítica) no debe poder tirar abajo el estado agregado de `/actuator/health` — sobre todo porque ese mismo endpoint es el Health Check Path configurado en Render; sin esta exclusión, un blip de Redis podría hacer que Render considere el servicio entero "no saludable" aunque los usuarios reales no notaran nada raro.

**Bug real de infraestructura, no de código, encontrado al aplicar el fix**: el build de Maven falló en la fase de `resources` (`MalformedInputException: Input length = 1` al filtrar `application.properties`), la primera vez que se agregó un comentario con tildes (`crítica`, `está`, `acá`) a ese archivo — hasta ese momento, todo el contenido de `application.properties` había sido siempre ASCII puro, sin acentos. La causa: el filtrado de recursos de Maven decodifica el archivo como texto usando la codificación declarada en `pom.xml` (normalmente UTF-8), y el archivo en disco quedó guardado con una codificación distinta (probablemente Windows-1252, común en editores de Windows si no se fuerza UTF-8) — los bytes de los caracteres acentuados no forman una secuencia válida bajo la codificación esperada. Solución rápida aplicada: reescribir el comentario sin tildes, mismo estilo ya usado en el resto del archivo. Quedó pendiente, sin resolver a fondo (no bloqueante): confirmar/forzar la codificación UTF-8 del proyecto en IntelliJ para poder usar acentos en archivos de configuración sin este problema en el futuro.

**Verificación final, con Redis apagado a propósito (`docker stop aerolinea-redis`) y evidencia correlacionada por horario**: `GET /api/vuelos/2` devolvió `200` con los datos correctos, y la consola mostró en el mismo instante `WARN ... RedisCacheErrorHandler : Error al leer del caché 'vuelo' (key=2): Unable to connect to Redis. Se continúa sin caché.` seguido de los `SELECT` de Hibernate yendo directo a la base — confirmando el fallback funcionando de punta a punta. (Un primer intento de reproducir esto había dado un 500 real, pero resultó ser contra una instancia del backend que todavía no tenía el fix compilado, por el bug de codificación de arriba — no una falla del diseño.) Suite completa y CI en verde después del push (commit `fix: manejar fallos de Redis sin romper el endpoint (CacheErrorHandler) y excluirlo del estado de /actuator/health`).

### Redis real en producción (Render Key Value)

Último paso para que el caché no solo "no rompa nada" sin Redis, sino que funcione de verdad en el entorno público — mismo criterio que Aiven para MySQL: un servicio gratuito administrado por proveedor, no una simulación.

- **Render Key Value** (plan free, sin persistencia — aceptable porque es solo un caché desechable), creado en la **misma región (Ohio)** que el backend, para poder usar la **URL interna** (`redis://red-...:6379`, sin TLS ni contraseña, solo alcanzable entre servicios Render del mismo workspace/región) en vez de la externa — menor latencia y no queda expuesto a internet.
- Render inyecta esa URL automáticamente como variable de entorno `REDIS_URL` en el servicio backend una vez vinculado.
- **Backend**: se reemplazaron `spring.data.redis.host`/`port` por una sola propiedad, mismo patrón ya usado con `server.port=${PORT:8080}`:
```properties
spring.data.redis.url=${REDIS_URL:redis://localhost:6379}
```
En Render usa la `REDIS_URL` real; localmente (donde esa variable no existe) cae en el valor por defecto, sin afectar el flujo de desarrollo con Docker Compose.

**Bug real, no de código: falla transitoria de infraestructura en el CI.** Un push a este cambio hizo fallar el job `test` de GitHub Actions con `wget: Failed to fetch https://repo.maven.apache.org/.../apache-maven-3.9.16-bin.zip` — el propio Maven Wrapper no pudo descargar Maven desde el repositorio central de Apache. No tenía nada que ver con el código: un simple **re-run** del job (sin tocar nada) pasó en verde al segundo intento. Lección: no todo fallo de CI es un bug real — antes de investigar a fondo, vale la pena descartar primero una falla transitoria de red con un reintento simple.

**Verificación con evidencia real de la instancia de producción, no solo "el endpoint no explotó"**: `GET /api/vuelos/3` contra la URL pública (`aeropass-backend.onrender.com`) devolvió `200` correcto — pero como el `CacheErrorHandler` nuevo hace que un Redis roto *también* devuelva 200 (con fallback a la base), un 200 solo no prueba que el caché esté funcionando de verdad. Para confirmarlo con evidencia real, mismo método que con Aiven (cliente aislado en un contenedor descartable de Docker, en vez de instalar herramientas nuevas de forma permanente):
```
docker run --rm -it redis:7-alpine redis-cli -u "rediss://usuario:contraseña@ohio-keyvalue.render.com:6379"
```
Requirió habilitar tráfico externo en la sección **Networking** de la instancia (por default, `External traffic not allowed`), agregando la IP pública propia a la lista blanca.

**Bug real de confusión, vale la pena anotarlo porque es un error común**: al buscar "mi IP" para la lista blanca, el primer intento fue correr `ipconfig` en Windows y copiar esa dirección — pero `ipconfig` muestra la IP **local** de la red doméstica (rango privado, ej. `192.168.x.x`), no la IP **pública** con la que la conexión realmente sale a internet. Render evalúa la IP pública real de origen, así que la del `ipconfig` nunca iba a coincidir (`AUTH failed: Client IP address is not in the allowlist`). Se resolvió buscando "cual es mi ip" en Google, que muestra la pública real. Una vez cargada la IP correcta, `redis-cli` conectó sin problema y `KEYS *` confirmó `vuelo::3` cacheado en la instancia real de Render — cierre definitivo del módulo, con Redis funcionando de punta a punta en los tres entornos (local, CI, producción).

---

## Nuevas features (5): Notificaciones por email (confirmación y cancelación de reserva)

Quinta feature del roadmap. Alcance acordado de antemano: dos disparadores (confirmación al crear una reserva, aviso al cancelarla), probando con Mailtrap (sandbox de email, gratuito) en vez de mandar correos reales durante el desarrollo.

**Mailtrap**: cuenta gratuita, sección **Email Testing → Sandboxes** (no la de "Email Sending", que pide verificar un dominio propio — se salteó ese paso con "Skip Step" porque no hace falta para testing). La pestaña **Integration → SMTP** del sandbox da directamente `Host`/`Port`/`Username`/`Password` listos para pegar, mismo criterio que con las credenciales de Aiven: sacarlos de la fuente real en vez de asumir un valor fijo.

**Backend (escrito por vos, como siempre):**
- Dependencia `spring-boot-starter-mail`. Con las propiedades de Mailtrap en `application.properties` (`spring.mail.host`/`port`/`username`/`password` + `spring.mail.properties.mail.smtp.auth=true` y `starttls.enable=true`), Spring Boot autoconfigura un bean `JavaMailSender` listo para inyectar — mismo patrón de autoconfiguración por propiedades ya visto con Redis y la base de datos.
- **`AsyncConfig`** (`@Configuration @EnableAsync`, implementando `AsyncConfigurer`): habilita el uso de `@Async` en el proyecto, con un `ThreadPoolTaskExecutor` propio (`corePoolSize=2`, `maxPoolSize=5`) en vez del executor default de Spring (que crea un hilo nuevo sin límite por cada llamada, poco prolijo para producción).
- **`EmailService`** (nuevo, en `service`), con `enviarConfirmacionReserva(...)` y `enviarCancelacionReserva(...)` marcados `@Async` — el request HTTP que crea/cancela una reserva no espera a que termine la conexión SMTP con Mailtrap, responde de una.
- **Diseño clave: los métodos de `EmailService` reciben datos sueltos (`String`, `LocalDateTime`, `BigDecimal`), nunca la entidad `Reserva` completa.** Motivo real, no solo estético: `@Async` corre en un hilo distinto al que atendió el request original, y las relaciones `@ManyToOne(LAZY)` (`Reserva.usuario`) dependen de que la sesión de Hibernate del hilo original siga activa para poder resolverse. Si se le pasara la entidad completa y el método async intentara acceder a una relación lazy que todavía no se había tocado en el hilo original, explotaría con `LazyInitializationException` — en un hilo async, encima, así que ese error quedaría silenciosamente logueado y nunca visible como un 500 al usuario. Solución: extraer todos los datos necesarios (`usuario.getNombre()`, `usuario.getEmail()`, etc.) en el hilo síncrono original, **antes** de disparar el método async, y pasarlos como parámetros simples — mismo principio ya aplicado en todo el proyecto con los DTOs (no filtrar el modelo interno más allá de donde hace falta), aplicado acá a un problema de concurrencia real, no solo de diseño.
- **Resiliencia, mismo criterio que el `CacheErrorHandler` de Redis**: el envío real (`enviar(...)`, método privado) está envuelto en un `try/catch` que solo loguea un `warn` si falla, sin relanzar la excepción — un email que no se pudo mandar (Mailtrap caído, credenciales mal puestas) nunca debería impedir que la reserva se cree o cancele correctamente. (Dato aparte: como el método público ya es `@Async void`, una excepción sin atrapar ahí tampoco se propagaría al llamador — Spring la trapea sola con su manejador default — pero el `try/catch` propio da control sobre el mensaje de log.)
- `DateTimeFormatter` (`dd/MM/yyyy HH:mm`) para mostrar la fecha de salida de forma legible en el cuerpo del email, en vez del `LocalDateTime.toString()` crudo (`2026-08-13T19:03:15.643`) que sale por default.
- **`ReservaService`** ahora inyecta `EmailService` por constructor, y llama a `enviarConfirmacionReserva(...)`/`enviarCancelacionReserva(...)` justo después de guardar la reserva en `crearReserva`/`cancelarReserva` respectivamente — usando las variables `usuario`/`vuelo` ya cargadas en memoria en `crearReserva` (sin riesgo de lazy loading, porque se buscaron explícitamente por repository, no por navegación de relación), y accediendo a `reserva.getUsuario()` de forma síncrona (antes del `@Async`) en `cancelarReserva`.
- Sin tildes en los textos de los emails ni en el código nuevo, mismo criterio preventivo que ya aprendimos con el bug de codificación de `application.properties`.

**Bug real del mismo bug-family de siempre: `Logger` importado del paquete equivocado.** Al escribir `LoggerFactory.getLogger(...)`, IntelliJ auto-importó el campo `Logger` como `java.util.logging.Logger` (el logger nativo de Java, sin método `warn`) en vez de `org.slf4j.Logger` (el que sí tiene `warn`/`info`/`error`, usado en el resto del proyecto) — mismo síntoma que ya vimos con `Pageable`/`ObjectMapper`: dos clases con el mismo nombre simple, el IDE ofrece la equivocada primero. Se corrige apuntando el `import` a `org.slf4j.Logger`.

**Bugs reales de test, al agregar la dependencia nueva a `ReservaService` — mismo patrón que "el mock no está preparado para la dependencia nueva" ya visto con Redis:**
- Faltaba `@Mock private EmailService emailService;` en `ReservaServiceTest` — sin él, `@InjectMocks` deja ese campo en `null` dentro del service bajo test, y los dos tests del camino feliz (`crearReserva`/`cancelarReserva` con datos válidos) tiraban `NullPointerException` al intentar llamar a un método sobre `null`.
- El fixture de `cancelarReserva_conDatosValidos_deberiaCancelarCorrectamente` nunca le había seteado un `usuario` a la `Reserva` de prueba (no hacía falta antes de esta feature) — como el código nuevo necesita `reserva.getUsuario()` para armar el email, explotaba con `NullPointerException` ahí, incluso antes de llegar a `emailService`. Se agregó `.usuario(usuarioValido())` al builder de ese test. Se sumaron además `verify(emailService, ...)` en los dos tests del camino feliz, confirmando que el email se dispara con los datos correctos — mismo criterio que ya se usaba con `verify(vueloRepository).save(vuelo)`.

**Verificación manual con evidencia real en Mailtrap**: se creó una reserva vía Swagger → llegó el email de confirmación a la bandeja del sandbox, con nombre, vuelo, fecha (ya formateada) y precio correctos. Se canceló esa misma reserva → llegó el segundo email, de cancelación, mismos datos correctos y mismo formato de fecha prolijo.

---

## Pagos sandbox con Stripe — completa: backend, frontend y producción ✅

Última feature grande del roadmap original, y la más larga de implementar de punta a punta. Diseño acordado en una sesión de planificación previa (documentado más abajo, sin tocar); backend implementado y testeado en una sesión posterior (Checkout Session + webhook + 108/108 tests, verificado en local); y en una tercera sesión se sumó el frontend (botón "Pagar" + páginas de éxito/cancelado) y se cerró el deploy completo a producción, incluyendo varios bugs reales que solo aparecen al pasar de local a producción (ver más abajo).

**Decisiones tomadas, las tres con la opción recomendada:**
- **Proveedor: Stripe** (por sobre MercadoPago) — mejor documentación, muy pedido en búsquedas laborales, y tiene Stripe CLI para testing local de webhooks sin necesitar un túnel público (ver más abajo).
- **Profundidad del flujo: realista, con webhook** (por sobre un endpoint que simule el pago directo) — se crea una Checkout Session real en Stripe, el usuario paga en el checkout hospedado, y Stripe confirma por webhook. Más trabajo, pero mucho más representativo de un sistema de pagos de producción real.
- **Integración con `Reserva`: el pago es requisito para confirmarse** (por sobre un pago desacoplado sobre una reserva ya confirmada) — se agrega un estado nuevo, la reserva queda "reteniendo" el asiento pero sin confirmar hasta que el pago se apruebe.

**Modelo de datos nuevo:**
- `EstadoReserva` suma `PENDIENTE_PAGO` (además de `CONFIRMADA`/`CANCELADA`). `crearReserva` deja la reserva en `PENDIENTE_PAGO` (asiento ya descontado, igual que ahora) en vez de `CONFIRMADA` directo.
- Entidad nueva `Pago`: `id`, relación con `Reserva`, `stripeSessionId` (id externo de Stripe), `estado` (`PENDIENTE`/`APROBADO`/`RECHAZADO`), `monto`, timestamps.

**Flujo elegido dentro de Stripe: Checkout Session (hospedada por Stripe), no Payment Intent + Elements (formulario de tarjeta propio).** El backend crea la sesión y devuelve una URL; el frontend solo redirige el navegador ahí. Cuando el pago se confirma pasan dos cosas en paralelo: Stripe redirige al usuario a una `success_url` (solo la experiencia visual) y le manda un webhook al backend server-to-server (la confirmación real). **La reserva se confirma solo con el webhook, nunca con el redirect** — el redirect lo puede manipular cualquiera cambiando la URL a mano; el webhook viene firmado y verificado criptográficamente. Principio de seguridad clave: nunca confiar en el cliente para confirmar algo con dinero de por medio.

**Endpoints nuevos planificados:**
- `POST /api/reservas/{id}/pago`: crea la Checkout Session en Stripe para esa reserva, devuelve la URL de pago.
- `POST /api/webhooks/stripe`: recibe los eventos de Stripe, verifica la firma (`Stripe-Signature` + webhook secret), y en `checkout.session.completed` marca el `Pago` como `APROBADO` y la `Reserva` como `CONFIRMADA`.

**Seguridad del webhook**: ruta `permitAll()` en `SecurityConfig` (Stripe no manda JWT), protegida en cambio por la verificación de firma — un paradigma de seguridad distinto al resto de la API (no es "quién sos", es "esto realmente lo mandó Stripe y no fue alterado").

**Manejo de credenciales, con más cuidado que de costumbre**: a diferencia de la contraseña local de MySQL (commiteada sin problema por ser un valor descartable de desarrollo), la secret key de Stripe (aunque sea de test) **no debería quedar commiteada** en `application.properties` — se configura como variable de entorno, leída vía `${STRIPE_SECRET_KEY}` sin valor por defecto en el archivo. **Corrección importante encontrada al implementar**: no alcanza con setearla solo en la Run Configuration de IntelliJ — Maven Surefire corre los tests en un proceso hijo ("fork") que NO hereda las variables de entorno del Run Configuration, así que los tests con `@SpringBootTest` fallaban con `PlaceholderResolutionException`. La forma robusta es una **variable de entorno de usuario de Windows** (Panel de control → Variables de entorno → Variables de usuario), que sí la heredan por igual IntelliJ, Maven CLI y los tests — requiere cerrar y volver a abrir IntelliJ por completo para que la tome. Mismo tratamiento para `STRIPE_WEBHOOK_SECRET` más adelante.

**Testing local de webhooks sin exponer el backend a internet**: **Stripe CLI**, corriendo `stripe listen --forward-to localhost:8080/api/webhooks/stripe` — reenvía los eventos de test directo a la máquina local, sin necesitar un túnel público tipo ngrok (a diferencia de MercadoPago, que sí lo requeriría).

**Simplificación aceptada de antemano, documentada como pendiente**: si alguien crea una reserva y abandona el checkout sin pagar, el asiento queda retenido en `PENDIENTE_PAGO` indefinidamente. La solución "correcta" (expirar reservas pendientes de pago después de un tiempo, liberando el asiento) queda anotada como mejora futura, no bloqueante para el MVP.

**Mejora futura relacionada, sumada durante la planificación**: cuando se implemente la expiración de arriba, tiene sentido que el email de `PENDIENTE_PAGO` (reusando el `EmailService` ya construido en la feature de notificaciones) avise al usuario el tiempo restante para pagar antes de perder la reserva — así el usuario no se entera de la expiración recién cuando ya pasó. Depende directamente de que primero exista un tiempo límite definido (el punto anterior), así que no tiene sentido implementarlo antes que ese.

**Pasos (orden real de implementación):**
1. ~~Cuenta de Stripe en modo test, conseguir la secret key y (más adelante) el webhook signing secret.~~ ✅ País elegido: Estados Unidos (Argentina no está soportado por Stripe; se eligió EE.UU. solo para que la documentación oficial en inglés coincida con lo que se ve en el dashboard, sin relación con dónde está hosteado el proyecto en producción). Del asistente de onboarding, solo se tildó "Aceptar pagos por Internet" (Checkout Sessions vía API) — se descartaron "Crear una plataforma" (Stripe Connect) y "Enviar facturas" (Invoicing), no relevantes para este flujo.
2. ~~Dependencia `com.stripe:stripe-java` + configuración de la API key vía variable de entorno.~~ ✅ Versión correcta **33.3.0** (una versión inicial sugerida, 29.6.0, no existía en Maven Central y tiró error de dependencia sin resolver — corregido buscando la versión estable real).
3. ~~Nueva entidad `Pago` + migración del estado `PENDIENTE_PAGO` en `EstadoReserva` + ajuste de `crearReserva` para dejar la reserva en ese estado.~~ ✅
4. ~~Endpoint `POST /api/reservas/{id}/pago` (crear la Checkout Session).~~ ✅
5. ~~Endpoint `POST /api/webhooks/stripe` (recibir y verificar el evento, confirmar la reserva).~~ ✅
6. ~~Instalar Stripe CLI, correr `stripe listen`, probar el flujo completo en local con una tarjeta de test.~~ ✅ Instalado vía Scoop (`scoop bucket add stripe ...` + `scoop install stripe`). Confirmado con tarjeta de test `4242 4242 4242 4242`: reserva `PENDIENTE_PAGO` → pago en Stripe Checkout → webhook `checkout.session.completed` recibido → reserva `CONFIRMADA` y `Pago` en `APROBADO`.
7. ~~Frontend: botón "Pagar" que pega a `/pago` y redirige, más páginas de éxito/cancelado post-pago.~~ ✅ `crearSesionDePago` (llama al endpoint y hace `window.location.href = url` — redirección completa del navegador, no de React Router, porque el checkout vive en el dominio de Stripe), botón "Pagar" condicional en "Mis reservas" (solo si `estado === 'PENDIENTE_PAGO'`), páginas `/pago/exito` y `/pago/cancelado`. Construido en un sandbox aparte, verificado con `npm run build` + capturas automatizadas (Playwright, claro/oscuro, datos mockeados) antes de entregarse como `.zip` para aplicar y probar en el proyecto real — mismo criterio de "nunca tocar el proyecto real directamente" que se usa con el backend, extendido al frontend por decisión explícita (a diferencia de code sesiones anteriores, donde sí se commiteaba directo).
8. ~~Tests (unit de `PagoService` mockeando Stripe, controller tests del webhook).~~ ✅ 108/108 tests pasando.
9. ~~Deploy: nueva variable de entorno en Render, webhook configurado apuntando a la URL de producción.~~ ✅ Endpoint de webhook nuevo creado en el dashboard de Stripe (ámbito "Tu cuenta", no "Cuentas conectadas" — esa segunda opción es para plataformas tipo marketplace con Stripe Connect, no aplica acá) apuntando a `https://aeropass-backend.onrender.com/api/webhooks/stripe`, con su propio signing secret **distinto** al de `stripe listen` local. Ese secret + `STRIPE_SECRET_KEY` se cargaron como variables de entorno en Render (no como "Secret File" — ese mecanismo monta un archivo en el filesystem del contenedor, mientras que el código lee `${VARIABLE}` como variable de entorno del proceso, que es un mecanismo distinto). Dos bugs reales encontrados y corregidos en este paso, detallados más abajo (columna `estado` como `ENUM` nativo en la base de Aiven, y rutas de React Router devolviendo 404 en Vercel).

**Implementación real — clases nuevas:** `StripeConfig` (`@PostConstruct` seteando `Stripe.apiKey` desde `${stripe.secret.key}`, patrón estático legacy de `stripe-java`, no el `StripeClient` inyectable más nuevo — se deja como mejora futura, sin tocar código que ya funciona), `Pago` (entidad, `ManyToOne` a `Reserva` a propósito, para permitir reintentos de pago sobre la misma reserva), `EstadoPago` (`PENDIENTE`/`APROBADO`/`RECHAZADO`), `PagoRepository`, `PagoService` (`crearSesionDePago` + `confirmarPago`), `WebhookController`. `ReservaService.crearReserva` se ajustó para dejar la reserva en `PENDIENTE_PAGO` y se sacó de ahí el envío del email de confirmación (se movió a `PagoService.confirmarPago`, que es donde corresponde ahora que el pago gatea la confirmación).

**Bugs reales encontrados al implementar:**
- **Hibernate/MySQL — columna ENUM nativa no se amplía sola.** Al agregar `PENDIENTE_PAGO` al enum de Java, `POST /api/reservas` empezó a tirar `Data truncated for column 'estado'`: Hibernate 6+/7 mapea `@Enumerated(EnumType.STRING)` a un `ENUM(...)` nativo de MySQL con la lista de valores fija al momento de crear la tabla, y `ddl-auto=update` no la actualiza cuando el enum de Java gana constantes nuevas. Solución en desarrollo: dropear y dejar que Hibernate recree las tablas afectadas (respetando el orden por FK: `pagos` antes que `reservas`).
- **`@MockBean` está deprecado/removido en este stack — es `@MockitoBean`.** Al agregar `PagoService` como dependencia nueva de `ReservaController`, `ReservaControllerTest` explotó con `NoSuchBeanDefinitionException` (mismo patrón de siempre: mock faltante para una dependencia nueva). La anotación correcta en Spring Framework 6.2+/Boot 4.1 es `@MockitoBean` (paquete `org.springframework.test.context.bean.override.mockito`), no la vieja `@MockBean` de `org.springframework.boot.test.mock.mockito`.
- **`@PostMapping` faltante en el método del webhook → `NoResourceFoundException`.** El método `recibirEventoStripe` estaba bien escrito pero sin la anotación de ruta — sin ella, Spring no lo registra como handler de nada, y el request cae al manejador de recursos estáticos (el mismo que serviría un `.html`), tirando `No static resource api/webhooks/stripe`. Fácil de confundir con un problema de firma/seguridad porque el síntoma es un 500/404 genérico, pero no tiene nada que ver — se nota en que Spring Security sí deja pasar el request (los logs muestran que atraviesa toda la cadena de filtros sin problema) y el error aparece recién en el `DispatcherServlet`.
- **Slice test del webhook (`@WebMvcTest`) necesita `@Import(SecurityConfig.class)` + mocks de `JwtUtil`/`UsuarioDetailsService`.** Mismo patrón que ya se usa en `ReservaControllerTest`: sin importar la config real de seguridad, el `permitAll()` de `/api/webhooks/**` no se aplica en el contexto del slice test. Y como `SecurityConfig` arma un `JwtAuthenticationFilter` que depende de esos dos beans, hace falta mockearlos aunque el endpoint bajo test no los use.
- **Testear código que usa la API estática legacy de Stripe (`Session.create(...)`, `Webhook.constructEvent(...)`) requiere `Mockito.mockStatic(...)`**, no el mockeo normal de instancias — técnica nueva para el proyecto. Se usa dentro de un `try (MockedStatic<X> x = mockStatic(X.class)) { ... }` (try-with-resources), importante para que el mock estático se "desmockee" automáticamente al salir del bloque y no contamine otros tests.
- El `webhook signing secret` que da `stripe listen` **cambia en cada corrida** del comando — al reiniciar la terminal o el login de Stripe CLI, hay que actualizar `STRIPE_WEBHOOK_SECRET` con el valor nuevo. En la práctica no siempre cambia (se puede comparar el valor nuevo contra el guardado con `echo $env:STRIPE_WEBHOOK_SECRET` en PowerShell antes de tocar nada — ojo con escribir el nombre de la variable, no pegar el valor `whsec_...` en el lugar del nombre, que da una consulta a una variable inexistente y no un error visible).

**Bugs reales encontrados al pasar a producción (sesión aparte, después de verificar todo en local):**
- **`Data truncated for column 'estado'` en producción, mismo bug que en local pero en una base distinta.** La base de Aiven se había creado en algún momento antes de sumar `PENDIENTE_PAGO` al enum de Java, y esa migración nunca se replicó ahí (cada entorno tiene su propia base física — arreglar la de Docker local no toca la de Aiven). `SHOW CREATE TABLE reservas` confirmó la causa exacta: la columna seguía como `enum('CANCELADA','CONFIRMADA')`, sin `PENDIENTE_PAGO`. Se resolvió de forma más robusta que en local: en vez de re-ampliar el `ENUM` (que volvería a romperse con el próximo estado nuevo que se agregue), se convirtió la columna a `VARCHAR`, alineado con `@Enumerated(EnumType.STRING)`: `ALTER TABLE reservas MODIFY COLUMN estado VARCHAR(20) NOT NULL;`, corrido desde DBeaver conectado directo a Aiven (Aiven no tiene un Query Editor propio en su consola, a diferencia de otros de sus servicios). Lección reforzada: cada entorno (Docker local, Aiven producción) tiene su propio schema físico — una migración manual hecha en uno no viaja sola al otro, ni con `ddl-auto=update` (que de por sí solo agrega, nunca modifica un tipo de columna existente).
- **404 en `/pago/exito` en producción (Vercel), aunque la misma ruta funcionaba perfecto en local (`npm run dev`).** La causa no es del código React sino de cómo sirve archivos un hosting de sitios estáticos: cuando se navega *dentro* de la SPA (clicks en `<Link>` de React Router), todo el ruteo lo resuelve el JavaScript ya cargado en el navegador, sin pedirle nada al servidor. Pero la redirección de Stripe después del pago es una **navegación completa del navegador** directo a `.../pago/exito` — eso sí le pide al servidor de Vercel un archivo/ruta real con ese nombre, que no existe (React Router es 100% client-side, esa ruta no es un archivo). El servidor de desarrollo de Vite (`npm run dev`) sirve `index.html` como fallback automáticamente para cualquier ruta, por eso el bug era invisible en local. Solución: `vercel.json` en la raíz del repo del frontend con un rewrite que manda cualquier ruta no-archivo a `index.html`, dejando que React Router recién ahí decida qué mostrar:
```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

**Verificación manual con evidencia real, en local y en producción**:
- **Local**: reserva creada vía Swagger (`PENDIENTE_PAGO`) → `POST /api/reservas/{id}/pago` devolvió una URL de Stripe Checkout real → pago completado en el navegador con tarjeta de test → `stripe listen` mostró el evento `checkout.session.completed` reenviado con respuesta `200` → `GET /api/reservas/{id}` confirmó `estado: CONFIRMADA` y el `Pago` asociado en `APROBADO`. Repetido después con el frontend real (botón "Pagar" en "Mis reservas"), mismo resultado, con capturas de las páginas de éxito/cancelado en claro y oscuro.
- **Producción**: reserva creada desde el frontend de Vercel → botón "Pagar" → Checkout de Stripe real → pago con tarjeta de test → los 5 eventos del endpoint de webhook de producción (visibles en el dashboard de Stripe) devolvieron `200 OK`, incluido `checkout.session.completed` → redirección a `/pago/exito` renderizando correctamente (después del fix del `vercel.json`) → "Mis reservas" mostrando la reserva como `Confirmada`.

**Mejora futura anotada, no implementada**: migrar del patrón estático legacy (`Stripe.apiKey` + `Session.create(...)`) al `StripeClient` inyectable, que es el patrón recomendado actualmente en la documentación oficial de `stripe-java` (el legacy sigue soportado, "sin planes de remover" según Stripe, así que no es urgente).

---

## Paginación de `Usuario` y `Reserva` en el panel admin ✅

Feature chica, surgida como ítem anotado al cerrar la paginación/filtrado de `Vuelo`: esas dos colecciones también crecen con el uso normal del sistema (a diferencia de `Avion`, flota chica y estática), así que eran candidatas reales al mismo tratamiento.

**Backend, mismo patrón ya usado en `Vuelo` (sin filtros, a diferencia de ese)**: `Pageable` como parámetro del controller, `Page<Entidad>` devuelto por el service, mapeado a `PageResponseDTO<DTO>` con el `PageMapper` ya existente. Como `Usuario`/`Reserva` no necesitan filtros (a diferencia de `Vuelo`, que sí), no hizo falta tocar los repositorios — `JpaRepository.findAll(Pageable)` alcanza, sin ningún método `@Query` propio.

**Detalle real del proceso: cambiar `listarTodos()`/`listarTodas()` de `List<T>` a `Page<T>` es un cambio de firma, no solo de tipo de retorno.** Antes de tocar el código, se usó "Find Usages" en IntelliJ sobre cada método de service para confirmar todos los callers reales (el controller, y los tests) — mismo tipo de chequeo que ya había hecho falta con `Vuelo`/Dashboard antes. Encontró un caller extra no obvio en cada caso: los tests de controller y de service, que también necesitaron reescribirse para mockear `Page`/`PageImpl`/`PageRequest` en vez de un `List` plano.

**Bug real (de testing, no de la app): Swagger UI + parámetro `Pageable` sin otros `@RequestParam`.** Al probar `GET /api/usuarios` en Swagger, el widget de "Try it out" para un parámetro `Pageable` trae un editor JSON con un valor por defecto `"sort": []` (array vacío). Al ejecutar sin tocar ese campo, Swagger UI manda literal `sort=[]` en la query string — Spring Data intenta usar eso como nombre de propiedad de ordenamiento y explota con `InvalidDataAccessApiUsageException: Sort expression '[]: ASC' must only contain property references...`. No es un bug de la app: un cliente real (el propio frontend) nunca manda ese parámetro si no se lo pasás explícitamente, así que nunca se da esta combinación fuera de Swagger. Se soluciona para probar borrando la línea `"sort": []` del JSON antes de ejecutar, o probando con una URL directa (`?page=0&size=10`, sin `sort`). Es muy probable que el mismo problema exista en el endpoint de `Vuelo` si se prueba de la misma forma exacta — simplemente no se había topado antes.

**Frontend: paginación real en el panel admin, distinta a la de `VuelosAdminPage`.** Revisando el código antes de implementar, se encontró que `VuelosAdminPage.jsx` en realidad **no** tiene paginación real — usa el mismo truco `{ size: 1000 }` que el Dashboard, para mostrar todo en una lista plana (la paginación real con botones vive en `VuelosPage.jsx`, la página pública). Pero la idea original anotada para esta feature pedía paginación real *en el panel admin* de Usuarios y Reservas — así que acá se armó el patrón de `VuelosPage.jsx` (estado de página actual/total/última, botones "Anterior"/"Siguiente", "Página X de Y", ocultos si `totalPaginas <= 1`) en `UsuariosAdminPage.jsx` y `ReservasAdminPage.jsx`, en vez de repetir el truco de `VuelosAdminPage.jsx`.

**Mismo problema de siempre con `DashboardPage.jsx` al paginar un endpoint que antes devolvía todo.** Como ya había pasado con `Vuelo`, paginar `GET /api/usuarios` y `GET /api/reservas` rompía los cálculos agregados del Dashboard (total de admins, ingresos confirmados, reservas por estado), que necesitan la lista completa, no una página. Mismo apaño ya documentado en la feature de `Vuelo`: pedir `{ size: 1000 }` en el Dashboard en vez de sumar un endpoint de agregación server-side (que sigue anotado como mejora futura, ver más abajo).

**Bug real encontrado al verificar con datos simulados: el gráfico de barras "Reservas por estado" mostraba el valor crudo del enum (`PENDIENTE_PAGO`, `CONFIRMADA`) en vez de una etiqueta legible, y `PENDIENTE_PAGO` no tenía color propio (caía al color de respaldo azul del componente `BarList`).** Ya existía un mapa `ETIQUETAS` (enum → texto legible) dentro de `Badge.jsx`, usado en el resto de la app — se exportó ese mismo mapa (antes solo interno del archivo) y se reusó en `DashboardPage.jsx` para no duplicar el mapeo en un segundo lugar, más se agregó `PENDIENTE_PAGO: 'bg-amber-500'` a `COLOR_ESTADO_RESERVA`.

**Detalle de testing (no bug real, anécdota de cómo se verificó)**: al armar el script de Playwright para mockear las respuestas de `/api/usuarios` y `/api/reservas` y sacar capturas, un patrón de ruta demasiado amplio (`**/api/usuarios**`) interceptó por error el propio archivo fuente `src/api/usuarios.js` que sirve el servidor de desarrollo de Vite (coincidía con el patrón por tener "api/usuarios" en el path), rompiendo la carga del módulo JS de la página entera. Se corrigió acotando el patrón al host real del backend (`http://localhost:8080/api/usuarios**`) en vez de un glob genérico — anécdota de cómo un mock de red mal acotado puede romper cosas que no tienen nada que ver con el endpoint que se quiere mockear.

**Verificación**: 108/108 tests backend. Frontend verificado con `npm run build` + capturas de Playwright con datos simulados (23 usuarios / 17 reservas, repartidos en varias páginas) confirmando: contenido correcto por página, botón "Anterior" deshabilitado en la primera página, "Siguiente" deshabilitado en la última, contador "Página X de Y" preciso, modo oscuro con buen contraste, y el Dashboard mostrando los totales agregados reales (23 y 17, no solo los de una página) gracias al truco `{ size: 1000 }`. Entregado como de costumbre en zips para aplicar y probar en el proyecto real antes de commitear.

---

## Extendiendo el caché con Redis a `Usuario` y `Reserva` ✅

Primer ítem tomado de la lista de "Ideas para más adelante": extender a otras dos entidades el mismo patrón `@Cacheable`/`@CacheEvict` ya probado con `Vuelo` (ver la feature de Redis más arriba). Backend puro, sin cambios de frontend.

**Reutilización directa del patrón existente, sin cambios de configuración.** `CacheConfig` ya define un `RedisCacheConfiguration` default (TTL 10 min, serialización JSON, `disableCachingNullValues()`) que aplica a **cualquier** `cacheNames` nuevo sin configuración adicional — los nombres `"usuario"` y `"reserva"` heredan ese comportamiento automáticamente, no hizo falta tocar `CacheConfig` para nada.

- **`UsuarioService.buscarPorIdCacheado`** (nuevo, análogo a `buscarPorIdCacheado` de `Vuelo`): `@Cacheable(cacheNames = "usuario", key = "#id")`, devuelve el DTO, llama internamente a `buscarPorId` (que sigue devolviendo la entidad, sin cachear, para no arriesgar trabajar con datos viejos en las mutaciones). `UsuarioController.bucarPorId` pasó a llamar a este método nuevo en vez del viejo.
- **`@CacheEvict(cacheNames = "usuario", key = "#id")`** agregado sobre `actualizarRol` — la única mutación por id que existe hoy sobre `Usuario` (no hay `editar`/`eliminar` genérico todavía).
- **`ReservaService.buscarPorIdCacheado`**, mismo patrón exacto. `ReservaController.buscarPorId` pasó a llamarlo.
- **`@CacheEvict(cacheNames = "reserva", key = "#id")`** agregado sobre `cancelarReserva`.

**El caso especial: invalidar el caché de `Reserva` desde el webhook de Stripe.** `PagoService.confirmarPago(Event event)` (el método que procesa el evento `checkout.session.completed` del webhook, ver la feature de Stripe) también cambia el `estado` de una `Reserva` a `CONFIRMADA` — necesitaba la misma invalidación que `cancelarReserva`, pero un `@CacheEvict(key = "#id")` directo sobre `confirmarPago` no era viable: el método recibe un `Event` de Stripe como único parámetro, no un `reservaId` — el id de la reserva se obtiene recién a mitad de la ejecución, vía `pago.getReserva().getId()`, después de deserializar el evento y buscar el `Pago` correspondiente.

Solución: un método nuevo, chico y dedicado en `ReservaService`:
```java
@CacheEvict(cacheNames = "reserva", key = "#id")
public void evictarCacheReserva(Long id) {
    // Cuerpo vacío a propósito: existe solo para que Spring dispare el
    // @CacheEvict a través del proxy con un id ya conocido, no para hacer
    // ningún trabajo real.
}
```
`PagoService` pasó a inyectar `ReservaService` por constructor (dependencia nueva) y a llamar `reservaService.evictarCacheReserva(reserva.getId())` justo después de guardar la reserva confirmada (`reservaRepository.save(reserva)`).

**Por qué no alcanzaba con evictar desde dentro del propio `ReservaService`/`PagoService` de cualquier forma.** Volvió a aplicar el "self-invocation problem" ya documentado en la feature de Redis de `Vuelo`: el `@CacheEvict` solo se dispara si la llamada pasa por el proxy de Spring, es decir, si viene de un bean **distinto**. Como `confirmarPago` vive en `PagoService` y necesita evictar el caché de `Reserva`, la solución no podía ser un método privado dentro de `PagoService` ni una llamada `this.algo()` dentro de `ReservaService` — tenía que ser exactamente lo que se hizo: un método público en `ReservaService`, invocado desde `PagoService` (bean distinto), cruzando el proxy real.

**Ajustes de tests, mismo patrón ya visto con `Vuelo` ("el controller ya no llama al método viejo"):**
- `UsuarioControllerTest.buscarPorId_*` y `ReservaControllerTest.buscarPorId_*`: se corrigieron para mockear `buscarPorIdCacheado` en vez de `buscarPorId`, devolviendo el DTO (o `null`) directo en vez de un `Optional`.
- `PagoServiceTest`: se agregó `@Mock private ReservaService reservaService;` (dependencia nueva de `PagoService`). En `confirmarPago_PagoPendiente_confirmaReservaYEnviaEmail` se sumó `verify(reservaService, times(1)).evictarCacheReserva(1L)`, confirmando que la eviction se dispara exactamente una vez con el id correcto. En `confirmarPago_pagoYaAprobado_noVuelveAProcesar` se sumó `verifyNoInteractions(reservaService)` — si el pago ya estaba aprobado, el método corta antes y **no** debería tocar el caché para nada. El test de pago no encontrado no necesitó cambios (falla antes de llegar a esa parte del código).

**Verificación manual con evidencia real de Redis, en tres escenarios distintos:**
1. **`Usuario`**: `GET /api/usuarios/{id}` → `KEYS *` mostró `usuario::{id}` cacheado. `PUT /api/usuarios/{id}/rol` → `KEYS *` inmediatamente después ya no lo mostraba (evicted). Un `GET` posterior repobló el caché con el rol nuevo.
2. **`Reserva` vía `PUT /cancelar`**: mismo ciclo completo (`GET` puebla → `PUT /cancelar` evict → `GET` repobla con `estado: CANCELADA`), confirmado con `redis-cli`.
3. **`Reserva` vía webhook de Stripe (el caso especial)**: se reservó un vuelo nuevo, se cacheó con un `GET /api/reservas/{id}` (`estado: PENDIENTE_PAGO`), se completó un pago real de prueba en Stripe Checkout con `stripe listen` corriendo en paralelo (todos los eventos, incluido `checkout.session.completed`, devolvieron `200`), y `KEYS *` confirmó que `reserva::{id}` había desaparecido — evicted por `evictarCacheReserva` desde `PagoService`, sin que nadie llame a `cancelarReserva`/`confirmarPago` manualmente. Un `GET /api/reservas/{id}` posterior devolvió `estado: CONFIRMADA` y `KEYS *` mostró `reserva::{id}` de nuevo, ya con el dato fresco — cerrando el ciclo completo también para el camino menos obvio (invalidación disparada por un webhook externo, no por un endpoint propio).

Con los tres escenarios confirmados con evidencia real de `redis-cli` (no solo lectura de código), y con el flujo de pago real de Stripe de por medio en el tercero, la feature quedó verificada de punta a punta. Suite completa: 108/108.

**Gap encontrado y ya corregido (no quedó pendiente): `crearReserva`/`cancelarReserva` no invalidaban el caché de `Vuelo`.** `ReservaService.crearReserva`/`cancelarReserva` modifican `vuelo.asientosDisponibles` (para reflejar el asiento ocupado/liberado) hablando directo con `VueloRepository`, sin pasar nunca por `VueloService` — así que, durante los 10 minutos de TTL, un `GET /api/vuelos/{id}` cacheado podía mostrar un `asientosDisponibles` desactualizado justo después de una reserva o cancelación sobre ese vuelo.

Se corrigió con el mismo patrón ya usado para el caso especial del webhook de Stripe: un método nuevo y dedicado en `VueloService`,
```java
@CacheEvict(cacheNames = "vuelo", key = "#id")
public void evictarCacheVuelo(Long id) {
    // Cuerpo vacío a propósito, mismo patrón que evictarCacheReserva.
}
```
invocado desde `ReservaService` (que ya tenía a `VueloService` como dependencia inyectada por otro motivo, así que no hizo falta tocar el constructor) justo después de cada `vueloRepository.save(vuelo)`, tanto en `crearReserva` como en `cancelarReserva` — cruzando el proxy de Spring correctamente al venir de un bean distinto, evitando de nuevo el "self-invocation problem".

De paso se corrigió también la inconsistencia ya anotada de `cancelarReserva` sin `@Transactional` (a diferencia de `crearReserva`, que sí la tenía) — mismo motivo que siempre: modifica dos entidades en dos `save()` separados, y sin la anotación un fallo a mitad de camino podía dejar un asiento liberado sin que la reserva quedara marcada como cancelada.

**Verificación manual con `redis-cli`, en los dos caminos**: `GET /api/vuelos/{id}` (puebla `vuelo::{id}`) → `POST /api/reservas` sobre ese vuelo → `KEYS *` confirma que `vuelo::{id}` desapareció. Repetido igual para `cancelarReserva` (recachear con un `GET`, cancelar, confirmar que se evictó de nuevo). Ambos casos confirmados. Nota del proceso: la primera pasada del test dio "falso positivo de que no pasó nada" porque nunca se había hecho el `GET` previo para poblar el caché — evictar una clave que no existe no rompe ni avisa nada, así que sin ese paso previo el test no prueba nada en ningún sentido. Suite completa sin cambios de cantidad, solo nuevas aserciones: 108/108.

### Bug real de producción, encontrado recién al desplegar: los cache *hits* de verdad rompían con `ClassCastException` — y afectaba también a `Vuelo`

Al probar la feature recién desplegada contra producción, `GET /api/usuarios/{id}` dio `200` la primera vez y `500` la segunda vez, con el mismo id, sin tocar nada en el medio. El log de Render mostró la excepción real (el cliente solo ve el mensaje genérico enmascarado):

```
java.lang.ClassCastException: class java.util.LinkedHashMap cannot be cast to class com.pablo.aerolinea.dto.UsuarioResponseDTO
	at com.pablo.aerolinea.service.UsuarioService$$SpringCGLIB$$0.buscarPorIdCacheado(<generated>)
```

**Causa raíz**: `GenericJacksonJsonRedisSerializer` (la versión para Jackson 3 que ya usa este proyecto) **no activa el "default typing" automáticamente** al construirse solo con un `ObjectMapper`, a diferencia de su predecesora `GenericJackson2JsonRedisSerializer` (Jackson 2), que sí lo hacía sola. "Default typing" es lo que hace que el JSON guardado en Redis incluya el nombre completo de la clase real, para que al leerlo de vuelta Jackson sepa reconstruir el objeto correcto en vez de un `Map` genérico. Sin eso, la primera llamada (cache *miss*, va a la base, guarda en Redis) funciona igual porque el objeto que se devuelve es el que arma el service directamente — el problema aparece recién en la segunda llamada (cache *hit* real, lee de Redis y deserializa), que devuelve un `LinkedHashMap` en vez del DTO, y el cast que hace el proxy de Spring al devolverlo con el tipo declarado del método explota. Confirmado contra la documentación oficial de Spring Data Redis (nunca activa el default typing por su cuenta) y un issue conocido de Spring Boot con el mismo síntoma exacto.

**Hallazgo más importante: este bug probablemente afectaba también a `Vuelo` desde que se implementó el caché, y nunca se detectó.** Repasando cómo se verificó `Vuelo` en su momento (más arriba en este archivo): el flujo siempre fue `GET` (miss, escribe) → chequear el JSON crudo en `redis-cli` → `PUT` (evict) → `GET` de nuevo (miss otra vez, porque se acababa de invalidar). **Nunca se probaron dos `GET` seguidos sin ningún evict en el medio** — que es exactamente el escenario que dispara un cache *hit* real y expone el bug. Moraleja para el futuro: verificar que el caché "escribe bien" (ver el JSON en Redis) no alcanza — hay que probar explícitamente el camino de lectura repetida, sin invalidación de por medio, porque es un escenario distinto con un código distinto ejecutándose (deserialización, no serialización).

**El fix**, en `CacheConfig.cacheConfiguration()`: armar el `ObjectMapper` con el "default typing" activado explícitamente antes de pasárselo al serializer, usando un `PolymorphicTypeValidator` (del paquete `tools.jackson.databind.jsontype`, la variante Jackson 3 — no `com.fasterxml.jackson.databind.jsontype`, que es Jackson 2 y no es compatible con el `ObjectMapper` que ya usa el proyecto):

```java
PolymorphicTypeValidator validador = BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("com.pablo.aerolinea.dto")
        .build();

ObjectMapper mapperConTipos = objectMapper.rebuild()
        .activateDefaultTyping(validador, DefaultTyping.NON_FINAL)
        .build();
```
y usar `mapperConTipos` (en vez de `objectMapper` directo) al construir el `GenericJacksonJsonRedisSerializer`. Con esto, el JSON guardado en Redis pasó a verse así (formato `WRAPPER_ARRAY`: un array de dos elementos, el nombre de la clase y el objeto — una variante válida de cómo Jackson embebe el tipo, distinta pero equivalente a la clásica propiedad `@class` adentro del objeto):
```
["com.pablo.aerolinea.dto.UsuarioResponseDTO",{"email":"...","id":2,"nombre":"...","rol":"ADMIN"}]
```

**Verificación, en local y en producción, de los tres cache names**: `GET` dos veces seguidas al mismo id en `vuelo`, `usuario` y `reserva` → `200` en ambas (antes del fix, la segunda daba `500` en los tres). Confirmado además con `redis-cli` que el valor guardado ahora trae el tipo embebido, y que el camino especial del webhook de Stripe (evict + repoblado automático al confirmarse un pago) también funciona sin explotar — probado con un pago real de Stripe tanto en local (`stripe listen`) como en producción (contra el checkout real, sin `stripe listen`, ya que el webhook le pega directo a Render), en ambos casos con el frontend redirigiendo correctamente a la página de éxito. Suite completa sin cambios: 108/108 (este bug, al depender de la serialización real contra Redis, **no** lo detectan los tests unitarios existentes — mockean el repository y nunca pasan por Redis de verdad; la única forma real de confirmarlo fue manual, con `redis-cli`).

**Detalles operativos encontrados en el camino, al desplegar este fix, sin relación con el bug de arriba**: dos causas distintas hicieron que el deploy a Render tardara o se colgara antes de llegar a probar nada:
- **Aiven (MySQL) se había apagado por inactividad** (comportamiento normal del plan gratuito tras un tiempo sin conexiones) — el health check de Actuator no podía conectarse a la base, devolvía `503`, y Render se quedaba esperando indefinidamente un `200` que nunca llegaba (`Waiting for internal health check...`). Se resolvió entrando al dashboard de Aiven y volviendo a encender el servicio.
- **El propio backend en Render (plan gratuito) también entra en "cold start"** tras un rato sin tráfico — al primer request nuevo, Render muestra una pantalla de "despertando" (`SERVICE WAKING UP...`) mientras reinicia la instancia, algo totalmente normal y sin relación con ningún bug, que simplemente hay que esperar (medio minuto aprox.) antes de reintentar.

---

## Ideas para más adelante (explícitamente no planificar todavía)

- **Un segundo proyecto que incluya microservicios.** Anotado a pedido explícito, para después de terminar las features nuevas de AeroPass (ver roadmap) — la idea es que sirva como oportunidad de reforzar seguridad/JWT en un contexto nuevo, más un primer acercamiento real a arquitectura distribuida (comunicación entre servicios, service discovery, posiblemente un API Gateway). No se define alcance ni tecnología todavía a propósito — es una nota para el futuro, no una tarea activa.
- **Endpoint de agregación server-side para las estadísticas del Dashboard admin.** La solución "correcta" al trade-off que rompió `DashboardPage.jsx` al paginar `Vuelo`, y después de nuevo con `Usuario`/`Reserva` — hoy resuelto de forma pragmática pidiendo `{ size: 1000 }` en las tres colecciones en vez de traer todo sin paginar. Quedaría un endpoint que calcule los totales/agregados del lado del servidor sin necesidad de traer todos los registros al cliente.
- **Extender el cacheo con Redis a los demás endpoints de lectura que lo justifiquen.** Ya cachea `GET /api/vuelos/{id}` (prueba de concepto original), y ahora también `GET /api/usuarios/{id}` y `GET /api/reservas/{id}`. Queda pendiente revisar el resto de la API con el mismo criterio (endpoints de lectura frecuente, con costo real de ir a la base, y con una invalidación clara vía `@CacheEvict` en las mutaciones correspondientes) y decidir cuáles conviene sumar — candidatos a evaluar: `GET /api/vuelos` (la lista paginada/filtrada), `GET /api/aviones` (flota chica y estática, buen candidato porque cambia poco), y cualquier otro que aparezca al revisar. Se trabaja igual que el resto de los temas pendientes de esta lista: recién después de terminar las features nuevas que quedan en el roadmap.
- **Enviar también un email de notificación cuando un vuelo asociado cambia de estado (demorado/cancelado).** Alcance descartado a propósito al arrancar la feature de notificaciones (se eligió el alcance más chico: solo confirmación/cancelación de reserva) — quedaría pendiente evaluar si vale la pena sumarlo, tocando `VueloService` además de `ReservaService`.

---

## Próximos temas pendientes (roadmap)

1. ~~Entidad `Avion` (relación `@ManyToOne` desde `Vuelo`)~~ ✅
2. ~~Entidad `Usuario` con roles (`ADMIN` / `USUARIO`)~~ ✅
3. ~~Entidad `Reserva` (relaciona `Usuario` + `Vuelo`, lógica de negocio con transacciones reales)~~ ✅
4. ~~Seguridad con Spring Security + JWT sobre `Usuario`/roles~~ ✅
5. ~~Testing con JUnit + Mockito~~ ✅
6. ~~Documentación con Swagger/OpenAPI~~ ✅
7. ~~Frontend en React (login, vuelos, reservas)~~ ✅
8. ~~Dockerizar el proyecto (MySQL + backend, reemplazando XAMPP)~~ ✅
9. ~~Deploy (Aiven MySQL + Render backend + Vercel frontend)~~ ✅
10. ~~Completar backend con endpoints faltantes para el panel de administración~~ ✅
11. ~~Rediseño completo del frontend (Tailwind CSS, panel ADMIN completo, landing page, perfil de usuario, dashboard con estadísticas, modo oscuro)~~ ✅
12. ~~`GET /api/usuarios/me` para que el Perfil muestre el nombre real (backend + tests + frontend), verificado en local y producción~~ ✅
13. ~~Deploy del frontend rediseñado (Vercel) y verificación contra producción~~ ✅
14. ~~Usuario de demo (rol ADMIN) + README en los dos repos, para que cualquiera pueda probar el proyecto sin depender de vos~~ ✅
15. ~~Repaso final y buenas prácticas~~ ✅
16. Nuevas features para sumar al CV:
    - ~~Paginación/filtrado server-side en vuelos (backend + frontend, verificado en Swagger y en el navegador con datos reales)~~ ✅
    - ~~Spring Boot Actuator (health/info/metrics con seguridad por rol, verificado en local y producción, health check de Render configurado)~~ ✅
    - ~~CI/CD vía GitHub Actions (tests automáticos + deploy a Render condicionado a que pasen)~~ ✅ (verificación del badge de Render en el dashboard pendiente de confirmar la próxima vez, sin bloquear el cierre)
    - ~~Pagos sandbox con Stripe: backend (Checkout Session + webhook + 108/108 tests), frontend (botón "Pagar" + páginas de éxito/cancelado) y deploy completo a producción (webhook de Stripe apuntando a Render, fix del `ENUM` en Aiven, fix de rutas SPA en Vercel), verificado con evidencia real en local y en producción~~ ✅
    - ~~Caché con Redis (`@Cacheable`/`@CacheEvict` en `GET /api/vuelos/{id}`, TTL 10 min, `CacheErrorHandler` para degradar con gracia, health check corregido, y Redis real en producción vía Render Key Value — verificado con `redis-cli` en local y en producción)~~ ✅
    - ~~Notificaciones por email (confirmación y cancelación de reserva, vía Mailtrap sandbox, `@Async` con manejo de errores resiliente, verificado con evidencia real en la bandeja de Mailtrap)~~ ✅
    - ~~Paginación server-side en `Usuario` y `Reserva` (backend con el mismo patrón de `Vuelo`, panel admin con paginación real a diferencia del truco usado en `VuelosAdminPage`, Dashboard ajustado, verificado con 108/108 tests y capturas)~~ ✅
    - ~~Caché con Redis extendido a `Usuario` y `Reserva` (mismo patrón `@Cacheable`/`@CacheEvict` de `Vuelo`, más un caso especial de invalidación disparada desde el webhook de Stripe vía `PagoService`→`ReservaService.evictarCacheReserva`, verificado con `redis-cli` en tres escenarios incluyendo un pago real de Stripe, y 108/108 tests). Incluyó un bug real de producción encontrado al desplegar (`ClassCastException` por falta de "default typing" en `GenericJacksonJsonRedisSerializer`, afectaba también a `Vuelo`), corregido y verificado en local y producción~~ ✅
    - ~~Cierre de los dos gaps detectados durante la feature anterior: `crearReserva`/`cancelarReserva` ahora invalidan también `vuelo::{id}` (nuevo `VueloService.evictarCacheVuelo`, mismo patrón que el caso del webhook), y `cancelarReserva` ya tiene `@Transactional` — verificado con `redis-cli` en ambos caminos y 108/108 tests~~ ✅
17. (Más adelante, no planificado todavía) Segundo proyecto con microservicios, enfocado en reforzar seguridad/JWT y dar un primer paso en arquitectura distribuida.
