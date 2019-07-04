package dcos.metronome
package api.v1.models

import dcos.metronome.api.JsonSchema
import dcos.metronome.model.{ JobRunSpecOverrides, JobSpec, ScheduleSpec }

package object schema {

  implicit lazy val JobSpecSchema: JsonSchema[JobSpec] = {
    JsonSchema.fromResource("/public/api/v1/schema/jobspec.schema.json")
  }
  implicit lazy val ScheduledSpecSchema: JsonSchema[ScheduleSpec] = {
    JsonSchema.fromResource("/public/api/v1/schema/schedulespec.schema.json")
  }
  implicit lazy val JobRunSpecOverrideSchema: JsonSchema[JobRunSpecOverrides] = {
    JsonSchema.fromResource("/public/api/v1/schema/jobrunoverride.schema.json")
  }
}
