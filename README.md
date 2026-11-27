# Shugyō 修行

Your training journey: turn textbook chapters into lessons, flashcards and practice quizzes that make hard ideas simple and a little fun.

## What it does (stage 1)

- **Scan your textbook**: an in-app browser where you sign in to your class site (like Moodle), McGraw Hill or any site. It opens where you left off, has a start page with bookmarks and recent sites, and opens new-window links (like a textbook launched from Moodle) in their own tab. **Scan page** reads the page in every frame, including learning objectives, bold key terms, tables and figures with their captions. It also taps each linked key term to read the book's pop-up definition (⋮ to turn off). Scan each module's page in turn: scans are kept on the phone until you build, even if the app closes. For readers that hide their text, **Snap** takes a picture of the screen, and **⋮ → Snap whole page** scrolls and snaps for you.
- **Other ways in**: screenshots or photos, a PDF (pick the pages), pasted text, or share a link, text, pictures or a PDF to Shugyō from any app.
- **Build a study set** (or add the pages to one you already have): Gemini (free key) or Claude reads everything and writes:
  - the book's learning objectives, each in plain words with a friendly explanation
  - key terms with the book's definition, a plain-words version, a memory trick and an example
  - tables reproduced with a one-line takeaway
  - diagrams cut out of your snaps, explained, with their labels
  - practice questions (multiple choice, select all that apply, true/false) from recall to real-life scenarios, each with a hint and an explanation
- **Learn**: objective by objective. Explain it another way (simpler, analogy, story, memory trick, nurse lens, step by step), ask your own question, then pass a quick check to learn it.
- **Flashcards**: spaced repetition (Again / Hard / Good / Easy), term-first or definition-first, star cards, diagram cards.
- **Practice quiz**: pick how many, hints, back button, explanations, review and retry what you missed.
- **Notes**: overview, searchable glossary, tables and diagrams.
- **XP, levels and streaks** across all your sets.

Coming next: **Battle Dojo** (turn-based battles with study creatures that level up as you master terms) and **Exams** (timed mock exams across chapters).

## Install

Each push builds an APK with GitHub Actions. Open **Releases**, tap the newest `Shugyo build N`, and download `Shugyo-N.apk`. Every build is signed with the same key, so new versions install over old ones and keep your study sets.

## First run

1. Settings → paste your free **Gemini API key** (from Google AI Studio; the same key as Yomiage works). A Claude key is optional.
2. Tap **＋ Scan a chapter** → **Open my textbook**, go to your class site, sign in, open a chapter, and tap **Scan page** on each page (or **Snap**).
3. Tap **Build**, add the class name, and build.

## How it works

- `scan/ScanScript` runs in every frame of the browser (androidx.webkit document-start script + web message listener). It walks the page into headings, paragraphs (bold terms marked `**like this**`), list items, tables and pictures. Pictures are handed over directly when the page allows it, otherwise downloaded with the browser's sign-in cookies.
- If a page doesn't look right, **⋮ → Page problems** lists the errors it ran into.
- `scan/WebScanner` also takes PixelCopy snaps of the browser for readers that draw text as images, and scrolls the reader for whole-page snaps.
- `build/Builder` splits long chapters into parts, asks the AI for one JSON study set per part (`build/Prompts`), repairs cut-off JSON (`build/JsonFix`), joins the parts, crops figures out of snaps using the AI's boxes, and saves the set.
- `data/Store` keeps each set in `files/decks/<id>/` with its diagrams and progress. `data/Srs` is the spaced repetition, mastery and XP.
