CREATE KEYSPACE IF NOT EXISTS test_wikidata WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS test_wikidata.articles (
    wiki text,
    language text,
    title text,
    chunk_no int,
    id int,
    revision int,
    body text,
    all_minilm_l6_v2_embedding vector<float, 384>,
    PRIMARY KEY ((wiki, language, title), chunk_no)
);

CREATE CUSTOM INDEX IF NOT EXISTS all_minilm_l6_v2_ann ON test_wikidata.articles(all_minilm_l6_v2_embedding) USING 'SAI'
  WITH OPTIONS = { 'similarity_function': 'COSINE' };


CREATE CUSTOM INDEX IF NOT EXISTS id_idx ON test_wikidata.articles(id) USING 'SAI';