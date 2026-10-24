# FamilyGram family hub

Base: main 006680517d77e8e2d4f202a538ece8fa30117a35.
Branch: feature/family-hub. Existing feature/ghost-mode and its local staged files are untouched.

Requested: shared tasks, family calendar, partner album/date search, Our bookmarks,
photo launcher choices, searchable FamilyGram settings, couple widget, hug,
daily mutual question, goal/contributions, synced video, private screen,
unheard voice queue/silence skip/resume, release updates/APK, random heart/kiss,
resumable two-player games, itemized bill splitting, conversation statistics.

Architecture: ordinary Telegram messages carry a bounded versioned FamilyGram
operation after human-readable text. FamilyGram renders local inline controls;
other clients retain the readable message and transport footer. No external
backend. The sender is taken from the actual Telegram message, never the payload.
SQLite stores confirmed operations and a history index per actual user ID/dialog.
Edits/deletions of transport messages do not silently erase previously accepted
shared actions; reversal is an explicit later operation. Shared interactive UI
requires FamilyGram on both devices. Secret chats and disappearing media are
excluded from the shared-operation protocol.

Never mark a message read merely for synchronizing. Paginate history without
readHistory calls. Show scanned-history coverage for statistics. No live message
will be sent during development tests.

Verification and delivery results will be recorded here before the PR is opened.

## Verification checkpoint

- First remote snapshot: 7bb36c6e4d16a8acfa47221d5b69c14fff9d6ff2; draft PR #2.
- Core verification now runs 8,547 checks, including different per-account
  Telegram message IDs, exact bill remainders, duplicate contributions,
  question commitments, legal turns and birthdays before 1970.
- Java syntax: 24 source files. New resources: 203 English/Russian keys;
  standalone Android aapt2 resource compilation succeeds.
- First GitHub Android compilation reached javac and found an ambiguous import
  and concrete privacy-rule casts. Both are corrected in the follow-up snapshot.
- No live Telegram messages were sent by development tests. Two-device runtime
  behavior, media synchronization latency, launcher-specific pinning and widget
  resizing still require device QA before a production release.
- Local Gradle bootstrapping cannot use this runtime's Java network transport;
  the GitHub Actions Android compilation is the authoritative type-check gate.

History synchronization fills gaps since the last known head and resumes older
history indexing. The user can stop it. Shared operations are replayed in logical
clock/nonce/sender order, not account-local Telegram ID order. Native history
clearing removes this workspace's local index, voice progress and operation log.
