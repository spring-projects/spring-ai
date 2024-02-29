# Qdrant Vector Store

[Reference Documentation](https://docs.spring.io/spring-ai/reference/0.8-SNAPSHOT/api/vectordbs/qdrant.html#qdrant-vectorstore-properties)

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
