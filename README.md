# scala-crap

A [CRAP](https://testing.googleblog.com/2011/02/this-code-is-crap.html) score for every Scala method, and a CI gate that won't pass on bad data.

```
CRAP = CC² × (1 − coverage)³ + CC
```

Complexity comes from parsing the sources with [scalameta](https://scalameta.org), so Scala 2.13 and Scala 3 both work. Coverage comes from [scoverage](https://github.com/scoverage) XML reports. A CC 18 method needs splitting, since even full coverage leaves it at 18; a CC 3 method at 0% needs a test.

## Use

Build the jar, then run it from the root of a Mill or sbt project after producing a scoverage report:

```bash
./mill assembly                                  # in this repository
java -jar out/assembly.dest/out.jar --help
```

```bash
# Mill project                                   # sbt project
./mill __.test                                   sbt coverage test coverageReport
./mill __.scoverage.xmlReport
java -jar /path/to/out.jar --threshold 10        java -jar /path/to/out.jar --threshold 10
```

It prints methods worst first and writes `.metrics/crap-scala.edn`, in the format [crapper](https://github.com/unclebob/crapper) writes.

| Option | |
| --- | --- |
| `--root DIR` | project root; default the current directory |
| `--coverage FILE` | a scoverage XML report, repeatable; default found under the root |
| `--no-coverage` | complexity only |
| `--threshold N` | gate on the worst score; see exit codes |
| `--allow-stale` | let the gate pass on a report older than the sources |
| `--changed` | only files git reports as changed |
| `--top N` | print only the worst N; the EDN keeps every method |
| `--edn FILE` | where to write the EDN |
| `PATH...` | only sources whose path contains one of these |

| Exit code | Meaning |
| --- | --- |
| 0 | no `--threshold`, or the worst score is at or below it |
| 1 | bad arguments, or `--changed` outside a git repository |
| 2 | a score is above the threshold |
| 3 | the scores can't be trusted, so the gate refuses to pass |

## Why exit code 3

A CRAP gate is only as good as its coverage data, and a stale report fails quietly. A failed test can skip the report step, and the scores then come from the previous run's report against the new code: well-tested methods score as uncovered, and newly tested ones keep their old scores. With `--threshold`, each of these is exit 3:

- the coverage report is older than a source file (`--allow-stale` overrides this one);
- no report exists, or a named one is missing;
- the report matches none of the sources, as when it came from another checkout;
- a source file doesn't parse.

Without `--threshold` they are warnings.

## What is counted

**Complexity** is 1 plus one for each `if`, `while`, `do`, `for`, `for` guard, `case` clause (`match`, partial function, `catch`), `case` guard, `&&` and `||`. Function literals count toward the enclosing method. Local methods are scored on their own as `outer.inner`, and methods of nested types on their own. Abstract methods are skipped.

**Coverage** is statement coverage, each statement counted in the innermost method containing its start. A method with no statements in an instrumented file, such as one returning a constant, counts as covered. A file missing from the report has unknown coverage and no score.

**Sources** are every `.scala` file under the root except test directories (`test`, `tests` or `it` beside or directly under `src`), `*Suite`, `*Spec`, `*Test` and `*Tests` files, build output, and hidden directories. An aggregate `xmlReportAll` report is preferred over per-module reports.

## Agreement with crapper

On [ADAM](https://github.com/oluies/adam) (2,208 Scala methods, scored in 4.7 s), complexity matches crapper's Scala strategy on every method name both report, and coverage matches on all but 9 of 1,937 names. Those 9 have no scoverage statements: crapper scores them as uncovered and this tool as covered. Secondary constructors are scored here and skipped there.

## Development

```bash
./mill test                                      # tests
./mill scoverage.xmlReport                       # coverage from the last test run
./mill assembly
java -jar out/assembly.dest/out.jar --threshold 10
```

CI runs this gate on the project itself.

Started from a script in ADAM, and built to agree with crapper's Scala scoring. Apache License 2.0.
