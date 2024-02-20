package com.ossuminc.riddl.language.parsing

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionContext

class RiddlParsing(maxParallelParsing: Int) {

  val es: ExecutorService = Executors.newWorkStealingPool(maxParallelParsing)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(es)

}
