# Local dev environment for GroveServer.
#
# Usage:
#   1. Copy this file to `dev-env.ps1` (which is gitignored).
#   2. Fill in your actual values below.
#   3. Dot-source it into your PowerShell session BEFORE running mvnw:
#        . .\dev-env.ps1
#      The leading dot + space matters — it loads the variables into
#      your current shell instead of a child process.
#   4. Then:
#        .\mvnw.cmd spring-boot:run

# -- Java (only needed if JAVA_HOME isn't set permanently) --
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.7-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# -- Database (matches docker-compose.yml: MySQL on host port 3308) --
$env:DB_URL      = "jdbc:mysql://localhost:3308/grove_training?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USER     = "root"
$env:DB_PASSWORD = "change_me"   # match backend/.env's MYSQL_ROOT_PASSWORD

# -- CORS / frontend origin (frontend runs on 3001 so it can sit beside retire-front) --
$env:CORS_ALLOWED_ORIGINS = "http://localhost:3001"

# -- Team sign-up --
# Code employees enter to create an account. Change it when someone leaves.
$env:TEAM_SIGNUP_CODE = "grove-team"
# Managers' emails — these accounts get the Admin pages (upload, team).
$env:ADMIN_EMAILS = "rebeccanagelmg@gmail.com"

# -- Bunny Stream (bunny.net → Stream → your library → API) --
$env:BUNNY_LIBRARY_ID   = ""
$env:BUNNY_API_KEY      = ""
$env:BUNNY_TOKEN_KEY    = ""   # Security → Embed view token authentication
$env:BUNNY_CDN_HOSTNAME = ""   # e.g. vz-abc123-456.b-cdn.net
