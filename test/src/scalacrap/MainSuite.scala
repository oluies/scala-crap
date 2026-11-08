package scalacrap

import java.nio.file.attribute.FileTime
import java.nio.file.{ Files, Path }

class MainSuite extends munit.FunSuite {

  private val source = "object O { def m(y: Boolean) = if (y) 1 else 2 }\n"

  private def project(): (Path, Path) = {
    val root = Files.createTempDirectory("project").toRealPath()
    val file = root.resolve("src/O.scala")
    Files.createDirectories(file.getParent)
    Files.writeString(file, source)
    (root, file)
  }

  /** A report of one statement inside m, with its modification time an hour after the source's. */
  private def report(root: Path, file: Path, invoked: Boolean, sourceName: String = ""): Path = {
    val path = root.resolve("out/scoverage/xmlReportAll.dest/scoverage.xml")
    Files.createDirectories(path.getParent)
    val name = if (sourceName.isEmpty) file.toString else sourceName
    Files.writeString(path, s"""<s><statement source="$name" start="35" invocation-count="${if (invoked) 1 else 0}" ignored="false"/></s>""")
    Files.setLastModifiedTime(path, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis + 3600000))
    path
  }

  private def gate(root: Path, threshold: String, extra: String*): Int =
    Main.run(List("--root", root.toString, "--threshold", threshold) ++ extra)

  private def edn(root: Path) = Files.readString(root.resolve(".metrics/crap-scala.edn"))

  test("the gate passes when the worst score is within the threshold") {
    val (root, file) = project()
    report(root, file, invoked = true)
    assertEquals(gate(root, "2"), Main.ok)
    assert(edn(root).contains(":name \"m\", :namespace \"O\", :complexity 2, :coverage 100.0000, :crap 2.0000"), edn(root))
  }

  test("the gate fails when a score is above the threshold") {
    val (root, file) = project()
    report(root, file, invoked = false)
    assertEquals(gate(root, "5.9"), Main.overThreshold)
    assertEquals(gate(root, "6"), Main.ok)
  }

  test("a report and a root that name the same file through a symbolic link match") {
    val (root, file) = project()
    report(root, file, invoked = true)
    val link = Files.createTempDirectory("links").resolve("project")
    Files.createSymbolicLink(link, root)
    assertEquals(Main.run(List("--root", link.toString, "--threshold", "2")), Main.ok)
    assert(edn(link).contains(":coverage 100.0000"), edn(link))
  }

  test("a stale report fails the gate, and --allow-stale lets it through") {
    val (root, file) = project()
    val r = report(root, file, invoked = true)
    Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(r).toMillis + 1000))
    assertEquals(gate(root, "100"), Main.untrustworthy)
    assertEquals(gate(root, "100", "--allow-stale"), Main.ok)
  }

  test("a missing report fails the gate") {
    val (root, _) = project()
    assertEquals(gate(root, "100"), Main.untrustworthy)
  }

  test("a named report that does not exist fails the gate") {
    val (root, _) = project()
    assertEquals(gate(root, "100", "--coverage", root.resolve("none.xml").toString), Main.untrustworthy)
  }

  test("a report from another checkout fails the gate") {
    val (root, file) = project()
    report(root, file, invoked = true, sourceName = "/elsewhere/O.scala")
    assertEquals(gate(root, "100"), Main.untrustworthy)
  }

  test("a file that does not parse fails the gate") {
    val (root, file) = project()
    report(root, file, invoked = true)
    Files.writeString(root.resolve("src/Bad.scala"), "object {")
    Files.setLastModifiedTime(root.resolve("src/Bad.scala"), Files.getLastModifiedTime(file))
    assertEquals(gate(root, "100"), Main.untrustworthy)
  }

  test("without a threshold, problems only warn") {
    val (root, _) = project()
    assertEquals(Main.run(List("--root", root.toString)), Main.ok)
    assert(edn(root).contains(":coverage nil, :crap nil"), edn(root))
  }

  test("--no-coverage passes the gate on complexity alone") {
    val (root, _) = project()
    assertEquals(gate(root, "0", "--no-coverage"), Main.ok)
  }

  test("path filters limit the sources") {
    val (root, file) = project()
    report(root, file, invoked = true)
    Main.run(List("--root", root.toString, "Nothing"))
    assert(!edn(root).contains(":name"), edn(root))
  }

  test("--edn writes where it is told, and --top limits only the table") {
    val (root, file) = project()
    report(root, file, invoked = true)
    val out = root.resolve("custom/x.edn")
    assertEquals(Main.run(List("--root", root.toString, "--edn", out.toString, "--top", "0")), Main.ok)
    assert(Files.readString(out).contains(":name \"m\""))
  }

  test("--changed outside git is a usage error; --help is fine") {
    val (root, _) = project()
    assertEquals(Main.run(List("--root", root.toString, "--changed")), Main.usageError)
    assertEquals(Main.run(List("--help")), Main.ok)
    assertEquals(Main.run(List("--nope")), Main.usageError)
  }
}
