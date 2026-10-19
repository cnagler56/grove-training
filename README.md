# Grove Salons Training

Private training-video site for the Grove Salons team. Spring Boot backend
(`backend/`, GroveServer) + Next.js frontend (`frontend/`, grove-front), same
stack as retirement-planner and just4ag. Video files live in **Bunny Stream**;
this app only stores titles, categories, who's watched what, and accounts.

## How it works

- **Accounts:** staff sign up with a shared team code (`TEAM_SIGNUP_CODE`).
  Emails in `ADMIN_EMAILS` become managers. Managers can turn accounts off
  (when someone leaves), reset passwords, and promote other managers on the
  **Team** page. When someone leaves, turn them off *and* change the team code.
- **Uploading:** a manager fills in title/category/notes and picks a file. The
  backend creates the video in Bunny and hands the browser a signed, 6-hour TUS
  ticket. The browser then uploads straight to Bunny (resumable). The file never
  passes through Railway.
- **Watching:** employees see READY videos grouped by category, and can mark
  them watched. Player URLs are signed and expire after 4 hours (with
  Bunny's embed token auth on), so a copied link stops working.
- **Tracking:** each video page shows managers who has watched it. The Team
  page shows how many videos each person has watched.

## Local dev

Ports: backend **8084**, MySQL **3308** (container `grove-mysql`), frontend **3001**.
These don't collide with AgriServer (8081/3306) or retirement-planner (8083/3307/3000).

All local backend settings live in one gitignored file, `backend/.env`.
docker-compose reads it for the MySQL password, and Spring Boot reads it
for everything else (via spring-dotenv). It holds:

```
MYSQL_ROOT_PASSWORD=...
TEAM_SIGNUP_CODE=...
ADMIN_EMAILS=you@example.com
BUNNY_LIBRARY_ID=...
BUNNY_API_KEY=...
BUNNY_TOKEN_KEY=...
BUNNY_CDN_HOSTNAME=vz-....b-cdn.net
```

```powershell
cd backend
docker compose up -d
.\mvnw.cmd spring-boot:run
```

The frontend needs no settings locally: it talks to `http://localhost:8084`
by default. (In production, set `NEXT_PUBLIC_API_URL` in Vercel.)

```powershell
cd frontend
npm install
npm run dev                          # http://localhost:3001
```

Tests use in-memory H2, so no Docker is needed: `.\mvnw.cmd test`.

## Bunny Stream setup (one time)

1. Sign up at bunny.net, then go to **Stream** and **Add Video Library** ("Grove Training").
2. Library → **API**: copy the *Library ID*, *API Key*, and *CDN Hostname*.
3. Library → **Security**: enable *Embed view token authentication* and copy the key.
   Also add your site's domain under *Allowed referrers*.
4. Set `BUNNY_LIBRARY_ID`, `BUNNY_API_KEY`, `BUNNY_CDN_HOSTNAME`, `BUNNY_TOKEN_KEY` on the backend.

## Deploy

**Backend (Railway):** deploy `backend/` with its Dockerfile, plus a MySQL service. Env:

| Var | Value |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | from Railway MySQL (`jdbc:mysql://…`) |
| `CORS_ALLOWED_ORIGINS` | your Vercel URL, e.g. `https://training.grovesalons.com` |
| `COOKIE_SECURE` | `true` (needed for the cross-site cookie: Secure + SameSite=None) |
| `TEAM_SIGNUP_CODE` | the code you give staff |
| `ADMIN_EMAILS` | managers' emails, comma-separated |
| `BUNNY_*` | from the Bunny setup above |

**Frontend (Vercel):** root directory `frontend`, env `NEXT_PUBLIC_API_URL` = Railway backend URL.

Some browsers (Safari especially) block third-party cookies. If sign-in doesn't
stick on iPhones, put the frontend and backend on subdomains of one domain
(e.g. `training.grovesalons.com` + `api.grovesalons.com`). The cookie then
counts as same-site.

## Expected cost

At ~15 staff and 2–3 new videos/month: Bunny Stream is about $1–3/mo (storage
plus delivery; Bunny has a ~$1/mo account minimum). Railway (small JVM capped
at 256 MB, plus MySQL) is about $5–10/mo. Vercel Hobby is free.
