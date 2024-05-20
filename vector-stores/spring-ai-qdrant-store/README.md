# Qdrant Vector Store

[Reference Documentation](https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/api/vectordbs/qdrant.html)

## Run locally

### Accessing the Web UI

First, run the Docker container:

```
docker run -p 6333:6333 -p 6334:6334 \
    -v $(pwd)/qdrant_storage:/qdrant/storage:z \
    qdrant/qdrant
```

The GUI is available at http://localhost:6333/dashboard

## Qdrant references

- https://qdrant.tech/documentation/interfaces/
- https://github.com/qdrant/java-client
