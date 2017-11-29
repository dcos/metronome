#!/bin/bash

DOCS_DIR="`pwd`"
REPO_DIR="$DOCS_DIR/../.."
GH_PAGES_NAME="metronome-gh-pages"
GH_PAGES_DIR="$REPO_DIR/$GH_PAGES_NAME"

# fail on error
set -e

# clone as separate repo
cd $REPO_DIR;
rm -fr "$GH_PAGES_NAME"
git clone git@github.com:dcos/metronome.git $GH_PAGES_NAME;

# switch branch
cd $GH_PAGES_DIR;
git checkout gh-pages;

# copy all docs from master
cd $DOCS_DIR;
cp -r * "$GH_PAGES_DIR";
raml2html "$DOCS_DIR/../api/src/main/resources/public/api/api.raml" > "$GH_PAGES_DIR/docs/generated/api.html";

# commit changes
cd $GH_PAGES_DIR;
git add .
git commit -m "Docs synced"

echo "Changes synced but not pushed - you need to push the commit"
