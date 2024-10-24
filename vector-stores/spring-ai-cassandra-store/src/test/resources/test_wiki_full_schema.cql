/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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