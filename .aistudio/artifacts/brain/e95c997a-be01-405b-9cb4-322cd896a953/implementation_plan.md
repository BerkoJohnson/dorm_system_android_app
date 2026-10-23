# SHS Boarding & Dormitory Management System

A high-reliability, offline-first Android application designed for Ghanaian Senior High School (SHS) administrators, housemasters, and campus gate security officers to manage dormitories, roll-call attendance, exeat permissions, and student gate movements.

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your responses, this implementation plan adopts an **offline-first local Room architecture** with a **configurable API sync engine**, allowing full on-campus operation in low-connectivity dormitory areas while syncing with central school servers when connected.

- **Confirmed Architecture**: Offline-first Room SQLite persistence with transactional sync queue and configurable remote Django API base URL.
- **Confirmed Role Coverage**: Multi-role portal with instant role switching and distinct workflows for **Housemasters** (roll call, dorm capacity, exeats), **Gate Security Officers** (camera QR scanner, movement authorization, gate logs), and **School Admins** (dashboard analytics, student directory, room allocations, audit logs).
- **Confirmed Verification Engine**: In-app Camera barcode/QR scanner paired with encrypted/verifiable digital student ID cards.

---

## 1. Overview & Core Concept

### What It Does
The SHS Boarding & Dorm Management app equips school staff with mobile tools to handle daily boarding operations directly on Android smartphones or tablets:
1. **Dormitory & Bed Occupancy**: Real-time room capacity, student bed allocations, vacancy monitoring, and housemaster roster views.
2. **Roll Call & Curfew Attendance**: Rapid nightly and morning dorm roll calls with automated flags for students on approved exeat, sick bay, or unexcused absence.
3. **Exeat Lifecycle Management**: Digital submission, housemaster/senior housemaster approval, departure sign-out, return reconciliation, and overdue breach tracking.
4. **Gate Security & QR Movement**: Live camera QR scanning at school gates to verify student identity and exeat status before logging departures or arrivals.
5. **Digital ID Cards**: Offline-renderable digital student identity cards with verifiable dynamic QR codes, house badges, and guardian contacts.
6. **Analytics & Audit**: Summary metrics on boarding counts, exeat volumes, roll-call completion, and movement audit logs.

### Target Audience & Personas
- **Housemasters & Housemistresses**: Manage specific boarding houses (e.g., Aggrey House, Guggisberg House, Ndewura Jakpa House), conduct evening dorm inspections, and review exeat requests.
- **Gate Security Officers**: Guard school checkpoints, scan student QR IDs during departures/arrivals, prevent unauthorized exits, and flag overdue returns.
- **School Administrators / Senior Housemasters**: Oversee school-wide boarding capacity, cross-house movements, systemic attendance trends, and configuration.

---

## 2. User Experience & Visual Design

### Key User Flows

#### A. Housemaster Daily Roll Call
1. Open the **Roll Call** tab; select House and Dormitory (e.g., "House 3 - Nelson Dorm Room B").
2. The roster loads showing bed numbers, student names, and existing statuses (e.g., tagged `ON EXEAT` if approved).
3. Tap rapid toggles: **Present**, **Absent**, **Sick Bay**, or **Permitted**.
4. Review summary badge (e.g., 28 Present, 1 Sick Bay, 1 Absent) and tap **Submit & Lock Roll Call**.
5. Session is saved to Room, marked for sync, and locked with an audit timestamp.

#### B. Exeat Request & Approval Flow
1. Student or housemaster drafts an exeat with category (Medical, Academic, Family/Emergency), destination, and return deadline.
2. Housemaster reviews pending exeats in the **Exeat Hub**, inspecting previous exeat history.
3. Tap **Approve** (with optional note/condition) or **Reject** (with mandatory reason).
4. Approved exeat updates the student's status immediately across the system and unlocks gate clearance.

#### C. Gate Officer Camera QR Verification
1. Gate Officer opens the **Gate Check** screen and aims the camera at the student's ID card or mobile QR code.
2. The app decodes the student ID and exeat pass, verifying cryptographic signature and valid time window.
3. A large green clearance banner appears: **"APPROVED TO EXIT - Medical Exeat - Return by 18:00"** (or red alert if unapproved or expired).
4. Tap **Log Departure** (or **Log Return**); timestamp and gate officer badge are recorded instantly.

