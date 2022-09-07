/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reactific.riddl.language.testkit

import com.reactific.riddl.commands.CommandPlugin
import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/** A base class for specs that just want to run a command */
abstract class RunCommandSpecBase extends AnyWordSpec with Matchers {

  def runWith(
    commandArgs: Seq[String]
  ): Assertion = { CommandPlugin.runMain(commandArgs.toArray) must be(0) }
}
