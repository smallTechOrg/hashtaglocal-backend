package org.smalltech.hashtaglocal_backend.model;

/**
 * Defines the roles available to a user in the system.
 *
 * <ul>
 *   <li>{@code USER} – standard authenticated user; can report, verify and resolve issues.
 *   <li>{@code ADMIN} – platform administrator; can approve or reject issue actions and manage
 *       localities.
 * </ul>
 */
public enum UserRole {
  USER,
  ADMIN
}
