package org.springframework.ai.testcontainers.service.connection.elasticsearch;

import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchConnectionDetails;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.util.List;

/**
 * A {@link ContainerConnectionDetailsFactory} implementation that provides
 * {@link ElasticsearchConnectionDetails} for a {@link ElasticsearchContainer}.
 *
 * @author Laura Trotta
 * @see ContainerConnectionDetailsFactory
 * @see ElasticsearchConnectionDetails
 * @see ElasticsearchContainer
 * @since 1.0.2
 */
class ElasticsearchContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<ElasticsearchContainer, ElasticsearchConnectionDetails> {

	@Override
	public ElasticsearchConnectionDetails getContainerConnectionDetails(
			ContainerConnectionSource<ElasticsearchContainer> source) {
		return new ElasticsearchContainerConnectionDetails(source);
	}

	/**
	 * {@link ElasticsearchConnectionDetails} backed by a
	 * {@link ContainerConnectionSource}.
	 */
	private static final class ElasticsearchContainerConnectionDetails
			extends ContainerConnectionDetails<ElasticsearchContainer> implements ElasticsearchConnectionDetails {

		private ElasticsearchContainerConnectionDetails(ContainerConnectionSource<ElasticsearchContainer> source) {
			super(source);
		}

		@Override
		public List<Node> getNodes() {
			return List.of(new Node(getContainer().getHttpHostAddress(), getContainer().getMappedPort(9200),
					Node.Protocol.HTTP, getUsername(), getPassword()));
		}

		@Override
		public String getUsername() {
			return "elastic";
		}

		@Override
		public String getPassword() {
			return "changeme";
		}

		@Override
		public String getPathPrefix() {
			return "";
		}

		@Override
		public SslBundle getSslBundle() {
			return super.getSslBundle();
		}

	}

}
