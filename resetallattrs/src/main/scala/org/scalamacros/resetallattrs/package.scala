package org.scalamacros

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.{Context => BlackboxContext}
import scala.reflect.macros.whitebox.{Context => WhiteboxContext}

package object resetallattrs {
  implicit class ResetAllAttrs(val c: BlackboxContext) {
    def resetAllAttrs[T, U](tree: T): U = macro Macros.impl
  }
}

package resetallattrs {
  object Macros {
    def impl(c: WhiteboxContext)(tree: c.Tree): c.Tree = {
      ???
    }
  }
}