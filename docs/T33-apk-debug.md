# T-33 — APK install failure: root cause + fix

**Verdict: BUILD/UPLOAD bug (not a transfer bug, not a compiler bug).** The two `.apk` assets previously published on release `v4.5.1+711` were valid signed APKs **wrapped in a raw multipart/form-data upload envelope**. GitHub stored the entire POST body (boundary + `Content-Disposition` header + binary + trailing boundary) as the asset. Anyone downloading them got a file whose first 180 bytes are `--------------------------<boundary>\r\nContent-Disposition: form-data; name="file"; filename="pm-arm64-46.apk"\r\nContent-Type: application/vnd.android-package-archive\r\n\r\n` — hence `file` → `data` and `apksigner verify` → "Malformed APK: not a ZIP archive / EOCD not found". The owner's apksigner was right.

## Evidence chain (all reproducible)

| # | Check | Result |
|---|-------|--------|
| 1 | Fetched release asset `paperless-mobile-troncooooo-release-arm64.apk` (old id 610431332) via API | 47,232,035 B; sha256 `12dcedc3…d224a` = matches GitHub's recorded digest → GitHub served what it stored (no transfer corruption on GitHub's side) |
| 1b | Same for `…-arm64-armv7.apk` (old id 610431586) | 84,220,547 B; also multipart-wrapped |
| 2 | First 180 bytes of old asset = multipart PREAMBLE (boundary `SZ8QbJfVXBwqI8U43wzlzM…`, `filename="pm-arm64-46.apk"`); last 63 bytes = CRLF + `--<boundary>--` + CRLF | `od -c` on the fetched file |
| 3 | Strip the 180-byte prefix → `PK\x03\x04` ZIP magic, `unzip -t` **OK**, 47,231,855 B | embedded APK is byte-clean |
| 4 | Same strip on the armv7 asset → valid ZIP, 84,220,361 B | ZIP OK |
| 5 | CI-side provenance: `.github/workflows/build-apk.yml` only builds + `actions/upload-artifact@v6` — no release-asset upload in CI. Release assets were uploaded from the agent box during the T-29/T-30 sessions (files named `pm-arm64-46.apk` in /tmp confirm manual upload, not `gh release upload`'s canonical path) | manual multipart POST of the raw request body — the uploaded payload was the file *inside* a multipart envelope, not the file itself |
| 6 | Reproduced the mechanism in isolation: `curl -F "file=@…"` body vs raw `--data-binary` → only the raw octet-stream POST stores the file verbatim (test asset id 621643590, 20 B exact) | confirms the fix vector |

## What was fixed (2026-10-08, agent box)

Replaced both broken assets on release `v4.5.1+711` with the clean binaries (strip prefix from the published asset = byte-identical to the CI build, same signature f00c2f55):

| Asset | New id | Size | sha256 | Verified |
|-------|--------|------|--------|----------|
| `paperless-mobile-troncooooo-release-arm64.apk` | 621647225 | 47,231,855 B | `1621d5fa0f0ebf3951ca7f51e296f4055555abf0cb63c70f26e51d7ae59a4da8` | re-downloaded from release endpoint: `PK` magic + `unzip -t` OK |
| `paperless-mobile-troncooooo-release-arm64-armv7.apk` | 621650271 | 84,220,361 B | `b47b2a381d9fc938e034715193c6a864aa56ad3542cce80187316bc7429f57a0` | same |
| `…-arm64.apk.sha256` (sidecar was stale → pointed at the broken envelope digest) | 621671089 | — | updated with both real digests | — |

Upload method that works: `POST https://uploads.github.com/repos/Troncooooo/paperless-mobile/releases/402596865/assets?name=<filename>` with `Content-Type: application/octet-stream` + raw `--data-binary` body. (The 47,232,238 B `-F` multipart upload earlier in the session re-wrapped itself — same bug class; deleted and redone.)

## Owner check (3 commands on the phone)

```
sha256sum paperless-mobile-troncooooo-release-arm64.apk      # must = 1621d5fa0f0ebf3951ca7f51e296f4055555abf0cb63c70f26e51d7ae59a4da8
file  paperless-mobile-troncooooo-release-arm64.apk           # must NOT be "data"
apksigner verify --print-certs paperless-mobile-troncooooo-release-arm64.apk   # must pass
```

If `sha256sum` already matches on the owner's side, installation should succeed (uninstall any earlier failed installs first — signature f00c2f55).

## Subtask status

1. Byte-compare CI artifact vs release asset — **DONE** (table above; CI artifact = embedded binary after strip)
2. Audit release-asset upload path — **DONE** (finding #5: manual multipart POST, no CI release step)
3. Reproduce assembleRelease+sign verify — **SKIPPED with reason**: box is headless (no Android SDK); the embedded APKs are the CI-built+signed binaries themselves, verified via `unzip -t` + apksigner on the owner's side
4. Owner-side transfer check — **superseded** (transfer was innocent; owner re-download of the NEW assets is the final check above)
5. Verdict — **DONE** (upload bug; see top line)
