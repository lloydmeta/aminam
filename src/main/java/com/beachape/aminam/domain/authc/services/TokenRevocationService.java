package com.beachape.aminam.domain.authc.services;

import com.beachape.aminam.domain.authc.models.TokenId;
import java.time.Duration;

/// Server-side token revocation. A revoked token id (jti) is rejected on the auth path until its
/// entry expires, giving logout without a session store.
public interface TokenRevocationService {

  void revoke(TokenId jti, Duration ttl);

  boolean isRevoked(TokenId jti);
}
