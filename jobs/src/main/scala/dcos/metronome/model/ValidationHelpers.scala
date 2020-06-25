package dcos.metronome.model

import com.wix.accord.{Failure, GroupViolation, RuleViolation, Success, Validator, Violation}
import dcos.metronome.Seq

object ValidationHelpers {
  def genericValidator[T](body: (T) => Seq[String]): Validator[T] = { t =>
    val violations: Set[Violation] = body(t).iterator.map { errorMessage => RuleViolation(t, errorMessage) }.toSet
    if (violations.isEmpty)
      Success
    else
      Failure(violations)
  }

  def withHint[U](hint: String, validator: Validator[U]): Validator[U] =
    (value: U) => {
      validator(value).map { violations =>
        violations.map {
          case RuleViolation(value, constraint, path) =>
            RuleViolation(value, constraint + " " + hint, path)
          case GroupViolation(value, constraint, children, path) =>
            GroupViolation(value, constraint + " " + hint, children, path)
        }
      }
    }
}
