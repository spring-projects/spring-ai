#!/usr/bin/env bash

# Execute this script to deploy the needed Azure OpenAI models to execute the integration tests.
#
# For this, you need to have Azure CLI installed: https://learn.microsoft.com/cli/azure/install-azure-cli
#
# Azure CLI runs on:
# - Windows (using Windows Command Prompt (CMD), PowerShell, or Windows Subsystem for Linux (WSL)): https://learn.microsoft.com/cli/azure/install-azure-cli-windows 
# - macOS: https://learn.microsoft.com/cli/azure/install-azure-cli-macos
# - Linux: https://learn.microsoft.com/cli/azure/install-azure-cli-linux
# - Docker: https://learn.microsoft.com/cli/azure/run-azure-cli-docker
#
# Once installed, you can run the following commands to check your installation is correct:
# az --version
# az --help

echo "Setting up environment variables..."
echo "----------------------------------"
PROJECT="spring-ai-open-ai-official-$RANDOM-$RANDOM-$RANDOM"
RESOURCE_GROUP="rg-$PROJECT"
LOCATION="swedencentral"
AI_SERVICE="ai-$PROJECT"
TAG="$PROJECT"

echo "Creating the resource group..."
echo "------------------------------"
az group create \
  --name "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --tags system="$TAG"

# If you want to know the available SKUs, run the following Azure CLI command:
# az cognitiveservices account list-skus --location "$LOCATION"  -o table

echo "Creating the Cognitive Service..."
echo "---------------------------------"
az cognitiveservices account create \
  --name "$AI_SERVICE" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --custom-domain "$AI_SERVICE" \
  --tags system="$TAG" \
  --kind "OpenAI" \
  --sku "S0"

# If you want to know the available models, run the following Azure CLI command:
# az cognitiveservices account list-models --resource-group "$RESOURCE_GROUP" --name "$AI_SERVICE" -o table  

echo "Deploying Embedding Models"
echo "=========================="

models=("text-embedding-ada-002" "text-embedding-3-small" "text-embedding-3-large")
versions=("2" "1" "1")
skus=("Standard" "Standard" "Standard")

for i in "${!models[@]}"; do
  model="${models[$i]}"
  sku="${skus[$i]}"
  version="${versions[$i]}"
  echo "Deploying $model..."
  az cognitiveservices account deployment create \
    --name "$AI_SERVICE" \
    --resource-group "$RESOURCE_GROUP" \
    --deployment-name "$model" \
    --model-name "$model" \
    --model-version "$version"\
    --model-format "OpenAI" \
    --sku-capacity 1 \
    --sku-name "$sku" || echo "Failed to deploy $model. Check SKU and region compatibility."
done

echo "Storing the key and endpoint in environment variables..."
echo "--------------------------------------------------------"
AZURE_OPENAI_KEY=$(
  az cognitiveservices account keys list \
    --name "$AI_SERVICE" \
    --resource-group "$RESOURCE_GROUP" \
    | jq -r .key1
  )
AZURE_OPENAI_ENDPOINT=$(
  az cognitiveservices account show \
    --name "$AI_SERVICE" \
    --resource-group "$RESOURCE_GROUP" \
    | jq -r .properties.endpoint
  )

echo "AZURE_OPENAI_KEY=$AZURE_OPENAI_KEY"
echo "AZURE_OPENAI_ENDPOINT=$AZURE_OPENAI_ENDPOINT"

# Once you finish the tests, you can delete the resource group with the following command:
#echo "Deleting the resource group..."
#echo "------------------------------"
#az group delete --name "$RESOURCE_GROUP" --yes
