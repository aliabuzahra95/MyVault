# Ask AI Decommission — Stage 2 Report

## Outcome

Stage 2 is complete. Conversational Ask AI is no longer reachable from MyVault's user interface. The existing chat implementation and stored data remain dormant so later deletion and migration work can be reviewed separately.

The retained AI experience is now the focused **Structure & Format** action in the note editor.

## User-visible changes

- Removed the Ask AI floating launcher and panel from Home/Study.
- Removed the Ask AI floating launcher and panel from Library.
- Removed the Ask AI floating launcher and panel from Courses.
- Removed Ask AI from the note reading toolbar.
- Removed the selected-text Ask AI action from the note editor.
- Replaced the note editor's general Ask AI button with **Structure & Format**.
- Removed the Ask AI action for selected PDF activity items while retaining **Create Study Note**.
- Removed the Ask AI destination from the active navigation graph.

## Retained behaviour

- Run Structure Only.
- Run Intelligent Structure.
- Provider and model selection used by these formatting actions.
- Formatting result preview, copy, insert, and replace workflows.
- Note editing, selected-text narration, PDF reading, highlighting, annotations, and activity-feed study-note creation.

## Deliberately dormant for rollback

No conversational data or infrastructure was deleted in this stage. The following remain in source or storage but have no user-facing route:

- Note Ask AI screen and chat components.
- Shared inline AI panel, view model, repository, and provider clients.
- Conversation/history database tables and DAOs.
- Library conversational PDF cache and retrieval implementation.
- Existing stored conversations and messages.
- The legacy Ask AI destination declaration, which is no longer registered in the navigation graph.

This is intentional. Physical deletion and data migrations belong to later approved decommissioning stages.

## Regression protection

`AskAiSurfaceDecommissionContractTest` now checks that:

- the active navigation graph cannot open Ask AI;
- Home, Library, and Courses do not mount the shared chat panel;
- the editor retains Structure Only and Intelligent Structure;
- reading and PDF activity surfaces do not expose Ask AI actions.

## Verification

- Debug Kotlin compilation: passed.
- Debug unit tests: 131 passed.
- Debug APK assembly: passed.
- Generated APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Navigation audit: no active `VaultDestination.AskAi` or `AskAiScreen` reference.
- Shared-screen audit: no `HomeInlineAiPanel` or `HomeInlineAiViewModel` mounted by Home, Library, or Courses.

## Deferred product opportunity

The previously explored **Ask about this āyah** concept should be reconsidered after the decommission is complete. It should be treated as a bounded Qur'an utility, not as a return of general Ask AI.

Recommended first version:

- āyah-specific actions such as explanation, vocabulary, themes, and commentary comparison;
- context limited to the chosen āyah and explicitly selected trusted sources;
- a compact result surface without general workspaces, tutoring checkpoints, or permanent chat orchestration;
- follow-up conversation considered only after the single-action version proves useful.

## Stage boundary

Stage 3 has not started. No database tables, histories, provider clients, caches, or conversational source files were deleted.
