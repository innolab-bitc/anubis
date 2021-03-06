/*
 * Copyright 2017 The Mifos Initiative.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mifos.anubis.security;

import com.google.gson.Gson;
import io.jsonwebtoken.*;
import io.mifos.anubis.annotation.AcceptedTokenType;
import io.mifos.anubis.api.v1.TokenConstants;
import io.mifos.anubis.api.v1.domain.TokenContent;
import io.mifos.anubis.api.v1.domain.TokenPermission;
import io.mifos.anubis.provider.InvalidKeyTimestampException;
import io.mifos.anubis.provider.TenantRsaKeyProvider;
import io.mifos.anubis.service.PermittableService;
import io.mifos.anubis.token.TokenType;
import io.mifos.core.lang.ApplicationName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Myrle Krantz
 */
@Component
public class TenantAuthenticator {
  private final TenantRsaKeyProvider tenantRsaKeyProvider;
  private final String applicationNameWithVersion;
  private final Gson gson;
  private final Set<ApplicationPermission> guestPermissions;

  @Autowired
  public TenantAuthenticator(
      final TenantRsaKeyProvider tenantRsaKeyProvider,
      final ApplicationName applicationName,
      final PermittableService permittableService,
      final @Qualifier("anubisGson") Gson gson) {
    this.tenantRsaKeyProvider = tenantRsaKeyProvider;
    this.applicationNameWithVersion = applicationName.toString();
    this.gson = gson;
    this.guestPermissions
        = permittableService.getPermittableEndpointsAsPermissions(AcceptedTokenType.GUEST);
  }

  AnubisAuthentication authenticate(
      final @Nonnull String user,
      final @Nonnull String token,
      final @Nonnull String keyTimestamp) {
    try {
      final JwtParser parser = Jwts.parser()
          .requireSubject(user)
          .requireIssuer(TokenType.TENANT.getIssuer())
          .setSigningKey(tenantRsaKeyProvider.getPublicKey(keyTimestamp));

      @SuppressWarnings("unchecked") Jwt<Header, Claims> jwt = parser.parse(token);

      final String serializedTokenContent = jwt.getBody().get(TokenConstants.JWT_CONTENT_CLAIM, String.class);
      final TokenContent tokenContent = gson.fromJson(serializedTokenContent, TokenContent.class);
      if (tokenContent == null)
        throw AmitAuthenticationException.missingTokenContent();

      final Set<ApplicationPermission> permissions = translatePermissions(tokenContent.getTokenPermissions());
      permissions.addAll(guestPermissions);

      return new AnubisAuthentication(token,
          jwt.getBody().getSubject(), permissions
      );
    }
    catch (final JwtException e) {
      throw AmitAuthenticationException.invalidToken();
    } catch (final InvalidKeyTimestampException e) {
      throw AmitAuthenticationException.invalidTokenKeyTimestamp("tenant", keyTimestamp);
    }
  }

  private Set<ApplicationPermission> translatePermissions(
      @Nonnull final List<TokenPermission> tokenPermissions)
  {
    return tokenPermissions.stream()
            .filter(x -> x.getPath().startsWith(applicationNameWithVersion))
            .flatMap(this::getAppPermissionFromTokenPermission)
            .collect(Collectors.toSet());
  }

  private Stream<ApplicationPermission> getAppPermissionFromTokenPermission(final TokenPermission tokenPermission) {
    final String servletPath = tokenPermission.getPath().substring(applicationNameWithVersion.length());
    return tokenPermission.getAllowedOperations().stream().map(x -> new ApplicationPermission(servletPath, x));
  }
}
