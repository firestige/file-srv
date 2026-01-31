#!/bin/bash
# Smart Git Commit Script
# Usage: git-commit.sh <commit-msg-file>
# Commits using the provided file

set -e

COMMIT_FILE="$1"

if [ -z "$COMMIT_FILE" ]; then
    echo "Error: Commit message file is required"
    echo "Usage: $0 <path-to-commit-msg-file>"
    exit 1
fi

if [ ! -f "$COMMIT_FILE" ]; then
    echo "Error: File not found: $COMMIT_FILE"
    exit 1
fi

git commit -F "$COMMIT_FILE"
echo "✓ Committed using file: $COMMIT_FILE"

