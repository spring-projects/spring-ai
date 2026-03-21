#!/bin/bash

curl https://api.mistral.ai/v1/chat/completions \
 -X POST \
 -H "Authorization: Bearer $MISTRAL_AI_API_KEY" \
 -H 'Content-Type: application/json' \
 -d '{
  "reasoning_effort": "high",
  "messages": [
    {
      "role": "system",
      "content": "You are a helpful assistant providing accurate short answers."
    },
    {
      "role": "user",
      "content": "What is the first planet of the solar system based on the mass in descending order?"
    }
  ],
  "model": "mistral-small-latest"
}'