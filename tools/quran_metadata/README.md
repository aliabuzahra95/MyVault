# QUL Quran Word Metadata Converter

Temporary verification pipeline for evaluating Quranic Universal Library (QUL)
word-level metadata before importing anything into MyVault production assets.

The converter is intentionally strict:

- rows attach only by `surah:ayah:wordPosition`
- Arabic must match MyVault `qpc_hafs.json` exactly or by the same normalized comparison used in the app
- duplicate IDs are rejected
- mismatched rows are rejected
- missing rows stay missing

Before that strict verification runs, QUL input rows are passed through a
compatibility preprocessor. MyVault's `qpc_hafs.json` remains canonical. The
preprocessor may remap harmless orthographic variants, merge consecutive QUL
tokens when they equal one MyVault word, or skip phrase-level QUL metadata when
one QUL value spans multiple MyVault words. It does not change displayed Quran
text and it does not guess missing metadata.

## Inputs

Place downloaded QUL export files in:

```text
tools/quran_metadata/qul_exports/
```

Supported file types:

- `.json`
- `.csv`
- `.sqlite`
- `.sqlite3`
- `.db`

The loader accepts common field names such as:

- `location`, `wordId`, `word_id`, `id`
- `text_qpc_hafs`, `arabic`, `arabicText`, `text`, `word`
- `translation`
- `transliteration`
- `root`
- `lemma`
- `definition`, `meaning`

## Run Sample Verification

This fetches public QUL sample pages for the required sample ranges and writes a
report under `build/qul-word-metadata/`.

```bash
python3 tools/quran_metadata/convert_qul_word_metadata.py --fetch-public-samples
```

## Run Export Verification

After QUL export files are placed in `qul_exports`, run:

```bash
python3 tools/quran_metadata/convert_qul_word_metadata.py
```

## Run Full Public Preview Verification

QUL bulk downloads require a signed-in download session. When those official
export files are not available locally, this fallback fetches QUL's public
per-ayah preview pages for English word-by-word translation, English
word-by-word transliteration, word root, and word lemma. It writes the temporary
preview export to `tools/quran_metadata/qul_exports/`, then runs the same strict
verification rules.

```bash
python3 tools/quran_metadata/convert_qul_word_metadata.py --fetch-public-full --workers 20
```

## Production Import

Do not use this until verification is clean.

```bash
python3 tools/quran_metadata/convert_qul_word_metadata.py --write-production
```

The production write is blocked unless there are no rejected rows, no duplicate
IDs, and no missing MyVault words.
