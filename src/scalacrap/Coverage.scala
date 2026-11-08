package scalacrap

import java.nio.file.{ Files, Path, Paths }
import scala.jdk.CollectionConverters._
import scala.util.Using

final case class Statement(start: Int, invoked: Boolean)

/** Statement coverage from scoverage XML reports. */
object Coverage {

  type Statements = Map[String, Seq[Statement]]

  def load(report: Path): Statements =
    (scala.xml.XML.loadFile(report.toFile) \\ "statement")
      .filter(s => (s \@ "ignored") != "true")
      .map(s => normalize(s \@ "source") -> Statement((s \@ "start").toInt, (s \@ "invocation-count").toInt > 0))
      .groupMap(_._1)(_._2)

  /** Reports for separate modules hold separate files; if one repeats, the first report wins. */
  def merge(reports: Seq[Statements]): Statements =
    reports.foldLeft(Map.empty: Statements)((merged, report) => report ++ merged)

  /** The aggregate report under root if there is one, else every module's report. */
  def findReports(root: Path): Seq[Path] = {
    val reports = Using.resource(Files.find(root, 12, (p, attrs) => attrs.isRegularFile && isReport(p)))(
      _.iterator.asScala.toList.sorted)
    val aggregate = reports.filter(_.toString.contains("xmlReportAll"))
    if (aggregate.nonEmpty) aggregate else reports
  }

  private def isReport(p: Path): Boolean =
    p.getFileName.toString == "scoverage.xml" &&
      (p.toString.contains("xmlReport") || p.toString.contains("scoverage-report"))

  /**
   * Coverage per method, each statement counted in its innermost enclosing
   * method. A method without statements in an instrumented file (one returning
   * a constant, say) is fully covered; a file missing from the report is unknown.
   */
  def assign(methods: Seq[Method], statements: Statements): Map[Method, Option[Double]] =
    methods.groupBy(_.file).flatMap { case (file, inFile) =>
      statements.get(normalize(file)) match {
        case None           => inFile.map(_ -> None)
        case Some(observed) => percentages(inFile, observed)
      }
    }

  private def percentages(methods: Seq[Method], statements: Seq[Statement]): Seq[(Method, Option[Double])] = {
    val byMethod = statements
      .flatMap(s => innermost(methods, s).map(_ -> s))
      .groupMap(_._1)(_._2)
    methods.map(m => m -> Some(percent(byMethod.getOrElse(m, Seq.empty))))
  }

  private def innermost(methods: Seq[Method], statement: Statement): Option[Method] =
    methods.filter(m => m.start <= statement.start && statement.start < m.end).minByOption(m => m.end - m.start)

  private def percent(statements: Seq[Statement]): Double =
    if (statements.isEmpty) 100.0 else 100.0 * statements.count(_.invoked) / statements.size

  private def normalize(path: String): String = Paths.get(path).normalize.toString
}
