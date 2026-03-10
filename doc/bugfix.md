# Bug Fix Standards

## 1. Core Principle: Diagnose First, Fix Second

**Prohibit intuitive code changes.** Must first produce a clear root cause report, confirm error reason before modifying code.

## 2. Diagnosis Workflow

### Step 1: Launch Subagent for Deep Code Reading
- **Must** use Task tool (subagent_type=Explore) to re-read relevant code
- Don't rely on previous conversation memory, **re-read source code every time**
- Subagent must cover:
  - All files involved in error stack trace
  - Call chain upstream and downstream (caller → callee complete chain)
  - Related config files, SQL, mappers

### Step 2: Ultra Think Deep Analysis
- Perform logic deduction for every relevant line of code
- List all possible error causes (minimum 2 hypotheses)
- Eliminate each hypothesis one by one until root cause is locked
- **If root cause cannot be determined, prohibit code changes, ask user first**

### Step 3: Output Diagnosis Report
- Write report to `errors/{bug-name}-diagnosis.md`
- Report must include following structure:

```markdown
# {Bug Description} — Diagnosis Report

## Error Phenomenon
- Error message / stack trace
- Reproduction conditions

## Code Analysis
- Involved files and critical line numbers
- Call chain diagram

## Root Cause Identification
**Root Cause:** {One-sentence description}
**Evidence:** {Line numbers + logic deduction}

## Eliminated Hypotheses
1. {Hypothesis A} — Elimination reason: ...
2. {Hypothesis B} — Elimination reason: ...

## Fix Plan
- Files to modify: ...
- Modification details: ...
```

### Step 4: Confirm Then Fix
- After report produced, wait for user confirmation that root cause is correct
- Only execute code modifications after confirmation

## 3. Prohibit
- Prohibit guessing cause without reading code
- Prohibit skipping diagnosis and jumping to code changes
- Prohibit looking only at error line, not call chain context
- Prohibit fixing multiple unrelated bugs in one report

## 4. Subagent Call Template

When using Task tool, use this prompt template:

```
Deep-read the following files to analyze {bug description}:
1. Read {file1}, focus on {specific method/line}
2. Read {file2}, trace call chain
3. Check related config {config file}
4. Output: all possible root cause hypotheses + evidence
```
