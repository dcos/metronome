/**
  * This log format should be used by the majority of DCOS clusters.
  *
  * It will match a log line starts with this:
  *
  *     1971-01-01 00:00:00: [
  *
  * It will configure logstash to join together lines if they lack the `[` at then end.
  *
  *     1971-01-01 00:00:00: [INFO] This is a
  *     1971-01-01 00:00:00: multi-line log message.
  */
object DcosLogFormat extends (String => Option[LogFormat]) {
  val regexPrefix = "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}:".r
  val regex = s"${regexPrefix} \\[".r

  val example = "1971-01-01 00:00:00: [INFO] {message}"

  override def apply(s: String): Option[LogFormat] =
    if (regex.findFirstMatchIn(s).nonEmpty)
      Some(
        LogFormat(
          codec = codec,
          example = example,
          unframe = unframe))
    else
      None

  private val codec = s"""|multiline {
                  |  pattern => "${regexPrefix} [^\\[]"
                  |  what => "previous"
                  |  max_lines => 1000
                  |}""".stripMargin

  val unframe = s"""|filter {
                    |  grok {
                    |    match => {
                    |      "message" => "(?<logdate>%{YEAR}-%{MONTHNUM}-%{MONTHDAY} %{TIME}): *%{GREEDYDATA:message}"
                    |    }
                    |    tag_on_failure => []
                    |    overwrite => [ "message" ]
                    |  }
                    |}""".stripMargin

}

case class LogFormat(
  codec: String,
  example: String,
  unframe: String,
  host: Option[String] = None
)

object LogFormat {
  def tryMatch(line: String): Option[LogFormat] = {
    DcosLogFormat(line)
  }
}
