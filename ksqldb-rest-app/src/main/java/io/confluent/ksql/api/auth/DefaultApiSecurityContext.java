/*
 * Copyright 2020 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.api.auth;

import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.security.Principal;
import java.util.Optional;

public final class DefaultApiSecurityContext implements ApiSecurityContext {

  private final Optional<Principal> principal;
  private final Optional<String> authToken;

  public static DefaultApiSecurityContext create(final RoutingContext routingContext) {
    final User user = routingContext.user();
    final Principal principal =
        user == null ? null : new ApiPrincipal(user.principal().getString("username"));
    final String authToken = routingContext.request().getHeader("Authorization");
    return new DefaultApiSecurityContext(principal, authToken);
  }

  private DefaultApiSecurityContext(final Principal principal, final String authToken) {
    this.principal = Optional.ofNullable(principal);
    this.authToken = Optional.ofNullable(authToken);
  }

  @Override
  public Optional<Principal> getPrincipal() {
    return principal;
  }

  @Override
  public Optional<String> getAuthToken() {
    return authToken;
  }

}
