# Human to-do

Steps only the owner can do, or that need the laptop (government sites aren't reachable from the cloud VM, and the
cloud session has no real browser to check the UI by eye). Kept in order. Exact instructions are filled in as each
step becomes ready.

## Laptop

Run these in Claude Code on the laptop (where `CLAUDE_CODE_REMOTE` is not `true`), after pulling the branch
`main`. Claude does them; the owner checks the results.

1. **Build the real holiday files** (Phase "Laptop", ADR 0007): fetch the DoPT office memorandum for Central
   Government holidays for 2026 and 2027 (and 2025 and 2028 if published, for the year edges), and write
   `backend/src/main/resources/holidays/india-YYYY.json` with `source.url` pointing at the official document. List
   any date that can't be checked against the document below, under "Owner".
2. **Check every screen in a real browser** at phone (360 px) and desktop widths, in light and dark mode, and save
   README screenshots.
3. **Run the quality gate**, commit and push.

## Owner

Steps that need the owner's accounts, money decisions or judgement. Full order is finalised at STOP 4.

1. **Verify the holiday data** the laptop phase builds: compare every date in
   `backend/src/main/resources/holidays/india-*.json` with the official document linked in its `source.url`, and
   check any date listed as unverified above.
2. **Merge into `main`**: done on 2026-10-29 by renaming the working branch to `main` on GitHub (history kept, old
   links redirect).
3. **Check Render's current plans** on render.com/pricing (the build VM can't reach render.com): confirm the Free
   web service still exists and how long it takes to wake after sleeping, and decide whether the judging period
   needs the always-on Starter instance. A paid plan costs money: the owner's call.
4. **Deploy** with the steps in [DEPLOYMENT.md](DEPLOYMENT.md#deploy-to-render-first-time), including the region
   choice before the first apply, then run "After every deploy" there (rate limit per device included).
5. **Add the live link** to `README.md` and push.
6. **Test on a phone** (the PRD's acceptance item) and run the PRD's 3-colleague check; record only real results.
7. **Submitted** on 29 Oct 2026 at 15:39 IST (the form says the submission can be edited any time). The answers are in
   `docs/SUBMISSION_ANSWERS.md`; if one changes, edit the submission to match. The form takes repository links, not a ZIP;
   `scripts/build-submission.sh` stays for anyone who asks for one.
