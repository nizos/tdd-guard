# Contributor Guidelines

## General Requirements

- **Contribution Process**: 
  - **Internal contributors**: Create feature branches and submit pull requests directly
  - **External contributors**: Fork the repository first, then create feature branches and submit pull requests
- **Branch Strategy**: Create branches from `main` for all contributions (feature, bugfix, docs, etc.)

## Code Quality Standards

Every commit must meet these requirements:

1. **Stable State**: Each commit should leave the codebase in a working, stable state
2. **Quality Assurance**: Run `npm run checks` - it must pass without errors
3. **Build Tests**: The package must build successfully with `npm run build`
4. **Hook Compatibility**: The Claude Code hook must function correctly with every commit
5. **Cross-Reporter Compatibility**: Changes to shared contracts must maintain compatibility with all reporters
6. **Don't break**: The existing functionality should not break

## Commit Message Guidelines

Follow our commit message format:

### Structure
```
Title: Brief description in imperative mood (under 70 characters)

Body:
Explain WHY the changes are needed (4-5 sentences max).
Reference relevant issues, meetings, or discussions.
Keep lines under 70 characters for readability.
You are writing this text for a reviewer. Don't make their life hard.

For implementation details see [ISSUE-NUMBER].
```

### Requirements

**Title:**
- Use English and imperative language ("Add feature" not "Added feature")
- Answer "WHAT?" - describe what the commit does
- Keep under 70 characters
- No issue numbers in the title
- Use appropriate scopes for monorepo structure:
  - Single package: `feat(go): add error handling`
  - Multiple packages: `feat: add error handling across reporters`
  - Breaking changes: `feat!: change shared contract interface`
  - Reporter-specific: `pytest: Add support for...` (legacy format accepted)

**Body:**
- Explain "WHY?" - provide context for the changes
- Reference issues, emails, or meetings with specific identifiers
- Use professional, neutral language
- Break lines at ~70 characters
- Provide appropriate context based on change complexity (see Context Guidelines below)

**Example Good Commits:**

```
feat(go): implement pass-through output for real-time test visibility

Users can now see test progress in real-time while the reporter processes
results. The reporter acts as a transparent filter, immediately passing
all output to stdout while simultaneously capturing it for processing.

This ensures users maintain visibility of test execution without delays,
addressing a key usability requirement for the Go reporter.

🤖 Generated with [Claude Code](https://claude.ai/code)
Co-Authored-By: Claude <noreply@anthropic.com>
```

```
fix(docs): correct stderr redirection syntax in Go examples

The 2>&1 redirection must come after the test target (./...) for
proper shell syntax. Incorrect placement would cause the command
to fail.
```

```
chore(cli): bump version to 0.9.1

Release includes Go reporter improvements and build failure handling.
See CHANGELOG.md for detailed changes.
```

### Context Guidelines

Provide appropriate context based on your change:

- **Simple fixes/typos**: Brief explanation acceptable
- **Feature additions**: Explain user benefit and approach  
- **Architecture changes**: Detailed reasoning required
- **Cross-language/reporter changes**: Impact on all affected components
- **Documentation updates**:
  - User-facing docs (README, setup): Explain user impact
  - API/code docs: Technical context required
  - Typo fixes/formatting: Minimal context acceptable

Focus on WHY over WHAT. Write for reviewers and future maintainers.

### AI-Assisted Development

AI collaboration is welcomed and encouraged. Contributors may include AI assistance acknowledgments in commit messages to maintain transparency about development methods.

**Acceptable AI signatures:**
- ✅ "🤖 Generated with [Claude Code](https://claude.ai/code)"
- ✅ "Co-Authored-By: Claude <noreply@anthropic.com>"
- ✅ "Implemented with AI assistance"

**Guidelines:**
- Focus commit message body on business reasoning (human decision-making)
- AI signatures acknowledge tool usage, not decision ownership
- Technical implementation details can reference AI assistance

### What to Avoid

- Vague titles like "fixed stuff" or "updates"
- Multiple unrelated changes in one commit
- Missing context about why changes were made
- Unprofessional language or jokes
- Lines exceeding 70 characters
- Mixing different types of changes (bug fixes + new features + refactoring)
- Adding fixes for previous commits. Just amend them yourself. Please.

## Pull Request Requirements

Before submitting your PR:

1. ✅ All commits follow the message guidelines above
2. ✅ `npm run checks` passes without errors (typecheck, lint, format, test)
3. ✅ `npm run build` completes successfully
4. ✅ All reporter tests pass with `npm run test:reporters`
5. ✅ Branch created from `main`
6. ✅ Package-specific tests pass for affected reporters
7. ✅ Node.js version compatibility verified (18+)
8. ✅ Claude Code hook integration tested manually
9. ✅ If changing shared contracts, all reporters tested
10. ✅ Package versions updated appropriately (if releasing)

## Quality Checklist

Use this checklist for each commit:

- [ ] Commit has clear, imperative title under 70 characters
- [ ] Body provides appropriate context for change complexity
- [ ] Professional language used throughout
- [ ] Lines broken at ~70 characters for readability
- [ ] References to relevant issues/meetings included (where applicable)
- [ ] Appropriate scope used for monorepo structure
- [ ] Code passes `npm run checks`
- [ ] Build completes with `npm run build`
- [ ] Changes are logically grouped (not mixing unrelated modifications)
- [ ] There are no fixes for previous commits in new commits
- [ ] Cross-reporter compatibility considered and tested
- [ ] TypeScript types are properly defined (no `any` without justification)
- [ ] Tests added/updated for new functionality
- [ ] Package boundaries respected (no cross-language dependencies)
- [ ] AI assistance acknowledged transparently (if applicable)

## Monorepo Requirements

### Package Structure
- **JavaScript/TypeScript reporters**: Must import shared functionality from 'tdd-guard' package only
- **Other language reporters**: Must be self-contained with no JavaScript dependencies
- **Shared contracts**: Test result JSON format must remain consistent across all reporters
- **Package independence**: Each reporter must maintain its own version, changelog, and documentation

### Cross-Package Changes
- **Breaking changes to shared contracts**: Use `feat!:` prefix and test all reporters
- **Multi-language compatibility**: Test affected reporters before committing
- **Version coordination**: Update package versions consistently when releasing
- **Documentation updates**: Update both main README and affected reporter READMEs

## Security Requirements

- Never execute untrusted code or commands
- Validate all file paths before operations
- Ensure reporters only write to designated output paths (`.claude/tdd-guard/data/test.json`)
- Document any new permissions or access requirements in commit message

---

*Note: These guidelines balance code quality with development efficiency, supporting both AI-assisted and traditional development workflows. They enable clear communication while avoiding unnecessary friction, helping maintain TDD Guard's high velocity and multi-language architecture.*