/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.callibrity.mocapi.aws;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionStore;
import com.callibrity.mocapi.session.dynamodb.DynamoDbMcpSessionStore;
import com.callibrity.mocapi.session.dynamodb.DynamoDbMcpSessionStoreAutoConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.journal.dynamodb.DynamoDbJournalAutoConfiguration;
import org.jwcarman.substrate.journal.dynamodb.DynamoDbJournalSpi;
import org.jwcarman.substrate.mailbox.dynamodb.DynamoDbMailboxAutoConfiguration;
import org.jwcarman.substrate.mailbox.dynamodb.DynamoDbMailboxSpi;
import org.jwcarman.substrate.notifier.sns.SnsNotifier;
import org.jwcarman.substrate.notifier.sns.SnsNotifierAutoConfiguration;
import org.jwcarman.substrate.spi.JournalSpi;
import org.jwcarman.substrate.spi.MailboxSpi;
import org.jwcarman.substrate.spi.Notifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class AwsStarterAutoConfigurationIT {

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
          .withServices(
              LocalStackContainer.Service.DYNAMODB,
              LocalStackContainer.Service.SNS,
              LocalStackContainer.Service.SQS);

  private static DynamoDbClient dynamoDbClient;

  @BeforeAll
  static void initAwsResources() {
    URI endpoint = localstack.getEndpoint();
    StaticCredentialsProvider credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
    Region region = Region.of(localstack.getRegion());

    dynamoDbClient =
        DynamoDbClient.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(credentials)
            .region(region)
            .build();

    createSessionsTable();
  }

  private static void createSessionsTable() {
    dynamoDbClient.createTable(
        CreateTableRequest.builder()
            .tableName("mocapi_sessions")
            .keySchema(
                KeySchemaElement.builder()
                    .attributeName("session_id")
                    .keyType(KeyType.HASH)
                    .build())
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("session_id")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .billingMode("PAY_PER_REQUEST")
            .build());
  }

  @AfterAll
  static void cleanup() {
    if (dynamoDbClient != null) {
      dynamoDbClient.close();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class AwsInfrastructureConfig {

    @Bean(destroyMethod = "")
    DynamoDbClient dynamoDbClient() {
      return dynamoDbClient;
    }

    @Bean(destroyMethod = "")
    SnsClient snsClient() {
      return SnsClient.builder()
          .endpointOverride(localstack.getEndpoint())
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
          .region(Region.of(localstack.getRegion()))
          .build();
    }

    @Bean(destroyMethod = "")
    SqsClient sqsClient() {
      return SqsClient.builder()
          .endpointOverride(localstack.getEndpoint())
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
          .region(Region.of(localstack.getRegion()))
          .build();
    }

    @Bean
    ObjectMapper objectMapper() {
      return JsonMapper.builder().build();
    }
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(AwsInfrastructureConfig.class)
        .withPropertyValues(
            "mocapi.session.dynamodb.verify-table=true",
            "mocapi.session.dynamodb.ensure-ttl=false",
            "substrate.mailbox.dynamodb.auto-create-table=true",
            "substrate.journal.dynamodb.auto-create-table=true",
            "substrate.notifier.sns.auto-create-topic=true")
        .withConfiguration(
            AutoConfigurations.of(
                DynamoDbMcpSessionStoreAutoConfiguration.class,
                DynamoDbMailboxAutoConfiguration.class,
                DynamoDbJournalAutoConfiguration.class,
                SnsNotifierAutoConfiguration.class));
  }

  @Test
  void dynamoDbBackedSessionStoreIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class))
                  .isInstanceOf(DynamoDbMcpSessionStore.class);
            });
  }

  @Test
  void dynamoDbBackedMailboxSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(MailboxSpi.class);
              assertThat(context.getBean(MailboxSpi.class)).isInstanceOf(DynamoDbMailboxSpi.class);
            });
  }

  @Test
  void dynamoDbBackedJournalSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(JournalSpi.class);
              assertThat(context.getBean(JournalSpi.class)).isInstanceOf(DynamoDbJournalSpi.class);
            });
  }

  @Test
  void snsBackedNotifierIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(Notifier.class);
              assertThat(context.getBean(Notifier.class)).isInstanceOf(SnsNotifier.class);
            });
  }

  @Test
  void sessionStoreSaveFindRoundTrip() {
    contextRunner()
        .run(
            context -> {
              McpSessionStore store = context.getBean(McpSessionStore.class);
              McpSession session =
                  new McpSession(
                          "2025-11-25",
                          new ClientCapabilities(null, null, null),
                          new Implementation("test-client", null, "1.0"))
                      .withSessionId(UUID.randomUUID().toString());

              store.save(session, Duration.ofHours(1));
              assertThat(store.find(session.sessionId())).isPresent().hasValue(session);
            });
  }
}
