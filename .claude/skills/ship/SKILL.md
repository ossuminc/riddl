# Ship Skill

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
   unset GITHUB_TOKEN && gh ...
   ```

4. When switching to `main`, always `git pull` first to
   ensure local main is up to date with origin.

5. Verify the version tag does not already exist:
   ```
   git tag -l <VERSION>
   ```

## Ship Steps

6. **Ensure the working tree is clean before tagging.**
   sbt-dynver derives the version from `git describe`;
   any dirty tree causes it to append a
   `<VERSION>-N-<hash>-<timestamp>` suffix instead of
   the clean `<VERSION>`. This suffix would propagate
   to BuildInfo, all published artifacts, and npm
   packages. Check:
   ```
   git status --porcelain
   ```
   If any files are modified or untracked, commit them
   now before proceeding.

7. Create an annotated git tag:
   ```
   git tag -a <VERSION> -m "Release <VERSION>"
   ```

8. Verify dynver resolves to the clean version:
   ```
   sbt 'show riddlc/version'
   ```
   The output must be exactly `<VERSION>` with no suffix.
   If it has a suffix, do NOT proceed — delete the tag
   (`git tag -d <VERSION>`), fix the issue, and re-tag.

9. Run the full test suite and publish all modules:
   ```
   sbt clean test publish
   ```
   Because the tag is on HEAD and the tree is clean,
   BuildInfo and all published artifacts will carry the
   clean `<VERSION>`. Verify in the sbt output.
   **If tests fail, delete the tag** (`git tag -d
   <VERSION>`) — do NOT push a broken release.

10. Push commits and tag to origin:
    ```
    git push origin main <VERSION>
    ```

11. **Write detailed release notes** and create the GitHub
    release. Do NOT use `--generate-notes` — the auto-generated
    notes are just commit titles and are not suitable for a
    public repository.

    Instead, read all commits since the previous tag:
    ```
    git log --format="%H %s" <PREV_TAG>..<VERSION>
    ```
    Then read the diffs for any non-trivial commits to
    understand what actually changed. Write human-readable
    release notes in this format:

    ```markdown
    ## What's New

    ### Features
    - **Feature name** — Clear description of what was added
      and why it matters to users.

    ### Bug Fixes
    - **Area affected** — What was broken and how it's fixed.

    ### Improvements
    - **Area affected** — What changed and why it's better.

    ### Internal
    - Dependency upgrades, CI fixes, documentation updates,
      and other changes that don't affect end users directly.
    ```

    Omit any section that has no entries. Focus on what users
    and consumers of the library need to know. Use clear,
    complete sentences — not just commit message echo.

    Create the release:
    ```
    unset GITHUB_TOKEN && gh release create <VERSION> \
      --title "Release <VERSION>" --notes "$(cat <<'EOF'
    <release notes here>
    EOF
    )"
    ```
    This triggers the Release Artifacts workflow (native
    builds, Homebrew formula update) and the npm-publish
    workflow automatically.

## Post-Release Verification

12. Confirm the release exists:
    ```
    unset GITHUB_TOKEN && gh release view <VERSION>
    ```

13. Run `git status` to confirm the working tree is clean.
    **Known issue:** The `sbt clean` step in the build may
    trigger sbt-ossuminc's copyright header formatter, which
    updates files that still have stale headers (e.g.,
    `"Ossum, Inc."` → `"Ossum Inc."`). If `git status` shows
    modified files that are only copyright header changes:
    - Commit them: `git add -u && git commit -m "Fix copyright headers"`
    - Push: `git push origin main`
    These changes are harmless formatting fixes and should be
    committed before merging back to `development`.

14. Switch back to `development` and merge the tag forward:
    ```
    git checkout development
    git merge main
    git push
    ```

15. Report a summary: tag, commit SHA, release URL, and any
    CI workflows triggered.

## If Something Fails

- If tests fail in step 9: delete the local tag
  (`git tag -d <VERSION>`), fix, and restart from step 6.
  Do NOT push a broken tag.
- If dynver shows a suffix in step 8: delete the tag, fix
  the dirty tree, and re-tag.
- If tag push fails in step 10: check if tag exists
  remotely.
- If publish fails in step 9: check credentials and retry
  (tag is still local, safe to retry).
- If `gh release create` fails in step 11: the tag is
  already pushed, so the release can be created manually
  or retried.
- **Never force-push tags** without explicit user approval.
