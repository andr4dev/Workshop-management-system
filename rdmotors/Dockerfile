# ═══════════════════════════════════════════════════════════════════════════════
#  RD MOTORS — la imagen que corre en la nube (spec 0011, fase 1)
# ═══════════════════════════════════════════════════════════════════════════════
#
# En desarrollo son dos cosas corriendo: el servidor en un puerto y la pantalla en
# otro. Aqui es UNA SOLA: la pantalla se construye y se mete adentro del servidor.
#
# Tres etapas, y la razon de cada una:
#
#   1. pantalla  Node construye la pantalla. Node NO llega a la imagen final.
#   2. servidor  Maven construye el jar, con la pantalla adentro.
#   3. (final)   Solo Java para correr, mas pg_dump para bajar la copia de la base.
#
# Que las herramientas de construccion no viajen a produccion no es limpieza: cada
# programa instalado en la imagen final es algo que hay que actualizar cuando le
# salga un problema de seguridad.
#
#   docker build -t rdmotors .
#   docker run --rm -p 8082:8080 --env-file .env.nube rdmotors
#
# El 8082 a proposito: el 8080 es del car-wash y el 8081 el del desarrollo.

# ── 1. La pantalla ────────────────────────────────────────────────────────────
FROM node:22-alpine AS pantalla
WORKDIR /pantalla

# Primero las dependencias, solas: mientras package.json no cambie, Docker se salta
# esta capa y no vuelve a bajar nada.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ── 2. El servidor ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS servidor
WORKDIR /codigo

# Igual que arriba: los pom primero, para que las dependencias queden en su capa.
COPY pom.xml ./
COPY domain/pom.xml domain/
COPY pos/pom.xml pos/
RUN mvn -B -e dependency:go-offline -DskipTests || true

COPY domain/ domain/
COPY pos/ pos/

# La pantalla construida entra al jar. 'static' es donde Spring la busca sola.
COPY --from=pantalla /pantalla/dist/ pos/src/main/resources/static/

# Sin pruebas: las pruebas se corren antes de construir la imagen, no adentro. Aqui
# necesitarian levantar Postgres dentro del build, que es lento y fragil.
RUN mvn -B -DskipTests package

# ── 3. Lo que corre ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

# pg_dump, para que el administrador pueda bajar la copia de la base (spec 0011,
# RF-010).
#
# EL CLIENTE NUNCA PUEDE SER MAS VIEJO QUE EL SERVIDOR. Al reves si: un pg_dump 18
# saca copias de un servidor 17 sin problema. Por eso aqui va el 18, que cubre
# tanto a Neon (18.6, el de produccion) como al Postgres 17 del computador.
#
# Paso de verdad al desplegar: el contenedor llevaba el 17 y Neon resulto ser 18.6.
# pg_dump se nego en seco -- "aborting because of server version mismatch" -- y se
# descubrio al bajar la primera copia, no antes. Si Neon sube a 19, esto hay que
# subirlo tambien: la senal es ese mismo mensaje en Ajustes > Respaldo.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
 && install -d /usr/share/postgresql-common/pgdg \
 && curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc \
      -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
 && echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] \
https://apt.postgresql.org/pub/repos/apt $(. /etc/os-release && echo $VERSION_CODENAME)-pgdg main" \
      > /etc/apt/sources.list.d/pgdg.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends postgresql-client-18 \
 && apt-get purge -y gnupg \
 && apt-get autoremove -y \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=servidor /codigo/pos/target/pos-*.jar rdmotors.jar

# Sin esto la aplicacion corre como root: si alguien logra ejecutar algo adentro,
# lo hace con todos los permisos.
RUN useradd --system --create-home --uid 10001 rdmotors
USER rdmotors

# 8080 es lo que esperan casi todas las nubes; el perfil 'nube' lee el puerto de la
# variable PORT, asi que el proveedor manda.
EXPOSE 8080

# La JVM, pensada para el plan gratis de las nubes (512 MB y una fracción de CPU):
#
#   TieredStopAtLevel=1   solo el compilador rapido. El optimizador pesado tarda mas en compilar de lo que
#                         ahorra en un POS con poca carga, y con 0,1 de CPU la compilacion es lo que mas demora.
#   UseSerialGC           un recolector de un solo hilo: menos memoria y nada de hilos de GC peleando por la CPU.
#   Xss512k               pila de hilo mas chica; Tomcat abre varios.
#   CICompilerCount=1     un solo hilo compilando.
#
# Medido con --memory=512m --cpus=0.1 contra Neon: el arranque paso de 258 s a 139 s y la memoria de 359 a 313 MB.
# En una maquina con mas CPU se pueden pisar poniendo la variable JAVA_TOOL_OPTIONS en el proveedor.
ENV JAVA_TOOL_OPTIONS="-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xss512k -XX:CICompilerCount=1"

ENTRYPOINT ["java", "-jar", "/app/rdmotors.jar"]
