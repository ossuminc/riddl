package com.reactific.riddl.language.validation

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.*

import scala.math.abs

trait StreamingValidationState extends ExampleValidationState {

  private var inlets: Seq[Inlet] = Seq.empty[Inlet]

  def addInlet(in: Inlet): this.type = {
    inlets = inlets :+ in
    this
  }

  private var outlets: Seq[Outlet] = Seq.empty[Outlet]

  def addOutlet(out: Outlet): this.type = {
    outlets = outlets :+ out
    this
  }

  private var pipes: Seq[Pipe] = Seq.empty[Pipe]

  def addPipe(out: Pipe): this.type = {
    pipes = pipes :+ out
    this
  }

  private var processors: Seq[Processor] = Seq.empty[Processor]

  def addProcessor(proc: Processor): this.type = {
    processors = processors :+ proc
    this
  }

  def checkStreaming(): Unit = {
    checkStreamingUsage()
    checkPipePersistence()
    checkUnattachedInlets()
    checkUnsubscribedPipes()
    checkUnpublishedPipes()
  }

  private def checkStreamingUsage(): Unit = {
    if (
      inlets.isEmpty && outlets.isEmpty && pipes.isEmpty && processors.isEmpty
    ) {
      add(
        Messages.style(
          "Models without any streaming data will exhibit minimal effect",
          root.loc
        )
      )
    }
  }

  private def checkPipePersistence(): Unit = {
    pipes.foldLeft[this.type](this) { (state: this.type, pipe) =>
      val pipeParents = state.symbolTable.parentsOf(pipe)
      val maybePipeContext = state.symbolTable.contextOf(pipe)
      require(maybePipeContext.nonEmpty, "Pipe with no Context")
      val pipeContext = maybePipeContext.get
      val maybeToInlet = pipe.to.flatMap(inlet =>
        state.resolvePathIdentifier[Inlet](inlet.pathId, pipeParents)
      )
      val maybeFromOutlet = pipe.from.flatMap(outlet =>
        state.resolvePathIdentifier[Outlet](outlet.pathId, pipeParents)
      )
      val maybeInletContext = maybeToInlet
        .flatMap(inlet => state.symbolTable.contextOf(inlet))
      val maybeOutletContext = maybeFromOutlet
        .flatMap(outlet => state.symbolTable.contextOf(outlet))
      val inletIsSameContext = maybeInletContext.nonEmpty &&
        (pipeContext == maybeInletContext.get)
      val outletIsSameContext = maybeOutletContext.nonEmpty &&
        (pipeContext == maybeOutletContext.get)

      if (pipe.hasOption[PipePersistentOption]) {
        if (outletIsSameContext && inletIsSameContext) {
          val message = s"The persistence option on ${pipe.identify} is not " +
            s"needed since both ends of the pipe connect within the same " +
            s"context"
          state.add(style(message, pipe.loc))
        } else state
      } else {
        if (!outletIsSameContext || !inletIsSameContext) {
          val message = s"The persistence option should be specified on " +
            s"${pipe.identify} because an end of the pipe is not connected " +
            s"within the same context"
          state.add(style(message, pipe.loc))
        } else state
      }
    }
  }

  private def checkUnattachedInlets(): Unit = {
    val inUseInlets: Seq[Inlet] = pipes.flatMap { pipe =>
      val parents = pipe +: symbolTable.parentsOf(pipe)
      pipe.to.flatMap[Inlet] { inletRef =>
        val maybe = resolvePathIdentifier[Inlet](inletRef.pathId, parents)
        maybe
      }
    }

    val unattachedInlets = inlets.toSet[Inlet] -- inUseInlets

    unattachedInlets.foldLeft[this.type](this) { (st: this.type, inlet) =>
      val message = s"${inlet.identify} is not attached to a pipe and will " +
        s"never " + s"receive any messages"
      st.add(error(message, inlet.loc))
    }
  }

