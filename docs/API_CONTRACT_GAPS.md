# API changes required for full school operations

The Android client implements best-effort local behavior where possible. These server contracts are needed to meet the complete operational requirements; the app must not pretend local state replaces server authorization.

## Roles, delegation, and scope

- Return documented canonical role codes for assistant housemaster, acting/delegated housemaster, headmaster, and matron (or use custom capability grants independent of the display role).
- Return a stable `house_id` and explicit capabilities for each account, including a read-only headmaster grant. The API must enforce house-level query and mutation scope on every endpoint, not just `auth/me/`.
- Add acting-duty assignment with house, delegate, start/end time, assigning officer, permitted actions, and revocation. Include the active delegate in authorization decisions and audit records.
- Confirm that a role/capability change immediately invalidates or restricts old sessions and queued actions.

## Exeats, handover, and notifications

- Derive the actor from authenticated server identity; accept local actor labels only as display hints, never as authorization evidence.
- Provide role/house-aware notification events for pending exeats, overdue returns, incidents, unmarked roll calls, and rejected queued work.
- For seconds-fast delivery, provide FCM project credentials/configuration, a device token registration/revocation endpoint scoped to user and tenant, and server-side event publishing. The current Android WorkManager poll cannot meet that latency.
- Include notification `id`, category/kind, title, safe preview text, record type/id, creation time, read state, and pagination cursor. Define whether read state is per-user.
- Define quiet-hour/digest preferences server-side if staff need consistent preferences across devices.

## Student cards and gate verification

- Issue short-lived, signed, rotating student pass tokens with expiry, revocation, audience/school ID, and a verification endpoint suitable for the gate workflow. A static `public_token` is not a secure rotating pass.
- Return card issue/expiry dates and stable card number, and expose authorized photo URLs or upload contracts with size/type limits.
- Gate verification must return authoritative pass/student status, revocation, and conflict information; do not rely on an old QR as proof of permission.

## Audit and reporting

- Keep an append-only server audit log for staff and system actions. Include actor ID/role, timestamp, action, record type/id, reason, tenant, and request/idempotency ID. The API must not permit clients to rewrite or delete prior audit events.
- Provide documented exports for audit logs and report formats (CSV/PDF as applicable).
- Add a report-template endpoint returning school-enabled template IDs, labels, filters, and supported export formats. Candidate templates: house occupancy, exeat register, incident register, and termly boarding returns.
- Support date, house, form, status, and template-specific filter contracts.

## App API compatibility

- Confirm the student create/update/photo routes and payload fields in the current client.
- Confirm the local-time reschedule payload contract (the client serializes selected local times as ISO-8601 instants).
- Publish stable error codes for account suspension, permission removal, house/tenant revocation, validation failure, conflict, and retryable server errors.
