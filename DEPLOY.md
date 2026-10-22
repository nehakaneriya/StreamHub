# 🚀 VideoStreamApp — Deployment Guide

Production me deploy karne ka complete guide. Setup details ke liye [README.md](README.md) dekhein.

---

## 1. Pehle decide karo — kahan deploy karna hai?

| Option | Best for | Complexity |
|---|---|---|
| **Docker Compose on a VPS** (recommended) | Full control, MySQL + Redis + app sab ek jagah | Medium |
| **PaaS** (Railway / Render / Fly.io) | Quick demo / college submission | Low (but MySQL/Redis alag se chahiye) |
| **Bare server (no Docker)** | Already configured server | High |

> **Note:** Is project me MySQL, Redis, backend aur nginx frontend — **4 services** hain. Isliye Docker Compose sabse aasan hai.

---

## 2. 🔴 Pre-flight checklist (deploy se PEHLE)

### 2.1 Secrets generate karo

```bash
# JWT secret (64+ chars)
openssl rand -base64 48

# Strong admin password (8+ chars, 1 letter, 1 number, 1 symbol)
# Example: "V!de0Str3am#2026"

# Redis password
openssl rand -hex 16

# MySQL password
openssl rand -hex 16
```

> ⚠️ Ye sab `.env` me daalo — **kabhi commit mat karo**. `JWT_SECRET` galat ya chhota hua to app **start hi nahi hoga** (intentional fail-fast).

### 2.2 Domain + HTTPS

Prod me `cookie-secure: true` hai → **HTTPS zaroori hai**, warna refresh cookie browser me store hi nahi hoga aur login fail hoga.

- Domain: `app.yourdomain.com`
- HTTPS: Let's Encrypt (nginx) ya Cloudflare
- Cert ke liye nginx config me `listen 443 ssl` + cert paths

### 2.3 OAuth2 redirect URIs update karo

Naye domain ke saath **dono providers me** update karo:

**Google Cloud Console** → APIs & Services → Credentials → OAuth 2.0 Client ID:
```
https://app.yourdomain.com/login/oauth2/code/google
```

**GitHub** → Settings → Developer Settings → OAuth Apps → Authorization callback URL:
```
https://app.yourdomain.com/login/oauth2/code/github
```

> ❗ Purane `localhost` wale URI hata dena — warna attacker unke through apna account link kar sakta hai.

### 2.4 Mail (SMTP)

Gmail app password ya koi real SMTP chahiye — warna **OTP kabhi nahi aayega** aur registration fail hogi.

---

## 3. Production `.env` (root directory me)

```bash
cp .env.example .env
```

