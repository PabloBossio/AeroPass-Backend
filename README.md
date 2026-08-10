# AeroPass — Backend (API REST)

API REST para un sistema de reservas de vuelos, desarrollada en Java con Spring Boot.

## Demo en vivo

- **Frontend:** https://aero-pass-frontend.vercel.app
- **Documentación interactiva (Swagger):** https://aeropass-backend.onrender.com/swagger-ui/index.html

Usuario de prueba (rol Administrador): `admin@example.com` / `admin12345`

> El backend corre en la capa gratuita de Render y "duerme" tras 15 minutos de inactividad — el primer request después de eso puede tardar entre 30 y 50 segundos.

## Funcionalidades

- CRUD de vuelos, aviones y usuarios, con reglas de negocio (validación de fechas y asientos, prevención de sobreventa, restricciones de capacidad, etc.).
- Sistema de reservas con manejo transaccional y bloqueo pesimista a nivel de base de datos para evitar condiciones de carrera en reservas concurrentes.
- Autenticación y autorización con Spring Security + JWT, con roles ADMIN/USUARIO.
- Documentación interactiva con Swagger/OpenAPI.
- Suite de más de 90 tests automatizados (JUnit 5 + Mockito): unit tests de servicio y tests de controller/seguridad con MockMvc.
- Manejo centralizado de excepciones (`@RestControllerAdvice`) con códigos de estado HTTP semánticamente correctos.
- Containerizado con Docker (Dockerfile multi-stage + Docker Compose para desarrollo local con MySQL).

## Stack técnico

Java 21 · Spring Boot · Spring Data JPA · Spring Security · JWT · Hibernate · MySQL · Docker · JUnit 5 / Mockito · Swagger/OpenAPI (springdoc) · Maven

## Arquitectura

Arquitectura en capas: Controller → Service → Repository, con DTOs y Mappers para desacoplar la API del modelo de datos interno, y manejo global de excepciones vía `@RestControllerAdvice`.

## Correrlo en local

Requiere Docker.

```bash
git clone https://github.com/PabloBossio/AeroPass-Backend.git
cd AeroPass-Backend
docker compose up -d      # levanta MySQL (+ phpMyAdmin opcional)
```

Después correr la app desde tu IDE (o `./mvnw spring-boot:run`), y entrar a `http://localhost:8080/swagger-ui/index.html`.

## Deploy

- **Base de datos:** MySQL en Aiven.
- **Backend:** contenedor Docker en Render.
- **Frontend:** Vercel (repo separado: [AeroPass-Frontend](https://github.com/PabloBossio/AeroPass-Frontend)).

## Autor

**Pablo Bossio** — [linkedin.com/in/pablo-bossio-909b27420](https://linkedin.com/in/pablo-bossio-909b27420)
