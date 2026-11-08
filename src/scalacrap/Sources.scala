package scalacrap

import java.nio.file.{ Files, LinkOption, Path }
import scala.jdk.CollectionConverters._
import scala.sys.process.Process
import scala.util.{ Try, Using }

/** Finds the production Scala sources of a Mill or sbt project. */
object Sources {

  private val buildOutput = Set("out", "target", "node_modules")
  private val testDirectories = Set("test", "tests", "it")
  private val testSuffixes = Seq("Suite.scala", "Spec.scala", "Test.scala", "Tests.scala")

  def find(root: Path): Seq[Path] =
    walk(root, root).filter(p => isProductionSource(root.relativize(p))).sorted

  /** Files git reports as changed, added or untracked. */
  def changed(root: Path): Either[String, Set[Path]] =
    Try(Process(Seq("git", "status", "--porcelain", "-uall"), root.toFile).!!).toEither.left
      .map(e => s"git status failed: ${e.getMessage}")
      .map(_.linesIterator.flatMap(_.drop(3).split(" -> ").lastOption).map(f => root.resolve(f).normalize).toSet)

  /** Tests live in a test directory beside src, directly under src, or in a *Suite-style file. */
  private[scalacrap] def isProductionSource(relative: Path): Boolean = {
    val segments = relative.iterator.asScala.map(_.toString).toList
    val (beforeSrc, fromSrc) = segments.init.span(_ != "src")
    val name = segments.last
    name.endsWith(".scala") && !testSuffixes.exists(name.endsWith) &&
    !beforeSrc.exists(testDirectories) && !fromSrc.lift(1).exists(testDirectories)
  }

  private def walk(root: Path, directory: Path): List[Path] =
    Using.resource(Files.list(directory))(_.iterator.asScala.toList).flatMap(p =>
      if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
        if (isSkipped(root.relativize(p))) Nil else walk(root, p)
      } else List(p))

  /** Hidden directories, and build output outside any src directory, where such names can be packages. */
  private def isSkipped(relative: Path): Boolean = {
    val segments = relative.iterator.asScala.map(_.toString).toList
    segments.last.startsWith(".") || (!segments.contains("src") && buildOutput(segments.last))
  }
}
