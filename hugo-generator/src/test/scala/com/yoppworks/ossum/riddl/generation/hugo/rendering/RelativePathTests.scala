package com.yoppworks.ossum.riddl.generation.hugo.rendering

import com.yoppworks.ossum.riddl.generation.hugo.HugoRoot
import com.yoppworks.ossum.riddl.generation.hugo.LukeAstWalker
import com.yoppworks.ossum.riddl.generation.hugo.RiddlTest

class RelativePathTests extends RiddlTest {

  lazy val namespace: HugoRoot = LukeAstWalker(container)

  val expectedPaths = List(
    "everything/_index.md",
    "everything/types/sometype.md",
    "everything/contexts/full/_index.md",
    "everything/contexts/full/types/agg.md",
    "everything/contexts/full/types/tim.md",
    "everything/contexts/full/types/num.md",
    "everything/contexts/full/types/peachtype.md",
    "everything/contexts/full/types/alt.md",
    "everything/contexts/full/types/enum.md",
    "everything/contexts/full/entities/someotherthing.md",
    "everything/contexts/full/types/oneormore.md",
    "everything/contexts/full/entities/something.md",
    "everything/contexts/full/entities/types/somethingdate.md",
    "everything/contexts/full/types/url.md",
    "everything/contexts/full/types/optional.md",
    "everything/contexts/full/types/stamp.md",
    "everything/contexts/full/types/boo.md",
    "everything/contexts/full/types/dat.md",
    "everything/contexts/full/types/str.md",
    "everything/contexts/full/types/zeroormore.md",
    "everything/contexts/full/types/ident.md"
  )

  "RelativePath" should {

    "be correctly mapped from `HugoRoot` (Namespace) nodes" in {
      pending
      val nodes = namespace.allContents.filterNot(_.isInstanceOf[HugoRoot]).toList
      val paths = nodes.map(RelativePath.of)
      val comparison = nodes.zip(paths)
      val pathStrings = paths.map(_.toString)

      comparison must not be empty
      pathStrings mustEqual expectedPaths
    }

  }

}
