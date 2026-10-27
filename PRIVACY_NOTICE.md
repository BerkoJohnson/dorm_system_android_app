# Student and staff data notice

This is a technical notice for the Android application, not a substitute for the school’s approved privacy notice, data-processing agreement, or legal review. The school must identify its data controller, contact details, lawful basis, retention schedule, and complaint route before production distribution.

## Data handled

The app may display or store student names and identifiers, photos, class/programme, house and bed allocation, guardian contacts, emergency or health notes, attendance and roll-call marks, exeat requests, gate movements, staff account details, and audit events. The exact fields depend on the school API.

## Where data is processed

The app communicates with the configured school server over HTTPS. It retains cached school data and pending operations in a local Room database so approved workflows can continue during network interruptions. Credentials and cached session metadata use Android Keystore-backed encryption. **The Room database itself is not encrypted in this release.** Use device encryption, a strong screen lock, managed-device controls, and prompt lost-device reporting. Android backup is disabled for this app.

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
