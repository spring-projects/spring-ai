# Spring AI S3 Chat Memory Repository

A Spring AI implementation of `ChatMemoryRepository` that stores conversation history in Amazon S3, providing scalable and cost-effective chat memory persistence for AI applications.

## Features

- **Scalable Storage**: Leverages Amazon S3 for virtually unlimited conversation storage
- **Cost-Effective**: Multiple S3 storage classes for cost optimization
- **JSON Serialization**: Rich metadata preservation with Jackson JSON serialization
- **Automatic Bucket Management**: Optional bucket creation and initialization
- **Pagination Support**: Efficient handling of large conversation datasets
- **Spring Boot Integration**: Full auto-configuration support
- **S3-Compatible Services**: Works with MinIO and other S3-compatible storage

## Quick Start

### Dependencies

Add the S3 Chat Memory Repository dependency to your project:

```xml
<!-- S3 Chat Memory Repository Starter (includes both core and auto-configuration) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-s3</artifactId>
</dependency>
```

### AWS SDK Requirements

This module requires **AWS SDK for Java 2.x** (`software.amazon.awssdk`). AWS SDK v1 (`com.amazonaws`) is **not supported**.

| Requirement | Minimum Version |
|-------------|-----------------|
| AWS SDK for Java 2.x | 2.20.0+ recommended |

Ensure your project uses the v2 SDK artifacts:

```xml
<!-- Correct: AWS SDK v2 (software.amazon.awssdk) -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>

<!-- NOT supported: AWS SDK v1 (com.amazonaws) -->
<!-- <dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-java-sdk-s3</artifactId>
</dependency> -->
```

### Basic Configuration

Configure your S3 chat memory repository in `application.properties`:

```properties
# Required: S3 bucket name
spring.ai.chat.memory.repository.s3.bucket-name=my-chat-memory-bucket

# Optional: AWS region (defaults to us-east-1)
spring.ai.chat.memory.repository.s3.region=us-west-2

# Optional: S3 key prefix (defaults to "chat-memory")
spring.ai.chat.memory.repository.s3.key-prefix=conversations

# Optional: Auto-create bucket if it doesn't exist (defaults to false)
spring.ai.chat.memory.repository.s3.initialize-bucket=true

# Optional: S3 storage class (defaults to STANDARD)
spring.ai.chat.memory.repository.s3.storage-class=STANDARD_IA
```

### AWS Credentials

Configure AWS credentials using one of the standard methods:

1. **Environment Variables**:
   ```bash
   export AWS_ACCESS_KEY_ID=your-access-key
   export AWS_SECRET_ACCESS_KEY=your-secret-key
   ```

2. **AWS Credentials File** (`~/.aws/credentials`):
   ```ini
   [default]
   aws_access_key_id = your-access-key
   aws_secret_access_key = your-secret-key
   ```

3. **IAM Roles** (recommended for EC2/ECS/Lambda deployments)

## Usage Examples

### Basic Usage with Auto-Configuration

```java
@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder,
                         ChatMemoryRepository chatMemoryRepository) {
        this.chatClient = chatClientBuilder
            .defaultAdvisors(new MessageChatMemoryAdvisor(
                new MessageWindowChatMemory(chatMemoryRepository, 10)))
            .build();
    }

    @PostMapping("/chat")
    public String chat(@RequestParam String message,
                      @RequestParam String conversationId) {
        return chatClient.prompt()
            .user(message)
            .advisors(advisorSpec -> advisorSpec
                .param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
            .call()
            .content();
    }
}
```

### Manual Configuration

```java
@Configuration
public class ChatMemoryConfig {

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .region(Region.US_WEST_2)
            .build();
    }

    @Bean
    public S3ChatMemoryRepository chatMemoryRepository(S3Client s3Client) {
        return S3ChatMemoryRepository.builder()
            .s3Client(s3Client)
            .bucketName("my-chat-conversations")
            .keyPrefix("chat-memory")
            .initializeBucket(true)
            .storageClass(StorageClass.STANDARD_IA)
            .build();
    }

    @Bean
    public ChatMemory chatMemory(S3ChatMemoryRepository repository) {
        return new MessageWindowChatMemory(repository, 20);
    }
}
```

### Custom S3 Endpoint (MinIO/LocalStack)

