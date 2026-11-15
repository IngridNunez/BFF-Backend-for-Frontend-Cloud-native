# BFF — Backend for Frontend de Sanos y Salvos

Punto de entrada de la API de **Sanos y Salvos** (plataforma para reunir mascotas perdidas con sus dueños). Es el único servicio que ve el navegador: maneja la sesión con cookies seguras, valida los tokens de **AWS Cognito** y reenvía cada petición al microservicio que corresponde.

Spring Boot 4 · Java 17 · Spring Security (OAuth2 Resource Server) · Spring Cloud (Config Client + Eureka Client) · Maven

## Cómo encaja en la arquitectura

```
Navegador ──► API Gateway ──► BFF ──► ms-mascotas  (MongoDB)
 (React)      (HTTP API)       │  ──► ms-alertas   (MySQL, correos)
                               │  ──► ms-chat      (MongoDB, WebSocket)
                               └─ Config Server · Eureka
Navegador ══ WebSocket (con ticket del BFF) ══► ms-chat      (no pasa por el BFF)
```

Qué hace el BFF:

- **Sesión por cookies `HttpOnly`:** el frontend obtiene los tokens de Cognito, se los entrega una vez a `POST /api/v1/auth/session` y el BFF los devuelve como cookies. El navegador nunca guarda tokens en `localStorage`.
- **Valida identidad:** access token (`client_id` + `token_use=access`), id token (`aud` + `token_use=id`) y que ambos sean de la misma persona (`sub`).
- **Proxy hacia los microservicios:** `/api/v1/<servicio>/...` se reenvía a `servicios.rutas.<servicio>`. A cada microservicio le llega solo lo necesario (ver [Seguridad](#seguridad)).
- **Ticket del chat:** `POST /api/v1/chat/ticket` firma un ticket de un solo uso (30 s) para abrir el WebSocket directo contra `ms-chat`, que no puede pasar por API Gateway ni por este proxy.

## Rutas

| Ruta | Acceso | Qué hace |
|---|---|---|
| `GET /actuator/health` | Público | Estado del servicio |
| `POST /api/v1/auth/session` | Público | Valida los tokens de Cognito y emite las cookies `access_token` e `id_token` |
| `GET /api/v1/auth/me` | Público (lee la cookie) | Correo y nombre de la persona con sesión; `401` si no hay |
| `POST /api/v1/auth/logout` | Público | Borra las cookies |
| `POST /api/v1/chat/ticket` | Con sesión | Ticket de un solo uso para el WebSocket del chat (`503` si el chat no está configurado) |
| `GET /api/v1/mascotas`, `GET /api/v1/mascotas/{id}` | Público | Listado y detalle (invitados) |
| `GET /api/v1/alertas/zona` | Público | Alertas cercanas |
| `POST /api/v1/contactos` | Público | Contactar al dueño de un reporte, sin cuenta |
| `POST /api/v1/mascotas/{id}/confirmar-reunificacion` | Público (autoriza el token del correo) | Confirmación desde el link del correo |
| `GET /api/v1/mascotas/mis-mascotas`, `GET /api/v1/mascotas/presigned-url` | Con sesión | |
| Cualquier otra ruta de `/api/v1/**` | Con sesión | Se reenvía al microservicio |

Servicios configurados (`servicios.rutas`): `mascotas`, `alertas`, `contactos` (→ ms-alertas) y `chat`. Una ruta sin servicio o con un servicio que no existe responde `404`; si el microservicio no responde, `503` con `Retry-After`.

## Seguridad

| Medida | Dónde |
|---|---|
| Cookies `HttpOnly`, `SameSite=Lax` y `Secure` (forzable con `BFF_COOKIES_SECURE=true` cuando el TLS se termina antes del BFF) | `AuthController`, `CookieUtil` |
| Anti-CSRF: `POST/PUT/PATCH/DELETE` exigen el header `X-Requested-With: web` | `CsrfHeaderFilter` |
| CORS con orígenes explícitos (`CORS_ALLOWED_ORIGINS`), sin comodín, con credenciales | `SecurityConfig` |
| Identidad confiable: se descartan los `X-User-Id` / `X-User-Email` que mande el cliente y se reenvían solo los armados desde el token validado | `ProxyService` |
| Lista de headers hacia los microservicios: `Content-Type`, `Accept`, `Accept-Language`, `Authorization` (reconstruido desde la cookie), `X-User-Id`, `X-User-Email`. **No** viajan `Cookie`, `Origin`, `X-Forwarded-*`, etc. | `ProxyService` |
| Límite de body: `413` sobre 1 MB, `411` si es *chunked* sin tamaño | `LimiteBodyFilter` |
| Timeouts hacia los microservicios (conexión 2 s, lectura 10 s) | `RestClientConfig` |
| Decoders de JWT sin llamadas de red al arrancar (las claves de Cognito se piden con el primer token) | `JwtDecoderConfig` |
| No se registran tokens, cookies ni correos | todo el código |

## Configuración

El `application.yml` local solo trae el nombre y el import del Config Server; **la configuración real vive en el repo `sanos-y-salvos-config_server`** (`config/bff.yml`). Variables de entorno:

| Variable | Obligatoria | Para qué |
|---|---|---|
| `COGNITO_USER_POOL_ID` | Sí | User Pool de Cognito (emisor y claves de los tokens) |
| `COGNITO_CLIENT_ID` | Sí | App Client de la SPA |
| `COGNITO_REGION` | No (`us-east-1`) | Región de Cognito |
| `CORS_ALLOWED_ORIGINS` | No (`http://localhost:5173`) | Orígenes del frontend, separados por coma |
| `CHAT_TICKET_SECRET` | Para el chat | Clave HS256 (mín. 32 caracteres), **la misma** que usa `ms-chat`. Vacía = el chat responde `503` |
| `CHAT_WS_URL` | Para el chat | URL del WebSocket que recibe el frontend (`ws://localhost:8084/api/v1/ws/chat` en local) |
| `BFF_COOKIES_SECURE` | No (`false`) | `true` en producción detrás de API Gateway/ALB |
| `BFF_MAX_BODY_BYTES` | No (`1048576`) | Tamaño máximo del body |
| `BFF_TIMEOUT_CONEXION_MS` / `BFF_TIMEOUT_LECTURA_MS` | No (`2000` / `10000`) | Timeouts hacia los microservicios |
| `SPRING_CONFIG_IMPORT` | No | Dirección del Config Server (en Docker: `optional:configserver:http://config-server:8888`) |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | No | Dirección de Eureka |

Crea un `.env` (ya está en `.gitignore`) con las obligatorias. **Nunca subas secretos al repositorio.**

## Ejecutar

**Con Docker (lo habitual).** El BFF se une a la red `sanos-y-salvos-network`, que crea el Config Server; levanta antes `config-server`, `discovery-server` y los microservicios. Desde la raíz del proyecto basta `./levantar-sanos-y-salvos.sh`. Solo el BFF:

```bash
docker compose up -d --build        # http://localhost:8081/actuator/health
```

**Sin Docker** (necesitas el Config Server arriba o variables equivalentes):

```bash
./mvnw spring-boot:run
```

**Construir el JAR:** `./mvnw package` (la imagen se arma con `docker compose build`, en 3 etapas: dependencias, compilación y JRE liviano).

## Tests

Se ejecutan **sin Cognito, sin Docker y sin red**: los decoders de JWT se simulan y los servicios externos se reemplazan por dobles o por servidores HTTP locales. El perfil de prueba está en `src/test/resources/application.yml`.

```bash
./mvnw test                                       # todo (offline: ./mvnw -o test)
./mvnw test -Dtest=ProxyServiceTest               # una clase
./mvnw test -Dtest='AuthControllerTest,JwtFilterTest'
```

Los informes quedan en `target/surefire-reports/`. En la consola verás líneas `ERROR` del `JwtFilter`: son esperadas, corresponden a las pruebas de rechazo (token inválido, subs distintos).

**89 pruebas, 0 fallos** (a la fecha de este README):

| Clase | Pruebas | Qué cubre |
|---|---|---|
| `ProxyServiceTest` | 18 | Reenvío (URL, puerto, query sin doble codificación, método, body nulo); respuestas (headers *hop-by-hop*, errores del microservicio tal cual); **lista de headers permitidos** (sin `Cookie`, `Origin`, `Authorization` del cliente); **identidad**: un cliente no puede suplantar `X-User-Id` / `X-User-Email`; rutas inválidas y servicio desconocido → `404`; servicio caído o con timeout → `503`; respuesta ilegible → `502`; no hay sondeo de salud previo |
| `AuthControllerTest` | 16 | Inicio de sesión (cookies `HttpOnly`/`SameSite`/`Path`, `Secure` con `https` o `BFF_COOKIES_SECURE`, no se emite `refresh_token`); rechazos: token inválido, id token inválido, `sub` distintos, campos faltantes o en blanco, token demasiado largo, JSON malformado (sin cookies emitidas); `/me` (con y sin sesión, token inválido, sin nombre); `/logout` |
| `CookieUtilTest` | 10 | Atributos de las cookies; lectura del header `Cookie` crudo (nombres parecidos, valores con `=`, cookies de terceros mal formadas) |
| `ProxyControllerIntegrationTest` | 10 | Cadena de seguridad real con `MockMvc`: rutas protegidas sin/con token, tokens con `sub` distintos, rutas públicas (mascotas, alertas, contactos), `POST` sin header anti-CSRF → `403`, health público |
| `JwtFilterTest` | 7 | Sin autenticación o anónima, sin id token, id token inválido, `sub` distintos, caso válido que propaga `X-User-Id`; ya no exige `refresh_token` |
| `ChatTicketServiceTest` | 5 | Ticket HS256 con `sub`, audiencia `ms-chat`, 30 s y `jti` único; falla cerrado sin clave, con clave corta o sin URL |
| `LimiteBodyFilterTest` | 5 | `413` sobre el máximo, `411` en *chunked* sin tamaño, pasan los bodies chicos, las peticiones sin body y las lecturas |
| `ChatTicketControllerIntegrationTest` | 4 | Ticket con sesión (firma verificada), sin sesión → `401`, sin header anti-CSRF → `403`, tokens con `sub` distintos → `401` |
| `CsrfHeaderFilterTest` | 4 | `POST/PUT/PATCH/DELETE` sin el header → `403`, con el header pasan, valor incorrecto, lecturas libres |
| `JwtDecoderConfigTest` | 3 | Crear los decoders no hace llamadas de red; un token que no es JWT se rechaza |
| `RestClientConfigTest` | 3 | Con servidor HTTP local real: el timeout de lectura corta, un servicio inalcanzable falla rápido, `PATCH` sigue funcionando |
| `LimiteBodyIntegrationTest` | 2 | El filtro de tamaño está cableado en la cadena real: un body enorme a una ruta pública → `413` sin llegar al proxy |
| `ChatTicketSinConfigurarIntegrationTest` | 1 | Sin `CHAT_TICKET_SECRET` el endpoint responde `503` |
| `BffApplicationTests` | 1 | El contexto de Spring carga |

Criterio del proyecto: cada funcionalidad nueva lleva **prueba positiva, rechazo sin autenticación y validación de entradas inválidas**. Las pruebas de seguridad se comprueban además quitando el arreglo a propósito (mutantes) para confirmar que fallan.

## Estructura

```
src/main/java/com/sanosysalvos/bff
├── config/       SecurityConfig, JwtDecoderConfig, RestClientConfig, ServiciosProperties
├── controller/   AuthController, ChatTicketController, ProxyController
├── security/     JwtFilter, CsrfHeaderFilter, LimiteBodyFilter, CookieBearerTokenResolver
├── service/      ProxyService, ChatTicketService
├── dto/          SesionRequestDto, UsuarioSesionDto, TicketChatDto
└── util/         CookieUtil
```

## Limitaciones conocidas

- **No hay `/auth/refresh`:** al vencer el token (1 h) hay que volver a iniciar sesión. El BFF ya no guarda el `refresh_token`.
- **El cierre de sesión no revoca** los tokens en Cognito (solo borra las cookies).
- **Sin reintentos ni cola:** si un microservicio no responde, el BFF devuelve `503` y el cliente decide reintentar.
- Detalle de la revisión de código y sus correcciones: `REVISION-CODE-REVIEW-BFF.md` y el texto del PR (en la raíz del proyecto).

## Flujo de trabajo

`main` (estable) → `develop` (integración) → `feature/*` o `fix/*` desde `develop`, con Pull Request y pruebas. Antes de empezar: `git switch develop && git pull origin develop`.
