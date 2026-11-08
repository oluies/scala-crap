package scalacrap

import java.util.Locale

object Report {

  def table(entries: Seq[Entry]): String = {
    val nameWidth = columnWidth(entries.map(_.method.name), 30, 48)
    val namespaceWidth = columnWidth(entries.map(_.method.namespace), 35, 72)
    val row = s"%-${nameWidth}s %-${namespaceWidth}s %4s %7s %8s"
    val header = row.format("Function", "Namespace", "CC", "Cov%", "CRAP")
    val rows = entries.map(e =>
      row.format(e.method.name, e.method.namespace, e.method.complexity.toString, percent(e.coverage), score(e.crap)))
    (Seq(header, "-" * header.length) ++ rows).mkString("\n")
  }

  private def columnWidth(values: Seq[String], minimum: Int, maximum: Int): Int =
    (minimum +: values.map(_.length)).max.min(maximum)

  private def percent(coverage: Option[Double]): String =
    coverage.fold("N/A")(c => "%5.1f%%".formatLocal(Locale.ROOT, c))

  private def score(crap: Option[Double]): String =
    crap.fold("N/A")(c => "%.1f".formatLocal(Locale.ROOT, c))

  /** The EDN crapper writes, readable by the same tools. */
  def edn(entries: Seq[Entry]): String =
    entries.map(ednEntry).mkString("{:entries [\n", "\n", "\n]}\n")

  private def ednEntry(e: Entry): String =
    s"  {:name ${ednString(e.method.name)}, :namespace ${ednString(e.method.namespace)}, " +
      s":complexity ${e.method.complexity}, :coverage ${ednNumber(e.coverage)}, :crap ${ednNumber(e.crap)}}"

  private def ednString(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def ednNumber(value: Option[Double]): String =
    value.fold("nil")(v => BigDecimal(v).setScale(4, BigDecimal.RoundingMode.HALF_UP).toString)
}