```bash
# ══════════════ DATABASE ══════════════
DB_NAME=videostreamdb
DB_PASSWORD=<yaha-mysql-password>

# ══════════════ REDIS ══════════════
REDIS_PASSWORD=<yaha-redis-password>

# ══════════════ JWT (64+ chars, REQUIRED) ══════════════
JWT_SECRET=<openssl-rand-base64-48-output>
JWT_ISSUER=videostream.com
JWT_ACCESS_TTL_SECONDS=300
JWT_REFRESH_TTL_SECONDS=604800
JWT_REFRESH_COOKIE_NAME=refreshToken
JWT_ADMIN_REFRESH_COOKIE_NAME=adminRefreshToken

# HTTPS ke saath ye ZAROORI hain:
JWT_COOKIE_SECURE=true
JWT_COOKIE_HTTP_ONLY=true
JWT_COOKIE_SAME_SITE=strict
JWT_COOKIE_DOMAIN=app.yourdomain.com

# ══════════════ ADMIN (strong password, policy ke saath) ══════════════
ADMIN_EMAIL=you@yourdomain.com
ADMIN_PASSWORD=<ApnaStrong@Pass123>
ADMIN_SEED_ENABLED=true

# ══════════════ FRONTEND URLS (CORS + OAuth redirect — EXACT match) ══════════════
FRONT_END_URL=https://app.yourdomain.com
FRONT_END_SUCCESS_REDIRECT=https://app.yourdomain.com/oauth/success
FRONT_END_FAILURE_REDIRECT=https://app.yourdomain.com/oauth/failure

# ══════════════ OAUTH2 ══════════════
GOOGLE_CLIENT_ID=<real-id>
GOOGLE_CLIENT_SECRET=<real-secret>
GITHUB_CLIENT_ID=<real-id>
GITHUB_CLIENT_SECRET=<real-secret>

# ══════════════ MAIL (OTP ke liye) ══════════════
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=you@gmail.com
MAIL_PASSWORD=<gmail-app-password>
MAIL_FROM=you@gmail.com

# ══════════════ PROFILE — YE SABSE IMPORTANT HAI ══════════════
SPRING_PROFILES_ACTIVE=prod

# ══════════════ RATE LIMITING ══════════════
RATE_LIMIT_LOGIN_MAX=5
RATE_LIMIT_LOGIN_WINDOW=15
RATE_LIMIT_OTP_MAX=5
RATE_LIMIT_OTP_WINDOW=15
RATE_LIMIT_RESEND_MAX=3
RATE_LIMIT_RESEND_WINDOW=60

# ══════════════ OTP ══════════════
OTP_TTL_SECONDS=300
OTP_RESEND_COOLDOWN_SECONDS=60
OTP_LOG_ENABLED=false       # prod me OTP log KABHI nahi hoga

# ══════════════ MISC ══════════════
LOG_FILE_PATH=logs/videostream.log
# Pehli deployment ke liye:
DDL_AUTO=update
# (baad me wapas `validate` kar dena)
```

---

## 4. Deploy karo

### 4.1 Server setup

```bash
# Ubuntu 22.04 / 24.04 example
sudo apt update && sudo apt install -y docker.io docker-compose-plugin git
sudo systemctl enable --now docker

# Project copy karo
git clone <your-repo> /opt/videostream
cd /opt/videostream
```

### 4.2 Pehli deployment

```bash
# 1. .env banao (upar wala template use karo)
nano .env

# 2. Build + start
docker compose up -d --build

# 3. Logs dekh ke confirm karo sab theek hai
docker compose logs -f backend
```

**Expected log:**
```
[REDIS-HEALTH] Redis is reachable (PONG) — caching, OTP, rate limiting and JWT blacklist all active.
Default admin user created for you@yourdomain.com
Started VideoStreamAppApplication
```

### 4.3 Schema lock (pehli deployment ke baad)

DDL_AUTO=update se tables ban gayi. Ab **wapas `validate`** pe set karo (production me auto-DDL nahi chahiye):

```bash
# .env me
DDL_AUTO=validate

docker compose up -d backend
```

---

## 5. Post-deploy verification

```bash
# 1. Health check
curl https://app.yourdomain.com/api/v1/health
# {"status":"UP","service":"VideoStreamApp",...}

# 2. Container states — sab "healthy"/"running" hone chahiye
docker compose ps

# 3. Logs me koi ERROR to nahi
docker compose logs backend | grep -i error
```

**Browser me manually check karo:**

| # | Check | Kaise |
|---|---|---|
| 1 | Homepage khul raha hai | `https://app.yourdomain.com` |
| 2 | Register + OTP aa raha hai | Naya email → email inbox me 6-digit code |
| 3 | Login karke UserHome khul raha hai | Login → `/UserHome` |
| 4 | **Google login** kaam kar raha hai | Google button → `/UserHome` |
| 5 | **GitHub login** kaam kar raha hai | GitHub button → `/UserHome` |
| 6 | Admin login | `/admin-login` → admin dashboard |
| 7 | Video upload + playback | Upload → thumbnail → play |
| 8 | Logout karke token invalid | Logout → back button → login page |

---

## 6. ⚠️ Common problems

