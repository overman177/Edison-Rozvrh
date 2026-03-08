# EdisonRozvrh Architecture

This project now follows a simple package split under:

- `cz.tal0052.edisonrozvrh.app`
- `cz.tal0052.edisonrozvrh.ui.*`
- `cz.tal0052.edisonrozvrh.data.*`
- `cz.tal0052.edisonrozvrh.widget`
- `cz.tal0052.edisonrozvrh.map`

## Package responsibilities

### `app`
- Android entry point (`MainActivity`).
- Shared app-level models and helpers currently used across app + widgets.

### `ui`
- Compose UI only.
- `ui/auth`: login/loading flow.
- `ui/home`: main tabs and schedule/results screens.
- `ui/design`: centralized visual config (`UiColorConfig`).
- `ui/theme`: Material theme setup.

### `data`
- `data/parser`: Edison HTML/JSON parsing.
- `data/repository`: network calls and orchestration of downloaded data.

### `widget`
- Home screen widget providers + RemoteViews service.

### `map`
- VSB map URL resolving and room/building mapping helpers.

## Rules to keep it clean

1. UI code stays in `ui/*` (no network/parsing there).
2. Edison parsing logic stays in `data/parser`.
3. HTTP/download logic stays in `data/repository`.
4. Widget-specific rendering and update logic stays in `widget`.
5. Do not add new feature files back into the root package.
6. Keep imports one-way where possible:
   - `ui` -> `app`/`data`/`map`
   - `data` does not depend on `ui`
   - `widget` can use shared app models/helpers
