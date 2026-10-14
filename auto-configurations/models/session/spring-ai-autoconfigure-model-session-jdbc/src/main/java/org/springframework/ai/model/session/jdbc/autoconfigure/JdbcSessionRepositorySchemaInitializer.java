/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.session.jdbc.autoconfigure;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.boot.jdbc.init.PropertiesBasedDataSourceScriptDatabaseInitializer;

/**
 * Performs database initialization for the JDBC Session Repository.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
class JdbcSessionRepositorySchemaInitializer
		extends PropertiesBasedDataSourceScriptDatabaseInitializer<JdbcSessionRepositoryProperties> {

	JdbcSessionRepositorySchemaInitializer(DataSource dataSource, JdbcSessionRepositoryProperties properties) {
		// MariaDB uses the MySQL schema script; without this mapping the @@platform@@
		// placeholder resolves to "mariadb" and schema-mariadb.sql does not exist.
		super(dataSource, properties, Map.of(DatabaseDriver.MARIADB, "mysql"));
	}

}
