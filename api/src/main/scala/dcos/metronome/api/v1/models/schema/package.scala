package dcos.metronome.api.v1.models

import dcos.metronome.api.JsonSchema
import dcos.metronome.model.{ ScheduleSpec, JobSpec }

package object schema {

  implicit lazy val JobSpecSchema: JsonSchema[JobSpec] = {
    JsonSchema.fromResource("/public/api/schema/v1/jobspec.schema.json")
  }
  implicit lazy val ScheduledSpecSchema: JsonSchema[ScheduleSpec] = {
    JsonSchema.fromResource("/public/api/schema/v1/schedulespec.schema.json")
  }
}
