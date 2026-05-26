package org.springframework.ai.chat.memory.repository.oracle;

import java.util.List;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.OracleChatMemoryRepositoryDialect;
import org.springframework.ai.chat.messages.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * An implementation of {@link ChatMemoryRepository} for Oracle.
 *
 * @since 2.0.0
 */
public final class OracleChatMemoryRepository implements ChatMemoryRepository {

	private final JdbcChatMemoryRepository delegate;

	private OracleChatMemoryRepository(JdbcChatMemoryRepository delegate) {
		this.delegate = delegate;
	}

	@Override
	public List<String> findConversationIds() {
		return this.delegate.findConversationIds();
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		return this.delegate.findByConversationId(conversationId);
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		this.delegate.saveAll(conversationId, messages);
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		this.delegate.deleteByConversationId(conversationId);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable DataSource dataSource;

		private @Nullable JdbcTemplate jdbcTemplate;

		private @Nullable PlatformTransactionManager transactionManager;

		private Builder() {
		}

		public Builder dataSource(DataSource dataSource) {
			this.dataSource = dataSource;
			return this;
		}

		public Builder jdbcTemplate(JdbcTemplate jdbcTemplate) {
			this.jdbcTemplate = jdbcTemplate;
			return this;
		}

		public Builder transactionManager(PlatformTransactionManager transactionManager) {
			this.transactionManager = transactionManager;
			return this;
		}

		public OracleChatMemoryRepository build() {
			var repository = JdbcChatMemoryRepository.builder().dialect(new OracleChatMemoryRepositoryDialect());

			if (this.dataSource != null) {
				repository.dataSource(this.dataSource);
			}

			if (this.jdbcTemplate != null) {
				repository.jdbcTemplate(this.jdbcTemplate);
			}

			if (this.transactionManager != null) {
				repository.transactionManager(this.transactionManager);
			}

			return new OracleChatMemoryRepository(repository.build());
		}

	}

}
