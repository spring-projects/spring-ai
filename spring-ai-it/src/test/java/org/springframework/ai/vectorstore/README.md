

Start Postgres+PGVecgor DB:

```
docker run -it --rm --name postgres -p 5432:5432 -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres ankane/pgvector
```

You can connect to this server like this:

```
psql -U postgres -h localhost -p 5432
```