# mocapi-aws-spring-boot-starter (all-in-one AWS starter)

## What to build

Create a new Maven module `mocapi-aws-spring-boot-starter`
that is a pure aggregation pom bundling the AWS-native
implementations of mocapi's pluggable SPIs:

- **Session store**: `mocapi-session-store-dynamodb` (spec 136)
- **Substrate Mailbox**: `substrate-mailbox-dynamodb`
- **Substrate Journal**: `substrate-journal-dynamodb`
- **Substrate Notifier**: `substrate-notifier-sns`

Follows the same pattern as the Redis (spec 122), Hazelcast
(spec 123), and PostgreSQL (spec 135) all-in-one starters.
Operators deploying mocapi to AWS (ECS, EKS, Lambda, App
Runner, EC2) pull in this one dependency and get a complete
mocapi stack running on AWS-managed services.

### Why a dedicated AWS starter

Among substrate's published backends, AWS is unique in that
the four SPIs are split across **two different AWS services**:

- DynamoDB handles session store, mailbox, and journal (state
  + message queue + event log use cases)
- SNS handles notifier (publish-subscribe use case)

There's no single AWS service that does everything the way
Redis or PostgreSQL do, so a "mocapi-dynamodb-spring-boot-starter"
would leave notifier falling back to in-memory. The
`mocapi-aws-spring-boot-starter` name reflects the
multi-service reality — it's the AWS-native way to run
mocapi, not the DynamoDB-specific way.

Substrate doesn't ship a `substrate-notifier-dynamodb` (DynamoDB
has DynamoDB Streams for change data capture, but that's a
different pattern than mocapi's notifier needs). SNS is the
right AWS-native fit for notifier — it's pub/sub, it's
managed, and substrate has a published implementation.

### Module structure

```
mocapi-aws-spring-boot-starter/
└── pom.xml   (no src/, no Java — aggregation only)
```

Add to the parent pom's `<modules>` list.

### pom.xml

```xml
<project ...>
  <parent>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-parent</artifactId>
    <version>${project.version}</version>
  </parent>

  <artifactId>mocapi-aws-spring-boot-starter</artifactId>
  <name>Mocapi - AWS Spring Boot Starter</name>
  <description>
    All-in-one AWS starter: bundles the mocapi Spring Boot
    starter plus AWS-native implementations of every
    pluggable SPI. Session store, mailbox, and journal run
    on DynamoDB; notifier runs on SNS. Ideal for mocapi
    deployments on ECS, EKS, Lambda, App Runner, or EC2
    with IAM-based authentication.
  </description>

  <dependencies>
    <!-- Core mocapi starter -->
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-spring-boot-starter</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- DynamoDB-backed McpSessionStore (spec 136) -->
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-session-store-dynamodb</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- Substrate AWS backends -->
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-mailbox-dynamodb</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-journal-dynamodb</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-notifier-sns</artifactId>
    </dependency>

    <!-- AWS SDK v2 clients — declared non-optional here so
         consumers pulling this starter get everything they
         need -->
    <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>dynamodb</artifactId>
    </dependency>
    <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>sns</artifactId>
    </dependency>
  </dependencies>
</project>
```

### Consumer usage

```xml
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-aws-spring-boot-starter</artifactId>
  <version>...</version>
</dependency>
```

And in `application.yml` (minimal example for a
Fargate/ECS/EKS deployment using IAM task roles for auth):

```yaml
mocapi:
  session-encryption-master-key: ${MOCAPI_MASTER_KEY:ChangeMeForProduction1234567890abcd}
  session:
    dynamodb:
      table-name: my-app-mocapi-sessions

# AWS SDK v2 picks up credentials from the default credential
# provider chain — no explicit config needed on ECS/EKS/Lambda
# with IAM task roles. For local development, set AWS_REGION and
# use AWS SSO / AWS profiles.

aws:
  region: us-east-1   # or via AWS_REGION env var
```

Infrastructure-as-code (CloudFormation, Terraform, CDK) is
responsible for creating the DynamoDB tables and SNS topics
before the application starts. The starter's module
documentation should include sample CloudFormation snippets.

### Required AWS resources

Operators must provision:

