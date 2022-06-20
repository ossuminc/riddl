package com.reactific.riddl.translator.kalix

import com.reactific.riddl.utils.TextFileWriter

import java.nio.file.Path

/** A writer for grpc/protobuffers files */
case class GrpcWriter(filePath: Path) extends TextFileWriter(filePath) {}
