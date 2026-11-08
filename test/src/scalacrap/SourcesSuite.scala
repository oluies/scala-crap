package scalacrap

import java.nio.file.{ Files, Paths }
import scala.sys.process.Process

class SourcesSuite extends munit.FunSuite {

  private def production(path: String) = Sources.isProductionSource(Paths.get(path))

  Seq("a/src/main/scala/x/F.scala", "src/x/F.scala", "module/src/F.scala", "src/main/scala/test/F.scala", "F.scala")
    .foreach(path => test(s"$path is production code")(assert(production(path))))

  Seq("a/src/test/scala/F.scala", "module/test/src/F.scala", "src/it/scala/F.scala", "src/x/FSuite.scala",
    "src/x/FSpec.scala", "src/x/FTest.scala", "src/x/FTests.scala", "src/x/F.java")
    .foreach(path => test(s"$path is not production code")(assert(!production(path))))

  private def write(root: java.nio.file.Path, relative: String) = {
    val p = root.resolve(relative)
    Files.createDirectories(p.getParent)
    Files.writeString(p, "object O")
    p
  }

  test("build output and hidden directories are skipped, unless they are packages under src") {
    val root = Files.createTempDirectory("sources")
    val kept = write(root, "src/out/F.scala")
    val target = write(root, "src/target/G.scala")
    write(root, "out/gen/H.scala")
    write(root, "module/target/I.scala")
    write(root, ".metals/J.scala")
    assertEquals(Sources.find(root), Seq(kept, target).sorted)
  }

  test("changed files come from git, including new ones") {
    val root = Files.createTempDirectory("changed")
    val git = (args: String) => assertEquals(Process(s"git $args", root.toFile).!, 0)
    git("init -q")
    git("config user.email t@example.com")
    git("config user.name t")
    write(root, "src/Old.scala")
    git("add .")
    git("commit -qm first")
    val added = write(root, "src/sub/New.scala").toRealPath()
    val changed = Sources.changed(root.toRealPath()).fold(fail(_), identity)
    assertEquals(changed, Set(added))
  }

  test("changed files outside a git repository are an error") {
    assert(Sources.changed(Files.createTempDirectory("nogit")).isLeft)
  }
}
