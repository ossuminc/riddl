package com.yoppworks.ossum.riddl.generation.hugo.rendering

import com.yoppworks.ossum.riddl.generation.hugo._

trait Renderer[A] {
  def render(element: A, relativePath: RelativePath): List[HugoContent]
}

private object RendererUtils {
  implicit class MarkdownFilenameExt(val str: String) extends AnyVal {
    def md: String = str + ".md"
  }

  @inline
  final def mkTypeRegion(typeFullName: String): String = typeFullName.split('.').toSeq match {
    case Nil | Seq(_) => "(global)"
    case ns :+ _      => ns.mkString(".")
  }

  @inline
  final def justTypeName(typeFullName: String): String = typeFullName.split('.').last

  def makeLinkTo(repr: HugoNode): String = { "" }
}

abstract private[hugo] class HugoTypeRenderers {
  import RendererUtils.MarkdownFilenameExt

  private def typeReplacements(
    fullTypeName: String,
    typeShortType: String,
    typeFullType: String,
    typeDescription: String = "(none)",
    typeNotes: String = ""
  ) = Map(
    "typeName" -> RendererUtils.justTypeName(fullTypeName),
    "typeFullType" -> typeFullType,
    "typeDescription" -> typeDescription,
    "typeShortType" -> typeShortType,
    "typeRegion" -> RendererUtils.mkTypeRegion(fullTypeName),
    "typeNotes" -> typeNotes
  )

  protected case class TypeNameContext(typeName: String, region: String)
  protected object TypeNameUtil {
    def apply[A](fullName: String)(f: TypeNameContext => A): A =
      f(TypeNameContext(RendererUtils.justTypeName(fullName), RendererUtils.mkTypeRegion(fullName)))
  }

  @inline
  protected final def oneOf[A](element: A): List[A] = List(element)

  private def hugoTypeRenderer[HT <: HugoType](
    repr: HT,
    relPath: RelativePath
  )(reps: Map[String, String]
  ) = TypeNameUtil(repr.fullName) { nameCtx =>
    oneOf(HugoTemplate(
      Templates.forHugo(repr).fold(throw _, identity),
      relPath / nameCtx.typeName.toLowerCase.md,
      reps
    ))
  }

  /*
  private val catchAll: Renderer[HugoType] =
    (_: HugoType, _: RelativePath) => List.empty[HugoContent]

  private val unhandledRenderer: Renderer[RiddlType.UnhandledType] =
    (e: RiddlType.UnhandledType, relPath: RelativePath) =>
      hugoTypeRenderer(e, relPath)(
        typeReplacements(e.fullName, "Undefined Type", "Undefined Type-Reference")
      )
   */

}

abstract private[hugo] class RendererInstances {}
