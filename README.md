# Quizify

Android app that helps students learn from PDF course materials by generating quizzes, summaries, and exam-style questions using AI.

## Features

- **Authentication** — email/password sign-in with Firebase (Remember me support)
- **PDF management** — import, organize, and view PDFs on device
- **Quiz generation** — create quizzes from PDF content (10/15/20 questions)
- **Exam & learning modes** — timed exam mode or learning mode with instant feedback
- **Fast summarization** — AI-powered PDF summaries
- **Offline exam subjects** — generate and practice exam questions without internet (local LLM)
- **Profile** — quiz history, XP, flashcards, password change, account deletion

## Tech stack

- Kotlin, Jetpack Compose, MVVM
- Firebase Authentication
- Groq API (cloud LLM)
- MediaPipe / Phi-4 mini (on-device LLM for offline mode)
- PdfBox Android (PDF text extraction)
- Local storage (SharedPreferences + file cache)

## Note

API keys and local config are not included in the repo. See `local.properties.example` for required values.
