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

import io.mifos.anubis.annotation.AcceptedTokenType;
import io.mifos.anubis.api.v1.RoleConstants;
import io.mifos.anubis.service.PermittableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author Myrle Krantz
 */
@Component
public class GuestAuthenticator {
  private Set<ApplicationPermission> permissions;

  @Autowired
  public GuestAuthenticator(final PermittableService permittableService) {
    this.permissions = permittableService.getPermittableEndpointsAsPermissions(AcceptedTokenType.GUEST);
  }

  AnubisAuthentication authenticate(final String user) {
    if (!user.equals(RoleConstants.GUEST_USER_IDENTIFIER))
      throw AmitAuthenticationException.invalidHeader();

    return new AnubisAuthentication(null, RoleConstants.GUEST_USER_IDENTIFIER, permissions);
  }
}
