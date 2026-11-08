package scalacrap

final case class Method(
    name: String,
    namespace: String,
    file: String,
    start: Int,
    end: Int,
    complexity: Int)

final case class Entry(method: Method, coverage: Option[Double]) {
  val crap: Option[Double] = coverage.map(Crap.score(method.complexity, _))
}

object Crap {

  /** CRAP = CC² × (1 − coverage)³ + CC, with coverage as a percentage. */
  def score(complexity: Int, coveragePercent: Double): Double = {
    val cc = complexity.toDouble
    cc * cc * math.pow(1.0 - coveragePercent / 100.0, 3) + cc
  }

  /** Worst first; methods without coverage after those with a score. */
  val ordering: Ordering[Entry] =
    Ordering.by(e => (-e.crap.getOrElse(-1.0), -e.method.complexity, e.method.namespace, e.method.name))
}
