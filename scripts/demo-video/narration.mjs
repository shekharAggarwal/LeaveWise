// Narration for the LeaveWise demo video (how to make it: tts.mjs, then record.mjs, then encode.mjs). `say` is what the voice reads (spelled for text-to-speech);
// `caption` is what the screen shows. Numbers match /api/plan for the inputs used on screen.
export const VOICE = 'Aman';
export const RATE = 120;

export const segments = [
  {
    id: 'title',
    steps: [
      {
        id: 'intro',
        say: "This is Leave Wise, Shekhar Aggarwal's entry for the Hack at Fifty Five A I hackathon. The voice is synthetic. Claude Code scripted and recorded this video.",
        caption: "LeaveWise, for the Hack at FiftyFive AI Hackathon. The voice is synthetic: Claude Code scripted and recorded this video.",
      },
    ],
  },
  {
    id: 'problem',
    steps: [
      {
        id: 'p1',
        say: "Every year, when the new holiday list comes out, people sit with a calendar, trying to work out which days to take off.",
        caption: "Every year, when the new holiday list comes out, people sit with a calendar, trying to work out which days to take off.",
      },
      {
        id: 'p2',
        say: "Holidays move every year, and your balance is limited, so order matters. Spend leaves on a weak break, and you can't afford a better one later.",
        caption: "Holidays move every year and your balance is limited, so order matters. Spend leaves on a weak break and you can't afford a better one later.",
      },
      {
        id: 'p3',
        say: "Calendar apps show holidays, but never where to put your leaves. Leave Wise does, and its plan is provably the best one for your inputs.",
        caption: "Calendar apps show the holidays, but never where to put your leaves. LeaveWise does, and its plan is provably the best for your inputs.",
      },
    ],
  },
  {
    id: 'product',
    steps: [
      {
        id: 'defaults',
        say: "It's one page, with no login. Every input has a default: next year, twelve leave days, Saturday and Sunday off, and long weekends.",
        caption: "One page, no login. Every input has a default: next year, 12 leave days, Saturday and Sunday off, long weekends.",
      },
      {
        id: 'list2026',
        say: "For this year, the holiday list comes prefilled with Fifty Five's calendar. Floating holidays start unticked, so you pick your own.",
        caption: "For this year the holiday list comes prefilled with FiftyFive's calendar. Floaters start unticked, so you pick your own.",
      },
      {
        id: 'paste',
        say: "Next year's list isn't out yet, so I'll paste one: the same holidays on their twenty twenty-seven dates, plus one line with a date that doesn't exist.",
        caption: "Next year's list isn't out yet, so I paste one: the same holidays on their 2027 dates, plus a line with a date that doesn't exist.",
      },
      {
        id: 'loaded',
        say: "Eleven holidays load, and the bad line gets its line number and the reason. Every input is checked in the browser and again on the server.",
        caption: "Eleven holidays load, and the bad line gets its line number and the reason. Inputs are checked in the browser and again on the server.",
      },
      {
        id: 'plan',
        say: "Now, plan my leaves. Twelve leaves become forty-one days off, across twelve breaks.",
        caption: "Plan my leaves: 12 leaves become 41 days off, across 12 breaks.",
      },
      {
        id: 'breaks',
        say: "Each break lists its dates, the leaves to take, the holidays inside it, and the days off each leave buys.",
        caption: "Each break lists its dates, the leave days to take, the holidays inside it and the days off each leave buys.",
      },
      {
        id: 'calendar',
        say: "The calendar marks leaves, holidays and weekly offs with a colour and a letter, so it works without colour too. Tap a day to see what it is.",
        caption: "The calendar marks leaves, holidays and weekly offs with a colour and a letter, so it works without colour too. Tap a day to see it.",
      },
      {
        id: 'ties',
        say: "When plans tie, earlier dates win, so these short breaks bunch up in January and February. For real trips, pick big trips.",
        caption: "When plans tie, earlier dates win, so these short breaks bunch up in January and February. For real trips, pick Big trips.",
      },
      {
        id: 'bigtrips',
        say: "The page says this plan is out of date. Plan again, and you get three trips: nine days around Republic Day, nine around Eid, and ten from Diwali to Chhath Puja.",
        caption: "The page flags the old plan. Plan again: three trips, 9 days around Republic Day, 9 around Eid, and 10 from Diwali to Chhath Pooja.",
      },
      {
        id: 'ics',
        say: "Add to calendar downloads an I C S file, with one all-day event per break, and the leave dates in its notes.",
        caption: "Add to calendar downloads an .ics file: one all-day event per break, with the leave dates in its notes.",
      },
    ],
  },
  {
    id: 'phone',
    steps: [
      {
        id: 'phone1',
        say: "It's built phone first, too. Here is the same plan at phone width.",
        caption: "It's built phone first, too. Here is the same plan at phone width.",
      },
      {
        id: 'phone2',
        say: "The calendar goes month by month, and a tap opens the day.",
        caption: "The calendar goes month by month, and a tap opens the day.",
      },
    ],
  },
  {
    id: 'code',
    steps: [
      {
        id: 'arch',
        say: "Now the code. One Spring Boot app serves the React page and the A P I, in one Docker image. The planner package is plain Java, with no Spring and no input or output, so it's tested on its own.",
        caption: "Now the code. One Spring Boot app serves the React page and the API, in one Docker image. The planner is plain Java, tested on its own.",
      },
      {
        id: 'dp',
        say: "This is the core: Break Optimizer dot solve. It is dynamic programming. Best of p and b is the best plan from day p onward, with up to b leaves.",
        caption: "The core is BreakOptimizer.solve, a dynamic program. best[p][b] is the best plan from day p onward with up to b leaves.",
      },
      {
        id: 'candidates',
        say: "A break in the best plan always starts and ends next to a working day you don't take. So only those runs are candidates, which keeps it exact and fast.",
        caption: "A break in the best plan starts and ends next to a working day you don't take, so only those runs are candidates. Exact and fast.",
      },
      {
        id: 'tiebreak',
        say: "This line is the tie-break: more days off first, then fewer leaves, then earlier dates. The same inputs always give the same plan.",
        caption: "The tie-break: more days off, then fewer leaves, then earlier dates. The same inputs always give the same plan.",
      },
      {
        id: 'oracle',
        say: "And this is how I know it's right. This property test makes random calendars, solves each with a brute force solver that tries every combination of leave days, and checks the planner agrees. Fifteen hundred calendars, all equal.",
        caption: "How I know it is right: random calendars, each solved by a brute-force solver that tries every combination. 1,500 calendars, all equal.",
      },
      {
        id: 'controller',
        say: "At the edge, the controller reads the clock once per request, and checks every input again on the server. Filters add a rate limit, a size limit and a strict content security policy.",
        caption: "The controller reads the clock once per request and re-checks every input. Filters add rate and size limits and a strict CSP.",
      },
    ],
  },
  {
    id: 'ai',
    steps: [
      {
        id: 'brief',
        say: "Claude Code built this from a written brief, with stop points where I reviewed the work. The rules it worked under are in Claude dot M D.",
        caption: "Claude Code built this from a written brief, with STOP points where I reviewed the work. Its rules are in CLAUDE.md.",
      },
      {
        id: 'agents',
        say: "Narrow jobs went to subagents on the cheapest model that could do them. A test designer wrote tests from the requirements before the code. An edge case hunter attacked the app, and reviewers checked each part.",
        caption: "Narrow jobs went to subagents on the cheapest model that could do them: a test designer, an edge-case hunter and three reviewers.",
      },
      {
        id: 'failures',
        say: "Every mistake a check caught is logged, with how it was found and the rule that stops it coming back. Here, a plan request at New Year midnight failed, because the clock was read twice. That's why the controller reads it once.",
        caption: "Every mistake a check caught is logged with the rule that prevents it. Here, New Year midnight broke a plan request: the clock was read twice.",
      },
    ],
  },
  {
    id: 'proof',
    steps: [
      {
        id: 'ci',
        say: "One quality gate runs every check, the same way on my laptop and in C I: the tests, mutation testing, a real browser check and a dependency scan. Today that scan caught three new advisories, and they were patched the same day.",
        caption: "One quality gate runs every check, locally and in CI: tests, mutation testing, a real-browser check and a dependency scan, which caught three advisories today.",
      },
    ],
  },
  {
    id: 'end',
    steps: [
      {
        id: 'close',
        say: "Leave Wise. The best plan for your leave, in under a minute, and proven. Thanks for watching.",
        caption: "LeaveWise: the best plan for your leave, in under a minute, and proven. Thanks for watching.",
      },
    ],
  },
];
