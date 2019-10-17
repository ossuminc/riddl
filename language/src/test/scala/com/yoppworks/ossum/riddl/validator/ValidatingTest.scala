package com.yoppworks.ossum.riddl.validator

import java.io.File

import com.yoppworks.ossum.riddl.parser.AST.Definition
import com.yoppworks.ossum.riddl.parser.AST
import com.yoppworks.ossum.riddl.parser.ParsingTest
import com.yoppworks.ossum.riddl.parser.TopLevelParser
import com.yoppworks.ossum.riddl.validator.Validation.ValidationMessage
import com.yoppworks.ossum.riddl.validator.Validation.ValidationOptions
import com.yoppworks.ossum.riddl.validator.Validation.ValidationState
import com.yoppworks.ossum.riddl.validator.Validation.defaultOptions
import org.scalatest.Assertion

import scala.reflect.runtime.universe._

/** Convenience functions for tests that do validation*/
class ValidatingTest extends ParsingTest {

  protected def validateFor[D <: Definition: TypeTag](
    dfntn: D,
    options: Seq[ValidationOptions] = defaultOptions
  ): Seq[ValidationMessage] = {
    val payload = ValidationState(options)
    val result = typeOf[D] match {
      case x if x =:= typeOf[AST.TypeDef] =>
        TypeValidator(dfntn.asInstanceOf[AST.TypeDef], payload)
      case x if x =:= typeOf[AST.DomainDef] =>
        DomainValidator(dfntn.asInstanceOf[AST.DomainDef], payload)
      case x if x =:= typeOf[AST.ContextDef] =>
        ContextValidator(dfntn.asInstanceOf[AST.ContextDef], payload)
      case x if x =:= typeOf[AST.InteractionDef] =>
        InteractionValidator(dfntn.asInstanceOf[AST.InteractionDef], payload)
      case x if x =:= typeOf[AST.FeatureDef] =>
        FeatureValidator(dfntn.asInstanceOf[AST.FeatureDef], payload)
      case x if x =:= typeOf[AST.EntityDef] =>
        EntityValidator(dfntn.asInstanceOf[AST.EntityDef], payload)
      case _ =>
        ???
    }
    result.asInstanceOf[Validation.ValidatorBase[D]].traverse.msgs.toSeq
  }

  def parseAndValidate[D <: Definition: TypeTag](input: String)(
    validation: (D, Seq[ValidationMessage]) => Assertion
  ): Assertion = {
    parseDefinition[D](input) match {
      case Left(msg) =>
        fail(msg)
      case Right(model: D @unchecked) =>
        val msgs = validateFor[D](model)
        validation(model, msgs)
    }
  }

  def validateFile(label: String, fileName: String)(
    validation: (Seq[AST.DomainDef], Seq[ValidationMessage]) => Assertion
  ): Assertion = {
    val directory = "language/src/test/input/"
    val file = new File(directory + fileName)
    TopLevelParser.parse(file) match {
      case Left(error) =>
        fail(s"$label:$error")
      case Right(domains) =>
        val messages = Validation.validate(domains)
        validation(domains, messages)
    }
  }
}
