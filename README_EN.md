<div align="center">

# MutCube

**An open-source Android AI client and programmable AI service container**

Multi-provider LLM chat · AI projects and memory · Agent Skills · MCP · Template services · Voice · Encrypted backup

> One intelligence, infinite forms.

English · [简体中文](README.md)

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg)
![Version](https://img.shields.io/badge/version-0.1.3-orange)

</div>

MutCube is a native Android AI application built with Kotlin and Jetpack Compose. It is both a multi-provider LLM chat
client and an AI host for project context, long-term memory, Agent Skills, MCP tools, and visual template services.

Instead of forcing every task into chat bubbles, MutCube lets the same AI take different forms: chat for open-ended
conversation, templates for stable workflows and structured data, Skills for reusable instructions, and MCP for external
tools.

## Project status

MutCube is under active development. The current app version is **0.1.3**, the package name is `com.dwl.mutcube`, and
the minimum supported version is Android 8.0 (API 26). The core chat, project, memory, provider, Skill, MCP, backup, and
built-in template flows are implemented, but UI, protocols, and storage schemas may still change before a stable release.

No commercial model API keys or free model credits are bundled. Cloud models, speech services, MCP servers, WebDAV,
and S3 require services and credentials supplied by the user.

The 2026-10-08 reliability and open-source preparation review is documented in the
[audit report](docs/OPEN_SOURCE_READINESS_AUDIT.md), including verified results and remaining limitations.
See the [documentation index](docs/README.md) for current references and historical acceptance records.

## Why MutCube

Most AI clients organize everything around conversations. MutCube adds three complementary concepts:

- **Projects** organize related conversations, instructions, model preferences, memories, and a template service. A
  conversation can still exist outside any project.
- **Skills** are reusable workflows using the Agent Skills structure. They can be selected automatically or explicitly
  from the composer.
- **Templates** are AI services with a GUI, declared actions, structured data, and permission boundaries. Template runs
  do not masquerade as ordinary chat messages.

In the built-in fitness template, for example, forms and calendars provide efficient interaction, AI organizes profile
information and generates candidate plans, while the host enforces permissions, schemas, version confirmation, and
persistence. Authorized project chats can discover and invoke the same template actions.

## Screenshots

Captured on a physical Android device: the empty home screen, a real AI response, and the built-in fitness template. These are not HTML mockups.

| Home | AI chat | Fitness template |
| --- | --- | --- |
| <img src="docs/screenshots/01-home.png" width="240" alt="MutCube home screen"> | <img src="docs/screenshots/03-chat.png" width="240" alt="AI response and message actions"> | <img src="docs/screenshots/07-fitness-intake.png" width="240" alt="Fitness profile intake"> |

[View all 10 screenshots and capture notes](docs/screenshots/README.md).

## Features

### Multi-provider AI chat

- OpenAI Chat Completions-compatible APIs, plus native Google Generative Language and Anthropic Messages protocols.
- Editable presets for Xiaomi MiMo, OpenAI, Google Gemini, Anthropic Claude, DeepSeek, SiliconFlow, OpenRouter,
  Alibaba Cloud Model Studio, Volcengine, Zhipu AI, Moonshot AI, and more; custom HTTPS endpoints and model IDs are
  supported.
- Provider enable/disable, drag reordering, model discovery, connection diagnostics, model aliases/icons/favorites,
  and declared capabilities for images, tools, reasoning, context windows, and output limits.
- Streaming responses, stop, retry, regenerate, edit-and-branch, candidate switching, and per-conversation system prompts.
- Token usage, cached tokens, generation speed, and duration; redacted request logs and detailed errors in developer mode.
- Markdown text, code, lists, and tables; wide tables scroll horizontally without forcing the whole message to overflow,
  and response text supports native selection and copying.

### Conversations, projects, context, and memory

- Rename, pin, search, soft-delete and undo, favorite, share, export, and branch conversations.
- Move conversations into or out of projects. Each project can override its provider, model parameters, instructions, and
  context policy.
- Source-attributed search across other conversations in the same project. Unscoped chats cannot access project data.
- Long conversations create compression checkpoints after message or context thresholds are reached; original messages
  remain intact in the local database.
- Global and project memories can be reviewed, edited, deleted, and scoped. AI memory writes require an explicit opt-in.
- Quick prompts, conversation modes, keyword-triggered knowledge entries, and AI-generated follow-up suggestions.

### Multimodal input, speech, and message actions

- Image, text, PDF, audio, and video attachments; long pasted text can be converted into a private TXT attachment.
- Image thumbnails, removable attachments, and Android share intents that create a new chat draft.
- System TTS/ASR and configurable speech models through an OpenAI-compatible provider, with selectable voices,
  streaming playback, and live recording waveforms.
- Copy, native partial text selection, share, translate, read/stop, favorite, and generation statistics.
- Optional foreground generation and completion notifications; lock-screen notifications do not expose message content.

### Agent Skills and MCP

- Import, create, export, search, and uninstall Skill ZIP packages following the Agent Skills directory structure.
- Automatic, manual, and disabled invocation modes, with global or project scope.
- On-demand Skill selection with visible version, source, and license metadata, plus basic selection and invocation audits.
- The current Android build reads `SKILL.md` and text reference resources, but **does not execute bundled Skill
  scripts**.
- MCP Streamable HTTP server management, bearer credentials, OAuth 2.0, tool discovery, and per-tool enablement.
- External calls can require ask/allow/deny approval and keep execution status. Credentials never enter chats, logs, or backups.

### Template service system

- Templates bind to projects rather than individual conversations. A project currently supports at most one template.
- Template GUIs and project chat share the same declarative Action engine; the host injects project identity and enforces
  access control.
- Templates declare collections and JSON contracts. The host provides Room persistence, and the data belongs to the user,
  not the template.
- AI work is stored as hidden `TemplateRun` records with input, status, context snapshots, and redacted errors, without
  polluting the normal conversation list.
- AI creates candidate versions; the active version changes only after user confirmation, while historical facts remain
  immutable by default.
- The bundled Fitness Log template supports profile forms or AI-assisted intake, daily workouts, actual set logging,
  candidate plans, and calendar history.
- Local `.mutcube-template` packages can be imported after resource-hash and permission review, then authorized per
  project. A browser SDK, Mock Host and packaging CLI are available, with English learning as the official starter and additional fitness/speech examples.
- Local packages are not developer-authenticated. Signing, a marketplace, automatic updates, and broader sensitive host
  APIs are not yet available.

See [Template modules](docs/TEMPLATE_MODULES.md) and the Chinese
[built-in template development guide](docs/TEMPLATE_DEVELOPMENT_GUIDE.md), plus the
[Package v1 specification](docs/TEMPLATE_PACKAGE_V1.md) and [Template SDK](template-sdk/README.md).

### Built-in fitness template: workout logging and AI plans

Fitness Log ships with the app; no separate import is required. Profiles, workout plans, actual results, and calendar history live within a project, demonstrating how a template GUI and AI chat cooperate through declarative host capabilities.

- **Profile intake:** fill out a form or discuss your situation with AI, then review and save. Fields include height, weight, goals, frequency, and training split, with optional experience, equipment, limitations, and familiar exercise working weights.
- **Workout planning:** AI creates candidates from saved profiles, the current plan, and recent training; it can also search authorized conversations in the same project for supporting context. Preview exercises, suggested weights, sets, rep ranges, and RIR before activating a plan. Chat content does not automatically become a training fact.
- **Actual results:** suggested weights and set counts are prefilled. Log actual reps and RIR (reps in reserve) per set, adjust weights, add or remove sets, omit exercises for the session, and add notes. Drafts are autosaved, with save-state checks when leaving.
- **Submission and next steps:** actual results are persisted before AI generates the next candidate. Generation failure does not discard submitted results and can be retried separately. Completed plans are not automatically reused for the next day; the next candidate requires activation.
- **Calendar and project chat:** marked dates show submitted workouts, plan snapshots, and results. Authorized project chat can retrieve training history or request plan adjustments; return to the template to preview and activate the new candidate.

Workflow: **open the template and bind a project → save a profile → generate, preview, and activate a plan → log a workout → submit results → review and activate the next plan**. Initial binding requires authorization; retained bindings do not require repeated confirmation. AI features require a configured Provider, model, and API key.

Currently, only one actual workout record per calendar day is supported. Chat cannot directly overwrite historical results or activate plans. AI suggestions may be incorrect and do not replace professional coaching or medical advice. See the Chinese [fitness workflow and data contract](docs/FITNESS_SERVICE_V2.md) for details. English learning remains the official third-party SDK starter; Fitness Log illustrates the complete built-in service workflow.

### Data, security, and backup

- Room stores conversations, projects, memories, template records, and execution metadata locally.
- API keys, OAuth tokens, and remote storage secrets are protected with Android Keystore and AES-GCM.
- Local backups use a checksummed ZIP, or a password-protected `.mutcube` archive using PBKDF2 and AES-256-GCM.
- WebDAV and S3 backup, preview and integrity verification before restore, and optional 7/14/30-day reminders.
- API keys and other credentials are never included in backups. Credentials and sensitive permissions must be configured
  again after restoring to a new device.
- Storage management, cache cleanup, security diagnostics, third-party license browsing, and update checks.

The detailed backup boundary is documented in [Data backup and restore](docs/DATA_BACKUP.md) (Chinese).

## Quick start

1. Open **Settings → Model services** after installing MutCube.
2. Enable a provider, enter its API key, and fetch models from the service or add a model ID manually.
3. Run connection diagnostics and select a default model.
4. Return to the home screen and start a chat. Empty drafts are not added to the recent list.
5. Create a project when related conversations need shared context.
6. Bind a built-in template to a project for a structured service, or import a standard Skill for a reusable workflow.

> [!IMPORTANT]
> Cloud providers receive the messages, extracted attachment text, and project context selected for a request. Review the
> privacy policy of your chosen provider before sending sensitive information. Local encryption in MutCube cannot replace
> the data-handling policy of a cloud service.

## Download and build

APK files are not committed to the source repository. Download installable prereleases from [GitHub Releases](https://github.com/damonwl/MutCube/releases), read the [v0.1.3 release notes](docs/releases/v0.1.3.md), and verify the included checksums. You can also build from source. Avoid APKs from untrusted sources using the MutCube name.

Requirements:

- Android Studio with support for AGP 9.3.1
- JDK 17
- Android SDK 37

```bash
git clone https://gitee.com/wlwanglei/mutcube.git
cd mutcube

# Debug APK: app/build/outputs/apk/debug/
./gradlew :app:assembleDebug

# Local acceptance build with a separate package ID and debug signing
./gradlew :app:assembleAlpha

# Unit tests and static analysis
./gradlew test
./gradlew :app:lintDebug

# Requires a running emulator or connected device
./gradlew :core:database:connectedDebugAndroidTest
```

The `alpha` build uses debug signing and the `com.dwl.mutcube.alpha` package ID. It is for local testing only. Public
distribution requires a separately configured release key that must be stored securely.

## Modules

```text
app/                Android entry point, Compose UI, settings, dependency assembly
core/model/         Domain models and cross-module contracts
core/database/      Room, DAOs, repositories, and migrations
core/ai/            Model protocols, streaming, tool calls, provider presets
core/context/       Project history retrieval and attachment text extraction
core/security/      Android Keystore credential storage
core/extensions/    Host extension contracts, including MCP
feature/chat/       Context assembly, generation, tool loops, message persistence
template/core/      Template protocol 2, manifests, actions, schema validation
template/runtime/   Authorized CRUD, AI runs, summaries, host adapters
template/builtin/   Built-in template declarations
template/ui/        Secure WebView, native confirmations, run history
docs/               Architecture, data, acceptance, and development documents
```

See [Architecture](docs/ARCHITECTURE.md), [Context and memory](docs/CONTEXT_AND_MEMORY.md),
[feature completion audit](docs/FEATURE_COMPLETION_AUDIT.md), and [development progress](docs/PROGRESS.md). These process
documents are currently maintained in Chinese.

## Known boundaries and roadmap

- Third-party templates support local installation, authorized data operations and configured provider TTS/ASR.
  Developer signatures, a marketplace, automatic updates and camera/notification/calendar host capabilities are not implemented yet.
- Skill script execution, remote Skill registries, and automatic Skill updates are not available yet.
- Project-history retrieval currently uses local keywords and bounded windows; it is not a full vector knowledge base.
- PDFs, scanned documents, audio, and video can be stored and shared as attachments, but not every format has full content
  extraction. The UI does not pretend unparsed files have been understood.
- WebDAV/S3 provide user-controlled backup and restore, not real-time multi-device sync or account-based cloud sync.
- The application UI is currently Chinese-first. Repository documentation is gradually being made bilingual.

The full target architecture and delivery boundaries are recorded under [`docs/`](docs/). Contributions focused on bug
fixes, tests, documentation, compatibility, and clearly scoped features are welcome.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for verification commands and architecture rules, and
[SECURITY.md](SECURITY.md) for vulnerability reporting. GitHub CI requires no provider keys and does not call live models.
The SDK and developer documentation stay in this repository; npm publication is not required.

1. Fork the repository and create a feature branch from the current main branch.
2. Read the [source and license policy](docs/ORIGIN_POLICY.md) and [open-source reuse process](docs/OPEN_SOURCE_REUSE.md).
3. Never commit API keys, signing files, user backups, real conversations, or other sensitive data.
4. Add tests for new behavior and run the affected module tests and lint checks.
5. Pull requests should describe the problem, implementation, validation, UI changes, and third-party licenses.

MutCube may reuse mature, license-compatible components, but does not copy or mechanically translate implementations from
projects with incompatible licenses.

## License and trademarks

Source code is available under the [MIT License](LICENSE). Third-party dependencies remain subject to their own licenses,
which can also be inspected inside the application.

The MutCube name and project logo are project brand identifiers. The MIT License grants copyright permissions for the
software; it does not automatically grant rights to use the name, logo, or other trademark identifiers.
