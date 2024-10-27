#!/bin/bash

# Store the root directory (where mvnw is located)
ROOT_DIR="$(pwd)"

# Check if we're in the correct directory
if [ ! -f "./mvnw" ]; then
    echo "Error: Must be run from directory containing mvnw"
    exit 1
fi

# Check if vector-store directory exists
if [ ! -d "./vector-stores" ]; then
    echo "Error: vector-stores directory not found"
    exit 1
fi

# Function to log with timestamp
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Get list of directories
log "Finding directories under ./vector-store"
directories=$(find ./vector-stores -type d -mindepth 1)

if [ -z "$directories" ]; then
    log "No directories found under ./vector-stores"
    exit 1
fi

# Print found directories
log "Found directories:"
echo "$directories" | sed 's/^/  /'

# Process each directory
while IFS= read -r dir; do
    log "Processing directory: $dir"
    cd "$dir" || continue
    
    log "Running Maven integration tests for $dir"
    # Use the mvnw from the root directory
    "$ROOT_DIR/mvnw" package -Pintegration-tests
    
    build_status=$?
    if [ $build_status -eq 0 ]; then
        log "Maven build completed successfully for $dir"
    else
        log "Maven build failed for $dir"
        # Return to root directory before exiting
        cd "$ROOT_DIR"
        exit 1
    fi
    
    # Return to root directory for next iteration
    cd "$ROOT_DIR"
done <<< "$directories"

log "All directories processed successfully"
