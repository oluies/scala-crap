package scalacrap

import java.nio.file.{ Files, Path }

/** The coverage to score with, and what is wrong with it. */
final case class CoverageCheck(
    perMethod: Map[Method, Option[Double]],
    missing: Option[String],
    stale: Option[String]) {

  val problems: Seq[String] = missing.toSeq ++ stale

  /** Complexity-only runs have no coverage to distrust; --no-coverage with a gate is the caller's choice. */
  def blocksGate(options: Options): Boolean =
    !options.noCoverage && (missing.nonEmpty || (stale.nonEmpty && !options.allowStale))
}

object CoverageCheck {

  def apply(options: Options, sources: Seq[Path], methods: Seq[Method]): CoverageCheck =
    if (options.noCoverage) CoverageCheck(methods.map(_ -> None).toMap, None, None)
    else fromReports(reportsFor(options), sources, methods)

  private def reportsFor(options: Options): Seq[Path] =
    if (options.coverage.nonEmpty) options.coverage else Coverage.findReports(options.root)

  private def fromReports(reports: Seq[Path], sources: Seq[Path], methods: Seq[Method]): CoverageCheck = {
    val statements = Coverage.merge(reports.filter(Files.exists(_)).map(Coverage.load))
    val perMethod = Coverage.assign(methods, statements)
    CoverageCheck(perMethod, missing(reports, statements, perMethod), stale(reports, sources))
  }

  private def missing(
      reports: Seq[Path],
      statements: Coverage.Statements,
      perMethod: Map[Method, Option[Double]]): Option[String] =
    reports.find(!Files.exists(_)).map(p => s"no coverage report at $p")
      .orElse(if (statements.isEmpty) Some("no coverage report found; scoring complexity only") else None)
      .orElse(
        if (perMethod.nonEmpty && perMethod.values.forall(_.isEmpty))
          Some("the coverage report matches none of the sources; was it produced from another checkout?")
        else None)

  /** A report older than a source describes other code: its offsets and counts no longer apply. */
  private def stale(reports: Seq[Path], sources: Seq[Path]): Option[String] = {
    val existing = reports.filter(Files.exists(_))
    val newest = sources.maxByOption(modified)
    for {
      oldest <- existing.minByOption(modified)
      source <- newest
      if modified(source) > modified(oldest)
    } yield s"coverage report $oldest is older than $source; rerun the tests and the report"
  }

  private def modified(path: Path): Long = Files.getLastModifiedTime(path).toMillis
}
