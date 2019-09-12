package dcos.metronome.utils

import play.api.libs.json.OFormat
import play.api.libs.functional.syntax._

object Formats {

  implicit class FormatWithDefault[A](val m: OFormat[Option[A]]) extends AnyVal {
    def withDefault(a: A): OFormat[A] = m.inmap(_.getOrElse(a), Some(_))
  }

}
