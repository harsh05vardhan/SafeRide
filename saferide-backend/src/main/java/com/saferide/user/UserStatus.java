package com.saferide.user;

/**
 * Account status. BLOCKED and DELETED users can never authenticate, even with correct
 * credentials - the distinction is who did it and whether it's reversible: BLOCKED is an
 * admin-driver-specific suspension with an existing unblock path
 * ({@code AdminDriverService}); DELETED (Phase - admin user management) is a soft delete -
 * the row and every ride/payment/rating/complaint it's ever been part of stays intact (nothing
 * here is ever hard-deleted, same reasoning {@code AuditLog}/{@code Notification} already
 * document for why those keep plain-long references rather than FKs), it just becomes
 * permanently unable to log in or be matched. There is no "undelete".
 */
public enum UserStatus {
    ACTIVE,
    BLOCKED,
    DELETED
}
