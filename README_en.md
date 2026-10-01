# Stellar (slim fork)

> A single-purpose tool: **start a privileged service via Root or ADB to obtain shell access, then use that access to switch the system's default password manager** (credential provider / autofill service).

Language: English | [中文](README.md)

---

## What this is

A **self-use, trimmed-down fork** of Stellar (a deeply customized Shizuku fork). It does exactly two things:

1. **Start the service and obtain shell / root access** — two manual paths:
   - **Root**: drop privileges to shell through `libsu` + `libchid`, then start the service.
   - **Wireless ADB**: pair in-app (mDNS discovery + pairing + connect), then start the service.
2. **Drive password-manager switching with that access** — read/write the three `settings secure`
   keys `credential_service`, `credential_service_primary`, `autofill_service`, with
   **transactional semantics**: pre-write snapshot comparison, per-key writes, read-back
   verification, and reverse-order rollback on failure.

The UI has only two pages: **Home** (start the service) and **PasswordSwitch**, built with
**Miuix** (HyperOS / MIUI design language) in the same shape as the SukiSU manager
(bottom navigation + grouped cards + status tags).

## Build

```bash
./gradlew :manager:assembleDebug     # debug APK
./gradlew :manager:assembleRelease   # release APK (falls back to debug signing without signing.properties)
./gradlew :server:assemble           # privileged server logic only
```

Release artifacts go to `out/apk/`; mappings go to `out/mapping/`.

Signing (`signing.properties`, not committed): `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEYSTORE_ALIAS` / `KEYSTORE_ALIAS_PASSWORD`.

## Module layout

```
manager/    Android app (UI + launcher + ADB stack + password switch), applicationId: roro.stellar.manager
server/     Privileged server logic, runs inside the ADB / Root process
api/        Client SDK submodule (separate Stellar-API repository)
  ├── aidl/        AIDL interfaces (IStellarService / IRemoteProcess / IRemotePtyProcess ...)
  ├── api/         Client API entry points (Stellar.kt / StellarHelper.kt)
  ├── provider/    StellarProvider (ContentProvider that receives the server Binder)
  ├── shared/      Shared constants (StellarApiConstants)
  └── userservice/ UserService framework (hosts the privileged password-switch worker)
```

## Core data flow

1. **Startup**: `manager/startup/` (Root via `Chid`) or `manager/adb/` (ADB) starts `server`.
2. **Binder distribution**: `server/binder/BinderDistributor` + `server/BinderSender` deliver the
   Binder to the manager through a ContentProvider (`manager/StellarManagerProvider`).
3. **Client connection**: `api/provider/StellarProvider` receives the Binder → `api/api/Stellar.kt`.
4. **Password switch**: `PasswordSwitchViewModel` starts `PasswordSwitchService` as a user service
   with shell / root identity; the service performs reads, writes and rollbacks over AIDL.

## Removed from upstream

- The Shizuku compatibility layer (`shizuku/` modules and `server/.../shizuku/`)
- Third-party client authorization (no permission dialog, no grants to third-party apps)
- In-app update check, terminal, command shortcuts, Apps management, Settings, Logs pages
- Boot auto-start / accessibility keep-alive / notification retry / boot script / process daemon
- All locales except English and Simplified Chinese

> The AIDL contract still declares `isShizukuCompatEnabled` / `setShizukuCompatEnabled` /
> `isDaemonEnabled` / `setDaemonEnabled`; the server implements them as harmless no-ops so the
> contract stays compatible with the `api/` submodule.

## License

See [LICENSE](LICENSE).
