# Gateway with transactional email

Same compose shape as [`quick-start/`](../quick-start) (gateway + Flightdeck + Postgres) with the transactional email surface from `#835` wired in: pick a transport (`log` / `resend` / `smtp`), invitation + welcome + password-reset emails actually deliver, every send goes through a durability layer with retry + dead-letter-queue + idempotency, and every send + retry + DLQ transition fires an audit event + Prometheus counter.

When to use this stack instead of `quick-start/`:

- You're integrating with a real SMTP relay or Resend account and want to test before pushing to prod.
- You want to see the `email_delivery_log` table fill up so the audit + retry behavior is concrete.
- You're tuning the delivery knobs (retry schedule, DLQ retention, idempotency window) and need a sandbox.

For the full reference — every property, every audit event payload, every Prometheus metric — see [Transactional Email](https://dvarahq.com/docs/flight-deck/transactional-email).

## Quick start

```bash
cp .env.example .env
# Mint secrets (DVARA_ENCRYPTION_MASTER_PASSWORD, the two ACTUATOR keys,
# DVARA_AUDIT_HMAC_SECRET) with: openssl rand -base64 32
# Set DVARA_LICENSE_KEY + OPENAI_API_KEY.

docker compose up -d
docker compose ps          # all 3 services should be healthy
open http://localhost:8090  # first visit → /setup
```

The default `.env.example` ships with `DVARA_FLIGHTDECK_EMAIL_TRANSPORT=log` — emails render to flightdeck's stdout so you can poke at the full flow without external dependencies.

## Verifying email delivery

### Mode 1 — `log` transport (default)

1. Open <http://localhost:8090> — you'll be redirected to `/setup`. Create the platform owner account.
2. Sign in at `/login`. From the Console, go to **Users → Invite user**, pick a role, submit.
3. Find the invitation link in the flightdeck logs:

   ```bash
   docker compose logs dvara-flightdeck | grep -B1 'link:'
   ```

   Sample output:

   ```
   Email (log transport) | to=alice@example.com subject=You've been invited to DVARA htmlChars=4690
     link: http://localhost:8090/register?token=ef726772-b312-4c40-b4a9-3b1ec7adb33f
   ```

   Copy the `http://localhost:8090/register?token=…` URL into your browser and finish the registration flow. Same `grep "link:"` pattern works for `/reset-password?token=…` and email verification — every CTA URL is logged on its own line at INFO. The rendered HTML body itself is **not** at INFO (it's 4-5 KB per send and would dominate the log stream); flip the `LogTransport` logger to DEBUG if you need to inspect the full HTML.

### Mode 2 — `resend` transport

1. Sign up at [resend.com](https://resend.com), create an API key (**Full access** — needs `POST /emails` + `GET /domains`).
2. Verify your sender domain at [resend.com/domains](https://resend.com/domains). The flightdeck pod will **refuse to boot** on a production-class profile if the sender domain isn't verified — the `dev` default profile this stack runs on tolerates it but logs a WARN. For first-day testing, use the sandbox sender `onboarding@resend.dev` which skips verification entirely (delivery is limited to your own Resend-verified email address while in sandbox mode).
3. Edit `.env`:

   ```bash
   DVARA_FLIGHTDECK_EMAIL_TRANSPORT=resend
   DVARA_FLIGHTDECK_EMAIL_FROM=onboarding@resend.dev   # or your verified address
   DVARA_FLIGHTDECK_EMAIL_RESEND_API_KEY=re_xxxxxxxxxxxxx
   ```

4. Restart flightdeck:

   ```bash
   docker compose up -d --force-recreate dvara-flightdeck
   docker compose logs dvara-flightdeck | grep -i 'email transport'
   ```

   Expected line: `Email transport: resend (sender=...)`.

5. Submit a `/users` invite from the Console. The recipient receives the invitation within a few seconds. Watch `docker compose logs dvara-flightdeck | grep -i 'sent\|resend'` for the `Resend send OK` confirmation.

**Common Resend errors** (flightdeck logs them at ERROR with the API response body):

| Resend error | Cause |
|---|---|
| `from_email_not_verified` | Sender domain in `DVARA_FLIGHTDECK_EMAIL_FROM` isn't verified at resend.com/domains. Use `onboarding@resend.dev` for testing. |
| `validation_error: to is invalid` | Recipient address is malformed. |
| `rate_limit_exceeded` | You're sending faster than your Resend plan allows. The retry sweeper will pick it up on the next tick (default 30s). |

### Mode 3 — `smtp` transport

For corporate SMTP, SES (via the SMTP interface), SendGrid relay, or any RFC 5321 server:

```bash
DVARA_FLIGHTDECK_EMAIL_TRANSPORT=smtp
DVARA_FLIGHTDECK_EMAIL_FROM=noreply@yourdomain.com
SPRING_MAIL_HOST=smtp.yourprovider.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=apikey
SPRING_MAIL_PASSWORD=your-smtp-password
# Defaults: STARTTLS on, auth on. Override via SPRING_MAIL_STARTTLS_ENABLE / SPRING_MAIL_AUTH.
```

Restart flightdeck and run the same `/users` invite flow.

## Inspecting the delivery log (recovery + audit)

For day-to-day onboarding under `transport=log`, prefer the log-grep path from [Mode 1](#mode-1--log-transport-default) — internal table names and JSONB key paths are platform-implementation details you shouldn't need to learn just to grab a registration link.

These SQL paths are for recovery / audit work that has no log-side equivalent: tailing the DLQ, replaying a dead-lettered row, answering "did this customer's invitation actually deliver three weeks ago?"

The durability layer writes every send through `dvara_main.email_delivery_log` before calling the transport. Tail it:

```bash
docker compose exec postgres psql -U dvara -c \
  "SELECT id, state, template, recipient, attempt_count, last_error, updated_at
   FROM dvara_main.email_delivery_log
   ORDER BY created_at DESC
   LIMIT 20;"
```

States:

| State | Meaning |
|---|---|
| `SENT` | Transport accepted the message. |
| `PENDING_RETRY` | Transient failure (timeout, 5xx, throttling). Retry sweeper will pick it up at `next_attempt_at`. |
| `DEAD_LETTERED` | Permanent failure (4xx, render error) OR exhausted retries. Operator review. |

A row that is `PENDING_RETRY` for longer than the configured backoff schedule means the sweeper isn't catching up — check `docker compose logs dvara-flightdeck | grep 'retry sweeper'`.

### Replaying a DLQ row

There's no `/v1/admin/email/replay` endpoint by design — replaying a DLQ row is a deliberate operator decision. Edit the row directly:

```sql
UPDATE dvara_main.email_delivery_log
SET state = 'PENDING_RETRY',
    next_attempt_at = NOW(),
    last_error = NULL
WHERE id = 'the-dlq-row-id-here';
```

The retry sweeper picks it up on the next tick.

## Observability

Every send + retry + DLQ transition emits an audit event:

```bash
docker compose exec postgres psql -U dvara -c \
  "SELECT event_type, payload->>'template' AS template,
          payload->>'recipient' AS recipient,
          payload->>'result' AS result,
          payload->>'terminal' AS terminal
   FROM dvara_main.audit_events
   WHERE event_type IN ('EMAIL_SENT', 'EMAIL_FAILED', 'EMAIL_RETRIED')
   ORDER BY created_at DESC
   LIMIT 20;"
```

And Prometheus counters at <http://localhost:8080/actuator/prometheus> (requires `Authorization: Bearer $DVARA_ACTUATOR_METRICS_API_KEY`):

```promql
# Send-success rate
rate(dvara_emails_sent_total{result="SUCCESS"}[5m]) / rate(dvara_emails_sent_total[5m])

# DLQ pressure
rate(dvara_emails_sent_total{result="MAX_ATTEMPTS_EXCEEDED"}[1h])

# Retry-storm signal
rate(dvara_emails_retried_total[5m])
```

## Tuning the durability layer

All ten delivery knobs ship with sensible defaults; uncomment + tune the `DVARA_FLIGHTDECK_EMAIL_DELIVERY_*` block in `.env.example` only if you have a specific reason. See [Transactional Email — Delivery knobs](https://dvarahq.com/docs/flight-deck/transactional-email#delivery-knobs) for the complete reference.

A few common adjustments:

- **Tight SLA / rate-limit pressure** — lower `INITIAL_BACKOFF_MS` to 5000–10000ms and bump `MAX_ATTEMPTS` to 8–10.
- **Compliance keeps DLQ rows longer** — raise `DLQ_RETENTION_DAYS` to 90 or 180. Audit-event rows are independent of this and follow the audit retention policy.
- **Disable the durability layer entirely** — `DVARA_FLIGHTDECK_EMAIL_DELIVERY_ENABLED=false` reverts to fire-and-forget. No DB row, no retry, no DLQ. Useful for tests that don't want a Postgres dep; **not recommended for any production-class install**.

## Stopping

```bash
docker compose down        # stop, keep data
docker compose down -v     # stop and delete postgres volume (drops every email_delivery_log + audit row)
```
