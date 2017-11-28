#!/bin/bash

DOCS_DIR="`pwd`"
REPO_DIR="$DOCS_DIR/../.."
GH_PAGES_NAME="metronome-gh-pages"
GH_PAGES_DIR="$REPO_DIR/$GH_PAGES_NAME"

# fail on error
set -e

raml2html "$DOCS_DIR/../api/src/main/resources/public/api/api.raml" > "$DOCS_DIR/docs/generated/api.html";
