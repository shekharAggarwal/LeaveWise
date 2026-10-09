# Human to-do

Steps only the owner can do, or that need the laptop (government sites aren't reachable from the cloud VM, and the
cloud session has no real browser to check the UI by eye). Kept in order. Exact instructions are filled in as each
step becomes ready.

## Laptop

Run these in Claude Code on the laptop (where `CLAUDE_CODE_REMOTE` is not `true`), after pulling the branch
`claude/inspiring-darwin-2m8wjb`. Claude does them; the owner checks the results.

1. **Build the real holiday files** (Phase "Laptop", ADR 0007): fetch the DoPT office memorandum for Central
   Government holidays for 2026 and 2027 (and 2025 and 2028 if published, for the year edges), and write
   `backend/src/main/resources/holidays/india-YYYY.json` with `source.url` pointing at the official document. List
   any date that can't be checked against the document below, under "Owner".
2. **Check every screen in a real browser** at phone (360 px) and desktop widths, in light and dark mode, and save
   README screenshots.
3. **Run the quality gate**, commit and push.

## Owner

Filled in at STOP 3 and STOP 4 with exact steps: verify the holiday data, merge the branch into `main`, deploy,
add the live link to the README, test on a phone, the 3-colleague check from the PRD, record the demo, build the
ZIP and submit by 29 Oct.
