# Mocapi AWS Spring Boot Starter

All-in-one AWS starter for Mocapi. Bundles the core Mocapi Spring Boot starter with
AWS-native implementations of every pluggable SPI:

| SPI | AWS Service | Module |
|-----|-------------|--------|
| Session Store | DynamoDB | `mocapi-session-store-dynamodb` |
| Substrate Mailbox | DynamoDB | `substrate-mailbox-dynamodb` |
| Substrate Journal | DynamoDB | `substrate-journal-dynamodb` |
| Substrate Notifier | SNS (+SQS) | `substrate-notifier-sns` |

## Usage

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-aws-spring-boot-starter</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```

## Providing AWS SDK Clients

This starter does **not** create `DynamoDbClient` or `SnsClient` beans. Consumers must
provide them via one of:

1. **Spring Cloud AWS** — add `spring-cloud-aws-starter-dynamodb` and
   `spring-cloud-aws-starter-sns` for auto-configured clients.
2. **Manual bean definitions** — define `@Bean` methods in your own `@Configuration`:

```java
@Configuration
public class AwsConfig {

    @Bean
    DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.create();
    }

    @Bean
    SnsClient snsClient() {
        return SnsClient.create();
    }
}
```

AWS SDK v2's default credential provider chain handles authentication automatically on
EC2 (instance profile), ECS (task role), EKS (IRSA), Lambda (execution role), and local
development (SSO / profiles / environment variables).

## Configuration

Minimal `application.yml`:

```yaml
mocapi:
  session-encryption-master-key: ${MOCAPI_MASTER_KEY}
  session:
    dynamodb:
      table-name: my-app-mocapi-sessions
```

Region is resolved by the AWS SDK v2 default provider chain (`AWS_REGION` environment
variable, `~/.aws/config`, or instance/task metadata).

## Required AWS Resources

Provision these before the application starts:

### 1. DynamoDB Table — Sessions

- Partition key: `session_id` (String)
- TTL attribute: `expires_at` (Number, epoch seconds)

**CloudFormation:**

```yaml
McpSessionsTable:
  Type: AWS::DynamoDB::Table
  Properties:
    TableName: my-app-mocapi-sessions
    BillingMode: PAY_PER_REQUEST
    AttributeDefinitions:
      - AttributeName: session_id
        AttributeType: S
    KeySchema:
      - AttributeName: session_id
        KeyType: HASH
    TimeToLiveSpecification:
      AttributeName: expires_at
      Enabled: true
```

**AWS CLI:**

```bash
aws dynamodb create-table \
  --table-name my-app-mocapi-sessions \
  --attribute-definitions AttributeName=session_id,AttributeType=S \
  --key-schema AttributeName=session_id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

aws dynamodb update-time-to-live \
  --table-name my-app-mocapi-sessions \
  --time-to-live-specification Enabled=true,AttributeName=expires_at
```

### 2. DynamoDB Table — Substrate Mailbox

See the [substrate-mailbox-dynamodb](https://github.com/jwcarman/substrate) module
documentation for the required table schema. When using `substrate.mailbox.dynamodb.auto-create-table=true`,
the table is created automatically.

### 3. DynamoDB Table — Substrate Journal

See the [substrate-journal-dynamodb](https://github.com/jwcarman/substrate) module
documentation for the required table schema. When using `substrate.journal.dynamodb.auto-create-table=true`,
the table is created automatically.

### 4. SNS Topic — Substrate Notifier

A single SNS topic for publish-subscribe notifications. When using
`substrate.notifier.sns.auto-create-topic=true`, the topic is created automatically.

## IAM Policy (Minimum Required Permissions)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:GetItem",
        "dynamodb:DeleteItem",
        "dynamodb:Query",
        "dynamodb:Scan",
        "dynamodb:DescribeTable",
        "dynamodb:DescribeTimeToLive",
        "dynamodb:UpdateTimeToLive",
        "dynamodb:CreateTable"
      ],
      "Resource": [
        "arn:aws:dynamodb:*:*:table/my-app-mocapi-sessions",
        "arn:aws:dynamodb:*:*:table/my-app-mocapi-mailbox",
        "arn:aws:dynamodb:*:*:table/my-app-mocapi-journal"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "sns:Publish",
        "sns:Subscribe",
        "sns:CreateTopic",
        "sns:ListSubscriptionsByTopic"
      ],
      "Resource": [
        "arn:aws:sns:*:*:my-app-mocapi-notifier"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "sqs:CreateQueue",
        "sqs:DeleteQueue",
        "sqs:GetQueueAttributes",
        "sqs:GetQueueUrl",
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:SetQueueAttributes"
      ],
      "Resource": [
        "arn:aws:sqs:*:*:substrate-*"
      ]
    }
  ]
}
```

Trim `CreateTable`, `CreateTopic`, and `CreateQueue` actions if you provision
resources externally and disable auto-creation.

## Local Development with LocalStack

Use [LocalStack](https://localstack.cloud/) for local development without an AWS account:

```yaml
# docker-compose.yml
services:
  localstack:
    image: localstack/localstack:3
    ports:
      - "4566:4566"
    environment:
      SERVICES: dynamodb,sns,sqs
```

Configure your application to point at LocalStack:

```java
@Profile("local")
@Configuration
public class LocalAwsConfig {

    @Bean
    DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
            .endpointOverride(URI.create("http://localhost:4566"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .region(Region.US_EAST_1)
            .build();
    }

    @Bean
    SnsClient snsClient() {
        return SnsClient.builder()
            .endpointOverride(URI.create("http://localhost:4566"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .region(Region.US_EAST_1)
            .build();
    }
}
```
