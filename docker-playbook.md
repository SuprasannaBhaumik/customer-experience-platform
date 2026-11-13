# Docker Playbook — Customer Experience Platform

How to build, start, check, debug and tear down the services with Docker Compose.

Compose file: `infrastructure/docker/docker-compose.yml`

All commands assume you run them from the **repository root**. Use this alias so you don't have to type `-f ...` every time:

```bash
alias dc='docker compose -f infrastructure/docker/docker-compose.yml'
```

Every `dc ...` command below is the same as `docker compose -f infrastructure/docker/docker-compose.yml ...`.

### Make the alias permanent

If you only type the alias in one terminal, it is lost when that terminal closes. To keep it, add it to `~/.zshrc`:

```bash
cat >> ~/.zshrc <<'ALIAS'

# Docker Compose shortcut for customer-experience-platform.
# Relative path: only works when run from the repository root.
alias dc='docker compose -f infrastructure/docker/docker-compose.yml'
ALIAS

source ~/.zshrc     # load it in the current terminal; new terminals get it automatically
```

> **Run it from the repo root only.** `infrastructure/docker/docker-compose.yml` is a relative path. Docker resolves it against your current directory, so `dc` only works from the repository root. From any other folder you get `open .../infrastructure/docker/docker-compose.yml: no such file or directory`. Run `cd` to the repo root first.

> **If the alias isn't loaded,** `dc` is the built-in Unix calculator (`/usr/bin/dc`). It does nothing useful, or it prints a strange error. Run `type dc`. It should say `dc is an alias for docker compose ...`.

---

## 1. What runs where

### Inside Docker (from `docker-compose.yml`)

| Compose service | Container name | Port inside the network | Port on your machine | Depends on |
|---|---|---|---|---|
| `profile-db` | `profile-db` | 5432 | **5433** | — |
| `favorites-db` | `favorites-db` | 5432 | **5434** | — |
| `product-db` | `product_db` | 5432 | **5435** | — |
| `order-db` | `order_db` | 5432 | **5436** | — |
| `payment-db` | `payment_db` | 5432 | **5437** | — |
| `redis` | *(generated)* | 6379 | **6379** | — |
| `api-gateway` | `api-gateway` | 8080 | **8080** | all six apps |
| `profile` | `profile` | 8081 | **8081** | `profile-db` (healthy) |
| `product` | `product` | 8082 | **8082** | `product-db` (healthy) |
| `order` | `order` | 8083 | **8083** | `order-db` (healthy) |
| `favorites` | `favorites` | 8084 | **8084** | `favorites-db` (healthy) |
| `payment` | `payment` | 8085 | **8085** | `payment-db` (healthy) |
| `address` | `address` | 8086 | **8086** | — (no database) |

The app containers start with `SPRING_PROFILES_ACTIVE=docker`, so they load `application-docker.yml`, which points them at the DB service names (`profile-db:5432`, etc.) instead of `localhost`.

### On your machine (not in Compose)

