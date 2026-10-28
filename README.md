# Edenest Android

Edenest is an Android client for boarding-school operations. It uses a school-operated HTTPS API and stores a working cache and queued actions on the device.

## Build and test

- Android Studio or Gradle with JDK 17
- Android SDK platform 36
- Build: `gradle assembleDebug`
- Unit tests: `gradle testDebugUnitTest`
- Instrumented tests: `gradle connectedDebugAndroidTest` with an Android 7.0+ device or emulator

The repository currently has no Gradle wrapper. CI uses a pinned Gradle distribution. Do not commit credentials, signing keys, service-account files, or production endpoints.

## School server operations

1. Deploy the companion API behind HTTPS with a valid certificate. Do not enable cleartext traffic.
2. Provision the school endpoint and school identity with an administrator. Staff devices should receive a verified school setup; staff should not be asked to invent a server address.
3. Configure API roles, capabilities, house assignments, active staff status, and supported notification/report endpoints on the server. Client-side filtering is only defense in depth; the server must enforce every read and write scope.
4. Configure encrypted database backups, access controls, retention, and a tested restore procedure on the server. Practice restoration before production use.
5. Before deployment, confirm the API contracts listed in [API contract gaps](./docs/API_CONTRACT_GAPS.md), especially role capabilities, audit retention, rotating student pass tokens, and notification delivery.
6. Set a school-owned support contact and privacy notice before distributing to staff. Replace the operational placeholders in [Privacy notice](./PRIVACY_NOTICE.md) with the school’s approved details.

## Release identity

The Android application ID is `com.edencreativesys.edenest`. Keep it stable for updates to existing installs. Changing it requires an explicitly coordinated migration and signing identity. `versionName` should follow semantic versioning for production releases.

## Data and privacy

The app handles student identifiers, photos, house/bed assignment, guardian contact details, health notes, attendance, exeat and gate records, and staff audit information. Android cloud backup is disabled, and screenshots/screen-recording of the app are blocked. The local Room database is encrypted with SQLCipher; the database key is generated per installation and wrapped by the Android Keystore (see [Privacy notice](./PRIVACY_NOTICE.md)). If the Keystore is lost (factory reset restore or keystore corruption) the app rebuilds the cache from the school server, so deliver the outbox before resetting a device. Encryption on-device does not remove the need for managed devices, screen locks, restricted access, and the school's retention and lost-device procedures.

## Deployment checklist

- Confirm API and image endpoints use HTTPS.
- Verify role and house scoping on the server using accounts from different houses.
- Verify sign-in revocation, session expiry, outbox rejection, and notification permission behavior.
- Confirm student-photo authorization and retention.
- Test upgrades and Room migrations from the current production version.
- Test restore and device-loss procedures with the school data-protection lead.
- Publish a support contact, incident response process, and availability/backup targets approved by the school.
