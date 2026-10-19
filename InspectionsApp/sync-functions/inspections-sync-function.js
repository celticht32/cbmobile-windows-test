/**
 * inspections-sync-function.js
 * Access Control Function (a.k.a. sync function) - ADMIN-ASSIGNS pattern.
 * Collection: fieldops.inspections   (COLLECTIONS='fieldops/inspections fieldops/checklists')
 *
 * Deployment: Capella App Services (NOT self-managed Sync Gateway).
 *   Role references do NOT take a "role:" prefix here (unlike self-managed Sync Gateway).
 *
 * Cast:
 *   - App users       = field inspectors (e.g. "bob"), each owns the inspections assigned to them.
 *   - Admin App Role  = "admin", held by the inspection manager App User ("manager").
 *   - Record          = an inspection (type "inspection"), owned via the "assignee" field.
 *
 * Model (access matrix):
 *   - Read:   the assigned inspector (their own) + the manager (all, via the "admin" channel).
 *   - Create: manager only (admin-assigns).
 *   - Update: the assigned inspector (their own) + manager.
 *             Reassign / edit a "closed" inspection / delete: manager only.
 *
 * Admin design: (A) named admin channel. Docs are routed to ADMIN_CHANNEL and the
 * "admin" App Role is granted that channel ONCE via the Admin REST API
 * (collection_access -> admin_channels:["admin"]) by setup-capella.sh.
 */
function (doc, oldDoc) {
  var DOC_TYPE      = "inspection";
  var ADMIN_ROLE    = "admin";   // principal: who may create/reassign/delete (no role: prefix on App Services)
  var ADMIN_CHANNEL = "admin";   // channel: where docs are routed for admins (design A)

  if (doc.type == DOC_TYPE || (doc._deleted && oldDoc && oldDoc.type == DOC_TYPE)) {

    // Stable identity from oldDoc on update/delete - never trust doc for these.
    var assignee = oldDoc ? oldDoc.assignee : doc.assignee;

    // Deletes: manager only. Tombstone inherits channels from the previous revision.
    if (doc._deleted) {
      requireRole(ADMIN_ROLE);
      return;
    }

    // Creates: manager only (admin-assigns) - inspectors cannot create.
    if (!oldDoc) {
      requireRole(ADMIN_ROLE);
    }

    // Required-field validation
    if (!doc.title)     throw ({ forbidden: "title is required" });
    if (!doc.status)    throw ({ forbidden: "status is required" });
    if (!doc.assignee)  throw ({ forbidden: "assignee is required" });

    // Immutable fields + state checks on update
    if (oldDoc && !oldDoc._deleted) {
      if (doc.type !== oldDoc.type)             throw ({ forbidden: "type cannot be changed" });
      if (doc.createdAt !== oldDoc.createdAt)   throw ({ forbidden: "createdAt cannot be changed" });
      if (doc.assignee !== oldDoc.assignee)     requireRole(ADMIN_ROLE);   // only the manager reassigns
      if (oldDoc.status === "closed")           requireRole(ADMIN_ROLE);   // closed = locked for inspectors
    }

    // Routing
    channel(assignee);         // the inspector's private channel (their username)
    channel(ADMIN_CHANNEL);    // the manager's channel (granted on the admin App Role)

    // No access() calls: the inspector's own channel is granted at App User creation and
    // the admin channel is granted statically on the admin App Role.

    // Read/write gate: the assigned inspector OR anyone holding the admin channel.
    requireAccess([assignee, ADMIN_CHANNEL]);
  }
}
