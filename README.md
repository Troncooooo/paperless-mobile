# 📱 Paperless Mobile (Flutter) & DevOps Documentation Hub

Welcome to the official repository for **Paperless-Mobile**, a cross-platform Flutter application designed to integrate seamlessly with [Paperless-ngx](https://github.com/paperless-ngx/paperless-ngx). 

This repository now serves as the central hub for:
1. **Mobile Application Development**: Core Flutter architecture, UI, and Feature implementations.
2. **DevOps & Security**: Dockerized testing environments, Paperless backends, and security audits (ISS-1, Custom CA loaders).

---

## 🚀 Quick Start

### 1. Mobile (Flutter)
```bash
flutter pub get
flutter run --release -d <device>
```

### 2. Backend & DevOps (Local Sandbox Environments)
The project includes a comprehensive `docker-compose` configuration to spin up a local Paperless environment.

**Run the Sandbox:**
```bash
chmod +x docker/run-local-sandbox.sh
./docker/run-local-sandbox.sh
```
*This will launch Paperless-ngx and its dependencies (Postgres, Kafka, Redis) locally.*

### 3. Run Automated Linting & Checks
To ensure code integrity across the monorepo:
```bash
# Flutter analysis and linting
cd paperless-mobile && flutter analyze && flutter test --coverage

# Backend security checks (Bandit/PEP8)
cd .. && python3 -m pipenv run bandit --recursive src/
```

---

## 📂 Project Structure & Directory Hierarchy 

| Path | Purpose | Notes |
| :--- | :--- | :--- |
| `lib/core/security/` | Session managers, CA loaders (custom). | Handles all network layer and TLS policies. |
| `docs/internal/SECURITY-ARCHITECTURE.md` | **CRITICAL**: Explains custom CA setup. | Mandatory reading for self-hosted users. |
| `test/core/security/` | Unit tests mocking OS trust stores. | Validates the fix for ISS-1 bypass. |
| `paperless-mobile/` | The primary Flutter application package. | Main logic, features (Login, Assets), and UI. |

---

## 🛡️ Security Enhancements (ISS-1 & Task 02)

Recent updates have focused entirely on hardening how the mobile client interacts with remote Paperless servers:

* **[TASK-1 - ISS-1]: TLS Certificate Validation Fix**: Previously, the app overrode the `badCertificateCallback` universally, making users vulnerable to Man-in-the-Middle (MITM) attacks. We have removed this bypass; SSL/TLS validation is now handled strictly by the OS truststore. 
* **[TASK-2]: In-App Custom CA Loader**: To balance security and usability for internal deployments, we introduced a settings tile where users can upload their own CA certificates directly into `flutter_secure_storage`. These are then dynamically applied to the underlying `Dio` HTTP client session. 

## ⚙️ Automated Pipeline Execution

We utilize an automated pipeline to maintain code quality across all environments:
1. **Pre-commit**: Checks for linting, security flaws, and formatting on Git push.
2. **CI**: Runs Flutter test coverage checks, static analysis (`flutter analyze`), and `bandit` scans on the Python/Django side of the backend.

## 📝 Contribution Guidelines
Please refer to our internal `[TASK-03]` report for the latest testing metrics and code-review requirements before submitting pull requests!
