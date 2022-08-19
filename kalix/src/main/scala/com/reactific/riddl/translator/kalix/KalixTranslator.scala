package com.reactific.riddl.translator.kalix

import com.reactific.riddl.language.AST._
import com.reactific.riddl.language._
import com.reactific.riddl.utils.Logger

import java.nio.file.Path
import scala.collection.mutable

case class KalixOptions(
  inputFile: Option[Path] = None,
  outputDir: Option[Path] = None,
  kalixPath: Option[Path] = None,
  projectName: Option[String] = None,
  withValidations: Boolean = false
) extends TranslatingOptions

object KalixOptions {
  val default: KalixOptions = KalixOptions()
}

case class KalixState(
  options: KalixOptions,
  symTab: SymbolTable
)
  extends TranslatorState[GrpcWriter] {}

object KalixTranslator extends Translator[KalixOptions] {

  /** Extract actual parents from stack
   * @param stack - The stack of AST elements
   * @return A ParentDefOf stack that has includes and RootContainers removed
   *
   * The stack goes from most nested to highest. We don't want to change the
   * stack (its mutable) so we reverse it, making a copy, then
   * drop all the root containers (file includes) to finally end up at a domain
   * and then map to just the name of that domain.
   */
  def parents(stack: Seq[Parent]): Seq[Parent] = {
    val result = stack.reverse.dropWhile(_.isRootContainer)
    result
  }

  /** Extract the code level package names
   * @param stack - The stack of AST elements
   * @return A Seq[String] from most abstract package to least abstract package
   *
   * It is presumed the stack has already had "parents" run on it
   * */
  def packages(stack: Seq[Parent]): Seq[String] = {
    stack.flatMap {
      case d: Domain =>
        d.getOptionValue[DomainPackageOption] match {
          case Some(pkg) =>
            pkg.map(_.s.toLowerCase)
          case None =>
            Seq(d.id.value.toLowerCase())
        }
      case c: Context =>
        c.getOptionValue[ContextPackageOption] match {
          case Some(pkg) =>
            pkg.map(_.s.toLowerCase)
          case None =>
            Seq(c.id.value.toLowerCase())
        }
      case _ =>
        // Others don't have package specifications
        Seq.empty[String]
    }
  }

  def setUp(
    c: Parent,
    options: KalixOptions,
    state: KalixState,
    stack: Seq[Parent],
    isApi: Boolean = false
  ): GrpcWriter = {
    state.addDir(c.id.format)
    val pars: Seq[Parent] = parents(stack)
    val pkgs = packages(pars) :+ (if (isApi) "api" else "domain")
    val prefix = Seq("main", "proto") ++ pkgs
    val name = prefix  :+ c.id.value
    val path = Path.of("src", name:_*)
    val fullPath: Path = options.outputDir.get.resolve(path)
    val writer = GrpcWriter(fullPath, pkgs, pars, state.symTab)
    state.addFile(writer)
    writer
  }

  override protected def translateImpl(
    root: AST.RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: KalixOptions
  ): Seq[Path] = {
    val paths = super.translateImpl(root, log, commonOptions, options)
    require(options.projectName.nonEmpty, "A project name must be provided")

    val state = KalixState(options, SymbolTable(root))
    val parentStack = mutable.Stack[ParentDefOf[Definition]]()

    val newState = Folding.foldLeftWithStack(state, parentStack)(root) {
      case (st, d: AST.Domain, stack) =>
        val writer = setUp(d, options, st, stack)
        writer.emitTypes(d.collectMessages)
        st
      case (st, c: AST.Context, stack) =>
        val writer = setUp(c, options, st, stack)
        writer.emitTypes(c.collectMessages)
        st
      case (st, e: AST.Entity, stack) =>
        val writer = setUp(e, options, st, stack)
        // writer.emitEntityApi(e)
        // writer.emitEntityImpl(e)
        st
      case (st, f: AST.Function, stack) => st
      case (st, a: AST.Adaptor, stack) => st
      case (st, s: AST.Saga, stack) => st
      case (st, s: AST.Story, stack) => st
      case (st, p: AST.Plant, stack) => st
      case (st, p: AST.Processor, stack) => st
      case (st, a: AST.Adaptation, stack) => st
      case (st, p: AST.Pipe, stack) => st
      case (st, t: AST.Term, stack)  => st

      case (st, _: RootContainer, _) => // skip, not needed
        st
      case (st, _, _) => // skip, handled by the MarkdownWriter
        st
    }
    paths ++ newState.close
  }
}
