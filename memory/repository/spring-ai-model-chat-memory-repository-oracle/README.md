# Spring AI Oracle Chat Memory Repository

[Chat Memory Documentation](https://docs.spring.io/spring-ai/reference/api/chat-memory.html#_chat_memory)

## Test Notes

### Use Existing Oracle Database

If `ORACLE_DATABASE_URL` is set, tests use that database instead of Testcontainers.

Optional credentials:
- `ORACLE_DATABASE_USERNAME`
- `ORACLE_DATABASE_PASSWORD`

### Rancher Desktop + Testcontainers

With Rancher Desktop, Ryuk can fail to start unless you set:

- `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`

Example:

```bash
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -pl memory/repository/spring-ai-model-chat-memory-repository-oracle \
  -Denforcer.skip=true \
  -Dmaven.build.cache.enabled=false \
  -Dtest=OracleChatMemoryRepositoryIT test
```
