import org.scalatest._
import org.scalamacros.resetallattrs._

class SignatureSuite extends FunSuite {
  test("path-dependent types work just fine") {
    def foo1(c: scala.reflect.macros.blackbox.Context)(x: c.Tree): c.Tree = {
      val x1: c.Tree = c.resetAllAttrs(x)
      x1
    }
    def foo2(c: scala.reflect.macros.whitebox.Context)(y: c.Tree): c.Tree = {
      val y1: c.Tree = c.resetAllAttrs(y)
      y1
    }
  }
}