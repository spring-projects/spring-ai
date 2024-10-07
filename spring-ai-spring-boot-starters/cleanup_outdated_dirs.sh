#!/bin/bash

# Cleanup script for outdated Spring AI starter directories
# This script will show each directory contents and ask for confirmation before deletion

# List of outdated directories
outdated_dirs=(
    "spring-ai-starter-anthropic"
    "spring-ai-starter-aws-opensearch-store"
    "spring-ai-starter-azure-openai"
    "spring-ai-starter-azure-store"
    "spring-ai-starter-bedrock-ai"
    "spring-ai-starter-cassandra-store"
    "spring-ai-starter-chroma-store"
    "spring-ai-starter-elasticsearch-store"
    "spring-ai-starter-gemfire-store"
    "spring-ai-starter-hanadb-store"
    "spring-ai-starter-huggingface"
    "spring-ai-starter-milvus-store"
    "spring-ai-starter-minimax"
    "spring-ai-starter-mistral-ai"
    "spring-ai-starter-model-chat-memory-cassandra"
    "spring-ai-starter-model-chat-memory-jdbc"
    "spring-ai-starter-model-chat-memory-neo4j"
    "spring-ai-starter-model-watsonx-ai"
    "spring-ai-starter-mongodb-atlas-store"
    "spring-ai-starter-moonshot"
    "spring-ai-starter-neo4j-store"
    "spring-ai-starter-oci-genai"
    "spring-ai-starter-ollama"
    "spring-ai-starter-openai"
    "spring-ai-starter-opensearch-store"
    "spring-ai-starter-oracle-store"
    "spring-ai-starter-pgvector-store"
    "spring-ai-starter-pinecone-store"
    "spring-ai-starter-postgresml-embedding"
    "spring-ai-starter-qdrant-store"
    "spring-ai-starter-qianfan"
    "spring-ai-starter-redis-store"
    "spring-ai-starter-stability-ai"
    "spring-ai-starter-transformers"
    "spring-ai-starter-typesense-store"
    "spring-ai-starter-vector-store-hanadb"
    "spring-ai-starter-vertex-ai-embedding"
    "spring-ai-starter-vertex-ai-gemini"
    "spring-ai-starter-vertex-ai-palm2"
    "spring-ai-starter-watsonx-ai"
    "spring-ai-starter-weaviate-store"
    "spring-ai-starter-zhipuai"
)

echo "Spring AI Starter Directory Cleanup Script"
echo "=========================================="
echo "This script will show you the contents of each outdated directory"
echo "and ask for your confirmation before deletion."
echo ""
echo "Total directories to review: ${#outdated_dirs[@]}"
echo ""

deleted_count=0
skipped_count=0
total_count=${#outdated_dirs[@]}

for i in "${!outdated_dirs[@]}"; do
    dir="${outdated_dirs[$i]}"
    current=$((i + 1))
    
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "Directory $current of $total_count: $dir"
    echo "═══════════════════════════════════════════════════════════════"
    
    if [ -d "$dir" ]; then
        echo "Contents of $dir:"
        ls -la "$dir"
        echo ""
        
        while true; do
            echo -n "Delete this directory? (Y/N): "
            read -r response
            case $response in
                [Yy]* ) 
                    echo "Deleting $dir..."
                    rm -rf "$dir"
                    if [ $? -eq 0 ]; then
                        echo "✓ Successfully deleted $dir"
                        deleted_count=$((deleted_count + 1))
                    else
                        echo "✗ Failed to delete $dir"
                    fi
                    break
                    ;;
                [Nn]* ) 
                    echo "Skipping $dir"
                    skipped_count=$((skipped_count + 1))
                    break
                    ;;
                * ) 
                    echo "Please answer Y or N"
                    ;;
            esac
        done
    else
        echo "Directory $dir does not exist - skipping"
        skipped_count=$((skipped_count + 1))
    fi
done

echo ""
echo "=========================================="
echo "Cleanup Summary:"
echo "  Total directories reviewed: $total_count"
echo "  Directories deleted: $deleted_count"
echo "  Directories skipped: $skipped_count"
echo "=========================================="
echo "Cleanup completed!"