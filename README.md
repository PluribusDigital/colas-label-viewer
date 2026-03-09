# TTB COLA Label Viewer

A demo application showing what a modern Certificate of Label Approval (COLA) search experience could look like for the TTB myTTB modernization effort.

**This is a demonstration artifact only.** It is not an official TTB product.

---

## What it does

Provides a fast, image-forward search interface over the public TTB COLA Registry — the same data available at the TTB Public COLA Registry portal. Users can search by brand name, fanciful name, class/type, or permit holder, filter by beverage type, and browse label images in a responsive grid.

The design benchmark is Sovos ShipCompliant LabelVision: full-text search across all label fields, visual label browsing, and response times that are meaningfully faster than the legacy Struts-based TTB registry UI.

---

## Data & legal

- **All data is public.** This application uses only the [TTB Public COLA Registry](https://data.ttb.gov/resource/n48b-hpqm.json) available through the data.gov ecosystem under the [Creative Commons Zero (CC0) license](https://creativecommons.org/publicdomain/zero/1.0/). There are no restrictions on its use.
- **Label images are served from TTB's own servers.** This application proxies image requests to `www.ttb.gov` — images are never downloaded, stored, or redistributed.
- **No proprietary data is used.** No internal TTB systems, non-public APIs, or restricted datasets are accessed.

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2, Java 17 |
| Database | PostgreSQL 16 (full-text search via `tsvector`) |
| Schema migrations | Flyway |
| External HTTP | Spring WebFlux `WebClient` |
| Frontend | Vanilla HTML/CSS/JS (no framework) |
| Container | Docker + Docker Compose |

---

## Running locally

**Prerequisites:** Docker Desktop

```bash
git clone <this-repo>
cd colas-label-viewer
docker compose up --build
```

The app starts on [http://localhost:8080](http://localhost:8080).

The database starts empty. Trigger an initial data sync from the TTB registry:

```bash
curl -X POST http://localhost:8080/api/admin/ingest
```

This fetches all public COLA records from `data.ttb.gov` and loads them into the local PostgreSQL database. Subsequent calls are idempotent — records are upserted, not duplicated.

---

## API endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/colas` | Search COLAs. Params: `q`, `beverageType`, `status`, `page`, `size` |
| `GET` | `/api/colas/{ttbId}` | Get a single COLA by TTB ID |
| `GET` | `/api/colas/filters/beverage-types` | List distinct beverage types for filter dropdown |
| `GET` | `/api/image/{ttbId}` | Proxy the label image for a given TTB ID |
| `POST` | `/api/admin/ingest` | Trigger a full sync from data.ttb.gov |
| `GET` | `/actuator/health` | Health check |

---

## Project structure

```
backend/
  src/main/java/gov/treas/ttb/cola/viewer/
    config/          WebClient and MVC configuration
    model/           JPA entity, DTOs, Socrata deserialization record
    repository/      Spring Data JPA with native FTS queries
    service/         Ingestion, search, image proxy
    controller/      REST endpoints
  src/main/resources/
    db/migration/    Flyway SQL migrations
    application.yml
  src/test/          Unit tests (field parsing) + integration tests (Testcontainers, WireMock)

frontend/
  index.html
  css/styles.css
  js/app.js
```

---

## How search works

PostgreSQL full-text search via a `GENERATED ALWAYS AS ... STORED` `tsvector` column combining four fields with weighted relevance:

| Field | Weight |
|---|---|
| Brand name | A (highest) |
| Fanciful name | B |
| Class/type | C |
| Permit holder | C |

Queries use `plainto_tsquery` (safe for raw user input), ranked by `ts_rank_cd` with fallback sort by approval date descending.

---

## For demo purposes only

This application was built to illustrate what a modernized COLA search experience could look like as part of the myTTB platform modernization initiative. It is not intended for production use, does not represent a commitment to any particular design or technology choice, and should not be used as an authoritative source of COLA data. The [official TTB COLA registry](https://www.ttb.gov/cola) remains the authoritative source.
