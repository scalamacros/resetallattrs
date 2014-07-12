### resetallattrs

In Scala 2.10, when macros were first introduced, our public API exposed two low-level methods: `Context.resetLocalAttrs` and
`Context.resetAllAttrs`. While these methods wouldn't be needed at all if our internal implementation of the macro API were more advanced,
at our past level of technology we needed them (and we still do) to deal with possible inconsistencies in partially synthetic trees.

Without going into details, experience has shown that `resetLocalAttrs` can deal with a majority of possible inconsistencies
having a mild chance of corrupting trees as a side-effect of its operation, whereas `resetAllAttrs` can deal with some additional inconsistencies,
but it's almost always guaranteed to corrupt trees. Go through our [Macrology 201](https://github.com/scalamacros/macrology201)
tutotial to learn more about this topic.

Anyway, based on what we've learned about resetAttrs methods, in Scala 2.11.0 we removed `resetAllAttrs` and renamed `resetLocalAttrs`
to `untypecheck`, branding `resetLocalAttrs` as the one and only public way of fixing inconsistencies in trees.
When doing that, we expected that `resetLocalAttrs` should be enough for virtually everyone who previously used `resetAllAttrs`.

Unfortunately, that ended up being not the case and due to binary compatibility constraints we can't just reintroduce `resetAllAttrs` in 2.11.x
(or even in 2.12.x, for that matter). So here we go, reinstating `resetAllAttrs` in a separate library for the cases when it's really necessary.

### Usage

In your SBT build you need to write a one-liner:

```
libraryDependencies += "org.scalamacros" %% "resetallattrs" % "1.0.0-SNAPSHOT"
```

Then in your macros you write another one-liner, and that enables you to use `resetAllAttrs` like in the good old days.
Beware, all usual caveats apply. Please try `untypecheck` first, and only then reach for `resetAllAttrs`.

```
def impl(c: Context)(...) = {
  ...
  import org.scalamacros.resetallattrs._
  c.resetAllAttrs(...)
  ...
}
```
