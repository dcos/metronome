package dcos.metronome.api.v1.models

import dcos.metronome.api.JsonSchema
import dcos.metronome.model.{ ScheduleSpec, JobSpec }

package object schema {

  implicit lazy val JobSpecSchema: JsonSchema[JobSpec] = {
    JsonSchema.fromResource("/public/api/v1/schema/jobspec.schema.json")
  }
  implicit lazy val ScheduledSpecSchema: JsonSchema[ScheduleSpec] = {
    JsonSchema.fromResource("/public/api/v1/schema/schedulespec.schema.json")
  }
}
