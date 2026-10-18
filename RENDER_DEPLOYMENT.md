# Deploying PlateMate Backend on Render

This guide takes the backend from this repo to a running Render Web Service, using:

- **Supabase Postgres** for the database (already used)
- **Supabase Storage** for uploaded files (Render's disk is wiped on every deploy and restart)
- **An email API (Resend or Brevo)** for OTP emails (Render Free blocks SMTP ports 25, 465 and 587, so Gmail SMTP cannot work there)

Everything is configured with environment variables. No secret is ever stored in Git.

---

## 1. Checklist of manual steps

Do these once. The rest of the document explains each one.

- [ ] Rotate any credential that was ever committed or shared (database password, Gmail app password, JWT secret)
- [ ] Supabase: create a **private** Storage bucket and copy the project URL and the `service_role` key
- [ ] Email: create a Resend (or Brevo) account, verify a sender, create an API key
- [ ] Render: create the Web Service (Docker) and add the environment variables from section 3
- [ ] Render: set `FRONTEND_URL` to your real frontend address
- [ ] Open `https://<your-service>.onrender.com/api/health` and check it answers `200`

---

## 2. Create the Render Web Service

1. Push this repo to GitHub.
2. In Render: **New +** -> **Web Service** -> connect the GitHub repo.
3. Settings:

| Setting | Value |
|---|---|
| Language / Runtime | **Docker** (Render finds the `Dockerfile` in the repo root) |
| Branch | `main` |
| Instance type | Free is enough to try it. See the notes on Free below |
| Health Check Path | `/api/health` (this endpoint exists, needs no login and returns HTTP 200) |

4. Add the environment variables (section 3), then **Create Web Service**.

Notes:

- Render sets the `PORT` variable and the app listens on it (`server.port=${PORT:8080}`). You do not need to set it.
- The Docker image uses Java 21 and starts with memory-friendly options (`JAVA_OPTS`, see the `Dockerfile`). You can override `JAVA_OPTS` as an environment variable.
- Render Free services go to sleep after a period without traffic and need some time to wake up, and they have a small RAM limit. Check Render's current free-tier limits before relying on it for real users.

---

## 3. Environment variables

Set these in Render under **Environment**. The same names, with safe placeholders, are in `.env.example`.

### Required (the app refuses to start without them)

| Name | Value | Where to get it |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require` | Supabase -> **Connect** -> use the **Session pooler** connection string and write it in JDBC form. Keep `sslmode=require` |
| `DB_USERNAME` | e.g. `postgres.<project-ref>` | Same connection string (pooler user names contain the project ref) |
| `DB_PASSWORD` | database password | Supabase -> Project Settings -> Database -> reset password if unsure |
| `JWT_SECRET` | long random string | Generate: `openssl rand -base64 64` |

### CORS

| Name | Value |
|---|---|
| `FRONTEND_URL` | Your deployed frontend, e.g. `https://platemate.vercel.app` (no trailing slash) |
| `CORS_ALLOWED_ORIGINS` | optional, more origins separated by commas. Wildcard sub domains work: `https://*.vercel.app` |
| `CORS_ALLOW_LOCALHOST` | optional, default `true`. Set `false` in production if you do not need `http://localhost:*` |

### Storage

| Name | Value |
|---|---|
| `STORAGE_TYPE` | `supabase` |
| `SUPABASE_URL` | `https://<project-ref>.supabase.co` (Supabase -> Project Settings -> API -> Project URL) |
| `SUPABASE_STORAGE_API_KEY` | the **service_role** key (same page). **Secret, backend only** |
| `SUPABASE_STORAGE_BUCKET_NAME` | exact name of the bucket from section 4 |
| `SUPABASE_STORAGE_PUBLIC` | optional, `false` (default) for a private bucket, `true` only if the bucket is Public |

### Email

| Name | Value |
|---|---|
| `EMAIL_PROVIDER` | `resend` or `brevo` (not `smtp`, it is blocked on Render Free) |
| `EMAIL_API_KEY` | API key from the provider |
| `EMAIL_FROM` | verified sender, e.g. `noreply@your-domain.com` |
| `EMAIL_FROM_NAME` | optional display name, default `Mess Management System` |

`MAIL_USERNAME` / `MAIL_PASSWORD` are only needed with `EMAIL_PROVIDER=smtp` (local development).

If a required value is missing the service stops at startup and the Render log names the missing variable.

---

## 4. Supabase Storage bucket

The backend uploads files with the Storage REST API, using the **service_role** key.

1. Supabase dashboard -> **Storage** -> **New bucket**.
2. Name it (for example `platemate-uploads`). Leave **Public bucket OFF** (recommended).
3. Optional but good: set a **file size limit** (10 MB matches the app) and allowed MIME types (`image/jpeg`, `image/png`, `image/webp`, `image/gif`, `application/pdf`).
4. Copy the project URL and the `service_role` key from **Project Settings -> API** into the Render variables.

The backend does **not** create buckets. If the name is wrong you will get an error like `Supabase Storage upload failed (HTTP 404). Check that the bucket exists...`.

**How files are served.** The database keeps references like `/uploads/stock-images/stock/tomato_1700000000000.webp` (the same shape as before). The backend answers that URL with a redirect to a short-lived signed URL for a private bucket, or to the public URL for a public bucket. `<img>` tags in the frontend keep working and the key never reaches the browser.

**Security notes.**

- The `service_role` key bypasses Row Level Security and has full access to the project. Keep it only in Render's environment variables. Never put it in the frontend, in Git, in logs or in screenshots.
- Like before, `/uploads/**` needs no login (a browser cannot send a JWT with an `<img>` tag). Anyone who knows an exact file path can open that one file. The bucket itself stays private and cannot be listed.
- The code accepts only images (and PDF for expense receipts), limits size, and builds file names from safe characters, so path traversal such as `../../x` is not possible.

**Moving existing files (manual, nothing is migrated or deleted automatically).**

Files saved on a Render disk before this change are gone after a redeploy. Files that still exist on your computer in `uploads/stock-images/` can be kept:

1. In the bucket, keep the same folder layout: `stock/`, `receipts/`, `profiles/`.
2. Upload the contents of your local `uploads/stock-images/` into the bucket root (dashboard drag and drop, or the Supabase CLI).
3. Database references already look like `/uploads/stock-images/<folder>/<file>`, so they keep working with no database change.
4. Check a few images in the app, and only then remove local copies if you want to.

---

## 5. Email provider

Render Free blocks outbound SMTP, so the backend sends mail through an HTTPS API. Pick one.

### Option A: Resend (`EMAIL_PROVIDER=resend`)

1. Create an account at resend.com.
2. **Domains** -> add and verify a domain you own (add the DNS records Resend shows).
3. **API Keys** -> create a key -> `EMAIL_API_KEY`.
4. `EMAIL_FROM` must use the verified domain, e.g. `noreply@your-domain.com`.

Without a verified domain you can only test with `onboarding@resend.dev`, which Resend limits to your own account email address. Real users will get an error until a domain is verified.

Free plan (as shown on Resend's pricing page when this was written, 2026-10-18): 3,000 emails per month and 100 per day. Check the page again, plans change.

### Option B: Brevo (`EMAIL_PROVIDER=brevo`)

1. Create an account at brevo.com.
2. Add and validate a **sender** (a single email address can be enough) or authenticate a domain.
3. Create an API key (SMTP & API section) -> `EMAIL_API_KEY`.
4. `EMAIL_FROM` = the validated sender address.

Brevo advertises a free plan with 300 emails per day. The Brevo client in this project follows Brevo's public API documentation but was only tested against a local stub, not the live service, so send one test OTP after configuring it.

### What happens when sending fails

The OTP is discarded and the API answers `503` with "We could not send the verification email right now. Please try again in a few minutes." Nothing is marked verified, and registration is rolled back, so the user can try again. The reason (for example `Resend rejected the email (HTTP 403): ...domain is not verified`) is in the Render log. OTP codes and API keys are never logged, and recipient addresses are masked.

---

## 6. CORS

- Browsers may call the API only from `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` and (unless disabled) `http://localhost:*`.
- `*` is rejected at startup because credentials are enabled.
- Preflight `OPTIONS` requests are answered before login checks.
- Write the value exactly like the browser address bar, without a trailing slash: `https://app.example.com`, not `https://app.example.com/`. A missing `https://` stops the startup with a clear message.
- Vercel preview deployments change their URL. Add `https://*.vercel.app` to `CORS_ALLOWED_ORIGINS` only if you want to allow all of them.

---

## 7. Test locally

```bash
cp .env.example .env     # then fill in DB_* and JWT_SECRET
./mvnw spring-boot:run   # Windows: .\mvnw.cmd spring-boot:run   (needs Java 21)
```

Defaults for local work: `STORAGE_TYPE=local` (files in `uploads/`), `EMAIL_PROVIDER=smtp`. If you have no mail account set `EMAIL_PROVIDER=console`: the OTP email is printed in the console and nothing is sent (development only).

Checks:

```bash
curl -i http://localhost:8080/api/health

# CORS preflight from an allowed origin: expect 200 and Access-Control-Allow-Origin
curl -i -X OPTIONS http://localhost:8080/api/candidates \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization"

# From an origin that is not allowed: expect 403 "Invalid CORS request"
curl -i -X OPTIONS http://localhost:8080/api/candidates \
  -H "Origin: https://evil.example.com" \
  -H "Access-Control-Request-Method: GET"
```

To try Supabase Storage from your computer, set the `STORAGE_TYPE` and `SUPABASE_*` variables in `.env`, upload a receipt from the app, and check the object appears in the bucket.

Docker (same image as Render):

```bash
docker build -t platemate-backend .
docker run --rm -p 8080:8080 --env-file .env platemate-backend
```

Run the tests: `./mvnw test`. Note that the existing `AppApplicationTests` starts the whole application, so it needs a reachable database (`DB_*` in `.env`) and will use it.

---

## 8. Troubleshooting

**Database does not connect**
- Use the **pooler** host and keep `?sslmode=require`. The direct `db.<ref>.supabase.co` host is IPv6 only and often unreachable from hosting platforms.
- The pooler user name is `postgres.<project-ref>`, not just `postgres`.
- A password with special characters is fine in `DB_PASSWORD` (it is not part of the URL). Do not add quotes.
- `Could not resolve placeholder 'DB_URL'` means the variable is missing in Render.

**Uploads fail or images do not show**
- `HTTP 404 ... bucket exists`: bucket name differs from `SUPABASE_STORAGE_BUCKET_NAME`, or the bucket was not created.
- `HTTP 401/403 ... SUPABASE_STORAGE_API_KEY`: wrong key. Use the `service_role` key, not the `anon` key. The newer `sb_secret_...` keys have not been tested with this client.
- `STORAGE_TYPE=supabase needs SUPABASE_URL...` at startup: one of the three variables is empty.
- Images return 404 right after switching to Supabase: older files must be uploaded to the bucket (section 4).
- If the frontend builds image links as `http://localhost:8080/uploads/...`, set the frontend's API base URL to the Render URL.
- `HTTP 413`: the file is above the bucket's size limit.

**CORS errors in the browser console**
- Compare the `Origin` shown in the error with `FRONTEND_URL`. They must match exactly (scheme, host, port, no trailing slash).
- Redeploy after changing environment variables.
- The service may still be waking up (Free tier). The first request can fail while it starts.

**Emails do not arrive / registration says "could not send the verification email"**
- Read the Render log line `Failed to send OTP email ... rejected the email (HTTP ...)`.
- `403` from Resend: the sender domain is not verified, or you are still using `onboarding@resend.dev` for someone other than yourself.
- `401`: wrong `EMAIL_API_KEY`.
- Check spam folders and the provider dashboard for bounces.
- `EMAIL_PROVIDER=smtp` on Render Free times out because SMTP is blocked.

**Service does not become healthy**
- Open the Render logs. Missing variables are reported by name. Health check path must be `/api/health`.

---

## 9. Behaviour changes to know about

- **OTP email failure is no longer hidden.** Previously the API answered "OTP sent" even when the email failed. Now it answers `503`, the OTP is removed, and registration is rolled back.
- **OTP codes are no longer written to the log.** Use `EMAIL_PROVIDER=console` for local development.
- **Uploads are validated.** Stock images and profile pictures must be JPG, PNG, WebP or GIF, expense receipts may also be PDF, maximum 10 MB (profile pictures 5 MB). A wrong type on the receipt upload endpoint returns `400` instead of `500`.
- **Logging is quiet by default** (INFO, no SQL values). Set `LOG_LEVEL_APP=DEBUG` or `SHOW_SQL=true` while debugging. Never enable `LOG_LEVEL_SQL_BINDINGS=TRACE` in production: it prints query values, including OTPs.
- **Supabase mode never falls back to local disk.** A failed upload is an error you can see, instead of a file that silently disappears at the next deploy.
- The single CORS configuration lives in `CorsConfig`. The old filter that reflected any `Origin` was removed.
- `ddl-auto=update` is still enabled (unchanged). It is convenient but it lets the app alter tables at startup. Consider `validate` or proper migrations once the schema is stable.