```java
@Bean
public S3Client s3Client() {
    return S3Client.builder()
        .endpointOverride(URI.create("http://localhost:9000")) // MinIO endpoint
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create("minioadmin", "minioadmin")))
        .forcePathStyle(true) // Required for MinIO
        .build();
}
```

### Advanced Usage with Custom Configuration

```java
@Service
public class ConversationService {

    private final S3ChatMemoryRepository repository;

    public ConversationService(S3ChatMemoryRepository repository) {
        this.repository = repository;
    }

    public List<String> getAllConversations() {
        return repository.findConversationIds();
    }

    public List<Message> getConversationHistory(String conversationId) {
        return repository.findByConversationId(conversationId);
    }

    public void archiveConversation(String conversationId) {
        // Get messages and save to archive location
        List<Message> messages = repository.findByConversationId(conversationId);
        // ... archive logic ...

        // Delete from active storage
        repository.deleteByConversationId(conversationId);
    }
}
```

## Configuration Properties

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `spring.ai.chat.memory.repository.s3.bucket-name` | S3 bucket name for storing conversations | - | ✅ |
| `spring.ai.chat.memory.repository.s3.region` | AWS region | `us-east-1` | ❌ |
| `spring.ai.chat.memory.repository.s3.key-prefix` | S3 key prefix for conversation objects | `chat-memory` | ❌ |
| `spring.ai.chat.memory.repository.s3.initialize-bucket` | Auto-create bucket if it doesn't exist | `false` | ❌ |
| `spring.ai.chat.memory.repository.s3.storage-class` | S3 storage class | `STANDARD` | ❌ |

**Note**: Message windowing (limiting the number of messages per conversation) is handled by `MessageWindowChatMemory`, not by the repository itself. This follows the standard Spring AI pattern where repositories handle storage and ChatMemory implementations handle business logic like windowing.

### Supported Storage Classes

- `STANDARD` - General purpose storage (default)
- `STANDARD_IA` - Infrequent access storage (lower cost)
- `ONEZONE_IA` - Single AZ infrequent access
- `REDUCED_REDUNDANCY` - Reduced redundancy storage

## S3-Specific Considerations

### Eventual Consistency

Amazon S3 provides **strong read-after-write consistency** for new objects and **strong consistency** for overwrite PUTS and DELETES. However, be aware of these characteristics:

- **New conversations**: Immediately readable after creation
- **Updated conversations**: Immediately readable after update
- **Deleted conversations**: Immediately consistent after deletion
- **Conversation listing**: May have slight delays in very high-throughput scenarios

### Performance Optimization

1. **Key Design**: The repository uses a flat key structure (`{prefix}/{conversationId}.json`) for optimal performance
2. **Batch Operations**: Each conversation is stored as a single JSON document for atomic updates
3. **Pagination**: Large conversation lists are automatically paginated using S3's native pagination

**Note on `findConversationIds()` at scale**: This method is not called during normal chat operations (message storage and retrieval use single-object S3 operations). However, if your application uses `findConversationIds()` for administrative purposes (e.g., listing conversations in a UI), be aware that S3's `listObjectsV2` returns a maximum of 1,000 objects per request. For repositories with many conversations, this requires sequential pagination calls (N/1,000 API calls), which may introduce latency. Consider implementing application-level caching or maintaining a separate conversation index if you frequently need to list all conversations.

### Cost Optimization

```properties
# Use Standard-IA for conversations older than 30 days
spring.ai.chat.memory.repository.s3.storage-class=STANDARD_IA

# Consider lifecycle policies for long-term archival
```

Example S3 Lifecycle Policy:
```json
{
    "Rules": [
        {
            "ID": "ChatMemoryLifecycle",
            "Status": "Enabled",
            "Filter": {
                "Prefix": "chat-memory/"
            },
            "Transitions": [
                {
                    "Days": 30,
                    "StorageClass": "STANDARD_IA"
                },
                {
                    "Days": 90,
                    "StorageClass": "GLACIER"
                }
            ]
        }
    ]
}
```

### Security Best Practices

