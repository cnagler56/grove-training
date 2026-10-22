# Grove Salons Training

Private training-video site for the Grove Salons team. Spring Boot backend
(`backend/`, GroveServer) + Next.js frontend (`frontend/`, grove-front), same
stack as retirement-planner and just4ag. Video files live in **Bunny Stream**;
this app only stores titles, categories, who's watched what, and accounts.

## How it works

- **Accounts:** staff sign up with a shared team code (`TEAM_SIGNUP_CODE`) and
  start as Stylist. Emails in `ADMIN_EMAILS` become Owner. When someone leaves,
  disable them on the **Team** page *and* change the team code.
- **Roles:** Owner > General manager > Manager > Lead > Stylist = Receptionist
  > Customer. Owner, GM, and Manager get the manager pages (uploads, Team).
  You can only change people ranked below you and only give roles below your
  own; an Owner can do anything except remove the last Owner. Customers can
  sign in but can't see training videos.
- **Hiring:** anyone can apply at `/apply` (no sign-in; honeypot + 5/hour per
  connection against spam). Only Owners see applicants (the **Applicants** tab,
  with a count of new ones) and only Owners get emailed about them.
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
# Optional: email owners about new job applications (blank = off, just logged)
MAIL_HOST=smtp.gmail.com
MAIL_USERNAME=you@gmail.com
MAIL_PASSWORD=...          # Gmail: a 16-character app password, not your normal one
APP_FRONTEND_URL=http://localhost:3001
```

For Gmail, turn on 2-Step Verification, then create an app password at
myaccount.google.com/apppasswords and use it as `MAIL_PASSWORD`.

```powershell
cd backend
docker compose up -d
.\mvnw.cmd spring-boot:run
```

The frontend needs no settings locally. The browser only talks to the site's
own address; `next.config.ts` forwards `/api/*`, `/me`, `/login`, `/logout`
and `/register` to the backend at `BACKEND_URL` (default `http://localhost:8084`).
That keeps the sign-in cookie first-party, which iPhones/Safari require.

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

Works on the free addresses (`*.vercel.app`, `*.up.railway.app`); add a
custom domain in Vercel later without code changes.

**Backend (Railway):** a project with a **MySQL** database and a service
deployed from this GitHub repo, **Root Directory `backend`** (it builds the
Dockerfile). Generate a public domain for the service under Settings → Networking.
Service variables:

| Var | Value |
|---|---|
| `DB_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}` |
| `DB_USER` | `${{MySQL.MYSQLUSER}}` |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `COOKIE_SECURE` | `true` |
| `TEAM_SIGNUP_CODE` | the code you give staff |
| `ADMIN_EMAILS` | owners' emails, comma-separated |
| `BUNNY_LIBRARY_ID` / `BUNNY_API_KEY` / `BUNNY_TOKEN_KEY` / `BUNNY_CDN_HOSTNAME` | same as `backend/.env` |
| `APP_FRONTEND_URL` | the Vercel URL, so email links point at the live site |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | optional, for notification emails |

Railway sets `PORT` itself; the app reads it. Check `https://<railway domain>/api/health`.

**Frontend (Vercel):** import this repo with **Root Directory `frontend`** and set
`BACKEND_URL` = the Railway URL (e.g. `https://grove-server-production.up.railway.app`).
The project name becomes the address, e.g. `grove-salons.vercel.app`.

**Bunny:** add the Vercel address under the library's Security → *Allowed referrers*.

The live database starts empty: sign up again with the team code (owners via
`ADMIN_EMAILS`), then re-add videos, team details, and schedules.

## Expected cost

At ~15 staff and 2–3 new videos/month: Bunny Stream is about $1–3/mo (storage
plus delivery; Bunny has a ~$1/mo account minimum). Railway (small JVM capped
at 256 MB, plus MySQL) is about $5–10/mo. Vercel Hobby is free.
