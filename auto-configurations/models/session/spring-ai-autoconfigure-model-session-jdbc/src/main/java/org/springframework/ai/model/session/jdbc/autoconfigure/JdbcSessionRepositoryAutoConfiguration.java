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

import javax.sql.DataSource;

import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.jdbc.JdbcSessionRepository;
import org.springframework.ai.session.jdbc.JdbcSessionRepositoryDialect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.init.DatabaseInitializationProperties;
import org.springframework.boot.sql.autoconfigure.init.OnDatabaseInitializationCondition;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.util.ClassUtils;

/**
 * Auto-configuration of the {@link JdbcSessionRepository} on the single
 * {@link DataSource} bean, and of the schema initialization. Backs off when the
 * application declares a {@link SessionRepository} of its own, of any type.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@AutoConfiguration(afterName = { "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
		"org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
		"org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration" })
// DatabaseInitializationProperties (spring-boot-jdbc, an optional dependency) is the base
// class of JdbcSessionRepositoryProperties and must be present too.
@ConditionalOnClass({ JdbcSessionRepository.class, DataSource.class, JdbcTemplate.class,
		DatabaseInitializationProperties.class })
@ConditionalOnSingleCandidate(DataSource.class)
@EnableConfigurationProperties(JdbcSessionRepositoryProperties.class)
public class JdbcSessionRepositoryAutoConfiguration {

	private static final boolean JPA_PRESENT = ClassUtils.isPresent("org.springframework.orm.jpa.JpaTransactionManager",
			JdbcSessionRepositoryAutoConfiguration.class.getClassLoader())
			&& ClassUtils.isPresent("jakarta.persistence.EntityManagerFactory",
					JdbcSessionRepositoryAutoConfiguration.class.getClassLoader());

	@Bean
	@ConditionalOnMissingBean(SessionRepository.class)
	@DependsOnDatabaseInitialization
	JdbcSessionRepository jdbcSessionRepository(DataSource dataSource, JdbcSessionRepositoryProperties properties,
			ObjectProvider<JdbcTemplate> jdbcTemplate, ObjectProvider<PlatformTransactionManager> transactionManager) {
		// Boot's JdbcTemplate carries spring.jdbc.template.*, and the application's
		// transaction manager keeps the session writes in its transactions, when it
		// manages this data source
		JdbcSessionRepository.Builder builder = JdbcSessionRepository.builder()
			.dataSource(dataSource)
			.dialect(JdbcSessionRepositoryDialect.from(dataSource))
			.indexedAttributes(properties.getIndexedAttributes());
		JdbcTemplate template = jdbcTemplate.getIfUnique();
		if (template != null) {
			builder.jdbcTemplate(template);
		}
		PlatformTransactionManager manager = transactionManager.getIfUnique();
		if (manager != null && managesDataSource(manager, dataSource)) {
			builder.transactionManager(manager);
		}
		return builder.build();
	}

	/**
	 * Whether the manager's transactions carry the connections of the data source, so the
	 * repository's multi-statement writes stay atomic in them: a JDBC or a JPA manager on
	 * the data source, or JTA. Any other manager, one for a message broker or a document
	 * store for example, or a JDBC one on another data source, opens transactions the
	 * statements never join; the repository then uses a manager of its own.
	 */
	static boolean managesDataSource(PlatformTransactionManager manager, DataSource dataSource) {
		if (manager instanceof JtaTransactionManager) {
			return true;
		}
		if (manager instanceof ResourceTransactionManager resourceManager
				&& resourceManager.getResourceFactory() == dataSource) {
			return true;
		}
		return JPA_PRESENT && Jpa.managesDataSource(manager, dataSource);
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(OnJdbcSessionRepositoryDatasourceInitializationCondition.class)
	JdbcSessionRepositorySchemaInitializer jdbcSessionRepositorySchemaInitializer(DataSource dataSource,
			JdbcSessionRepositoryProperties properties) {
		return new JdbcSessionRepositorySchemaInitializer(dataSource, properties);
	}

	static class OnJdbcSessionRepositoryDatasourceInitializationCondition extends OnDatabaseInitializationCondition {

		OnJdbcSessionRepositoryDatasourceInitializationCondition() {
			super("Jdbc Session Repository", JdbcSessionRepositoryProperties.CONFIG_PREFIX + ".initialize-schema");
		}

	}

	/** Touched only with spring-orm and the JPA API on the classpath. */
	private static final class Jpa {

		static boolean managesDataSource(PlatformTransactionManager manager, DataSource dataSource) {
			return manager instanceof JpaTransactionManager jpa && jpa.getDataSource() == dataSource;
		}

	}

}
