# Release Skill

Executes a full release cycle for the riddl project. Follow each
step in order. **STOP immediately** if any assertion fails and
report the problem.

## Arguments

The user should provide a version number (e.g., `1.10.1`). If
not provided:
1. Run `git tag --sort=-v:refname | head -5` to find the
   actual latest tag (not just reachable from current branch)
2. Run `git log --oneline <latest-tag>..HEAD` to see changes
3. Analyze per semver and **recommend** a version (don't ask
   the user to choose — present your recommendation and let
   them confirm or override)

## Pre-Flight Checks

1. Assert current branch is `main`:
   ```
   git branch --show-current
   ```
   If not on `main`, ask the user before switching. **Never
   publish from `development` or feature branches.**

2. Assert working tree is clean:
   ```
   git status --porcelain
   ```
   If dirty, list the uncommitted files and ask the user how
   to proceed.

3. **GITHUB_TOKEN handling**: Do NOT `unset GITHUB_TOKEN`
   globally — sbt needs it for GitHub Packages resolution.
   Only unset it immediately before `gh` commands:
   ```
   unset GITHUB_TOKEN && gh release create ...
   ```

4. When switching to `main`, always `git pull` first to
   ensure local main is up to date with origin.

5. Verify the version tag does not already exist:
   ```
   git tag -l <VERSION>
   ```

## Release Steps

5. Run the full test suite and confirm all tests pass:
   ```
   sbt clean test
   ```

6. Create an annotated git tag:
   ```
   git tag -a <VERSION> -m "Release <VERSION>"
   ```

7. Push the tag to origin:
   ```
   git push origin <VERSION>
   ```

8. Publish all modules to GitHub Packages:
   ```
   sbt clean test publish
   ```
   Verify the published version matches `<VERSION>` in the sbt
   output (sbt-dynver derives version from the tag).

9. Create a GitHub release:
   ```
   gh release create <VERSION> --title "Release <VERSION>" \
     --generate-notes
   ```
   This triggers the Release Artifacts workflow (native builds,
   Homebrew formula update) and the npm-publish workflow
   automatically.

## Post-Release Verification

10. Run `git status` to confirm the working tree is still clean.

11. Confirm the release exists:
    ```
    gh release view <VERSION>
    ```

12. Switch back to `development` and merge the tag forward:
    ```
    git checkout development
    git merge main
    git push
    ```

13. Report a summary: tag, commit SHA, release URL, and any
    CI workflows triggered.

## If Something Fails

- If tests fail in step 5: fix and re-run. Do NOT proceed.
- If tag push fails in step 7: check if the tag exists remotely.
- If publish fails in step 8: check credentials and retry.
- If `gh release create` fails in step 9: the tag is already
  pushed, so the release can be created manually or retried.
- **Never force-push tags** without explicit user approval.
