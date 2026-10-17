#!/usr/bin/env bash
set -euo pipefail

# setup-and-test invokes this hook before the Quarkus SNAPSHOT tests.
primary_image="public.ecr.aws/docker/library/postgres:18"
fallback_image="docker.io/library/postgres:18"

if timeout 120s docker pull "$primary_image"; then
    exit 0
fi

echo "::warning::PostgreSQL pull from ECR failed; trying Docker Hub."
if ! timeout 120s docker pull "$fallback_image"; then
    echo "::error::Unable to pull PostgreSQL 18 from either ECR or Docker Hub."
    exit 1
fi

# The demo test resource expects this name and reuses the locally tagged image.
docker tag "$fallback_image" "$primary_image"