| Problem | Cause | Fix |
|---|---|---|
| **App start nahi hota** | `JWT_SECRET` missing/short | `.env` me 64+ char secret daalo |
| **App start nahi hota** | `ADMIN_PASSWORD` policy fail | 8+ chars, 1 letter, 1 number, 1 symbol |
| **App start nahi hota** | "Unknown database" | MySQL container up nahi — `docker compose logs mysql` |
| **App start nahi hota** | "Schema-validation failed" | `DDL_AUTO=update` karo pehli deployment ke liye |
| **Login ke baad hi logout** | Cookie `Secure` but HTTP pe | HTTPS lagao, ya dev me `JWT_COOKIE_SECURE=false` |
| **CORS error console me** | `FRONT_END_URL` exact match nahi | Origin copy-paste karo, trailing slash mat rakho |
| **Google login redirect error** | Redirect URI mismatch | 2.3 ke hisaab se provider me update karo |
| **OTP email nahi aa raha** | SMTP galat / Gmail app password nahi | `MAIL_*` check karo, Gmail me App Password use karo |
| **Video upload fail (504)** | nginx timeout | `nginx.conf` me 600s already hai — rebuild karo |
| **Login "Too many attempts"** | Rate limit lag gaya | Redis counter clear karo (niche dekho) |
| **Video nahi dikhta** | Volume mount issue | `docker compose logs backend` me "Could not create video directories" dekho |
| **Frontend blank page** | CSP header block kar raha hai | Browser console dekho; `nginx.conf` me `Content-Security-Policy` adjust karo |

**Rate limit turant unlock karna ho to:**
```bash
docker compose exec redis redis-cli -a "$REDIS_PASSWORD" --scan --pattern 'ratelimit:*' \
  | xargs -r -I{} docker compose exec redis redis-cli -a "$REDIS_PASSWORD" DEL {}
```

---

## 7. Update / redeploy

```bash
cd /opt/videostream
git pull
docker compose up -d --build
```

> ⚠️ `docker compose down -v` **mat chalana** — `-v` volumes delete kar deta hai (MySQL data + uploaded videos DONO chale jayenge). Sirf `docker compose down` safe hai.

---

## 8. Backup (production me zaroori)

```bash
# MySQL dump
docker compose exec mysql mysqldump -uroot -p"$DB_PASSWORD" videostreamdb \
  > backup_$(date +%F).sql

# Uploaded videos (volume se)
docker run --rm -v videostreamapp_backend-videos:/data -v $(pwd):/backup alpine \
  tar czf /backup/videos_$(date +%F).tar.gz -C /data .
```

**Cron job bana do** (raat ko 3 baje):
```bash
0 3 * * * cd /opt/videostream && ./backup.sh >> /var/log/videostream-backup.log 2>&1
```

---

## 9. Performance tuning (optional)

`docker-compose.yml` me resources limit karo taaki ek service doosre ko na maarе:

```yaml
  backend:
    deploy:
      resources:
        limits:
          cpus: "2"
          memory: 1G
```

**Video transcoding heavy hai** — FFmpeg 4 resolutions me kaam karta hai. Kam RAM wale server (1GB) pe upload fail ho sakta hai → kam kam **2GB RAM** rakhein.

---

## 10. Security checklist (deploy se pehle tick karo)

- [ ] `SPRING_PROFILES_ACTIVE=prod` (dev nahi!)
- [ ] `JWT_SECRET` real 64+ char random
- [ ] `ADMIN_PASSWORD` strong, `admin123` nahi
- [ ] `JWT_COOKIE_SECURE=true` + **HTTPS** active
- [ ] `JWT_COOKIE_SAME_SITE=strict`
- [ ] `OTP_LOG_ENABLED=false` (prod me OTP log nahi hona chahiye)
- [ ] `FRONT_END_URL` exact origin
- [ ] OAuth redirect URIs naye domain ke saath update
- [ ] `.env` git me nahi hai (`git check-ignore .env` se confirm karo)
- [ ] Redis + MySQL me strong passwords
- [ ] `docker-compose.yml` me `ports` sirf zarurat ke hisaab se expose (MySQL 3307 bahar mat kholo)
- [ ] Backups chal rahe hain
- [ ] `DDL_AUTO=validate` (pehli deploy ke baad)
