package scalacrap

class CrapSuite extends munit.FunSuite {

  private def method(cc: Int, name: String = "m") = Method(name, "ns", "f", 0, 1, cc)

  test("a fully covered method scores its complexity") {
    assertEquals(Crap.score(5, 100.0), 5.0)
  }

  test("an uncovered method scores complexity squared plus complexity") {
    assertEquals(Crap.score(5, 0.0), 30.0)
  }

  test("partial coverage cubes the uncovered fraction") {
    assertEqualsDouble(Crap.score(10, 50.0), 22.5, 1e-9)
  }

  test("an entry without coverage has no score") {
    assertEquals(Entry(method(3), None).crap, None)
  }

  test("entries sort worst first, unknown coverage last") {
    val entries = Seq(Entry(method(3, "unknown"), None), Entry(method(2, "low"), Some(100.0)), Entry(method(4, "high"), Some(0.0)))
    assertEquals(entries.sorted(Crap.ordering).map(_.method.name), Seq("high", "low", "unknown"))
  }
}
