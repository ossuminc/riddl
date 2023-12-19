---
title: "testing"
description: "Ways to test riddl libraries"
draft: false
weight: 90
---

## Ways To Test RIDDL
### ScalaTest
There are many test points already defined in language/src/tests/scala using
ScalaTest. In general, any change in language should be done in TDD style with
tests cases written before code to make that test case pass.  This is how the
parser and validator were created. That tradition needs to continue.

### "Check" Tests
In `language/src/test/input` there are a variety of tests with `.check` files
that have the same basename as the `.riddl` file. The `.check` files have
the error output that is expected from validating the correctly parsing `.
riddl` file. This way we can identify changes in error and messages. These
tests are performed by the `CheckMessagesTest.scala` test suite which will
read all the riddl files in test/input/check and check them for validity and
compare the output messages to what's in the `.check` file. If there is no
corresponding `.check` file then the `.riddl` file is expected to validate
cleanly with no errors and no warnings.

This is where most regression tests should be added. The input should be
whittled down to the smallest set of definitions that cause a problem to
occur, and then it should succeed after the regression is fixed.

### "Examples" Tests
In `examples/src/test/scala` there is a `CheckExamplesSpec.scala` which runs
the parser and validator on the examples in `examples/src/riddl`. Each
sub-directory there is a separate example. They are expected to parse and
validate cleanly without issue 
