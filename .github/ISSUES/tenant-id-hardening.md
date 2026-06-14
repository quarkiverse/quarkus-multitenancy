---
title: "Tenant ID hardening: validate length and pattern and handle violations"
labels: ["enhancement", "area(http)"]
---
Summary (EL):
Προτείνουμε βελτιώσεις στην επικύρωση του tenant identifier για HTTP requests. Το branch feat/16-tenant-id-hardening εισήγαγε validator αλλά χρειάζεται διευκρινίσεις/βελτιώσεις.
Proposed changes:
- Επιστρέφει 400 αντί 401 για συντακτικά άκυρα tenant ids (configurable).
- Cache της compiled Pattern στο TenantIdValidator.
- Tests για όρια/περίπτωση απενεργοποίησης validation.
- Docs & changelog update.
Acceptance criteria:
- Unit/integration tests pass.
- Docs updated.
