package org.scalamacros

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.{Context => BlackboxContext}
import scala.reflect.macros.whitebox.{Context => WhiteboxContext}

package object resetallattrs {
  implicit class ResetAllAttrs(val c: BlackboxContext) {
    def resetAllAttrs[T](tree: T): Any = macro Macros.impl
  }
}

package resetallattrs {
  object Macros {
    def impl(c: WhiteboxContext)(tree: c.Tree): c.Tree = {
      import c.universe._
      val q"$_.ResetAllAttrs($context).resetAllAttrs[..$_](...$_)" = c.macroApplication
      q"new _root_.org.scalamacros.resetallattrs.Helper[$context.type]($context).impl($tree)"
    }
  }

  class Helper[C <: BlackboxContext](val c: C) {
    def impl(tree: c.Tree): c.Tree = ???
  }
}