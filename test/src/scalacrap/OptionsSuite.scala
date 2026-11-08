package scalacrap

import java.nio.file.Paths

class OptionsSuite extends munit.FunSuite {

  test("defaults") {
    val o = Options.parse(Nil).toOption.get
    assertEquals(o.threshold, None)
    assert(!o.changed && !o.allowStale && !o.noCoverage && !o.help)
  }

  test("flags and values") {
    val root = java.nio.file.Files.createTempDirectory("root").toRealPath()
    val args = List("--root", root.toString, "--coverage", "/c1", "--coverage", "/c2", "--edn", "/e", "--threshold", "9.5",
      "--top", "3", "--changed", "--allow-stale", "--no-coverage", "--help", "Foo", "Bar")
    val o = Options.parse(args).toOption.get
    assertEquals(o.root, root)
    assertEquals(o.coverage, Seq(Paths.get("/c1"), Paths.get("/c2")))
    assertEquals(o.edn, Some(Paths.get("/e")))
    assertEquals(o.threshold, Some(9.5))
    assertEquals(o.top, Some(3))
    assert(o.changed && o.allowStale && o.noCoverage && o.help)
    assertEquals(o.filters, Seq("Foo", "Bar"))
  }

  Seq(List("--nope"), List("--root"), List("--root", "/no/such/directory"), List("--threshold", "ten"), List("--top", "x")).foreach(args =>
    test(s"${args.mkString(" ")} is an error")(assert(Options.parse(args).isLeft)))
}
