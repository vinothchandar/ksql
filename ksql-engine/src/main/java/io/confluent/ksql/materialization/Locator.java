/*
 * Copyright 2019 Confluent Inc.
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

package io.confluent.ksql.materialization;

import java.net.URI;
import java.util.List;
import org.apache.kafka.connect.data.Struct;

/**
 * Type used to locate on which KSQL node materialized data is stored.
 *
 * <p>Data stored in materialized stores can be spread across KSQL nodes. This type can be used to
 * determine which KSQL server stores a specific key.
 */
public interface Locator {

  /**
   * Locate which KSQL node stores the supplied {@code key}. only includes nodes that are currently
   * considered alive.
   *
   * <p>Implementations are free to return an empty list if the location is not known at
   * this time or all known locations are currently unavailable</p>
   *
   * @param key the required key.
   * @return alive owning nodes, if any, beginning with the node with most recent value.
   */
  List<KsqlNode> locate(Struct key);

  /**
   * Reports a failure to fetch state from a remote node
   *
   * @param node node to which proxying failed
   */
  void reportFailure(KsqlNode node);

  interface KsqlNode {

    /**
     * @return {@code true} if this is the local node, i.e. the KSQL instance handling the call.
     */
    boolean isLocal();

    /**
     * @return The base URI of the node, including protocol, host and port.
     */
    URI location();
  }
}
