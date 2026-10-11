# BorrowBox

A web platform for borrowing and lending items across campus.

- `apps/api`: REST API under `/api/v1`. Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security, MySQL 8, Flyway, Maven.
- `apps/web`: React frontend in plain JavaScript (JSX), HTML and CSS. It uses Axios for all API calls, plus Vite, Tailwind, React Router, React Query and Recharts. It is an npm workspace, run from the repo root.

The requirements are in `docs/BorrowBox_Project_Documentation.pdf`. For status and decisions see [PROGRESS.md](PROGRESS.md), for database notes see [docs/DB_NOTES.md](docs/DB_NOTES.md), and for conventions see [CLAUDE.md](CLAUDE.md).

## Quick start

Prerequisites: JDK 21, Docker, and Node 20 or later.

```bash
docker compose up -d mysql            # MySQL 8 on :3306 (borrowbox/borrowbox)
cd apps/api && ./mvnw spring-boot:run # API on :8080 (dev profile: console emails, demo data)
npm install && npm run dev            # web on :5173, calls http://localhost:8080/api/v1
```

To run the API in a container as well, use `docker compose up --build`. Configuration is listed in `apps/api/.env.example` (API) and `apps/web/.env.example` (`VITE_API_URL`, `VITE_ALLOWED_EMAIL_DOMAIN`).

| Command | Purpose |
|---|---|
| `cd apps/api && ./mvnw -B verify` | Compile the API and run its tests (Testcontainers MySQL, or `TEST_DB_URL`) |
| `npm run dev` | Vite dev server for the web app (:5173) |
| `npm run lint` | ESLint (JS/JSX) for the web app |
| `npm run build` | Production build of the web app |
| `npm run preview` | Serve the production build locally |

Seeded logins (dev profile): `admin@campus.edu / Admin12345`, and `alice|bob|carol|dan@campus.edu / Demo12345`.

## Deploying (Render + Vercel)

- **Backend on Render:** `render.yaml` is a Blueprint that creates MySQL 8 (a private service with a disk; image in `deploy/mysql`) and the API (Docker, with a disk for uploads).
- **Frontend on Vercel:** set the root directory to `apps/web` and set `VITE_API_URL=/api/v1`. `apps/web/vercel.json` proxies `/api` and `/uploads` to the Render API, so the refresh cookie stays first-party.

