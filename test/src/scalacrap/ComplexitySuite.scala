package scalacrap

class ComplexitySuite extends munit.FunSuite {

  private def methods(source: String): Seq[Method] =
    Complexity.methods("Test.scala", source).fold(fail(_), identity)

  private def complexityOf(body: String): Int =
    methods(s"object O { def m(x: Int, y: Boolean): Any = { $body } }").head.complexity

  test("a method with no decisions has complexity 1") {
    assertEquals(complexityOf("x + 1"), 1)
  }

  Seq(
    "if" -> "if (y) 1 else 2",
    "while" -> "while (y) { x }",
    "do" -> "do { x } while (y)",
    "for" -> "for (i <- 1 to x) { i }",
    "for yield" -> "for (i <- 1 to x) yield i",
    "&&" -> "y && y",
    "||" -> "y || y"
  ).foreach { case (name, body) =>
    test(s"$name is one decision") {
      assertEquals(complexityOf(body), 2)
    }
  }

  test("each for guard is a decision") {
    assertEquals(complexityOf("for (i <- 1 to x if i > 0 if i < 5) yield i"), 4)
  }

  test("each case is a decision, and a guard another") {
    assertEquals(complexityOf("x match { case 1 => 1; case n if n > 2 => 2; case _ => 3 }"), 5)
  }

  test("catch cases and partial function cases count") {
    assertEquals(complexityOf("try x catch { case _: Exception => 0 }"), 2)
    assertEquals(complexityOf("List(x).collect { case 1 => 1 }"), 2)
  }

  test("function literals count toward the enclosing method") {
    assertEquals(complexityOf("List(x).map(i => if (y) i else 0)"), 2)
  }

  test("local methods are scored alone, as outer.inner") {
    val found = methods("object O { def outer(y: Boolean) = { def inner = if (y) 1 else 2; if (y) inner else 0 } }")
    assertEquals(found.map(m => m.name -> m.complexity).toSet, Set("outer" -> 2, "outer.inner" -> 2))
  }

  test("methods in nested types are not counted in the method around them") {
    val found = methods("object O { def m = { object N { def n(y: Boolean) = if (y) 1 else 2 }; N } }")
    assertEquals(found.map(m => m.name -> m.complexity).toSet, Set("m" -> 1, "n" -> 2))
  }

  test("namespaces follow packages and enclosing types") {
    val found = methods("package a.b\npackage c\nclass T { object U { def m = 1 } }")
    assertEquals(found.map(_.namespace), Seq("a.b.c.T.U"))
  }

  test("traits and classes are namespaces") {
    val found = methods("trait T { def a = 1 }\nclass C { def b = 1 }")
    assertEquals(found.map(m => m.namespace -> m.name), Seq("T" -> "a", "C" -> "b"))
  }

  test("package objects are a namespace") {
    assertEquals(methods("package a\npackage object b { def m = 1 }").map(_.namespace), Seq("a.b"))
  }

  test("abstract methods are not scored; secondary constructors are") {
    val found = methods("abstract class T(x: Int) { def a: Int; def this() = this(if (true) 1 else 2) }")
    assertEquals(found.map(m => m.name -> m.complexity), Seq("this" -> 2))
  }

  test("Scala 3 syntax parses") {
    val found = methods("enum E:\n  case A\n  def f(x: Int): Int = if x > 0 then 1 else 2\n")
    assertEquals(found.map(m => m.name -> m.complexity), Seq("f" -> 2))
  }

  test("a file that does not parse is an error") {
    assert(Complexity.methods("Bad.scala", "object {").isLeft)
  }
}
