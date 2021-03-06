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

import io.mifos.anubis.api.v1.domain.AllowedOperation;
import io.mifos.anubis.service.PermissionSegmentMatcher;
import io.mifos.core.api.util.ApiConstants;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.FilterInvocation;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;

/**
 * @author Myrle Krantz
 */
public class ApplicationPermission implements GrantedAuthority {
  public static final String URL_AUTHORITY = "maats_feather";

  private final List<PermissionSegmentMatcher> servletPathSegmentMatchers;

  private final AllowedOperation allowedOperation;

  public ApplicationPermission(
      final String servletPath,
      final AllowedOperation allowedOperation) {
    this.allowedOperation = allowedOperation;
    servletPathSegmentMatchers = PermissionSegmentMatcher.getServletPathSegmentMatchers(servletPath);
  }


  AllowedOperation getAllowedOperation() {
    return allowedOperation;
  }

  @Override public String getAuthority() {
    return URL_AUTHORITY;
  }

  boolean matches(final FilterInvocation filterInvocation, final String principal) {
    return matches(filterInvocation.getRequest(), principal);
  }

  boolean matches(final HttpServletRequest request, final String principal) {
    boolean isSu = principal.equals(ApiConstants.SYSTEM_SU);
    return matchesHelper(
        request.getServletPath(),
        request.getMethod(),
        (matcher, segment) -> matcher.matches(segment, principal, isSu));
  }

  private boolean matchesHelper(final String servletPath, final String method,
                                @Nonnull final BiPredicate<PermissionSegmentMatcher, String> segmentMatcher) {
    final boolean opMatches = allowedOperation.containsHttpMethod(method);
    final String[] requestPathSegments = servletPath.split("/");

    if (servletPathSegmentMatchers.size() == requestPathSegments.length + 1)
      return lastSegmentIsStarSegment(servletPathSegmentMatchers);

    if (servletPathSegmentMatchers.size() > requestPathSegments.length)
      return false;

    if (servletPathSegmentMatchers.size() < requestPathSegments.length)
      return lastSegmentIsStarSegment(servletPathSegmentMatchers);

    final boolean aNonMappableSegmentExistsInServletPath =
        IntStream.range(0, servletPathSegmentMatchers.size())
            .filter(i -> !segmentMatcher.test(servletPathSegmentMatchers.get(i), requestPathSegments[i]))
            .findFirst().isPresent();

    return opMatches && !aNonMappableSegmentExistsInServletPath;
  }

  private static boolean lastSegmentIsStarSegment(
      final List<PermissionSegmentMatcher> servletPathSegmentMatchers) {
    return servletPathSegmentMatchers.get(servletPathSegmentMatchers.size() -1).isStarSegment();
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof ApplicationPermission))
      return false;
    ApplicationPermission that = (ApplicationPermission) o;
    return Objects.equals(servletPathSegmentMatchers, that.servletPathSegmentMatchers)
        && allowedOperation == that.allowedOperation;
  }

  @Override public int hashCode() {
    return Objects.hash(servletPathSegmentMatchers, allowedOperation);
  }
}
