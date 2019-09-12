package dcos.metronome.utils

import com.wix.accord.Descriptions.{Description, Explicit, Generic, Indexed, Path}
import com.wix.accord.{Failure, GroupViolation, Result, RuleViolation, Success, Validator, Violation, validate}
import dcos.metronome.Seq
import play.api.libs.json.{Json, Writes}

/**
  * Mostly copied from marathon
  */
object Validation {

  /**
    * Is thrown if an object validation is not successful.
    *
    * TODO(MARATHON-8202) - convert to Rejection
    *
    * @param obj object which is not valid
    * @param failure validation information kept in a Failure object
    */
  case class ValidationFailedException(obj: Any, failure: Failure) extends scala.RuntimeException(s"Validation failed: $failure")

  def validateOrThrow[T](t: T)(implicit validator: Validator[T]): T = validate(t) match {
    case Success => t
    case f: Failure => throw ValidationFailedException(t, f)
  }

  /**
    * Apply a validation to each member of the provided (ordered) collection, appropriately appending the index of said
    * sequence to the validation Path.
    *
    * You can use this to validate a Set, but this is not recommended as the index of failures will be
    * non-deterministic.
    *
    * Note that wix accord provide the built in `each` DSL keyword. However, this approach does not lend itself well to
    * composable validations. One can write, just fine:
    *
    *     model.values.each is notEmpty
    *
    * But this approach does lend itself well for cases such as this:
    *
    *     model.values is (condition) or every(notEmpty)
    *     model.values is optional(every(notEmpty))
    *
    * @param validator The validation to apply to each element of the collection
    */
  implicit def every[T](validator: Validator[T]): Validator[Iterable[T]] = {
    new Validator[Iterable[T]] {
      override def apply(seq: Iterable[T]): Result = {
        seq.zipWithIndex.foldLeft[Result](Success) {
          case (accum, (item, index)) =>
            validator(item) match {
              case Success => accum
              case Failure(violations) =>
                val scopedViolations = violations.map { violation =>
                  violation.withPath(Indexed(index.toLong) +: violation.path)
                }
                accum.and(Failure(scopedViolations))
            }
        }
      }
    }
  }

  implicit class RichViolation(v: Violation) {
    def withPath(path: Path): Violation =
      v match {
        case RuleViolation(value, constraint, _) =>
          RuleViolation(value, constraint, path)
        case GroupViolation(value, constraint, children, _) =>
          GroupViolation(value, constraint, children, path)
      }
  }

  lazy val failureWrites: Writes[Failure] = Writes { f =>
    Json.obj(
      "message" -> "Object is not valid",
      "details" -> {
        allViolations(f)
          .groupBy(_.path)
          .map {
            case (path, ruleViolations) =>
              Json.obj(
                "path" -> path,
                "errors" -> ruleViolations.map(_.constraint))
          }
      })
  }

  /**
    * Given a wix accord violation, return a sequence of our own ConstraintViolation model, rendering the wix accord
    * paths down to a string representation.
    *
    * @param result The wix accord validation result
    */
  private[this] def allViolations(result: com.wix.accord.Result): Seq[ConstraintViolation] = {
    def renderPath(desc: Description): String = desc match {
      case Explicit(s)    => s"/${s}"
      case Generic(s)     => s"/${s}"
      case Indexed(index) => s"($index)"
      case _              => ""
    }
    def mkPath(path: Path): String =
      if (path.isEmpty)
        "/"
      else
        path.map(renderPath).mkString("")

    def collectViolation(violation: Violation, parents: Path): Seq[ConstraintViolation] = {
      violation match {
        case RuleViolation(_, constraint, path)   => Seq(ConstraintViolation(mkPath(parents ++ path), constraint))
        case GroupViolation(_, _, children, path) => children.to[Seq].flatMap(collectViolation(_, parents ++ path))
      }
    }
    result match {
      case Success => Seq.empty
      case Failure(violations) =>
        violations.to[Seq].flatMap(collectViolation(_, Nil))
    }
  }

  case class ConstraintViolation(path: String, constraint: String)

}
