---
title: Contributor Guidelines
---


# Contributor Guidelines

## Contributing Documentation Changes

Confused about our documentation? Run into a
pitfall that others also might run into? Help us making the Metronome documentation great.
 
The documentation published [here](https://dcos.github.io/metronome/) is
generated from the '/docs' directory of this repository.

If you just want to correct a spelling mistake or improve the wording of a sentence, browse
the markdown files [here](https://github.com/dcos/metronome/tree/master/docs) and use the edit
button above the markup. That will make it easy to create a pull request for us to review.

If you want to contribute a larger improvement to our documentation: 

* Edit the files in the docs directory.
* Render and check the result locally. Instructions are in
  [`docs/README.md`](https://github.com/dcos/metronome/blob/master/docs/README.md).
* If you are feeling like a perfectionist, check if there is already an issue about your documentation improvement
  [here](https://github.com/dcos/metronome/issues?q=is%3Aopen+is%3Aissue+label%3Adocs).
* Prefix your commit message with "Fixes #1234 - ", where #1234 is the GitHub issue number
  that your pull request relates to.
* Create a pull request against master.

Please rebase your pull requests on top of the current master using
  `git fetch origin && git rebase origin/master` and squash your changes to a single commit as
  described [here](http://gitready.com/advanced/2009/02/10/squashing-commits-with-rebase.html).
  
  Yes, we want you to rewrite history: the branch on which you are
  implementing your changes is only meant for your pull request. You can
  either rebase before or after you squash your commits, depending on how
  you'd like to resolve potential merge conflicts. The idea behind this is that we
  don't want an arbitrary number of commits for one pull request, but exactly
  one commit. This commit should be easy to merge onto master, therefore we
  ask you to rebase to master.
    
After your pull request has been accepted, there is still some manual work that we need to do to publish your 
documentation. But don't worry: we will do that for you.

## Getting Started with Code Changes

Maybe you already have a bugfix or enhancement in mind.  If not, there are a
number of relatively approachable issues with the label
['good first issue'](https://github.com/dcos/metronome/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22).

<!--
## License Agreement

_TODO_: Do we need a CLA?
-->

## Submitting Code Changes to Metronome

- A GitHub pull request is the preferred way of submitting patch sets. Please
  rebase your pull requests on top of the current master using
  `git fetch origin && git rebase origin/master`, and squash your changes to a single commit as
  described [here](http://gitready.com/advanced/2009/02/10/squashing-commits-with-rebase.html).
  Yes, we want you to rewrite history: the branch on which you are
  implementing your changes is only meant for your pull request. You can
  either rebase before or after you squash your commits, depending on how
  you'd like to resolve potential merge conflicts. The idea behind this is that we
  don't want an arbitrary number of commits for one pull request, but exactly
  one commit. This commit should be easy to merge onto master, therefore we
  ask you to rebase to master.
  
- Please start your commit message with "Fixes #1234 - ", where #1234 is the GitHub issue number
  that your pull request relates to. GitHub will automatically link this PR to the issue and make it more
  visible to others.

- Any changes in the public API or behavior must be reflected in the project
  documentation.

- Any changes in the public API or behavior must be reflected in the changelog.md.

- Pull requests should include appropriate additions to the unit test suite.

- If the change is a bugfix, the tests you add must fail without the patch
  as a safeguard against future regressions.

- Run all tests via the supplied `./bin/run-tests.sh` script (requires docker).

## Source Files

- Public classes should be defined in a file of the same name, except for
  trivial subtypes of a _sealed trait_.  In that case, the file name must be
  the plural form of the trait, and it must begin with a lowercase letter.

## Style

### Style Checker

Executing the `test` task in SBT also invokes the style checker
([scalastyle](http://www.scalastyle.org/)). Some basic style issues will
cause the build to fail:

- Public methods that lack an explicit return type annotation.
- Source code lines that exceed 120 columns.

Other potential problems are output as warnings.

### Type Annotations

Scala has a powerful type inference engine. To increase readability, include more type
annotations than the minumum required to make the program compile.

- Methods with `public` or `protected` scope must have an explicit return type
  annotation. Without it, an implementaiton change later on could
  accidentally change the public API if a new type is inferred.

- Nontrivial expressions must be explicitly annotated. Of course, "nontrivial"
  is subjective. As a rule of thumb, if an expression contains more than one
  higher-order function, annotate the result type and/or consider breaking the
  expression down into simpler intermediate values.

### Null

Avoid assigning the `null` value. It's usually only necessary when
calling into a Java library that ascribes special semantics to `null`. Use
`scala.Option` in all other cases.

### Higher Order Functions

#### GOOD:

```scala
xs.map(f)
```

```scala
xs.map(_.size)
```

```scala
xs.map(_ * 2)
```

```scala
xs.map { item =>
  item -> f(item)
}
```

```scala
xs.map {
  case a: A if a.isEmpty => f(a)
  case a: A              => g(a)
  case b: B              => h(b)
  case other: _          => e(other)
}
```

_Note: match expressions must be exhaustive unless the higher-order function
signature explicitly expects a `PartialFunction[_, _]`, as in `collect`, and
`collectFirst`._

```scala
for {
  x <- xs
  y <- x
  z <- y
} yield z
```

#### BAD:

```scala
xs map f // dangerous if more infix operators follow
```

```scala
xs.map(item => f(item)) // use curlies
```

```scala
xs.flatMap(_.flatMap(_.map(_.z))) // replace with a for comprehension
```

