<!-- README.md — IsoFlow 저장소의 GitHub 소개 문서 (영문) -->

# IsoFlow

**Automatic piping isometric drawings from PCF / IDF, in the browser.**

IsoFlow reads plant piping export files — **PCF** (Piping Component File) and **IDF** (Intergraph
PDS Isometric Data File) — resolves the pipe topology, projects it onto an isometric plane, and
produces a fully dimensioned, annotated isometric drawing that can be exported to **DXF** and
**PDF** together with **BOM**, **cut list** and **weld list**.

In other words: it does what the ISOGEN-class engines do, implemented from scratch.

[한국어 README](README.ko.md) ·
[Requirements](docs/system-requirements.md) ·
[Architecture](docs/architecture.md) ·
[Symbol set](docs/symbol-set.md) ·
[Roadmap](docs/plans/로드맵_수행목록.md)

![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5.7-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-6-646CFF?logo=vite&logoColor=white)
![three.js](https://img.shields.io/badge/three.js-0.171-000000?logo=threedotjs&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-437291?logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)

---

## Features

| | |
|---|---|
| **Input** | PCF (ASCII keyword blocks) and IDF (PDS fixed-column records) parsed into one neutral IR |
| **3D viewer** | The imported piping model in three.js, **Z-up**, orbit controls, axis gizmo |
| **2D viewer** | The generated isometric drawing (orthographic camera, pan/zoom, dark & light theme) |
| **Topology** | Joint merging, connectivity graph (JGraphT), unconnected / bore-mismatch diagnostics |
| **Geometry** | Axis classification, skew resolution, isometric projection, optional length compression |
| **Symbols** | SKEY-driven 2D symbol library, isometric placement by **shear** (not rotation) |
| **Annotation** | Dimension planning, labels with collision avoidance, weld numbering, spool/line data |
| **Sheets** | Automatic multi-sheet splitting with continuation marks, balanced distribution |
| **Overlap handling** | Back-line breaking at screen crossings, detail bubbles for congested symbol clusters |
| **Tables** | BOM, cut list, weld list — computed **before** length compression so quantities stay true |
| **Export** | DXF (R12 ASCII, own writer), PDF (PDFBox), and the three tables as files |
| **Styling** | Paper size, symbol scale, dimension rules, compression, visibility toggles — all overridable per request |
| **Extensibility** | Upload your own symbol set (JSON) and generate against it |
| **i18n** | Korean (default) and English, react-i18next |

## How it works

```
PCF / IDF upload
      │  POST /api/pipelines/import
      ▼
 parser      PcfParser / IdfParser → UnitNormalizer     (coords mm, bore mm)
 model       neutral IR
 topology    JointResolver → PipeGraph → Diagnostics
 geometry    Rebaser (bbox centre → origin)
 scene       Scene3D  ──────────────────────────────►  3D viewer (three.js)

"Generate isometric"
      │  POST /api/isometrics/generate
      ▼
 geometry    AxisClassifier → SkewResolver → IsoProjection
 layout      Router → DimensionPlanner → Annotator
             → CollisionResolver → CrossingBreaker → DetailPlanner → SheetSplitter
 symbol      SkeyTable lookup
 scene       Scene2D per sheet  ─────────────────────►  2D viewer
 table       BOM / cut list / weld list

"Export"
      │  POST /api/isometrics/export?format=dxf|pdf|bom|cutlist|weldlist
      ▼
 export      DxfWriter (R12 ASCII) · PdfRenderer (PDFBox) · table writers
      ▼
 file download
```

Two contracts hold the system together:

- **Scene JSON is the front-end ↔ back-end contract.** `schemas/*.json` is authoritative and
  `src/types/*.ts` mirrors it.
- **`engine/` is pure domain code.** No Spring, no JPA, no servlet, no export dependency — an
  ArchUnit test (`EngineArchitectureTest`) enforces it, so the engine also runs from a CLI, a
  batch job or a plain unit test.

## Tech stack

| Area | Choice |
|---|---|
| Build / framework | Vite 6 + React 19 + TypeScript |
| Styling | Tailwind CSS v4 (`@tailwindcss/vite`), dark / light theme |
| State | Zustand |
| 3D render | three.js 0.171 — `PerspectiveCamera` + `OrbitControls`, **Z-up** |
| 2D render | three.js 0.171 — `OrthographicCamera` + troika-three-text |
| Icons / i18n | react-icons (Material) · react-i18next |
| Backend | Spring Boot 3.3 (Java 21) + PostgreSQL + Flyway |
| Engine | **Java only** — JGraphT (graph) + JTS (geometry) + PDFBox (PDF) + own DXF writer |
| Tests | Vitest (front) · JUnit 5 + AssertJ + ArchUnit + golden snapshots (back) |

## Getting started

### Prerequisites

- Node.js 20+
- JDK 21
- PostgreSQL 14+ *(optional — see `dev-nodb` below)*
- Python 3 *(optional — only for the symbol-set tooling)*

### Frontend

```bash
npm install
npm run dev
```

Opens on **http://localhost:9100** and proxies `/api` to the backend on **8290**.
(9100 / 8290 are chosen so IsoFlow can run next to Verso on 9000 / 8190.)

### Backend

```bash
cd backend && ./gradlew bootRun
```

Serves **http://localhost:8290**. Flyway applies `db/migration/V*.sql` at start-up; JPA never
touches the schema (`ddl-auto: none`). Create the database once:

```bash
createdb -U postgres isoflow
```

No PostgreSQL at hand? Everything except user symbol sets works without it:

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev-nodb'
```

### Scripts

```bash
npm run build              # tsc -b && vite build
npm test                   # vitest — geometry unit tests
npm run symbols:validate   # symbol set integrity check
npm run symbols:sheet      # regenerate docs/symbol-sheet.svg
cd backend && ./gradlew build   # compile + tests (incl. golden regression)
```

Both `npm run build` and `./gradlew build` are expected to pass before any change lands.

## REST API

The current implementation is stateless — the file is uploaded with each request. Once the
persistence layer is wired up these move to id-based calls.

| Method | Path | Parameters |
|---|---|---|
| `POST` | `/api/pipelines/import` | `file` → `{ scene3d, diagnostics }` |
| `POST` | `/api/isometrics/generate` | `file`, `compress?`, `style?` (JSON), `symbolSetId?` → `{ scenes[], diagnostics, fileName }` |
| `POST` | `/api/isometrics/export` | `file`, `format` (`dxf`\|`pdf`\|`bom`\|`cutlist`\|`weldlist`), `style?`, `symbolSetId?` → file |
| `GET` | `/api/styles/default` | → `IsoStyle` defaults |
| `GET` `POST` | `/api/symbol-sets` | list · upload (`file`, `name`, `description?`) |
| `DELETE` | `/api/symbol-sets/{id}` | remove a set |

`scenes` is **always an array** — a drawing may span several sheets.
Errors are language-neutral: `{ code, <interpolation params…>, error }`; translation is the
front-end's job. A malformed `style` is rejected with `INVALID_STYLE` rather than silently
falling back to defaults.

## Project layout

```
src/
  components/{layout,viewer,dialogs}/   AppLayout, Viewer3D, Viewer2D, StyleDialog, …
  viewer/                               Scene3DRenderer, Scene2DRenderer, AxisGizmo
  types/                                scene2d, scene3d, isoStyle  ← mirror of schemas/ & IsoStyle.java
  store/  api/  hooks/  i18n/
schemas/                                Scene JSON contract (authoritative)
backend/src/main/java/co/atools/isoflow/
  engine/                               pure domain — parser, model, topology, geometry,
                                        layout, symbol, scene, table, style, diagnostic
  export/                               dxf, pdf, sheet, table
  pipeline/ isometric/ symbolset/ web/  REST layer (Spring)
backend/src/main/resources/
  db/migration/V{n}__*.sql              schema authority (Flyway)
  engine/symbols-2d.json                2D symbol shape library
  engine/skey-table.json                SKEY → symbol mapping
backend/src/test/resources/
  golden/                               Scene2D snapshots (+ SVG for eyeballing)
  tools/                                Python validation / preview tools
docs/
```

## Notes from building it

A few things that are easy to get wrong when generating isometrics, and how IsoFlow handles them:

- **Coordinates must be rebased.** PCF coordinates are absolute plant coordinates such as
  `5650130.600`. Feed those to three.js (float32) and you get jitter and z-fighting. The engine
  subtracts the bbox centre as `origin` and ships **local millimetres** only.
- **Units are mixed inside one file.** `UNITS-BORE INCH` next to `UNITS-CO-ORDS MM` is normal.
  The parser absorbs it; the internal standard is coordinates in mm, bore in mm.
- **`CENTRE-POINT` means different things per component.** For an ELBOW/BEND it is the *corner*
  where the two pipe axes meet — **not** the arc centre. Using it as the arc centre bulges the
  arc outward and flips its apparent direction. The arc centre is derived as
  `O = C + normalize(t1+t2) · (L / cos(half))`.
- **Isometric symbol placement is a shear, not a rotation.** Place the symbol in the plane that
  contains the pipe axis and project: local → drawing becomes a single affine map. Rotating
  inside the plane makes symbols look like pasted-on plan views.
- **Dimensions are measured between centre-line intersections**, not elbow tangent points.
  Without that filter, PCF drawings drown in repeated elbow-leg lengths and IDF drawings in 17 mm
  steps. Dropped points are folded into their neighbours so run totals stay exact.
- **Quantities are counted before compression.** Dimension planning and BOM aggregation both
  finish before length compression runs — measure afterwards and purchase quantities shrink
  silently.
- **Crossings are not always overlaps.** Two pipes that also meet in 3D are a real branch and must
  not be broken; only pipes that merely overlap on screen get the back one cut, decided via the
  null space of the projection.
- **Detail bubbles enlarge spacing, not symbols.** Scaling a congested region up scales the
  symbols too, leaving the overlap ratio unchanged — positions spread, symbol size stays.
- **Unknown input is preserved, never dropped.** Unrecognised PCF keywords are kept verbatim in
  `attrs`; unresolved values (e.g. the still-undecoded IDF bore field) raise a diagnostic instead
  of being guessed.

The IDF support was reverse-engineered from real samples without a spec — the findings
(0.01 mm coordinate scale, split elbow/tee legs, run-vs-branch detection) are written up in the
[roadmap](docs/plans/로드맵_수행목록.md).

## Status

| Milestone | |
|---|---|
| M0 Scaffolding | ✅ |
| M1 PCF parser + IR + topology | ✅ |
| M2 3D viewer | ✅ |
| M3 Isometric projection + 2D symbols + 2D viewer | ✅ |
| M4 Dimensions + annotation | ✅ |
| M5 BOM + sheet layout + DXF/PDF export | ✅ |
| M6 Sheet splitting, overlap avoidance, IDF, style system | ✅ |

Open questions are tracked at the end of the [roadmap](docs/plans/로드맵_수행목록.md) — chiefly an
official IDF specification, an in-house drawing template / symbol standard, and a commercial
ISOGEN output to benchmark quality against.

## Contributing

- Comments and documentation are written in **Korean**; new files get a one-line header comment.
- Every user-facing string goes through i18n — `ko.json` → `en.json` → `t()`. The two key sets
  must match.
- Drawing constants live only in `engine/style/IsoStyle` (and its front-end mirror
  `src/types/isoStyle.ts`); `IsoStyleContractTest` catches drift.
- Never edit an applied Flyway migration — add a new `V{n}__*.sql`.
- Changing the drawing output breaks the golden snapshots. Review `build/golden-actual/*.svg` by
  eye, then update with `./gradlew test -Dgolden.update=true` if the change was intended.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
