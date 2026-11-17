## Summary

<!-- What changed and why? Link the issue: Closes #123 -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor / maintenance
- [ ] Documentation
- [ ] Question bank content

## Verification

- [ ] `make lint`
- [ ] `mvn test`
- [ ] `make test-frontend`
- [ ] `make check-ui`
- [ ] `node scripts/check-bank.mjs` (if bank files changed)
- [ ] Playwright E2E (if UI changed)
- [ ] `mvn verify` (100% JaCoCo gate)

## Checklist

- [ ] One logical change per commit; conventional commit messages
- [ ] No new compiler warnings; Java 8 syntax rules respected in `src/main/java`
- [ ] No `any` in TypeScript; no raw SQL
- [ ] Tests added or updated for changed behaviour
- [ ] Docs updated where behaviour or configuration changed
- [ ] No branded names, copied questions, or protected content
