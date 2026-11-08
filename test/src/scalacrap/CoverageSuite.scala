package scalacrap

import java.nio.file.Files

class CoverageSuite extends munit.FunSuite {

  private def method(name: String, start: Int, end: Int, file: String = "/a/F.scala") =
    Method(name, "ns", file, start, end, 1)

  test("coverage is the share of a method's statements that ran") {
    val m = method("m", 0, 100)
    val statements = Map("/a/F.scala" -> Seq(Statement(1, true), Statement(2, true), Statement(3, false), Statement(4, false)))
    assertEquals(Coverage.assign(Seq(m), statements)(m), Some(50.0))
  }

  test("a statement counts for its innermost method only") {
    val outer = method("outer", 0, 100)
    val inner = method("inner", 10, 20)
    val statements = Map("/a/F.scala" -> Seq(Statement(5, true), Statement(15, false)))
    val coverage = Coverage.assign(Seq(outer, inner), statements)
    assertEquals(coverage(outer), Some(100.0))
    assertEquals(coverage(inner), Some(0.0))
  }

  test("a method's end is exclusive") {
    val m = method("m", 0, 10)
    assertEquals(Coverage.assign(Seq(m), Map("/a/F.scala" -> Seq(Statement(10, false))))(m), Some(100.0))
  }

  test("a method without statements in an instrumented file is covered") {
    val m = method("m", 0, 10)
    assertEquals(Coverage.assign(Seq(m), Map("/a/F.scala" -> Seq(Statement(50, false))))(m), Some(100.0))
  }

  test("a file missing from the report has unknown coverage") {
    val m = method("m", 0, 10)
    assertEquals(Coverage.assign(Seq(m), Map.empty[String, Seq[Statement]])(m), None)
  }

  test("paths are compared normalized") {
    val m = method("m", 0, 10, "/a/./F.scala")
    assertEquals(Coverage.assign(Seq(m), Map("/a/F.scala" -> Seq(Statement(1, true))))(m), Some(100.0))
  }

  test("a report is read per source, leaving out ignored statements") {
    val report = Files.createTempFile("scoverage", ".xml")
    Files.writeString(report,
      """<scoverage><packages><package><classes><class><methods><method><statements>
        |<statement source="/a/F.scala" start="3" invocation-count="2" ignored="false"/>
        |<statement source="/a/F.scala" start="9" invocation-count="0" ignored="false"/>
        |<statement source="/a/F.scala" start="7" invocation-count="0" ignored="true"/>
        |</statements></method></methods></class></classes></package></packages></scoverage>""".stripMargin)
    assertEquals(Coverage.load(report), Map("/a/F.scala" -> Seq(Statement(3, true), Statement(9, false))))
  }

  test("the first report wins for a file in two reports") {
    val first = Map("/a" -> Seq(Statement(1, true)))
    val second = Map("/a" -> Seq(Statement(1, false)), "/b" -> Seq(Statement(2, false)))
    assertEquals(Coverage.merge(Seq(first, second)), Map("/a" -> Seq(Statement(1, true)), "/b" -> Seq(Statement(2, false))))
  }

  private def touch(root: java.nio.file.Path, relative: String) = {
    val p = root.resolve(relative)
    Files.createDirectories(p.getParent)
    Files.writeString(p, "<scoverage/>")
    p
  }

  test("an aggregate report is preferred over module reports") {
    val root = Files.createTempDirectory("reports")
    touch(root, "out/a/scoverage/xmlReport.dest/scoverage.xml")
    val all = touch(root, "out/scoverage/xmlReportAll.dest/scoverage.xml")
    assertEquals(Coverage.findReports(root), Seq(all))
  }

  test("without an aggregate, every module and sbt report is used") {
    val root = Files.createTempDirectory("reports")
    val mill = touch(root, "out/a/scoverage/xmlReport.dest/scoverage.xml")
    val sbt = touch(root, "b/target/scala-2.13/scoverage-report/scoverage.xml")
    touch(root, "docs/scoverage.xml")
    assertEquals(Coverage.findReports(root).toSet, Set(mill, sbt))
  }
}
