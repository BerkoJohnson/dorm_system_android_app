# Student and staff data notice

This is a technical notice for the Android application, not a substitute for the school’s approved privacy notice, data-processing agreement, or legal review. The school must identify its data controller, contact details, lawful basis, retention schedule, and complaint route before production distribution.

## Data handled

The app may display or store student names and identifiers, photos, class/programme, house and bed allocation, guardian contacts, emergency or health notes, attendance and roll-call marks, exeat requests, gate movements, staff account details, and audit events. The exact fields depend on the school API.

## Where data is processed

The app communicates with the configured school server over HTTPS. Sign-in credentials and cached session metadata use Android Keystore-backed encryption. It retains cached school data and pending operations in a local Room database so approved workflows can continue during network interruptions. The Room database is encrypted with SQLCipher (AES-256). The database key is 32 random bytes generated per installation and wrapped with an Android Keystore AES-GCM key; only the wrapped blob is stored on device. If the Keystore cannot yield the master key (for example after a factory-reset restore, or keystore corruption), the previously encrypted cache becomes unreadable and the app rebuilds it from the school server instead of crashing - queued but undelivered changes in that build are lost, so staff should deliver the outbox before resetting a device. **Known limits:** a rooted attacker can read both the database and the wrapped key blob, and device-level encryption of the OS remains the backstop if the keystore falls back. Screenshots and screen recording are blocked while the app is in the foreground. Android backup is disabled for this app.

## Notifications

Local notifications are polled from the school API when connected. Android notification permission is required on Android 13 and later. Notification previews may be visible on a locked screen subject to device settings. Avoid including unnecessary sensitive student details in server notification titles or messages.

## Retention and rights

The school must define how long student records, audit records, queued offline work, and device caches are retained and how staff should report corrections or access requests. Sign-out removes the active credentials and operational cache under the app’s current retention behavior; account-scoped audit events are retained locally and are not displayed to another account. Contact the school administrator for access, correction, retention, or deletion requests.

## Required school details before release

- School/data-controller name and privacy contact
- Approved processing purposes and lawful basis
- Student, guardian, and staff retention schedule
- Lost-device and remote session-revocation procedure
- Server hosting region, processors, backup policy, and restore contacts
- Incident and data-breach reporting route
