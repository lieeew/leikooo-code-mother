# Plan Mode Standards

## 1. Plan Output Location
- All plans must be written to `plans/` folder
- File naming: `plans/{feature-name}-plan.md`
- Prohibit oral descriptions only, plan must be documented

## 2. Multi-Solution Principle
- **Provide minimum 2 solutions, recommend 3**
- Each solution must include:
  - Solution name
  - Core idea (1-2 sentences)
  - Pros
  - Cons / Risks
  - Recommendation score (1-5 stars)

## 3. Proactive Questioning
- After entering plan mode, **confirm requirement boundaries with user first**, then present solutions
- Must clarify:
  - Impact scope (which modules affected?)
  - Constraints (performance requirements? backward compatibility?)
  - Priority (speed or quality first?)
- If user requirements are vague, list all clarification points in bulleted list, **do not self-assume**

## 4. Plan File Template

```markdown
# {Feature Name} — Architecture Design

## Requirement Clarification
- [ ] {Clarification point 1}
- [ ] {Clarification point 2}

## Solution One: {Name}
**Idea:** ...
**Pros:** ...
**Cons:** ...
**Recommendation Score:** ⭐⭐⭐⭐

## Solution Two: {Name}
**Idea:** ...
**Pros:** ...
**Cons:** ...
**Recommendation Score:** ⭐⭐⭐

## Final Selection
> To be filled after user confirmation
```

## 5. Prohibit
- Prohibit providing only one solution then starting implementation
- Prohibit skipping user confirmation before implementation
- Prohibit writing implementation code in plan (plan only describes what & why, not how details)
