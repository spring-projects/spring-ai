
# Milvus VectorStore

https://milvus.io/

## Start Milvus Store

from withing the `src/test/resources/` folder run:

```
docker-compose up
```

To clean the environment:

```
docker-compose down; rm -Rf ./volumes
```


Then connect to the vector store on http://localhost:19530 or for management http://localhost:9001 (user: `minioadmin`, pass: `minioadmin`)

## Throubleshooting

If docker complains for resources:

```
docker system prune --all --force --volumes
```