# App signing (CI)

`ci-release.keystore` is the canonical release signing key for
Troncooooo/paperless-mobile CI builds. Every build uses the **same
identity** so new APKs install as updates over earlier ones.

- Alias: `paperless-mobile-ci`
- Store password: `paperless-ci-storepass-2026`
- Key password: `paperless-ci-storepass-2026`
- DName: CN=Paperless Mobile CI, OU=Mobile, O=Troncooooo, L=Amsterdam, C=NL
- Validity: 10000 days from 2026-09-15

Keep this file in the repo (or a private mirror). Losing it means losing
the ability to update the installed app without a clean reinstall.