  def checkProcessorShape(proc: Processor): this.type = {
    val ins = proc.inlets.size
    val outs = proc.outlets.size

    def generateError(
      proc: Processor,
      req_ins: Int,
      req_outs: Int
    ): this.type = {
      def sOutlet(n: Int): String = {
        if (n == 1) s"1 outlet"
        else if (n < 0) {
          s"at least ${abs(n)} outlets"
        } else s"$n outlets"
      }

      def sInlet(n: Int): String = {
        if (n == 1) s"1 inlet"
        else if (n < 0) {
          s"at least ${abs(n)} outlets"
        } else s"$n inlets"
      }

      this.addError(
        proc.loc,
        s"${proc.identify} should have " + sOutlet(req_outs) + " and " +
          sInlet(req_ins) + s" but it has " + sOutlet(outs) + " and " +
          sInlet(ins)
      )
    }

    if (!proc.isEmpty) {
      proc.shape match {
        case _: Source =>
          if (ins != 0 || outs != 1) {
            generateError(proc, 0, 1)
          } else {
            this
          }
        case _: Flow =>
          if (ins != 1 || outs != 1) {
            generateError(proc, 1, 1)
          } else {
            this
          }
        case _: Sink =>
          if (ins != 1 || outs != 0) {
            generateError(proc, 1, 0)
          } else {
            this
          }
        case _: Merge =>
          if (ins < 2 || outs != 1) {
            generateError(proc, -2, 1)
          } else {
            this
          }
        case _: Split =>
          if (ins != 1 || outs < 2) {
            generateError(proc, 1, -2)
          } else {
            this
          }
        case _: Router =>
          if (ins < 2 || outs < 2) {
            generateError(proc, -2, -2)
          } else {
            this
          }
        case _: Multi =>
          if (ins < 2 || outs < 2) {
            generateError(proc, -2, -2)
          } else {
            this
          }
        case _: Void =>
          if (ins > 0 || outs > 0) {
            generateError(proc, 0, 0)
          } else {
            this
          }
      }
    } else {
      this
    }
  }

  private def checkUnsubscribedPipes(): Unit = {
    val unattachedOutletPipes: Set[Pipe] = pipes.filter(_.to.isEmpty).toSet
    val subscribedPipes: Seq[Pipe] = subscriptions.flatMap { case (sub, pars) =>
      resolvePathIdentifier[Pipe](sub.pipe.pathId, pars).flatMap { pipe: Pipe =>
        if (pipe.to.isEmpty) Some(pipe)
        else {
          val message =
            s"Subscribing to ${pipe.identify} with attached ${pipe.to.get.format} will yield no data"
          addError(sub.loc, message)
          Option.empty[Pipe]
        }
      }.toSeq
    }.toSeq

    val unsubscribedPipes: Set[Pipe] = unattachedOutletPipes -- subscribedPipes

    for { pipe <- unsubscribedPipes } {
      val message = s"${pipe.identify} has no subscribers"
      addMissing(pipe.loc, message)
    }
  }

  private def checkUnpublishedPipes(): Unit = {
    val unattachedInletPipes: Set[Pipe] = pipes.filter(_.from.isEmpty).toSet
    val publishedPipes: Seq[Pipe] = publishings.flatMap { case (sub, pars) =>
      resolvePathIdentifier[Pipe](sub.pipe.pathId, pars).flatMap { pipe: Pipe =>
        if (pipe.to.isEmpty) Some(pipe)
        else {
          val message =
            s"Publishing to ${pipe.identify} with attached ${pipe.from.get.format} will receive no data"
          addError(sub.loc, message)
          Option.empty[Pipe]
        }
      }.toSeq
    }.toSeq
    val unpublishedPipes: Set[Pipe] = unattachedInletPipes -- publishedPipes

    for { pipe <- unpublishedPipes } {
      val message = s"${pipe.identify} has no publishers"
      addMissing(pipe.loc, message)
    }
  }

}