#### D. Admin Dashboard & Room Allocations
1. View overall boarding statistics: Total Boarders, Occupancy Rate %, Active Exeats, and Flagged Absentees.
2. Inspect Houses and Dorms to reassign rooms, detect over-capacity hazards, or manage house rosters.
3. View the system **Audit Log** and sync status with the central school server.

### Visual Identity & Theme
- **Aesthetic Direction**: Authoritative, clean institutional academic styling tailored for Ghanaian Senior High Schools.
- **Color Palette**:
  - Primary: Deep Academic Navy (`#0D2040`)
  - Accent / Gold: Warm Ghanaian Ochre Gold (`#D99B26` / `#E6A834`)
  - Surface Background: Crisp Ivory Off-White (`#F7F8FA`)
  - Success / Clearance: Forest Emerald (`#1B7A43`)
  - Danger / Alert: Crimson Rust (`#C02A2A`)
  - Warning: Deep Amber (`#D97706`)
- **Typography**: Clean, highly legible sans-serif hierarchy with bold tabular numeric displays for roll calls and capacity counters.
- **Micro-interactions & Tactile Feedback**:
  - High-contrast clearance banners on QR scans with haptic vibration feedback.
  - Sticky bottom action bars with clear 48dp touch targets for fast single-handed dormitory inspections.
  - Synchronized badge counters on navigation items for pending exeat requests.

---

## 3. Key Product Decisions & Trade-Offs

### 1. Offline-First Room Storage + Configurable API Sync
- **Chosen Approach**: Room Database acts as the single source of truth on the Android device. A background Sync Manager maintains a FIFO change queue and synchronizes records whenever network connectivity is established.
- **Why**: Dormitories and campus gates often suffer from intermittent Wi-Fi or cellular dead zones. Operations such as roll call and gate clearance cannot be blocked by network lag.
- **Sync Architecture**: Supports both standalone offline operation (with synthetic initial school seed data) and live REST endpoint synchronization via user-configured base URL and auth token.

### 2. Embedded Camera QR Scanner with ML Kit / CameraX
- **Chosen Approach**: Built-in Android CameraX with Google ML Kit Barcode Scanning for fast, zero-delay on-device QR recognition. Fallback manual search is also provided for damaged physical ID cards.
- **Why**: Security guards need sub-second scan response times at campus gates without requiring external handheld scanning hardware.

### 3. Role-Based Navigation & Audit Trail
- **Chosen Approach**: Top-level role selector and security boundary that customizes available tabs and action privileges, while recording the acting officer's ID and timestamp on all status changes.
- **Why**: In typical SHS environments, one tablet or smartphone may be shared among duty masters or gate staff during shift rotations.

---

## 4. Technical Architecture & Data Strategy

### System Component Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│ ┌────────────────┐ ┌────────────────┐ ┌──────────────────────────────┐ │
│ │  Housemaster   │ │  Gate Officer  │ │       Admin Dashboard        │ │
│ │  • Roll Call   │ │  • QR Scanner  │ │  • Boarding Analytics       │ │
│ │  • Exeat Hub   │ │  • Movement Log│ │  • Student Directory & Rooms │ │
│ │  • Dorm Beds   │ │  • ID Card View│ │  • Audit Logs & Sync Config  │ │
│ └───────┬────────┘ └───────┬────────┘ └──────────────┬───────────────┘ │
└─────────┼──────────────────┼─────────────────────────┼─────────────────┘
          │                  │                         │
┌─────────▼──────────────────▼─────────────────────────▼─────────────────┐
│                           View Models                                  │
│  • DashboardViewModel       • RollCallViewModel                        │
│  • ExeatViewModel           • GateMovementViewModel                   │
│  • StudentViewModel         • SyncSettingsViewModel                   │
└────────────────────────────┬───────────────────────────────────────────┘
                             │
