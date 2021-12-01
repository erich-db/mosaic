package com.databricks.mosaic.expressions.format

import com.databricks.mosaic.expressions.format.Conversions.{geom2wkt, hex2geom}
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.expressions.{ExpectsInputTypes, Expression, ExpressionDescription, NullIntolerant, UnaryExpression}
import org.apache.spark.sql.types.{DataType, StringType}

@ExpressionDescription(
  usage = "_FUNC_(expr1) - Returns the wkt string representation.",
  examples =
    """
    Examples:
      > SELECT _FUNC_(a);
       "POLYGON(0 0, ...)"
  """,
  since = "1.0")
case class HexToWKT(hex_text: Expression) extends UnaryExpression with ExpectsInputTypes with NullIntolerant with CodegenFallback {

  override def inputTypes: Seq[DataType] = Seq(StringType)

  override def dataType: DataType = StringType

  override def toString: String = s"hex_to_wkt($hex_text)"

  override def nullSafeEval(input1: Any): Any = {
    val geom = hex2geom(input1)
    geom2wkt(geom)
  }

  override def makeCopy(newArgs: Array[AnyRef]): Expression = {
    val asArray = newArgs.take(1).map(_.asInstanceOf[Expression])
    val res = HexToWKT(asArray(0))
    res.copyTagsFrom(this)
    res
  }

  override def child: Expression = hex_text
}
