package scalacrap

import scala.meta._

/**
 * Finds methods and their cyclomatic complexity: 1 plus one for each if, while,
 * do, for, for guard, case clause (match, partial function, catch), case guard,
 * && and ||. Function literals count toward the enclosing method; nested
 * methods, classes and objects are scored on their own.
 */
object Complexity {

  /** The methods in a source file, or the parser's message. */
  def methods(file: String, text: String): Either[String, Seq[Method]] =
    parse(file, text).map(visit(file)(_, "", None).distinctBy(m => (m.start, m.end, m.name)))

  private def parse(file: String, text: String): Either[String, Source] = {
    val input = Input.VirtualFile(file, text)
    dialects.Scala213(input).parse[Source] match {
      case Parsed.Success(source) => Right(source)
      case first: Parsed.Error =>
        dialects.Scala3(input).parse[Source] match {
          case Parsed.Success(source) => Right(source)
          case _: Parsed.Error        => Left(first.message)
        }
    }
  }

  private def visit(file: String)(tree: Tree, namespace: String, enclosing: Option[String]): Seq[Method] =
    typeScope(tree) match {
      case Some((name, template)) => template.children.flatMap(visit(file)(_, qualify(namespace, name), None))
      case None                   => visitMember(file)(tree, namespace, enclosing)
    }

  private def typeScope(tree: Tree): Option[(String, Template)] = tree match {
    case p: Pkg.Object  => Some(p.name.value -> p.templ)
    case t: Defn.Class  => Some(t.name.value -> t.templ)
    case t: Defn.Trait  => Some(t.name.value -> t.templ)
    case t: Defn.Object => Some(t.name.value -> t.templ)
    case t: Defn.Enum   => Some(t.name.value -> t.templ)
    case _              => None
  }

  private def visitMember(file: String)(tree: Tree, namespace: String, enclosing: Option[String]): Seq[Method] = {
    val recur = visit(file) _
    tree match {
      case p: Pkg => p.body.stats.flatMap(recur(_, qualify(namespace, p.ref.syntax), None))
      case d: Defn.Def =>
        val name = enclosing.fold(d.name.value)(outer => s"$outer.${d.name.value}")
        Method(name, namespace, file, d.pos.start, d.pos.end, 1 + decisions(d.body)) +: recur(d.body, namespace, Some(name))
      case c: Ctor.Secondary =>
        Seq(Method("this", namespace, file, c.pos.start, c.pos.end, 1 + decisions(c)))
      case other => other.children.flatMap(recur(_, namespace, enclosing))
    }
  }

  private def qualify(namespace: String, name: String): String =
    if (namespace.isEmpty) name else s"$namespace.$name"

  /** Decision points in a tree, not descending into nested methods or types. */
  private def decisions(tree: Tree): Int =
    decisionsAt(tree) + tree.children.filterNot(isScoredSeparately).map(decisions).sum

  private def decisionsAt(tree: Tree): Int = tree match {
    case _: Term.If | _: Term.While | _: Term.Do | _: Term.For | _: Term.ForYield | _: Enumerator.Guard => 1
    case c: Case                                                                                       => if (c.cond.isDefined) 2 else 1
    case t: Term.ApplyInfix if t.op.value == "&&" || t.op.value == "||"                                => 1
    case _                                                                                             => 0
  }

  private def isScoredSeparately(tree: Tree): Boolean = tree match {
    case _: Defn.Def | _: Defn.Class | _: Defn.Object | _: Defn.Trait | _: Defn.Enum | _: Ctor.Secondary => true
    case _                                                                                               => false
  }
}
