/**
 * checklists-sync-function.js
 * Access Control Function (a.k.a. sync function) - SHARED READ-ONLY pattern.
 * Collection: fieldops.checklists   (COLLECTIONS='fieldops/inspections fieldops/checklists')
 *
 * Deployment: Capella App Services (NOT self-managed Sync Gateway).
 *   Role references do NOT take a "role:" prefix here (unlike self-managed Sync Gateway).
 *
 * Model (access matrix):
 *   - Read:   every authenticated App User (inspectors and the manager) via the public "!" channel.
 *   - Create: manager (admin App Role) only.
 *   - Update: manager (admin App Role) only. Inspectors are read-only.
 */
function (doc, oldDoc) {
  var DOC_TYPE = "checklist";

  if (doc.type == DOC_TYPE || (doc._deleted && oldDoc && oldDoc.type == DOC_TYPE)) {

    // Only the manager (admin App Role) may create, edit, or delete - checked first.
    requireRole("admin");

    // Deletes: tombstones need no field validation and inherit the public channel.
    if (doc._deleted) {
      return;
    }

    // Required-field validation
    if (!doc.title) throw ({ forbidden: "title is required" });

    // Immutable fields on update
    if (oldDoc && !oldDoc._deleted) {
      if (doc.type !== oldDoc.type) throw ({ forbidden: "type cannot be changed" });
    }

    // Route to the public channel - all authenticated users receive it (read-only).
    channel("!");
  }
}
