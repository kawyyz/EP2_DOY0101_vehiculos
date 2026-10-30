# ms-vehiculos

Microservicio de vehículos con su pipeline de CI/CD en GitHub Actions.
Evaluación Parcial 2 de DOY0101 (Ingeniería DevOps), Duoc UC.

Integrantes: Lino Requena y Arion Ruiz-Tagle.

Es el mismo microservicio de la evaluación anterior, pero lo sacamos del monorepo para que el
pipeline pueda construir el módulo solo. Java 21, Spring Boot 4.1.2, JPA, MySQL 8.4 y Swagger.

## La API

Corre en el puerto 9004.

| Método | Ruta | |
|---|---|---|
| GET | `/api/v1/vehiculos` | lista todos |
| GET | `/api/v1/vehiculos/{id}` | 404 si no está |
| POST | `/api/v1/vehiculos` | 409 si la patente ya existe, 400 si el body no valida |
| PUT | `/api/v1/vehiculos/{id}` | |
| DELETE | `/api/v1/vehiculos/{id}` | 204 |
| GET | `/actuator/health` | lo usan Docker y el pipeline |
| GET | `/actuator/info` | devuelve el commit que está corriendo |
| GET | `/swagger-ui/index.html` | documentación |

![Swagger UI](docs/swagger-ui.png)

## Levantarlo

```bash
cp .env.example .env     # y completar las claves
docker compose up -d --wait
```

No se necesita Java ni Maven, todo se compila dentro de la imagen. El `--wait` hace que el comando
no vuelva hasta que los dos healthchecks estén en verde. Para bajarlo, `docker compose down`.

El `.env` no se sube al repo. En el servidor lo escribe el pipeline con los secrets.

## El pipeline

Está en `.github/workflows/ci-cd.yml` y se dispara con push y con pull request contra `main`.
Son cinco jobs:

**test.** Corre `./mvnw -B verify`: 19 tests (10 del service con Mockito, 9 del controller con
MockMvc) y después la verificación de cobertura. La regla de JaCoCo en el `pom.xml` exige 70% de
líneas y el build falla si baja de ahí; hoy vamos en 53 de 53. El reporte HTML queda como artefacto
de la corrida. Los tests no solo prueban el camino feliz: están los 409 por patente duplicada, los
404 y los 400 de validación.

![Cobertura](docs/cobertura-jacoco.png)

**security.** `snyk test` para las dependencias y `snyk code test` para nuestro código, los dos con
`--severity-threshold=high`, así que un hallazgo alto o crítico deja el job en rojo. Las excepciones
van versionadas en `.snyk` y con fecha de vencimiento. Hoy hay una sola, por `jaxb-core`: entra por
Hibernate, todavía no tiene versión parchada y nuestros endpoints no reciben XML. Vence el
26-01-2027 y ahí hay que revisarla de nuevo. Aparte, Dependabot mira Maven, Docker y las actions
cada semana y abre PRs.

**build-image.** El Dockerfile es multi-stage: una etapa compila con el JDK y la imagen final es
solo JRE, corriendo con el usuario `app` en vez de root. Después Trivy escanea la imagen con
`exit-code: 1` en CRITICAL y HIGH, o sea que una imagen con vulnerabilidades altas no alcanza a
publicarse. Recién ahí sube a GHCR con dos tags: el SHA del commit y `latest`.

**deploy.** Solo en push a `main`, ver abajo.

**alert.** Si cualquier etapa falla en `main`, abre solo un issue con el SHA y el link a la corrida.

En un pull request corren los tres primeros, así que si algo está roto se ve antes de mergear.
Además `main` está protegida con un ruleset: todo entra por PR y no se puede mergear sin esos tres
checks en verde. Así ningún commit llega a desplegarse sin pasar por los controles.

## El despliegue

Desplegamos en un servidor propio con Docker que no tiene ningún puerto abierto a internet. El
runner de GitHub llega hasta él uniéndose a nuestra red privada de Tailscale con una auth key
efímera, y de ahí entra por SSH. Al terminar la corrida el nodo desaparece.

Antes desplegábamos en una EC2, pero la instancia se cayó y el job empezaba a fallar en el
`ssh-keyscan`. Moverlo acá nos dejó un destino que controlamos nosotros.

El job copia el `docker-compose.yml`, escribe el `.env` remoto con los secrets, y hace
`docker compose pull` y `up -d --no-build --wait`. El `--no-build` importa: el servidor no compila
nada, solo baja la imagen que ya pasó por los tests, Snyk y Trivy.

Algunas cosas de seguridad del despliegue:

- La llave SSH del pipeline está limitada en el `authorized_keys` del servidor con
  `from="100.64.0.0/10"` (el rango de Tailscale) más `no-agent-forwarding`. Aunque se filtrara, no
  sirve desde fuera de la red.
- No hace falta `docker login` en el servidor porque el paquete de GHCR es público, así no queda
  ninguna credencial de registry guardada ahí.
- Los secrets viven en el environment `staging`, no a nivel de repositorio: `TAILSCALE_AUTHKEY`,
  `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` y las de MySQL.
- Como el repositorio es público, importa que el deploy esté condicionado a push sobre `main`. Un
  pull request desde un fork no lo ejecuta ni ve esos secrets.
- El workflow parte con `permissions: contents: read` y cada job pide solo lo que necesita.

## Trazabilidad

La idea es poder mirar el servicio corriendo y saber de qué commit salió.

El SHA entra como build-arg al Dockerfile y queda en dos lugares: como label
`org.opencontainers.image.revision` de la imagen y como variable de entorno. La imagen se publica
con ese mismo SHA de tag, y el deploy instala ese tag, no `latest`. La app lo expone en
`/actuator/info`.

El último paso del pipeline hace curl ahí y compara contra el commit que acaba de construir. Si lo
que está respondiendo en el servidor no es ese, el job falla. No basta con que el despliegue no tire
error, tiene que contestar el código correcto:

```
$ curl -s http://100.108.34.4:9004/actuator/info
{"git-sha":"3c85ab018ea8e204cb2e2768b4b8d93598ac557b"}
```

## Recursos y orquestación

En el `docker-compose.yml` los dos servicios tienen límites de 1 CPU y 512M. La JVM va acotada a
mano (`-Xmx256m`, `UseSerialGC`) y MySQL va con `performance-schema=OFF`, buffer pool de 128M, 50
conexiones y sin binlog, porque el servidor es chico y los dos tienen que caber.

La app depende de la base con `condition: service_healthy`, no con un `depends_on` pelado, así no
intenta conectarse a un MySQL que todavía está inicializando. Los dos tienen healthcheck y
`restart: unless-stopped`. MySQL no publica puertos al host: solo la app expone el 9004.

## Lo que nos costó

- El `mvnw` estaba versionado sin permiso de ejecución y el build de la imagen moría con
  "permission denied".
- El servidor se quedaba sin memoria con MySQL y la JVM por defecto. De ahí salieron los límites.
- Un crítico de Snyk sin parche disponible. En vez de bajar el umbral y dejar pasar todo, fijamos a
  mano las versiones de Jackson que sí tenían arreglo y documentamos la que no.
- La migración a Spring Boot 4 cambia los starters: hay que usar `spring-boot-starter-webmvc` y, en
  los tests de controller, `spring-boot-starter-webmvc-test`.
