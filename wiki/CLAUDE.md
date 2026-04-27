---
title: Wiki Schema
description: How this wiki is structured and how the LLM should maintain it
---

# Wiki Schema

This is a personal work-tracking wiki for everything Patrik does in `~/src/`. The LLM owns this directory — Patrik reads it, the LLM writes it. The vault root is one level up (`~/src/`); this wiki is one folder inside that vault, so Obsidian wikilinks resolve across both.

## What this wiki is for

A persistent, compounding artifact that tracks projects, decisions, learnings, and sessions across all work in `~/src/`. Cross-project synthesis is a primary goal — patterns that appear in one project (e.g. how to structure prompt versioning, how to handle decimal money) should accumulate into reusable concept pages.

## Source scope (wide)

Anything that informs work in `~/src/` is a candidate source:

1. **Claude Code sessions** — what we discussed, decided, and built. Captured as session pages.
2. **Project artifacts** — specs, plans, READMEs, design docs that live inside project directories. The wiki references them via wikilinks; it does not duplicate them.
3. **Git activity** — significant commits, PRs, branches. Summarized when material to a decision or status update.
4. **External sources** — articles, papers, docs, podcasts, talks. Clipped into `raw/` and summarized into `sources/`.

## Directory structure

```
wiki/
  CLAUDE.md         this file — the schema
  index.md          content catalog (all pages, by category)
  log.md            chronological append-only log of operations
  projects/         one page per project in ~/src/
  concepts/         reusable ideas, patterns, gotchas (cross-project)
  decisions/        ADR-style decision records (with rationale)
  sessions/         Claude Code session summaries
  sources/          summaries of ingested sources (specs, articles, etc.)
  raw/              immutable source files (clipped articles, PDFs, transcripts)
```

The wiki is just markdown + an Obsidian vault. No build step. No database.

## Page conventions

Every page has YAML frontmatter:

```yaml
---
title: Human-readable title
type: project | concept | decision | session | source
created: YYYY-MM-DD
updated: YYYY-MM-DD
tags: [tag1, tag2]
---
```

Source pages add:
```yaml
source_path: ../path/to/original   # relative to wiki/, or external URL
ingested: YYYY-MM-DD
```

Project pages add:
```yaml
status: active | paused | archived
project_path: ../project-dir
```

Decision pages add:
```yaml
decision_date: YYYY-MM-DD
status: proposed | accepted | superseded
supersedes: [decision-name]    # optional
superseded_by: [decision-name] # optional
```

### Filenames

- kebab-case: `invoice-to-journal.md`, `bigdecimal-scale-equality.md`
- No dates in filenames (frontmatter handles dates)
- Decisions can use a short slug: `two-llm-calls-not-one.md`

### Links

Obsidian wikilinks: `[[invoice-to-journal]]`, `[[bigdecimal-scale-equality]]`. For cross-vault links to project artifacts (e.g. `~/src/PLAN.md`), use `[[PLAN]]` — Obsidian resolves by basename.

External URLs use standard markdown: `[label](https://...)`.

## Operations

### Ingest

When Patrik points to a source (file path, URL, or content):

1. Read the source in full. For PDFs/long articles, summarize the substantive content, not the boilerplate.
2. Discuss the key takeaways with Patrik before writing — a quick "here's what I'm pulling out of this" exchange beats a silent dump.
3. Write a summary page in `sources/` with frontmatter, a TL;DR, key claims/quotes (with citations to the original), and a "see also" list of related wiki pages.
4. Update relevant `projects/`, `concepts/`, and `decisions/` pages. A single source might touch 5–15 pages.
5. Append an entry to `log.md` in the format below.
6. Update `index.md` if any new pages were created.

If a source is an external clip, drop the raw file in `raw/` first, then ingest from there.

### Query

When Patrik asks a question:

1. Read `index.md` first to find candidate pages.
2. Read the candidates. For broader queries, also `grep` across the wiki.
3. Synthesize an answer with citations (wikilinks to the pages that supplied each claim).
4. If the answer surfaces a connection or analysis worth keeping, ask Patrik whether to file it back as a new page (`concepts/`, `decisions/`, or as a sub-section of an existing page). Good answers compound into the wiki.

### Lint

When Patrik asks for a health check:

- Contradictions between pages (same claim, different values).
- Stale claims newer sources have superseded.
- Orphan pages (no inbound wikilinks) — either link them in or archive.
- Concepts mentioned in 2+ pages but lacking their own page.
- Missing cross-references (page A talks about X, page B is about X, no link).
- Suggestions for new sources or questions to investigate.

### Log format

`log.md` is append-only. Each entry starts with a parseable header so `grep "^## \[" log.md` works:

```
## [YYYY-MM-DD] <op> | <short title>
- Pages: [[page-a]], [[page-b]], ...
- Notes: one-line summary of what changed or was found
```

Where `<op>` ∈ {`ingest`, `query`, `lint`, `session`, `decision`, `refactor`}.

## Co-evolving this schema

This file is v0.1 — the first cut. As patterns emerge (new page types, new conventions, new operations), update this schema rather than creating exceptions. The schema is the contract; exceptions are debt.

When updating CLAUDE.md, append a brief note to `log.md` with op `schema` so the evolution is visible.
