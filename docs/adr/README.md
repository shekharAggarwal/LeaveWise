# Architecture decision records

Short records of real decisions: context, the decision, what it costs. Status is `Accepted` unless a later ADR
supersedes it. Add a new ADR rather than rewriting history; update the "Consequences" of an old one only with a
dated note.

| ADR | Decision |
| --- | --- |
| [0001](0001-single-deployable.md) | One Spring Boot jar serves the page and the API |
| [0002](0002-no-database.md) | No database; holiday lists are bundled JSON resources |
| [0003](0003-optimisation-algorithm.md) | Exact dynamic programming over candidate breaks |
| [0004](0004-deterministic-tie-breaks.md) | A total order on plans, so the same inputs give the same plan |
| [0005](0005-brute-force-oracle-testing.md) | A brute-force oracle and property tests prove optimality |
| [0006](0006-year-edges-and-planning-window.md) | Year edges and planning from tomorrow |
| [0007](0007-holiday-data-source.md) | Holiday data from an official Government of India source only |
| [0008](0008-astryx-design-system.md) | Astryx 0.6.6 with the Stone theme, pinned exactly |
