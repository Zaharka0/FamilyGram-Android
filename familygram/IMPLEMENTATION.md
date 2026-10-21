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
