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

package io.confluent.ksql.execution.streams.materialization.ks;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Lists;
import com.google.errorprone.annotations.Immutable;
import io.confluent.ksql.execution.streams.materialization.Locator;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.state.HostInfo;

/**
 * Kafka Streams implementation of {@link Locator}.
 */
final class KsLocator implements Locator {

  private final String stateStoreName;
  private final KafkaStreams kafkaStreams;
  private final Serializer<Struct> keySerializer;
  private final URL localHost;
  private final RoutingFilter availabilityFilter;
  private final RoutingFilter stalenessFilter;

  KsLocator(
      final String stateStoreName,
      final KafkaStreams kafkaStreams,
      final Serializer<Struct> keySerializer,
      final URL localHost,
      final RoutingFilter availabilityFilter,
      final RoutingFilter stalenessFilter
  ) {
    this.kafkaStreams = requireNonNull(kafkaStreams, "kafkaStreams");
    this.keySerializer = requireNonNull(keySerializer, "keySerializer");
    this.stateStoreName = requireNonNull(stateStoreName, "stateStoreName");
    this.localHost = requireNonNull(localHost, "localHost");
    this.availabilityFilter = requireNonNull(availabilityFilter, "availabilityFilter");
    this.stalenessFilter = requireNonNull(stalenessFilter, "stalenessFilter");
  }

  @Override
  public List<KsqlNode> locate(final Struct key) {
    final KeyQueryMetadata metadata = kafkaStreams
        .queryMetadataForKey(stateStoreName, key, keySerializer);

    if (metadata == KeyQueryMetadata.NOT_AVAILABLE) {
      return Collections.emptyList();
    }

    final List<HostInfo> hostList = Lists.newArrayList();
    hostList.add(metadata.getActiveHost());
    hostList.addAll(metadata.getStandbyHosts());
    return hostList.stream().map(this::asNode)
        .filter(node -> availabilityFilter.filter(node, stateStoreName, metadata.getPartition()))
        .filter(node -> stalenessFilter.filter(node, stateStoreName, metadata.getPartition()))
        .collect(Collectors.toList());
  }

  private KsqlNode asNode(final HostInfo hostInfo) {
    return new Node(
        isLocalHost(hostInfo),
        buildLocation(hostInfo)
    );
  }

  private boolean isLocalHost(final HostInfo hostInfo) {
    if (hostInfo.port() != localHost.getPort()) {
      return false;
    }

    return hostInfo.host().equalsIgnoreCase(localHost.getHost())
        || hostInfo.host().equalsIgnoreCase("localhost");
  }

  private URI buildLocation(final HostInfo remoteInfo) {
    try {
      return new URL(
          localHost.getProtocol(),
          remoteInfo.host(),
          remoteInfo.port(),
          "/"
      ).toURI();
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to convert remote host info to URL."
          + " remoteInfo: " + remoteInfo);
    }
  }

  @Immutable
  private static final class Node implements KsqlNode {

    private final boolean local;
    private final URI location;

    private Node(final boolean local, final URI location) {
      this.local = local;
      this.location = requireNonNull(location, "location");
    }

    @Override
    public boolean isLocal() {
      return local;
    }

    @Override
    public URI location() {
      return location;
    }
  }
}
