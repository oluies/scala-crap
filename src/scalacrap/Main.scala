package scalacrap

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Path }

object Main {

  val ok = 0
  val usageError = 1
  val overThreshold = 2
  val untrustworthy = 3

  def main(args: Array[String]): Unit = sys.exit(run(args.toList))

  def run(args: List[String]): Int =
    Options.parse(args) match {
      case Left(message)                 => fail(s"$message\n${Options.usage}")
      case Right(options) if options.help => println(Options.usage); ok
      case Right(options)                 => selectSources(options).fold(fail, score(options, _))
    }

  private def fail(message: String): Int = {
    System.err.println(message)
    usageError
  }

  private def selectSources(options: Options): Either[String, Seq[Path]] = {
    val all = Sources.find(options.root).filter(matches(options.filters))
    if (options.changed) Sources.changed(options.root).map(changed => all.filter(changed)) else Right(all)
  }

  private def matches(filters: Seq[String])(path: Path): Boolean =
    filters.isEmpty || filters.exists(path.toString.contains)

  private def score(options: Options, sources: Seq[Path]): Int = {
    val parsed = sources.map(p => p -> Complexity.methods(p.toString, new String(Files.readAllBytes(p), UTF_8)))
    val unparsed = parsed.collect { case (p, Left(message)) => s"could not parse $p: $message" }
    val methods = parsed.collect { case (_, Right(found)) => found }.flatten
    val coverage = CoverageCheck(options, sources, methods)
    val entries = methods.map(m => Entry(m, coverage.perMethod(m))).sorted(Crap.ordering)

    print(options, entries, sources.size)
    writeEdn(options, entries)
    (unparsed ++ coverage.problems).foreach(p => System.err.println(s"warning: $p"))
    exitCode(options, entries, unparsed, coverage)
  }

  private def print(options: Options, entries: Seq[Entry], files: Int): Unit = {
    val unknown = entries.count(_.coverage.isEmpty)
    println(Report.table(options.top.fold(entries)(entries.take)))
    println(s"\nScored ${entries.size} methods in $files files; ${entries.count(_.crap.exists(_ >= 10.0))} at or above CRAP 10; " +
      s"$unknown without coverage.")
  }

  private def writeEdn(options: Options, entries: Seq[Entry]): Unit = {
    val path = options.edn.getOrElse(options.root.resolve(".metrics/crap-scala.edn"))
    Files.createDirectories(path.getParent)
    Files.write(path, Report.edn(entries).getBytes(UTF_8))
    println(s"Wrote $path")
  }

  private def exitCode(options: Options, entries: Seq[Entry], unparsed: Seq[String], coverage: CoverageCheck): Int =
    options.threshold.fold(ok) { threshold =>
      val blocked = unparsed.nonEmpty || coverage.blocksGate(options)
      if (blocked) untrustworthy
      else if (entries.flatMap(_.crap).maxOption.exists(_ > threshold)) overThreshold
      else ok
    }
}
