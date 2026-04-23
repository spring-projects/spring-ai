CREATE KEYSPACE IF NOT EXISTS test_wikidata WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS test_wikidata.articles (
    wiki text,
    language text,
    title text,
    chunk_no int,
    id int,
    revision int,
    body text,
    PRIMARY KEY ((wiki, language, title), chunk_no)
);