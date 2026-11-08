package scalacrap

import java.nio.file.{ Path, Paths }
import scala.annotation.tailrec
import scala.util.Try

final case class Options(
    root: Path = Paths.get("").toAbsolutePath,
    coverage: Seq[Path] = Seq.empty,
    edn: Option[Path] = None,
    threshold: Option[Double] = None,
    top: Option[Int] = None,
    changed: Boolean = false,
    allowStale: Boolean = false,
    noCoverage: Boolean = false,
    help: Boolean = false,
    filters: Seq[String] = Seq.empty)

object Options {

  val usage: String =
    """usage: scala-crap [options] [path filter ...]
      |
      |  --root DIR       project root (default: the current directory)
      |  --coverage FILE  scoverage XML report; repeatable (default: found under the root)
      |  --no-coverage    complexity only
      |  --threshold N    exit 2 when the worst CRAP is above N; exit 3 when the scores can't be trusted
      |  --allow-stale    let --threshold pass on a coverage report older than the sources
      |  --changed        only files git reports as changed
      |  --top N          print only the worst N
      |  --edn FILE       EDN output (default: .metrics/crap-scala.edn under the root)
      |  --help""".stripMargin

  private val switches: Map[String, Options => Options] = Map(
    "--help" -> (_.copy(help = true)),
    "--changed" -> (_.copy(changed = true)),
    "--allow-stale" -> (_.copy(allowStale = true)),
    "--no-coverage" -> (_.copy(noCoverage = true))
  )

  private val valued: Map[String, (Options, String) => Either[String, Options]] = Map(
    "--root" -> ((o, v) => Try(Paths.get(v).toRealPath()).toEither.left.map(_ => s"--root: no such directory: $v").map(p => o.copy(root = p))),
    "--coverage" -> ((o, v) => Right(o.copy(coverage = o.coverage :+ Paths.get(v).toAbsolutePath))),
    "--edn" -> ((o, v) => Right(o.copy(edn = Some(Paths.get(v).toAbsolutePath)))),
    "--threshold" -> ((o, v) => Try(v.toDouble).toEither.left.map(_ => s"--threshold needs a number, not $v").map(n => o.copy(threshold = Some(n)))),
    "--top" -> ((o, v) => Try(v.toInt).toEither.left.map(_ => s"--top needs a whole number, not $v").map(n => o.copy(top = Some(n))))
  )

  @tailrec
  def parse(args: List[String], options: Options = Options()): Either[String, Options] =
    if (args.isEmpty) Right(options)
    else step(args, options) match {
      case Left(message)          => Left(message)
      case Right((rest, updated)) => parse(rest, updated)
    }

  private def step(args: List[String], options: Options): Either[String, (List[String], Options)] = args match {
    case flag :: rest if switches.contains(flag)      => Right((rest, switches(flag)(options)))
    case flag :: value :: rest if valued.contains(flag) => valued(flag)(options, value).map(rest -> _)
    case flag :: _ if flag.startsWith("--")           => Left(s"unknown or incomplete option: $flag")
    case other                                        => Right((other.tail, options.copy(filters = options.filters :+ other.head)))
  }
}