1. **DynamoDB table for sessions** (per spec 136):
   - Partition key: `session_id` (String)
   - TTL attribute: `expires_at` (Number, epoch seconds)
2. **DynamoDB table for substrate mailbox** — whatever schema
   substrate-mailbox-dynamodb requires. Document from the
   substrate module's docs.
3. **DynamoDB table for substrate journal** — ditto.
4. **SNS topic for substrate notifier** — a single topic that
   the notifier publishes to and that the application's
   processes subscribe to via SQS fan-out or direct HTTPS
   subscription. Document from the substrate notifier's docs.
5. **IAM role** with permissions for all four services. See
   the "IAM policy" section below.

### IAM policy (minimum required permissions)

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
        "dynamodb:UpdateTimeToLive"
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
        "sns:ListSubscriptionsByTopic"
      ],
      "Resource": [
        "arn:aws:sns:*:*:my-app-mocapi-notifier"
      ]
    }
  ]
}
```

The exact set of DynamoDB actions depends on what
substrate-mailbox-dynamodb and substrate-journal-dynamodb
actually do. `Query` and `Scan` are defensive additions
assuming the mailbox polls for pending items and the journal
may range-read by time window; verify against the substrate
modules' actual access patterns. Trim the list to the minimum
principle of least privilege.

## Acceptance criteria

### Module structure

- [ ] New module `mocapi-aws-spring-boot-starter` exists as a
      child of the parent pom.
- [ ] Module is listed in the parent pom's `<modules>` list.
- [ ] The starter has **no Java source** — it's a pure
      aggregation pom with only a `pom.xml`.
- [ ] Apache 2.0 license header in the `pom.xml` matches the
      rest of the project.

### Dependencies

- [ ] The pom declares transitive dependencies on:
  - `mocapi-spring-boot-starter`
  - `mocapi-session-store-dynamodb` (from spec 136)
  - `substrate-mailbox-dynamodb`
  - `substrate-journal-dynamodb`
  - `substrate-notifier-sns`
  - `software.amazon.awssdk:dynamodb`
  - `software.amazon.awssdk:sns`
- [ ] None of the above are marked `optional` or `provided` —
      all regular compile-scope dependencies.
- [ ] The substrate module versions are resolved via the
      parent pom's `<dependencyManagement>` section.
- [ ] The AWS SDK v2 versions are resolved via the AWS SDK
      BOM (`software.amazon.awssdk:bom`) if one is managed in
      the parent pom. If not, add the BOM import as part of
      this spec.

### DynamoDbClient + SnsClient bean discovery

- [ ] The starter does NOT define `@Bean` methods for
      `DynamoDbClient` or `SnsClient`. Those beans come from
      one of:
  - **Spring Cloud AWS** — if the consumer adds
    `io.awspring.cloud:spring-cloud-aws-starter-dynamodb`
    and `spring-cloud-aws-starter-sns`, Spring Cloud AWS's
    auto-configuration creates the beans.
  - **Manual consumer bean definitions** — the consumer can
    `@Bean DynamoDbClient dynamoDbClient() { ... }` in their
    own configuration.
  - **Spring Boot's own AWS support** (if it ever adds
    native DynamoDB/SNS auto-config).
- [ ] Document this in the starter's README: consumers must
      provide `DynamoDbClient` and `SnsClient` beans, either
      via Spring Cloud AWS or manually. The starter is
      backend-implementation-agnostic with respect to how
      the AWS SDK clients get constructed.

### Integration test

- [ ] An integration test uses `@SpringBootTest` with
      Testcontainers LocalStack running both DynamoDB and
      SNS services, and verifies that:
  - A `McpSessionStore` bean is present and is the
    DynamoDB-backed implementation (not the in-memory
    fallback).
  - A substrate `Mailbox` bean is the DynamoDB-backed one.
  - A substrate `Journal` bean is the DynamoDB-backed one.
  - A substrate `Notifier` bean is the SNS-backed one.
  - An end-to-end save/find round-trip on the session store
    works against LocalStack DynamoDB.
- [ ] The test provides `DynamoDbClient` and `SnsClient`
      beans manually (pointing at LocalStack endpoints)
      since LocalStack doesn't go through Spring Cloud AWS's
      credential chain.

### Documentation

- [ ] The starter's pom `<description>` clearly states it's
      an all-in-one AWS stack and lists which AWS services
      it uses (DynamoDB + SNS).
- [ ] A README (optional but encouraged) documents:
  - Required AWS resources (three DynamoDB tables + one SNS
    topic)
  - IAM policy with the minimum required permissions
  - Sample CloudFormation / Terraform / CDK snippets for
    provisioning the resources
  - How to supply `DynamoDbClient` and `SnsClient` beans
    (Spring Cloud AWS or manual)
  - Sample `application.yml` with mocapi + AWS region config
  - Local development pattern with LocalStack

### Build and reactor

- [ ] `mvn verify` passes across the full reactor.
- [ ] The new module's build produces only a `pom.xml`
      artifact, no jar.

### Follow-up: AWS example app

- [ ] Document in the commit message (or flag as a follow-up
      spec) that an `mocapi-example-aws` module similar to
      specs 126-129 would be a valuable addition — a working
      example app that uses this starter, with LocalStack
      docker-compose for local development and a CloudFormation
      stack for real AWS deployment. Do NOT create the
      example app as part of this spec; scope stays on the
      starter itself.

## Implementation notes

- **Spring Boot naming convention**: this starter must be
  named `mocapi-aws-spring-boot-starter`, NOT
  `mocapi-spring-boot-starter-aws`. Same rationale as
  specs 122, 123, and 135.
- **Dependency on spec 136** (DynamoDB session store). Spec
  136 must be merged before this starter can be built.
- **Substrate module coordinates** — verified on Maven
  Central:
  - `org.jwcarman.substrate:substrate-mailbox-dynamodb` ✓
  - `org.jwcarman.substrate:substrate-journal-dynamodb` ✓
  - `org.jwcarman.substrate:substrate-notifier-sns` ✓
- **Why SNS for notifier, not DynamoDB Streams?** DynamoDB
  Streams is a change-data-capture mechanism for DynamoDB
  table mutations — it's the wrong pattern for general
  pub/sub. Substrate's notifier SPI expects "publish a
  message, fan out to subscribers," and SNS is the
  canonical AWS service for that. SNS also integrates with
  SQS for reliable fan-out (SNS → SQS → multiple consumers),
  Lambda invocation, HTTPS webhooks, and email — the
  operational flexibility matters for production
  deployments.
- **Why no `substrate-notifier-dynamodb`?** DynamoDB isn't a
  pub/sub system. Substrate's design correctly puts
  notifier on SNS. If DynamoDB Streams ever gets a
  substrate module, that's a separate decision for
  substrate, not mocapi.
- **AWS SDK v2 version alignment**: use the `software.amazon.awssdk:bom`
  to pin all AWS SDK versions in one place. Spring Cloud
  AWS and the various substrate-*-dynamodb/sns modules all
  depend on AWS SDK v2, and version skew between them
  causes hard-to-diagnose runtime errors. The BOM import
  prevents skew.
- **Region resolution**: AWS SDK v2 picks up the region from
  (in order): a `Region.of(...)` call on the builder, the
  `AWS_REGION` env var, the `~/.aws/config` file, or the
  EC2 instance metadata / ECS task metadata / Lambda
  runtime environment. The starter doesn't need to set the
  region explicitly — Spring Cloud AWS or the runtime
  environment handles it.
- **IAM auth**: the starter doesn't introduce any
  credential-handling code. AWS SDK v2's default credential
  provider chain does the right thing on EC2 (instance
  profile), ECS (task role), EKS (IRSA), Lambda
  (execution role), and local dev (SSO / profiles / env
  vars). Document this in the README but don't try to
  configure it in the starter.
- **LocalStack caveats for tests**: LocalStack's DynamoDB
  emulation is mostly faithful but has occasional quirks
  (TTL cleanup timing, exact error codes). The integration
  test should exercise the happy path and a few key edge
  cases but not attempt to verify AWS-specific billing,
  IAM, or CloudWatch behaviors.
- **No Java code in the starter**. Pure aggregation pom.
- **Commit granularity**: one commit for the new module +
  parent pom update + any integration test. If the
  integration test is too heavy to sit inside this module
  (LocalStack needs Docker), put it in a separate module or
  gate it behind a profile.
