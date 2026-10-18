# PlateMate Backend

This is the backend of PlateMate, a mess management system. I made it to handle the daily work of a hostel mess, like who is eating, who paid the fees, what is in the stock, how much money went out and how much came in. Everything is in one Spring Boot app and it talks to a Postgres database (I use Supabase).

The frontend is in a separate repo. This repo is only the API.

## What it can do

- Login, signup with email OTP, forgot password, and JWT tokens (access and refresh)
- Candidates (the people who eat in the mess), with profile photo, joining date, monthly fee, active / left status
- Fee collection for every month, who paid and who is pending
- Daily attendance and daily meals (breakfast, lunch, evening, dinner)
- Weekly menu
- Expenses (vegetables, grocery, gas, etc.)
- Stock with low stock alerts and expiry date alerts
- Staff, their salary, payments and advances
- Party / banquet orders with advance and balance
- College billing (monthly bill for each college, how much received, how much pending)
- Billing summary, monthly profit and loss, and reports (can export to Excel / CSV)

## Tech used

- Java 21
- Spring Boot 3.2.8 (Web, Security, Data JPA, Validation, Mail, Cache)
- PostgreSQL (Supabase)
- JWT (jjwt)
- Maven (wrapper is included, so you don't need to install Maven)
- Gmail SMTP for sending OTP mails

## Before you start

1. **Java 21** is needed. Please don't use a newer Java like 26. The project uses Lombok and the newer Java breaks it, the build gives weird errors like `ClassFormatError` or class not found. Check with `java -version`.
2. A Postgres database. Supabase is the easiest, the free one is enough for testing.
3. A Gmail account with an **App Password** (not your normal password) if you want the OTP mails to work.

## How to run it

**1. Clone the repo**

```bash
git clone https://github.com/Prathmesh-Dubey/platemate-backend.git
cd platemate-backend
```

**2. Make your .env file**

All the secrets are NOT inside the code. They come from a `.env` file which is ignored by git, so it never goes to GitHub.

```bash
cp .env.example .env
```

On Windows you can just copy the file and rename it to `.env`.

Now open `.env` and fill your own values:

| Name | What to put |
|---|---|
| `DB_URL` | Your Postgres JDBC url (for Supabase use the pooler one, session mode, port 5432) |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `MAIL_USERNAME` | Your Gmail address |
| `MAIL_PASSWORD` | The 16 letter Google app password |
| `JWT_SECRET` | A long random string. You can make one with `openssl rand -base64 64` |

Don't put quotes around the values. The file is read like a properties file so the quotes would become part of the value.

If any of these is missing the app will not start, and it will tell you which one is missing. That is on purpose.

**3. Start the app**

Mac / Linux:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The server starts on `http://localhost:8080`.

If you see the message `DATABASE CONNECTION SUCCESSFUL` in the console, then everything is fine.

## About the database tables

The tables are created and updated automatically by Hibernate (`ddl-auto=update`), so you don't need to run any SQL by yourself. Just give an empty database and start the app.

Be careful when you point it to a database that has real data, because the app can change the tables on startup.

## API

Everything starts with `/api`:

| Path | For |
|---|---|
| `/api/auth` | login, register, OTP, profile, change password |
| `/api/candidates` | candidates / members |
| `/api/attendance` | daily attendance |
| `/api/meals` | daily meals |
| `/api/menus` | weekly menu |
| `/api/expenses` | expenses |
| `/api/stock` | stock items, stock transactions, alerts |
| `/api/staff` | staff, payments, advances |
| `/api/parties` | party orders |
| `/api/departments` | departments |
| `/api/college-billing` | college wise monthly billing |
| `/api/billing` | fee collection, billing summary, profit and loss |
| `/api/reports` | reports and exports |

All of them need a JWT token (`Authorization: Bearer <token>`) except `/api/auth/**` and `/uploads/**`.

There is also a `postman.json` file in the repo. Import it in Postman and you get all the requests ready to test.

## Folder structure

```
src/main/java/com/mess/app
  controller/   the REST endpoints
  service/      business logic
  repository/   database queries
  entity/       database tables
  dto/          request and response objects
  config/       security, CORS, JWT filter
  exception/    error handling
  util/         file upload helper
```

## Uploaded files

Profile photos and receipts are saved in the `uploads/` folder on the machine where the app runs. This folder is ignored by git.

Please remember, if you deploy on a platform like Railway or Render, this folder gets deleted on every redeploy. In that case use Supabase storage (the code already supports it, set `storage.type` to `supabase` and give the storage url and key).

## Deploying (some things I learned)

- Set the same names from `.env.example` as environment variables on your host. You don't need a `.env` file there.
- Some hosts block the Gmail SMTP port (587), so the OTP mail will not go. If that happens use a mail service that works over HTTPS, like Resend.
- Supabase direct connection can fail from some hosts because it is IPv6 only. Use the pooler connection string.
- Before going live, turn off the heavy debug logging in `application.properties` (the `DEBUG` / `TRACE` lines and `show-sql`). They make the logs very big and slow.

## Security

- Never commit your `.env`. It is already in `.gitignore`.
- If a password or key ever got pushed by mistake, change it immediately, deleting the commit is not enough.

## Author

Made by Prathmesh Dubey.
