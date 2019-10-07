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

import java.util.Properties;
import java.util.Random;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public final class GenerateMetricCube {

  private static final String[] CLUSTER_NAMES = {"cluster_xyz", "cluster_abc", "cluster_lmn"};
  private static final String[] TOPIC_NAMES = {"topic_1", "topic_2", "topic_3"};
  private static final String[] ENVS = {"dev", "staging", "prod"};
  private static final String[] METRIC_NAMES = {"event_rate", "fetch_latency_ms"};

  private final String bootstrapServers;
  private final Random random;

  private GenerateMetricCube(final String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
    this.random = new Random();
  }

  private static Properties setProduceConsumeProperties(final String clientId,
      final String bootStrapServers) {
    final Properties clientProps = new Properties();
    clientProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapServers);
    clientProps.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
    clientProps.put(ProducerConfig.LINGER_MS_CONFIG, 5000);
    clientProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 128 * 1024);
    clientProps.put(ProducerConfig.SEND_BUFFER_CONFIG, 1024 * 1024);
    clientProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    clientProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return clientProps;
  }

  private void produceEvents(final double produceSleepIntervalMs) {
    final Properties producerProps = setProduceConsumeProperties("metrics-cube-producer",
        bootstrapServers);
    long totalProduced = 0;
    long startMs = System.currentTimeMillis();
    try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
      while (true) {
        final long ts = System.currentTimeMillis();
        final String metricName = METRIC_NAMES[random.nextInt(METRIC_NAMES.length)];
        double metricValue = 0.0;
        if (metricName.equals(METRIC_NAMES[0])) {
          metricValue = 100 + random.nextInt(100);
        } else if (metricName.equals(METRIC_NAMES[1])) {
          metricValue = 10 + 10 * random.nextGaussian();
        }
        final String cluster = CLUSTER_NAMES[random.nextInt(CLUSTER_NAMES.length)];
        final String topic = TOPIC_NAMES[random.nextInt(TOPIC_NAMES.length)];
        final String env = ENVS[random.nextInt(ENVS.length)];

        // explode into a cube.
        for (String clusterValue : new String[] {cluster, null}) {
          for (String topicValue : new String[] {topic, null}) {
            for (String envValue : new String[] {env, null}) {
              final String json = String.format("{"
                  + " \"metrics_name\" : \"%s\","
                  + " \"metrics_value\" : %f,"
                  + " \"dimensions\" : {"
                  + " \"cluster\" : %s,"
                  + " \"topic_name\" : %s,"
                  + " \"env\" : %s"
                  + "},"
                  + " \"ts\" : %d"
                  + "}", metricName, metricValue,
                  clusterValue == null ? null : "\"" + clusterValue + "\"",
                  topicValue == null ? null : "\"" + topicValue + "\"",
                  envValue == null ? null : "\"" + envValue + "\"",
                  ts);
              System.out.println(json);
              producer.send(new ProducerRecord<>("metrics",
                  String.format("%s-%s-%s-%d", clusterValue, topicValue, envValue, ts),
                  json));
            }
          }
        }

        totalProduced++;
        if (totalProduced % 10000 == 0) {
          final long timeElapsedMs = ts - startMs;
          System.out.println("Total produced :" + totalProduced + " in " + timeElapsedMs);
          startMs = System.currentTimeMillis();
        }

        try {
          final long milliSecs = Double.valueOf(produceSleepIntervalMs).longValue();
          final double fraction = produceSleepIntervalMs - milliSecs;
          Thread.sleep(milliSecs, Double.valueOf(fraction * 1000000).intValue());
        } catch (InterruptedException ie) {
          // do nothing and continue on
        }
      }
    }
  }

  public static void main(final String[] args) throws Exception {
    final GenerateMetricCube cube =  new GenerateMetricCube("localhost:9092");
    cube.produceEvents(1000.1);
    System.out.println("Hello world");
  }
}