┌────────────────────────────▼───────────────────────────────────────────┐
│                         Repository Layer                               │
│  • BoardingRepository       • ExeatRepository                          │
│  • AttendanceRepository     • MovementRepository                       │
│  • SyncRepository (Network & Queue Dispatcher)                         │
└──────────────┬─────────────────────────────────────────┬───────────────┘
               │                                         │
┌──────────────▼────────────────┐       ┌────────────────▼───────────────┐
│     Room SQLite Database      │       │     Remote Django REST API     │
│  • Students, Houses, Rooms    │◄─────►│  • /api/v1/students/           │
│  • AttendanceRecords          │ Sync  │  • /api/v1/exeats/             │
│  • ExeatRequests              │ Queue │  • /api/v1/gate-movements/     │
│  • GateMovements              │       │  • /api/v1/roll-call/          │
│  • SyncQueue & AuditLogs      │       │  • /api/v1/houses/             │
└───────────────────────────────┘       └────────────────────────────────┘
```

### Data Model & Entities

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│    House     │1    N │     Room     │1    N │   Student    │
│  • id        ├───────┤  • id        ├───────┤  • id        │
│  • name      │       │  • houseId   │       │  • studentId │ (e.g. SHS/2024/0481)
│  • gender    │       │  • dormName  │       │  • fullName   │
│  • capacity  │       │  • capacity  │       │  • classForm │ (SHS 1, 2, 3)
└──────────────┘       └──────────────┘       │  • houseId    │
                                              │  • roomId     │
                                              │  • bedNumber  │
                                              │  • qrCodeKey  │
                                              │  • status     │ (IN_DORM, EXEAT, SICK)
                                              └───────┬──────┘
                                                      │
                       ┌──────────────────────────────┼──────────────────────────────┐
                       │1                           1 │1                           1 │
                       ▼N                             ▼N                             ▼N
              ┌──────────────────┐           ┌──────────────────┐           ┌──────────────────┐
              │ AttendanceRecord │           │   ExeatRequest   │           │   GateMovement   │
              │  • id            │           │  • id            │           │  • id            │
              │  • studentId     │           │  • studentId     │           │  • studentId     │
              │  • date          │           │  • category      │           │  • exeatId       │
              │  • sessionType   │           │  • departureTime │           │  • movementType  │ (DEPART, RETURN)
              │  • status        │           │  • expectedReturn│           │  • timestamp     │
              │  • housemasterId │           │  • status        │           │  • officerId     │
              │  • notes         │           │  • approvedBy    │           │  • remarks       │
              └──────────────────┘           └──────────────────┘           └──────────────────┘
```

---

## 5. Phased Implementation Strategy

- **Phase 1: Database & Seed Engine**: Implement Room Entities, DAOs, Database, and pre-seeded Ghanaian SHS dataset (Houses, Dorms, Students with authentic IDs and QR tokens).
- **Phase 2: Repository & ViewModel Architecture**: Implement Reactive repositories exposing `Flow<T>`, handling status updates, roll-call submissions, and sync queues.
- **Phase 3: Multi-Role Dashboard & Dorm Management**: Create the role-switching header, overview metrics cards, House & Dorm capacity viewer, and Bed allocation manager.
- **Phase 4: Roll Call & Attendance System**: Build the interactive dormitory roll-call screen with rapid status toggling, automatic exeat tagging, summary locks, and historical attendance review.
- **Phase 5: Exeat Lifecycle Hub**: Implement exeat list with status filtering (Pending, Approved, Departed, Overdue), detail view, creation dialog, and approval/rejection workflows with reasons.
- **Phase 6: Gate Security, QR Scanner & Digital ID Cards**: Build the integrated QR camera scanner with clearance feedback banner, manual search fallback, gate movement logging, and verifiable student digital ID cards.
- **Phase 7: Reports, Audit Trail & API Sync Configuration**: Implement exportable boarding reports, system audit logs, and the Django REST API sync settings screen with connectivity testing.
- **Phase 8: Build Verification & Polish**: Verify full compilation with `compile_applet`, test all workflows, and set platform metadata in `metadata.json` and `strings.xml`.