1. **IAM Permissions**: Use minimal required permissions
   ```json
   {
       "Version": "2012-10-17",
       "Statement": [
           {
               "Effect": "Allow",
               "Action": [
                   "s3:GetObject",
                   "s3:PutObject",
                   "s3:DeleteObject",
                   "s3:ListBucket"
               ],
               "Resource": [
                   "arn:aws:s3:::your-chat-bucket",
                   "arn:aws:s3:::your-chat-bucket/*"
               ]
           }
       ]
   }
   ```

2. **Bucket Encryption**: Enable server-side encryption
   ```properties
   # S3 bucket should have default encryption enabled
   ```

3. **Access Logging**: Enable S3 access logging for audit trails

### Monitoring and Observability

The repository integrates with Spring Boot's observability features:

```properties
# Enable metrics
management.metrics.export.cloudwatch.enabled=true

# Enable tracing
management.tracing.enabled=true
```

Monitor these key metrics:
- S3 request latency
- Error rates (4xx/5xx responses)
- Storage usage and costs
- Conversation access patterns

## Error Handling

The repository handles common S3 scenarios gracefully:

- **Bucket doesn't exist**: Creates bucket if `initialize-bucket=true`, otherwise throws `IllegalStateException`
- **Network issues**: Retries with exponential backoff (AWS SDK default)
- **Access denied**: Throws `IllegalStateException` with clear error message
- **Invalid conversation ID**: Normalizes to "default" conversation
- **Malformed JSON**: Throws `IllegalStateException` during deserialization

## Testing

### Integration Testing with LocalStack

```java
@Testcontainers
class S3ChatMemoryRepositoryIT {

    @Container
    static final LocalStackContainer localstack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
            .withServices("s3");

    @Test
    void testConversationStorage() {
        S3Client s3Client = S3Client.builder()
            .endpointOverride(localstack.getEndpoint())
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
            .region(Region.of(localstack.getRegion()))
            .forcePathStyle(true)
            .build();

        S3ChatMemoryRepository repository = S3ChatMemoryRepository.builder()
            .s3Client(s3Client)
            .bucketName("test-bucket")
            .build();

        // Test conversation operations...
    }
}
```

### Property-Based Testing

The repository includes comprehensive property-based tests using jqwik:

```java
@Property(tries = 100)
void conversationRoundTrip(@ForAll("conversationIds") String conversationId,
                          @ForAll("messageLists") List<Message> messages) {
    repository.saveAll(conversationId, messages);
    List<Message> retrieved = repository.findByConversationId(conversationId);
    assertThat(retrieved).isEqualTo(messages);
}
```

## Migration from Other Repositories

### From JDBC Chat Memory Repository

```java
// 1. Export existing conversations
List<String> conversationIds = jdbcRepository.findConversationIds();
Map<String, List<Message>> conversations = new HashMap<>();
for (String id : conversationIds) {
    conversations.put(id, jdbcRepository.findByConversationId(id));
}

// 2. Import to S3 repository
for (Map.Entry<String, List<Message>> entry : conversations.entrySet()) {
    s3Repository.saveAll(entry.getKey(), entry.getValue());
}
```

## Troubleshooting

### Common Issues

1. **"Bucket does not exist" error**
   - Set `spring.ai.chat.memory.repository.s3.initialize-bucket=true`
   - Or create the bucket manually in AWS Console

2. **"Access Denied" errors**
   - Verify AWS credentials are configured
   - Check IAM permissions for the bucket
   - Ensure bucket policy allows access

3. **Slow performance**
   - Check AWS region configuration (use closest region)
   - Consider using VPC endpoints for EC2 deployments
   - Monitor S3 request metrics

4. **High costs**
   - Use appropriate storage class (`STANDARD_IA` for infrequent access)
   - Implement lifecycle policies for archival
   - Monitor storage usage patterns

### Debug Logging

Enable debug logging to troubleshoot issues:

```properties
logging.level.org.springframework.ai.chat.memory.repository.s3=DEBUG
logging.level.software.amazon.awssdk.services.s3=DEBUG
```

## Documentation

For more information about Spring AI Chat Memory, see the [official documentation](https://docs.spring.io/spring-ai/reference/api/chatmemory.html).

## Contributing

Contributions are welcome! Please read the [contribution guidelines](../../../../../CONTRIBUTING.adoc) before submitting pull requests.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../../../../../LICENSE.txt) file for details.