| App | Port | Why it matters |
|---|---|---|
| `security/authorization-server` | 9000 | Issues the JWTs. Reads users from the **host** Postgres (`localhost:5432/profile`), not from `profile-db`. |
| `security/authorizer` | 9001 | The gateway calls it for **every** request at `http://host.docker.internal:9001` (set in the gateway's `application-docker.yml`). |

### Things to know before you start

1. **Container names and service names differ for some DBs.** `docker compose ...` commands take the **service** name (`product-db`). Plain `docker ...` commands take the **container** name (`product_db`).
2. **The app services are not reachable from your machine.** Only the gateway (8080), the DBs (5433–5437) and Redis (6379) are published. To reach `profile:8081` directly you must go through `docker compose exec` (section 5).
3. **The app images are Alpine-based and have no `curl`.** They do have BusyBox `wget`, which is what the in-container checks below use.
4. **Actuator** (`/actuator/health`) is on `profile`, `favorites`, `order`, `payment` and `address`. It is **not** on `product` or `api-gateway`.
5. **Every gateway request needs `Authorization: Bearer <token>`.** No header → `401` straight away. With a header, the gateway asks the authorizer on the host; if the authorizer isn't running you get an error instead of your response.

---

## 2. Build

The Dockerfiles only copy `target/*.jar`; they don't compile anything. **Build the jars first**, or the image build fails (or ships an old jar).

```bash
# Build every module's jar (skip tests for speed)
./mvnw clean package -DskipTests

# Or build only one module (plus what it needs)
./mvnw clean package -DskipTests -pl payment -am
```

Check the jars exist:

```bash
ls profile/target/*.jar favorites/target/*.jar product/target/*.jar \
   order/target/*.jar payment/target/*.jar address/target/*.jar \
   gateway/api-gateway/target/*.jar
```

Build the images:

```bash
dc build                 # all images
dc build payment         # one image
dc build --no-cache payment   # rebuild from scratch (ignores cached layers)
```

---

## 3. Start

```bash
# Start everything in the background, rebuilding images from the current jars
dc up -d --build

# Start only the databases + Redis (useful when running the apps from your IDE)
dc up -d profile-db favorites-db product-db order-db payment-db redis

# Start one app and what it depends on (e.g. payment + payment-db)
dc up -d --build payment
```

Then start the security apps **on the host**, in separate terminals:

```bash
./mvnw -pl security/authorization-server spring-boot:run
./mvnw -pl security/authorizer spring-boot:run
```

---

## 4. Check status

```bash
dc ps                    # status of every service; DBs show (healthy) when ready
dc ps -a                 # include containers that have exited
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'   # compact view
dc top                   # processes running in each container
docker stats             # live CPU / memory per container (Ctrl+C to exit)
```

DB health status (from the `pg_isready` healthcheck in the compose file):

```bash
docker inspect --format '{{.State.Health.Status}}' profile-db
docker inspect --format '{{.State.Health.Status}}' favorites-db
docker inspect --format '{{.State.Health.Status}}' product_db
docker inspect --format '{{.State.Health.Status}}' order_db
docker inspect --format '{{.State.Health.Status}}' payment_db
```

Expected: `healthy`. If you see `starting`, wait a few seconds; `unhealthy` → check its logs (section 6).

---

## 5. Health-check each service

### 5.1 From inside the container (direct, bypasses the gateway and auth)

```bash
dc exec profile   wget -qO- http://localhost:8081/actuator/health
dc exec favorites wget -qO- http://localhost:8084/actuator/health
dc exec order     wget -qO- http://localhost:8083/actuator/health
dc exec payment   wget -qO- http://localhost:8085/actuator/health
dc exec address   wget -qO- http://localhost:8086/actuator/health
```

Expected: `{"status":"UP"}`.

`product` has no actuator. Check that it started by reading its logs:

```bash
dc logs product | grep -i "Started"
```

### 5.2 Container-to-container (checks Docker DNS)

This proves one container can reach another by **service name**, the same way `order` calls `http://product:8082`:

```bash
dc exec order wget -qO- http://payment:8085/actuator/health
dc exec order wget -S -qO- http://product:8082/api/v1/products/1   # -S prints the HTTP status
```

Inside a container, `localhost` means *that container itself*, not your machine and not other containers. Use service names.

### 5.3 Through the gateway (the real client path)

Get an access token from the authorization server first; see `readme_authorizer_setup.md` §4 for the token flow. Then:

```bash
export TOKEN='<paste access token>'

# No token → expect 401 (proves the gateway is up and the filter is active)
curl -i http://localhost:8080/api/v1/products/1

# With a token
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/products/1
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/profiles/1
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/orders/1
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/customers/1/favorites
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/payments/1
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/address   # expect "Hello User"
```

Gateway routes (from `gateway/api-gateway/src/main/resources/application-docker.yml`):

| Path | Goes to |
|---|---|
| `/api/v1/profiles/**` | `profile:8081` |
| `/api/v1/products/**` | `product:8082` |
| `/api/v1/orders/**` | `order:8083` |
| `/api/v1/customers/{customerId}/favorites/**` | `favorites:8084` |
| `/api/v1/payments/**` | `payment:8085` |
| `/api/v1/address/**` | `address:8086` |

What the status codes mean at the gateway:

| Status | Meaning |
|---|---|
| `401` | Missing/invalid `Bearer` token |
| `403` | Token is valid but lacks the scope/role for this route |
| `404` | No gateway route matched, or the downstream service returned 404 |
| `5xx` | Authorizer on the host unreachable, or the downstream container is down |

### 5.4 Postgres

```bash
# Is each DB accepting connections?
dc exec profile-db   pg_isready -U profile_user   -d profile_db
dc exec favorites-db pg_isready -U favorites_user -d favorites_db
dc exec product-db   pg_isready -U product_user   -d product_db
dc exec order-db     pg_isready -U order_user     -d order_db
dc exec payment-db   pg_isready -U payment_user   -d payment_db

# Open a psql shell
dc exec payment-db psql -U payment_user -d payment_db

# Handy psql commands once inside:
#   \dt              list tables
#   \d <table>       describe a table
#   select * from <table> limit 10;
#   \q               quit

# Run one query without opening a shell
dc exec payment-db psql -U payment_user -d payment_db -c '\dt'
```

From your machine (needs `psql` installed locally) use the published ports:

```bash
psql -h localhost -p 5433 -U profile_user   -d profile_db     # password: profile_password
psql -h localhost -p 5434 -U favorites_user -d favorites_db   # password: favorites_password
psql -h localhost -p 5435 -U product_user   -d product_db     # password: product_password
psql -h localhost -p 5436 -U order_user     -d order_db       # password: order_password
psql -h localhost -p 5437 -U payment_user   -d payment_db     # password: payment_password
```

### 5.5 Redis

```bash
dc exec redis redis-cli ping            # expect PONG
dc exec redis redis-cli keys '*'        # list cached keys (fine for local dev only)
dc exec redis redis-cli monitor         # watch every command live (Ctrl+C to exit)
```

---

## 6. Logs

```bash
dc logs                         # all services
dc logs payment                 # one service
dc logs -f payment              # follow (like tail -f)
dc logs -f --tail 100 payment order api-gateway   # last 100 lines of several, then follow
dc logs --since 5m payment      # only the last 5 minutes
dc logs payment | grep -i -E "error|exception"
```

---

## 7. Get inside a container

```bash
dc exec payment sh              # shell in an Alpine app container (no bash)
dc exec api-gateway bash        # the gateway image is Ubuntu-based, so bash works
dc exec payment env | sort      # environment variables the app sees
dc exec payment ls -l /app      # confirm the jar that was copied in
```

---

## 8. Network checks

```bash
docker network ls                       # find the compose network (name ends in _default)
docker network inspect docker_default   # containers on it and their IPs
dc exec order nslookup product          # resolve a service name from inside a container
```

The network is named after the folder holding the compose file (`docker`), so it is usually `docker_default`. Confirm with `docker network ls`.

---

## 9. Restart, rebuild, redeploy one service

After a code change in, say, `payment`:

```bash
./mvnw clean package -DskipTests -pl payment -am   # 1. new jar
dc up -d --build payment                           # 2. rebuild image + recreate container
dc logs -f payment                                 # 3. watch it start
```

Other useful forms:

```bash
dc restart payment          # restart the same container (does NOT pick up a new jar)
dc stop payment             # stop without removing
dc start payment            # start a stopped container
dc up -d --force-recreate payment   # recreate even if nothing changed
```

---

## 10. Failure drills

```bash
# Product down → watch order's retry / circuit breaker (Day 13)
dc stop product
curl -i -H "Authorization: Bearer $TOKEN" -X POST http://localhost:8080/api/v1/orders ...
dc logs -f order
dc start product

# Payment DB down → payment should fail cleanly, not hang
dc stop payment-db
dc logs -f payment
dc start payment-db

# Circuit-breaker state for order (health indicator is registered)
dc exec order wget -qO- http://localhost:8083/actuator/health
```

---

## 11. Stop and clean up

```bash
dc stop                 # stop everything, keep containers + data
dc down                 # stop and remove containers + network, KEEP DB data
dc down -v              # also delete the DB volumes (wipes all data)
dc down --rmi local     # also delete the images Compose built
```

Volumes and disk usage:

```bash
docker volume ls | grep db-data     # the five DB volumes
docker system df                    # disk used by images / containers / volumes
docker image prune                  # remove dangling images left by rebuilds
```

---

## 12. Troubleshooting

| Symptom | Likely cause | Check / fix |
|---|---|---|
| `COPY failed: no source files were specified` during build | Jar not built | `./mvnw clean package -DskipTests` |
| Container shows `Exited (1)` | App crashed on start | `dc logs <service>`; often a DB connection or config error |
| `Connection refused` to `localhost:5432` in app logs | `docker` profile not active, or code uses `localhost` | `dc exec <service> env | grep SPRING_PROFILES_ACTIVE` |
| App container stuck in `Created` | Waiting for its DB to become healthy | `dc ps`; `dc logs <db-service>` |
| `port is already allocated` | Something on the host already uses 8080/5433–5437/6379 | `lsof -i :8080` and stop it, or change the left side of `ports:` |
| Gateway returns `401` with a token | Token expired or wrong issuer/audience | Get a new token; check authorizer logs on the host |
| Gateway returns `5xx` for every request | Authorizer on host (9001) not running | Start `security/authorizer`; `curl -i http://localhost:9001` |
| Code change not visible | Old jar or old image | Rebuild the jar, then `dc up -d --build <service>` |
| `No such container: product-db` with plain `docker` | Container name is `product_db` | Use `dc ... product-db` or `docker ... product_db` |
| `Cannot connect to the Docker daemon` / "docker isn't running" | Active context is `colima`, but Colima is stopped | `colima start` (or open Docker Desktop and run `docker context use desktop-linux`); check with `docker info` |
| `dc` does nothing useful, or `no such file or directory` for the compose file | Alias not loaded, or you aren't in the repo root | `type dc`; `source ~/.zshrc`; `cd` to the repo root |

---

## 13. Quick reference

```bash
# Run everything from the repo root (the alias uses a relative path). Alias is in ~/.zshrc.
alias dc='docker compose -f infrastructure/docker/docker-compose.yml'

./mvnw clean package -DskipTests        # build jars
dc up -d --build                        # start everything
dc ps                                   # status
dc logs -f <service>                    # logs
dc exec <service> sh                    # shell in
dc exec payment wget -qO- http://localhost:8085/actuator/health   # health
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/products/1
dc up -d --build <service>              # redeploy one service
dc down                                 # stop (keep data)
dc down -v                              # stop + wipe data
```
