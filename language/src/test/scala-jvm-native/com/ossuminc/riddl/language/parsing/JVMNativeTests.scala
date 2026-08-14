/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.{ec, pc}

// Concrete runners for the abstract parser suites below. This file lives in
// `scala-jvm-native` (wired via `jvmNativeSrc("language")` in build.sbt), the
// source root shared by the JVM and Native rows, so these run on BOTH
// platforms from one declaration. Until 2026-08-14 the only concrete runners
// were `scalajvm/.../JVMTests.scala` and `scalajs/.../JSTests.scala`, so
// these 13 suites (169 cases) never ran on Native at all -- see BACKLOG.md
// "Close the JVM/Native test gap". JS runners stay separate in
// `scalajs/.../JSTests.scala`: JS parsing is a genuinely different platform
// and keeping it a distinct file avoids implying JS coverage this file does
// not provide.
class JVMNativeApplicationParsingTest extends ApplicationParsingTest
class JVMNativeCommonParserTest extends CommonParserTest
class JVMNativeHandlerTest extends HandlerTest
class JVMNativeMetaDataTest extends MetaDataTest
class JVMNativeModuleTest extends ModuleTest
class JVMNativeNebulaTest extends NebulaTest
class JVMNativeParsingTestTest extends ParsingTestTest
class JVMNativeProjectorTest extends ProjectorTest
class JVMNativeRepositoryTest extends RepositoryTest
class JVMNativeStatementsTest extends StatementsTest
class JVMNativeStreamingParserTest extends StreamingParserTest
class JVMNativeTypeParserTest extends TypeParserTest
class JVMNativeTokenParserTest extends TokenParserTest
