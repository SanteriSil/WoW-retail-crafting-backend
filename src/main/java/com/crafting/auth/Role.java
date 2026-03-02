package com.crafting.auth;

/**
 * Roles used in the 4-tier authorization model (PLAN.md §4.1).
 *
 * OWNER      — identified by owner.discord-id in config; never stored in allowed_users.
 * ADMIN      — stored in allowed_users with role='ADMIN'; assigned by Owner.
 * ALLOWED_USER — stored in allowed_users with role='ALLOWED_USER'; default for new users.
 *
 * Spring Security authority names are derived as "ROLE_" + name() —
 * e.g. ROLE_OWNER, ROLE_ADMIN, ROLE_ALLOWED_USER.
 */
public enum Role {
    OWNER,
    ADMIN,
    ALLOWED_USER
}
