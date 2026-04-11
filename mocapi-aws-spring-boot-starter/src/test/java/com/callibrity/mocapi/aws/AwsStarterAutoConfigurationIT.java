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

import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.core.atom.AtomSpi;
import org.jwcarman.substrate.core.journal.JournalSpi;
import org.jwcarman.substrate.core.mailbox.MailboxSpi;
import org.jwcarman.substrate.core.notifier.NotifierSpi;
import org.jwcarman.substrate.dynamodb.DynamoDbAutoConfiguration;
import org.jwcarman.substrate.dynamodb.atom.DynamoDbAtomAutoConfiguration;
import org.jwcarman.substrate.dynamodb.atom.DynamoDbAtomSpi;
import org.jwcarman.substrate.dynamodb.journal.DynamoDbJournalAutoConfiguration;
import org.jwcarman.substrate.dynamodb.journal.DynamoDbJournalSpi;
import org.jwcarman.substrate.dynamodb.mailbox.DynamoDbMailboxAutoConfiguration;
import org.jwcarman.substrate.dynamodb.mailbox.DynamoDbMailboxSpi;
import org.jwcarman.substrate.sns.SnsAutoConfiguration;
import org.jwcarman.substrate.sns.notifier.SnsNotifierAutoConfiguration;
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
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies that pulling the mocapi-aws-spring-boot-starter on the classpath (with LocalStack
 * emulating DynamoDB, SNS, and SQS) produces the full distributed stack: Substrate's DynamoDB
 * backend provides Atom/Mailbox/Journal, and Substrate's SNS backend provides the Notifier.
 */
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
            "substrate.dynamodb.atom.auto-create-table=true",
            "substrate.dynamodb.mailbox.auto-create-table=true",
            "substrate.dynamodb.journal.auto-create-table=true",
            "substrate.sns.notifier.auto-create-topic=true")
        .withConfiguration(
            AutoConfigurations.of(
                DynamoDbAutoConfiguration.class,
                DynamoDbAtomAutoConfiguration.class,
                DynamoDbMailboxAutoConfiguration.class,
                DynamoDbJournalAutoConfiguration.class,
                SnsAutoConfiguration.class,
                SnsNotifierAutoConfiguration.class));
  }

  @Test
  void dynamoDbBackedAtomSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(AtomSpi.class);
              assertThat(context.getBean(AtomSpi.class)).isInstanceOf(DynamoDbAtomSpi.class);
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
  void snsBackedNotifierSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(NotifierSpi.class);
            });
  }
}
