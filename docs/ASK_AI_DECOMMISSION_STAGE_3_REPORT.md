# Ask AI Decommission — Stage 3 Report

## Outcome

Stage 3 is complete. The conversational presentation layer made unreachable in Stage 2 has now been physically removed.

This stage removed UI code only. It did not remove provider credentials, provider clients, repositories, histories, database tables, migrations, caches, Qur'an memorisation features, or note-formatting infrastructure.

## Removed

- The standalone note Ask AI screen.
- The shared Home/Library/Courses Ask AI panel.
- The shared Ask AI input bar.
- The conversational attachment picker.
- Chat-specific shared UI controls.
- The obsolete Ask AI navigation destination.

Total removed from these presentation files and the route declaration: **2,538 lines**.

## Explicitly protected

### Note AI

- Structure & Format editor entry point.
- Run Structure Only.
- Run Intelligent Structure.
- Provider/model support used by note formatting.
- Preview, copy, insert, and replace workflows.

### Qur'an memorisation

- AI Listen action on āyah cards.
- `QuranAiListenSheet` recording and analysis experience.
- `QuranMemorizationRecorder`.
- Google and OpenAI speech-recognition provider choices.
- Memorisation analysis, scoring, comparison, attempt persistence, and dashboard data.
- AI Listen completion callback into the Qur'an view model.
- Surah testing and memorisation utilities.

### Data and backend rollback boundary

- Existing AI conversation and message records.
- Home chat history records.
- Library AI/PDF cache records.
- Conversational repositories, clients, prompt builders, state, and view model.
- Database schema and migrations.
- API keys, secrets, and credential configuration.

## Regression protection

The decommission contract test now verifies that the deleted presentation files remain absent. It also explicitly verifies that Qur'an AI Listen remains reachable and retains its recorder and speech-provider integration.

## Verification

- Kotlin compilation: passed.
- Unit tests: **133 passed**.
- Debug APK assembly: passed.
- Deleted-component reference audit: passed.
- Qur'an AI Listen source and navigation audit: passed.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Deferred opportunity

The future **Ask about this āyah** utility remains deferred until the decommission is complete. It should be designed as a bounded Qur'an action rather than restoring general conversational Ask AI.

## Stage boundary

No database cleanup or backend removal has started. No secret or private-key work has started. Those require separate stage approval and migration safeguards.
