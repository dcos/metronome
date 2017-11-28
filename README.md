# Metronome Docs and Website

## Run it locally

Ensure you have installed everything listed in the dependencies section before
following the instructions.

### Dependencies

* [Bundler](http://bundler.io/)
* [Node.js](http://nodejs.org/) (for compiling assets)
* Python
* Ruby
* [RubyGems](https://rubygems.org/)

### Instructions

#### Native OS

1. Install packages needed to generate the site

    * On Linux:

            $ apt-get install ruby-dev make autoconf nodejs nodejs-legacy python-dev npm

    * On Mac OS X:

            $ brew install node

2. Clone the Metronome repository

3. Change into the "docs" directory where docs live

        $ cd docs/

4. Install Bundler

        $ gem install bundler

5. Install the bundle's dependencies

        $ bundle install --path vendor/bundle

6. Start the web server

        $ bundle exec jekyll serve --watch

7. Visit the site at
   [http://localhost:4000/metronome/](http://localhost:4000/metronome/)

## Deploying the site

### Prerequisite
You will need the raml2html installed that works with RAML 0.8.

`npm install -g raml2html@3`

Run the `./update_docs.sh`

or the manual steps are:

1. Clone a separate copy of the Metronome repo as a sibling of your normal
   Metronome project directory and name it "metronome-gh-pages".

        $ git clone git@github.com:dcos/metronome.git metronome-gh-pages

2. Check out the "gh-pages" branch.

        $ cd /path/to/metronome-gh-pages
        $ git checkout gh-pages

3. Check out the appropriate release branch, then copy the contents of the "docs" directory in master to the root of your
   metronome-gh-pages directory.

        $ cd /path/to/metronome
        $ git checkout releases/1.x
        $ # to make sure we also remove deleted documentation, we need to delete all files first.
        $ # please note, rm -r ../metronome-gh-pages/* will not delete dot-files
        $ rm -r ../metronome-gh-pages/*
        $ cp -r docs/** ../metronome-gh-pages

4. Change to the metronome-gh-pages directory, commit, and push the changes

        $ cd /path/to/metronome-gh-pages
        $ git commit . -m "Syncing docs with release branch"
        $ git push
