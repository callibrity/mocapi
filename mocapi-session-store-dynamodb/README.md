# mocapi-session-store-dynamodb

DynamoDB-backed `McpSessionStore` for Mocapi. Adding this jar to a Spring Boot
application's classpath automatically replaces the in-memory session store with
a DynamoDB implementation, as long as a `DynamoDbClient` bean is present.

## Table Schema

The module expects a DynamoDB table with the following schema:

| Attribute    | Type       | Purpose                                      |
|-------------|------------|----------------------------------------------|
| `session_id` | String (S) | Partition key — the MCP session ID           |
| `payload`    | String (S) | JSON-serialized `McpSession`                 |
| `expires_at` | Number (N) | Unix epoch seconds — used as the TTL attribute |

Partition key: `session_id` (hash only, no sort key).

## Table Creation

### CloudFormation

```yaml
Resources:
  McpSessionsTable:
    Type: AWS::DynamoDB::Table
    Properties:
      TableName: mocapi_sessions
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

### AWS CLI

```bash
aws dynamodb create-table \
  --table-name mocapi_sessions \
  --attribute-definitions AttributeName=session_id,AttributeType=S \
  --key-schema AttributeName=session_id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

aws dynamodb update-time-to-live \
  --table-name mocapi_sessions \
  --time-to-live-specification Enabled=true,AttributeName=expires_at
```

## TTL Behavior

DynamoDB's TTL cleanup runs asynchronously and can lag by up to 48 hours after
the `expires_at` timestamp passes. The store filters expired items at read time
regardless, so `find()` never returns stale sessions. TTL handles eventual
cleanup to prevent unbounded table growth.

## Configuration Properties

| Property                            | Default            | Description                                          |
|-------------------------------------|--------------------|------------------------------------------------------|
| `mocapi.session.dynamodb.table-name` | `mocapi_sessions`  | DynamoDB table name                                  |
| `mocapi.session.dynamodb.verify-table` | `true`           | Verify the table exists at startup                   |
| `mocapi.session.dynamodb.ensure-ttl` | `true`            | Auto-enable TTL on the `expires_at` attribute        |

## Required IAM Permissions

Scope these to the specific table ARN:

- `dynamodb:PutItem`
- `dynamodb:UpdateItem`
- `dynamodb:GetItem`
- `dynamodb:DeleteItem`
- `dynamodb:DescribeTable` (if `verify-table` is enabled)
- `dynamodb:DescribeTimeToLive` (if `ensure-ttl` is enabled)
- `dynamodb:UpdateTimeToLive` (if `ensure-ttl` is enabled)
