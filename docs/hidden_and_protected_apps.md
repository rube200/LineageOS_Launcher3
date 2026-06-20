# Hidden & Protected apps (Trebuchet)

LineageOS Trebuchet lets users hide apps from the drawer and require device authentication before opening selected apps from launcher-controlled entry points.

## User guide

### Hidden apps

- Hidden apps are **removed from the app drawer**, drawer search, and **widget picker**.
- They can still be opened from **home screen shortcuts** placed before the app was hidden.
- Enable **Pinch out to open hidden apps** in Trebuchet settings, then quickly spread two fingers on the home screen to open the hidden apps list.
- The hidden apps list opens **without** device authentication. Protected apps listed there still require authentication when tapped.

### Protected apps

- Protected apps require **device lock authentication** (PIN, pattern, password, or fingerprint) when opened through Trebuchet.
- A **secure lock screen must be set** or protection has no effect (choices are saved and apply once a lock is configured).
- Protected launches are gated from: home screen, app drawer, recents/overview, taskbar, split-screen/app pairs, the hidden apps drawer, and Hidden & Protected apps manager.

### Hidden & Protected apps manager

- Open from **Trebuchet settings → Hidden & Protected apps**.
- Device authentication is required to open the manager and again after leaving it (for example via recents).
- Toggle hide/protect per app; tap a row to launch that app.

## Known limitations

| Limitation | Notes |
|------------|--------|
| Settings → Apps → Open | System Settings launches apps directly; Trebuchet cannot intercept. |
| Notification taps | Same as above. |
| Recents thumbnails | Task snapshots remain visible; auth applies on tap-to-open only. |
| Home screen shortcuts | Hiding removes apps from the drawer only, not existing workspace icons. |
| Package-level flags | Hide/protect applies by package name across profiles on the device. |
| Hidden drawer access | Anyone who can unlock the phone and knows the pinch gesture can view the hidden app list. |

## Developer reference

### Architecture

```
Settings / pinch gesture
        │
        ▼
TrustAppsActivity ──► TrustDatabaseHelper (trust_apps_db)
HiddenAppsDrawerActivity      │
                                ▼
HiddenAppsFilter ◄── AllAppsList, WidgetsModel
        │
TrustLaunchHelper ◄── Launcher, QuickstepLauncher, TaskView, taskbar, …
        │
LineageUtils (BiometricPrompt + KeyguardManager)
```

### Key files

| Area | Files |
|------|--------|
| UI | `TrustAppsActivity`, `HiddenAppsDrawerActivity`, `TrustAppsAdapter`, `HiddenDrawerAppsAdapter` |
| Launch gating | `TrustLaunchHelper`, `LineageUtils` |
| Storage | `TrustDatabaseHelper`, `TrustComponent`, `UpdateItemTask` |
| Drawer filtering | `HiddenAppsFilter`, `AllAppsList`, `WidgetsModel` |
| Pinch gesture | `WorkspaceTouchListener` |
| Settings | `launcher_preferences.xml`, `SettingsActivity` |
| Privacy | `TrustActivityIntents` (`FLAG_SECURE`, exclude from recents) |

### Database

- File: `trust_apps_db` (version 2)
- Table: `trust_apps` with unique `pkgname`, `hidden`, `protected` columns
- Flags are per package name, not per user profile

### Auth behavior

| Context | Auth required |
|---------|----------------|
| Open manager | Yes (cancel closes activity) |
| Open hidden drawer | No |
| Launch protected app | Yes (blocked entirely if no secure lock) |
| Open manager without lock | Allowed with toast; protected toggles hidden |

## Test checklist

**Setup:** secure lock enabled; test apps for hide-only, protect-only, and both.

1. **Manager** — auth on open; cancel closes; re-auth after recents; toggles persist; row tap launches with auth when protected.
2. **Hidden** — gone from drawer/search/widgets; visible in pinch drawer when gesture enabled; empty state when none hidden.
3. **Pinch** — only on home in normal state, gesture enabled, quick wide pinch; disabled when toggle off.
4. **Protected** — auth from drawer, home, recents, taskbar, hidden drawer, manager; cancel/fail does not open app.
5. **Bypass (expected)** — no auth from Settings → Apps → Open or notification tap.
6. **No lock** — protection inactive; warning dialog once if protected apps exist.
7. **Regression** — normal launches, long-press home, non-hidden drawer search.

## Future work

- Auth or PIN before opening the hidden apps drawer
- Obscure recents thumbnails for protected/hidden apps
- Remove or warn about workspace shortcuts when hiding
- Per-user (profile) flags in the database
- System-level launch interception (Settings, notifications)
