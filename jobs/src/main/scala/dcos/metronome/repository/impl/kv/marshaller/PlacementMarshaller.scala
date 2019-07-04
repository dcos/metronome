package dcos.metronome.repository.impl.kv.marshaller

import dcos.metronome.Protos
import dcos.metronome.model.{ ConstraintSpec, Operator, PlacementSpec }

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

object PlacementMarshaller {
  implicit class PlacementSpecToProto(val placement: PlacementSpec) extends AnyVal {
    def toProto: Protos.PlacementSpec = {
      Protos.PlacementSpec.newBuilder()
        .addAllConstraints(placement.constraints.toProto.asJava)
        .build()
    }
  }

  implicit class ProtoToPlacementSpec(val placementSpec: Protos.PlacementSpec) extends AnyVal {
    def toModel: PlacementSpec = PlacementSpec(constraints = placementSpec.getConstraintsList.asScala.toModel)
  }

  implicit class ConstraintsToProto(val constraints: Seq[ConstraintSpec]) extends AnyVal {
    def toProto: Iterable[Protos.PlacementSpec.Constraint] = constraints.map { constraint =>
      val builder = Protos.PlacementSpec.Constraint.newBuilder

      constraint.value.foreach(builder.setValue)

      builder
        .setAttribute(constraint.attribute)
        .setOperator(
          Protos.PlacementSpec.Constraint.Operator.valueOf(constraint.operator.name))
        .build()
    }
  }

  implicit class ProtosToConstraintSpec(val constraints: Iterable[Protos.PlacementSpec.Constraint]) extends AnyVal {
    def toModel: Seq[ConstraintSpec] = constraints.map { constraint =>
      val value = if (constraint.hasValue) Some(constraint.getValue) else None
      ConstraintSpec(
        attribute = constraint.getAttribute,
        operator = Operator.names(constraint.getOperator.toString),
        value = value)
    }(collection.breakOut)
  }
}
