# Security Policy

## Supported versions

Security fixes are applied to the latest release on `main` and the active `development`
branch. Older tags are not maintained.

| Version | Supported |
| ------- | --------- |
| Latest `main` | Yes |
| `development` | Yes |
| Older tags | No |

## Reporting a vulnerability

**Please do not open a public issue for security vulnerabilities.**

Use GitHub's private vulnerability reporting ("Report a vulnerability" on the Security tab)
or contact the repository owner directly. Include:

- A description of the issue and its impact
- Steps to reproduce, including versions and configuration
- Any suggested mitigation

You can expect an acknowledgement within a few days. We will coordinate a fix and a
coordinated disclosure timeline with you before publishing anything publicly.

## Production hardening checklist

Before exposing SprintJudge to a network:

- Set a strong `SPRINTJUDGE_ADMIN_PASSWORD` (production refuses to boot with the default).
- Run behind TLS with `SPRINTJUDGE_COOKIE_SECURE=true` (the prod default) and terminate TLS
  at nginx or Caddy.
- Pin `SPRINTJUDGE_EXECUTOR_MODE=nsjail` on Linux and verify the jail's bind mounts,
  rlimits, and tmpfs. Never run untrusted code with the `native` executor in production.
- Set explicit `SPRINTJUDGE_CORS_ALLOWED_ORIGINS`; wildcards with credentials are refused.
- Keep `/actuator/*` endpoints other than `/actuator/health` behind authentication.
- Apply network rate limits in the reverse proxy (see `deploy/nginx-sprintjudge.conf`).
