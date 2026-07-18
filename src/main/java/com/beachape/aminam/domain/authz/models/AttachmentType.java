package com.beachape.aminam.domain.authz.models;

/// Where a policy can be hung: an identity point (a membership) or a resource point (a database).
public enum AttachmentType {
  MEMBERSHIP,
  DATABASE
}
