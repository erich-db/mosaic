package com.databricks.labs.mosaic.expressions.geometry

import com.databricks.labs.mosaic.core.geometry.api.GeometryAPI
import com.databricks.labs.mosaic.core.index.{IndexSystem, IndexSystemID}
import com.databricks.labs.mosaic.core.types.ChipType
import org.apache.spark.sql.catalyst.expressions.{BinaryExpression, Expression, ExpressionInfo, NullIntolerant}
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.types.{BooleanType, DataType}

case class ST_ContainsMosaic(leftGeom: Expression, rightGeom: Expression, geometryAPIName: String, indexSystemName: String)
    extends BinaryExpression
      with NullIntolerant
      with CodegenFallback {

    lazy val geometryAPI: GeometryAPI = GeometryAPI.apply(geometryAPIName)
    lazy val indexSystem: IndexSystem = IndexSystemID.getIndexSystem(IndexSystemID.apply(indexSystemName))

    override def dataType: DataType = BooleanType

    // noinspection DuplicatedCode
    override def nullSafeEval(input1: Any, input2: Any): Any = {
        val leftChips = getChips(input1)
        val rightChips = getChips(input2)

        val matches = for {
            (f1, k, v1) <- leftChips
            (f2, `k`, v2) <- rightChips
        } yield ((f1, f2), (v1, v2))

        if (matches.isEmpty) false
        else {
            matches.foldLeft(true) { case (accumulator, (flags, geoms)) =>
                accumulator && {
                    if (flags._2) {
                        flags._1
                    } else {
                        val leftChipGeom = geometryAPI.geometry(geoms._1, "WKB")
                        val rightChipGeom = geometryAPI.geometry(geoms._2, "WKB")
                        leftChipGeom.contains(rightChipGeom)
                    }
                }
            }
        }
    }

    private def getChips(input: Any): Seq[(Boolean, Long, Array[Byte])] = {
        val arrayData = input.asInstanceOf[InternalRow].getArray(0)
        arrayData.toArray[InternalRow](ChipType).map(row => (row.getBoolean(0), row.getLong(1), row.getBinary(2)))
    }

    override def left: Expression = leftGeom

    override def right: Expression = rightGeom

    override def makeCopy(newArgs: Array[AnyRef]): Expression = {
        val asArray = newArgs.take(2).map(_.asInstanceOf[Expression])
        val res = ST_IntersectsMosaic(asArray(0), asArray(1), geometryAPIName, indexSystemName)
        res.copyTagsFrom(this)
        res
    }

    override protected def withNewChildrenInternal(newLeft: Expression, newRight: Expression): Expression =
        copy(leftGeom = newLeft, rightGeom = newRight)

}

object ST_ContainsMosaic {

    def registryExpressionInfo(db: Option[String]): ExpressionInfo =
        new ExpressionInfo(
          classOf[ST_ContainsMosaic].getCanonicalName,
          db.orNull,
          "st_contains_mosaic",
          """
            |    _FUNC_(left_index, right_index)) - Resolves a contains based on matched indices.
            """.stripMargin,
          "",
          """
            |    Examples:
            |      > SELECT _FUNC_(a, b);
            |        geom
            |        geom
            |        ...
            |        geom
            |  """.stripMargin,
          "",
          "agg_funcs",
          "1.0",
          "",
          "built-in"
        )

}
