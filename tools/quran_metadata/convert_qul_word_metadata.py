#!/usr/bin/env python3
"""
Temporary QUL Quran word metadata converter/verifier.

This tool prepares and verifies QUL word-level metadata against MyVault's
displayed QPC/Hafs text. It is deliberately conservative: a row is accepted only
when the word ID matches and the Arabic text aligns exactly or by MyVault's
normalization rule.
"""

from __future__ import annotations

import argparse
import csv
import html
import json
import re
import sqlite3
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path
from typing import Any, Iterable


DEFAULT_QPC_PATH = Path("app/src/main/assets/qpc_hafs.json")
DEFAULT_EXPORT_DIR = Path("tools/quran_metadata/qul_exports")
DEFAULT_OUTPUT_DIR = Path("build/qul-word-metadata")
DEFAULT_PRODUCTION_PATH = Path("app/src/main/assets/quran_word_metadata.json")

QUL_ENGLISH_WBW_TRANSLATION_ID = "59"
QUL_SOURCE = "Quranic Universal Library (QUL) / TarteelAI"
QUL_PUBLIC_PREVIEW_RESOURCES = {
    "translation": ("translation", "92", "translation"),
    "transliteration": ("transliteration", "71", "transliteration"),
    "root": ("morphology", "76", "root"),
    "lemma": ("morphology", "75", "lemma"),
}

SAMPLES = {
    "Al-Fatihah": ["1:1", "1:2", "1:3", "1:4", "1:5", "1:6", "1:7"],
    "Al-Baqarah 2:1-5": ["2:1", "2:2", "2:3", "2:4", "2:5"],
    "Ayat al-Kursi": ["2:255"],
    "Al-Ikhlas": ["112:1", "112:2", "112:3", "112:4"],
    "Al-Falaq": ["113:1", "113:2", "113:3", "113:4", "113:5"],
    "An-Nas": ["114:1", "114:2", "114:3", "114:4", "114:5", "114:6"],
}

ARABIC_DIGITS = set("٠١٢٣٤٥٦٧٨٩")
ARABIC_LETTER_PATTERN = re.compile(r"[\u0621-\u064A\u0671-\u06D3]")
METADATA_FIELDS = ("translation", "transliteration", "root", "lemma", "definition")


@dataclass
class MyVaultWord:
    word_id: str
    arabic: str
    normalized_arabic: str


@dataclass
class CandidateRow:
    word_id: str
    arabic: str = ""
    normalized_arabic: str = ""
    translation: str = ""
    transliteration: str = ""
    root: str = ""
    lemma: str = ""
    definition: str = ""
    source: str = QUL_SOURCE
    source_files: set[str] = field(default_factory=set)

    def merge(self, other: "CandidateRow") -> list[str]:
        conflicts: list[str] = []
        if self.arabic and other.arabic and normalize_arabic_word(self.arabic) != normalize_arabic_word(other.arabic):
            conflicts.append("arabic")
        if not self.arabic and other.arabic:
            self.arabic = other.arabic
        if not self.normalized_arabic and other.normalized_arabic:
            self.normalized_arabic = other.normalized_arabic
        for field_name in METADATA_FIELDS:
            current = getattr(self, field_name)
            incoming = getattr(other, field_name)
            if not current and incoming:
                setattr(self, field_name, incoming)
        self.source_files.update(other.source_files)
        return conflicts


def clean(value: Any) -> str:
    if value is None:
        return ""
    return html.unescape(str(value)).strip()


def first_value(row: dict[str, Any], names: Iterable[str]) -> str:
    lowered = {key.lower(): value for key, value in row.items()}
    for name in names:
        value = lowered.get(name.lower())
        if clean(value):
            return clean(value)
    return ""


def strip_trailing_verse_number(text: str) -> str:
    index = len(text) - 1
    while index >= 0 and text[index].isspace():
        index -= 1
    while index >= 0 and text[index] in ARABIC_DIGITS:
        index -= 1
    return text[: index + 1].rstrip()


def normalize_arabic_word(word: str) -> str:
    text = word.replace("\u0640", "")
    text = re.sub(r"[\u064B-\u065F\u0670\u06D6-\u06ED]", "", text)
    text = re.sub(r"[ۚۖۗۘۙۛۜ۝۞,.;:!?؟،؛ـ\-()\[\]{}]", "", text)
    return (
        text.replace("ٱ", "ا")
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("آ", "ا")
        .replace("ى", "ي")
        .strip()
    )


def compatibility_normalize_arabic(word: str) -> str:
    return normalize_arabic_word(word).replace("ء", "")


def load_myvault_words(qpc_path: Path, verse_keys: set[str] | None = None) -> dict[str, MyVaultWord]:
    source = json.loads(qpc_path.read_text(encoding="utf-8"))
    words: dict[str, MyVaultWord] = {}
    for verse_key, verse in source.items():
        if verse_keys is not None and verse_key not in verse_keys:
            continue
        surah, ayah = verse_key.split(":")
        position = 0
        arabic_text = strip_trailing_verse_number(clean(verse.get("text")))
        for match in re.finditer(r"\S+", arabic_text):
            arabic = match.group(0)
            normalized = normalize_arabic_word(arabic)
            if not normalized:
                continue
            position += 1
            word_id = f"{surah}:{ayah}:{position}"
            words[word_id] = MyVaultWord(word_id, arabic, normalized)
    return words


def myvault_words_by_verse(myvault_words: dict[str, MyVaultWord]) -> dict[str, list[MyVaultWord]]:
    grouped: dict[str, list[MyVaultWord]] = {}
    for word in myvault_words.values():
        verse_key = ":".join(word.word_id.split(":")[:2])
        grouped.setdefault(verse_key, []).append(word)
    return {
        verse_key: sorted(words, key=lambda item: natural_word_key(item.word_id))
        for verse_key, words in grouped.items()
    }


def row_to_candidate(row: dict[str, Any], source_file: str) -> CandidateRow | None:
    word_id = first_value(row, ["wordId", "word_id", "location", "id", "key"])
    if not word_id:
        verse_key = first_value(row, ["verse_key", "verseKey"])
        position = first_value(row, ["position", "word_position", "wordPosition"])
        if verse_key and position:
            word_id = f"{verse_key}:{position}"
    if not re.fullmatch(r"\d+:\d+:\d+", word_id):
        return None

    arabic = first_value(
        row,
        [
            "text_qpc_hafs",
            "qpc_hafs",
            "arabic",
            "arabicText",
            "text_uthmani",
            "uthmani",
            "text",
            "word",
        ],
    )
    normalized = first_value(row, ["normalizedArabic", "normalizedArabicText", "normalized_arabic"])
    if not normalized and arabic:
        normalized = normalize_arabic_word(arabic)

    candidate = CandidateRow(
        word_id=word_id,
        arabic=arabic,
        normalized_arabic=normalized,
        translation=first_value(row, ["translation", "meaning", "en", "english", "text_en"]),
        transliteration=first_value(row, ["transliteration", "translit", "roman", "latin"]),
        root=first_value(row, ["root", "root_ar", "arabic_root"]),
        lemma=first_value(row, ["lemma", "lemma_ar", "arabic_lemma"]),
        definition=first_value(row, ["definition", "brief_definition", "root_definition"]),
        source=QUL_SOURCE,
    )
    candidate.source_files.add(source_file)
    return candidate


def flatten_json_records(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        return [row for row in payload if isinstance(row, dict)]
    if isinstance(payload, dict):
        for key in ("records", "data", "rows", "words", "translations", "transliterations", "roots", "lemmas"):
            value = payload.get(key)
            if isinstance(value, list):
                return [row for row in value if isinstance(row, dict)]
        if all(isinstance(value, dict) for value in payload.values()):
            rows = []
            for key, value in payload.items():
                row = dict(value)
                row.setdefault("location", key)
                rows.append(row)
            return rows
    return []


def load_json_file(path: Path) -> list[CandidateRow]:
    records = flatten_json_records(json.loads(path.read_text(encoding="utf-8")))
    return [candidate for row in records if (candidate := row_to_candidate(row, path.name))]


def load_csv_file(path: Path) -> list[CandidateRow]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        return [candidate for row in reader if (candidate := row_to_candidate(row, path.name))]


def load_sqlite_file(path: Path) -> list[CandidateRow]:
    rows: list[CandidateRow] = []
    with sqlite3.connect(path) as conn:
        conn.row_factory = sqlite3.Row
        table_names = [
            item[0]
            for item in conn.execute("select name from sqlite_master where type='table'")
            if not str(item[0]).startswith("sqlite_")
        ]
        for table in table_names:
            quoted = '"' + table.replace('"', '""') + '"'
            try:
                cursor = conn.execute(f"select * from {quoted}")
            except sqlite3.DatabaseError:
                continue
            for db_row in cursor:
                row = dict(db_row)
                if candidate := row_to_candidate(row, f"{path.name}:{table}"):
                    rows.append(candidate)
    return rows


def load_export_candidates(export_dir: Path) -> list[CandidateRow]:
    if not export_dir.exists():
        return []
    rows: list[CandidateRow] = []
    for path in sorted(export_dir.iterdir()):
        if path.name.startswith(".") or path.is_dir():
            continue
        suffix = path.suffix.lower()
        try:
            if suffix == ".json":
                rows.extend(load_json_file(path))
            elif suffix == ".csv":
                rows.extend(load_csv_file(path))
            elif suffix in {".sqlite", ".sqlite3", ".db"}:
                rows.extend(load_sqlite_file(path))
        except Exception as exc:  # noqa: BLE001 - keep verifier tolerant of unrelated files.
            print(f"Warning: could not parse {path}: {exc}", file=sys.stderr)
    return rows


def fetch_url(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "MyVault-QUL-Verifier/1.0"})
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read().decode("utf-8")
        except Exception as exc:  # noqa: BLE001 - retries are diagnostic tooling, not app behavior.
            last_error = exc
            time.sleep(1 + attempt)
    raise RuntimeError(f"Could not fetch {url}: {last_error}")


def parse_qul_word_cards(page_html: str) -> list[CandidateRow]:
    chunks = page_html.split('<div class="bg-white border border-gray-200 rounded p-3">')[1:]
    rows: list[CandidateRow] = []
    for chunk in chunks:
        divs = re.findall(r"<div[^>]*>([\s\S]*?)</div>", chunk)[:3]
        values = [clean(re.sub(r"<[^>]*>", "", value)) for value in divs]
        if len(values) < 3 or not re.fullmatch(r"\d+:\d+:\d+", values[1]):
            continue
        row = CandidateRow(
            word_id=values[1],
            arabic=values[0],
            normalized_arabic=normalize_arabic_word(values[0]),
            translation=values[2],
            source=QUL_SOURCE,
        )
        row.source_files.add("QUL public ayah word page")
        rows.append(row)
    return rows


def extract_labeled_value(page_html: str, label: str) -> str:
    pattern = rf"<span class=\"font-semibold\">{re.escape(label)}:</span>\s*(?:<a[^>]*>)?([^<\n]+)"
    match = re.search(pattern, page_html)
    return clean(match.group(1)) if match else ""


def enrich_with_public_morphology(row: CandidateRow) -> None:
    encoded = urllib.parse.quote(row.word_id)
    page_html = fetch_url(f"https://qul.tarteel.ai/morphology/word?location={encoded}")
    row.root = row.root or extract_labeled_value(page_html, "Root")
    row.lemma = row.lemma or extract_labeled_value(page_html, "Lemma")
    row.source_files.add("QUL public morphology page")


def fetch_public_sample_candidates(sample_keys: list[str]) -> list[CandidateRow]:
    rows: dict[str, CandidateRow] = {}
    for verse_key in sample_keys:
        encoded_key = urllib.parse.quote(verse_key)
        page_html = fetch_url(
            "https://qul.tarteel.ai/ayah/"
            f"{encoded_key}/words?word_translation_id={QUL_ENGLISH_WBW_TRANSLATION_ID}"
        )
        for row in parse_qul_word_cards(page_html):
            try:
                enrich_with_public_morphology(row)
            except Exception as exc:  # noqa: BLE001 - morphology is useful but should not stop alignment.
                print(f"Warning: could not fetch morphology for {row.word_id}: {exc}", file=sys.stderr)
            rows[row.word_id] = row
    return list(rows.values())


def html_to_text(value: str) -> str:
    return clean(re.sub(r"\s+", " ", re.sub(r"<[^>]*>", "", value)))


def parse_public_preview_cards(
    page_html: str,
    verse_key: str,
    field_name: str,
    source_file: str,
) -> list[CandidateRow]:
    card_pattern = re.compile(
        r'<div[^>]*class="[^"]*bg-gray-50 p-2 rounded-lg border border-gray-200 text-center min-w-\[5rem\][^"]*"[^>]*>'
        r"[\s\S]*?"
        r'<div[^>]*class="[^"]*text-lg text-gray-800[^"]*mb-2 p-2[^"]*"[^>]*>(?P<arabic>[\s\S]*?)</div>'
        r"\s*"
        r'<div[^>]*class="[^"]*text-sm text-gray-600[^"]*"[^>]*>(?P<value>[\s\S]*?)</div>'
        r"\s*</div>",
        re.MULTILINE,
    )

    rows: list[CandidateRow] = []
    position = 0
    for match in card_pattern.finditer(page_html):
        arabic = html_to_text(match.group("arabic"))
        value = html_to_text(match.group("value"))
        if not ARABIC_LETTER_PATTERN.search(arabic):
            continue
        position += 1
        row = CandidateRow(
            word_id=f"{verse_key}:{position}",
            arabic=arabic,
            normalized_arabic=normalize_arabic_word(arabic),
            source=QUL_SOURCE,
        )
        setattr(row, field_name, value)
        row.source_files.add(source_file)
        rows.append(row)
    return rows


def fetch_public_preview_resource(verse_key: str, resource_kind: str, resource_id: str, field_name: str) -> list[CandidateRow]:
    encoded_key = urllib.parse.quote(verse_key)
    page_html = fetch_url(f"https://qul.tarteel.ai/resources/{resource_kind}/{resource_id}?ayah={encoded_key}")
    return parse_public_preview_cards(
        page_html=page_html,
        verse_key=verse_key,
        field_name=field_name,
        source_file=f"QUL public preview {resource_kind}/{resource_id}",
    )


def fetch_public_full_candidates(verse_keys: list[str], workers: int, export_dir: Path) -> list[CandidateRow]:
    export_dir.mkdir(parents=True, exist_ok=True)
    output_path = export_dir / "qul_public_preview_export.json"
    tasks = []
    rows: list[CandidateRow] = []
    with ThreadPoolExecutor(max_workers=workers) as executor:
        for verse_key in verse_keys:
            for resource_kind, resource_id, field_name in QUL_PUBLIC_PREVIEW_RESOURCES.values():
                tasks.append(executor.submit(fetch_public_preview_resource, verse_key, resource_kind, resource_id, field_name))
        for index, future in enumerate(as_completed(tasks), start=1):
            if index % 500 == 0:
                print(f"Fetched {index}/{len(tasks)} QUL preview pages...", file=sys.stderr)
            rows.extend(future.result())

    output_path.write_text(
        json.dumps([record_for_asset(row) for row in rows], ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Temporary public preview export: {output_path}", file=sys.stderr)
    return rows


def canonical_base_rows(myvault_words: dict[str, MyVaultWord]) -> list[CandidateRow]:
    return [
        CandidateRow(
            word_id=word.word_id,
            arabic=word.arabic,
            normalized_arabic=word.normalized_arabic,
            source=QUL_SOURCE,
        )
        for word in sorted(myvault_words.values(), key=lambda item: natural_word_key(item.word_id))
    ]


def split_metadata_rows(rows: Iterable[CandidateRow]) -> list[CandidateRow]:
    split_rows: list[CandidateRow] = []
    for row in rows:
        for field_name in METADATA_FIELDS:
            value = getattr(row, field_name)
            if not value:
                continue
            split_row = CandidateRow(
                word_id=row.word_id,
                arabic=row.arabic,
                normalized_arabic=row.normalized_arabic or normalize_arabic_word(row.arabic),
                source=row.source,
            )
            setattr(split_row, field_name, value)
            split_row.source_files = set(row.source_files)
            split_rows.append(split_row)
    return split_rows


def combine_metadata_values(field_name: str, rows: list[CandidateRow]) -> str:
    values = [getattr(row, field_name) for row in rows if getattr(row, field_name)]
    unique_values = list(dict.fromkeys(values))
    if field_name in {"root", "lemma"}:
        return " | ".join(unique_values)
    return " ".join(unique_values)


def row_with_field(word: MyVaultWord, field_name: str, value: str) -> CandidateRow:
    row = CandidateRow(
        word_id=word.word_id,
        arabic=word.arabic,
        normalized_arabic=word.normalized_arabic,
        source=QUL_SOURCE,
    )
    setattr(row, field_name, value)
    row.source_files.add("QUL compatibility preprocessor")
    return row


def align_field_rows_to_myvault(
    field_name: str,
    verse_key: str,
    source_rows: list[CandidateRow],
    target_words: list[MyVaultWord],
    stats: dict[str, int],
) -> list[CandidateRow]:
    aligned: list[CandidateRow] = []
    source_rows = sorted(source_rows, key=lambda item: natural_word_key(item.word_id))
    source_norms = [compatibility_normalize_arabic(row.arabic) for row in source_rows]
    target_norms = [compatibility_normalize_arabic(word.arabic) for word in target_words]

    @lru_cache(maxsize=None)
    def best_score(i: int, j: int) -> int:
        if i >= len(target_words) and j >= len(source_rows):
            return 0
        if i >= len(target_words):
            return -5 * (len(source_rows) - j)
        if j >= len(source_rows):
            return 0

        best = best_score(i + 1, j)
        best = max(best, -5 + best_score(i, j + 1))

        if source_norms[j] == target_norms[i]:
            best = max(best, 100 + best_score(i + 1, j + 1))

        combined_source = source_norms[j]
        for source_end in range(j + 2, min(len(source_rows), j + 4) + 1):
            combined_source += source_norms[source_end - 1]
            if combined_source == target_norms[i]:
                best = max(best, 95 + best_score(i + 1, source_end))

        combined_target = target_norms[i]
        for target_end in range(i + 2, min(len(target_words), i + 4) + 1):
            combined_target += target_norms[target_end - 1]
            if source_norms[j] == combined_target:
                best = max(best, 50 + best_score(target_end, j + 1))
        return best

    operations: list[tuple[str, int, int, int, int]] = []
    i = 0
    j = 0
    while i < len(target_words) or j < len(source_rows):
        current = best_score(i, j)
        if i < len(target_words) and j < len(source_rows) and source_norms[j] == target_norms[i]:
            if current == 100 + best_score(i + 1, j + 1):
                operations.append(("one_to_one", i, i + 1, j, j + 1))
                i += 1
                j += 1
                continue

        matched = False
        if i < len(target_words) and j < len(source_rows):
            combined_source = source_norms[j]
            for source_end in range(j + 2, min(len(source_rows), j + 4) + 1):
                combined_source += source_norms[source_end - 1]
                if combined_source == target_norms[i] and current == 95 + best_score(i + 1, source_end):
                    operations.append(("merge_source", i, i + 1, j, source_end))
                    i += 1
                    j = source_end
                    matched = True
                    break
        if matched:
            continue

        if i < len(target_words) and j < len(source_rows):
            combined_target = target_norms[i]
            for target_end in range(i + 2, min(len(target_words), i + 4) + 1):
                combined_target += target_norms[target_end - 1]
                if source_norms[j] == combined_target and current == 50 + best_score(target_end, j + 1):
                    operations.append(("grouped_source", i, target_end, j, j + 1))
                    i = target_end
                    j += 1
                    matched = True
                    break
        if matched:
            continue

        if i < len(target_words) and current == best_score(i + 1, j):
            operations.append(("skip_target", i, i + 1, j, j))
            i += 1
            continue

        if j < len(source_rows):
            operations.append(("skip_source", i, i, j, j + 1))
            j += 1
            continue

    for index, (operation, target_start, target_end, source_start, source_end) in enumerate(operations):
        if operation == "one_to_one":
            next_operation = operations[index + 1][0] if index + 1 < len(operations) else ""
            if field_name in {"translation", "transliteration", "lemma"} and next_operation == "skip_target":
                stats["grouped_source_metadata_skipped"] += 1
                continue
            source_row = source_rows[source_start]
            target_word = target_words[target_start]
            aligned.append(row_with_field(target_word, field_name, getattr(source_row, field_name)))
            if source_row.arabic != target_word.arabic:
                stats["orthographic_remaps"] += 1
            else:
                stats["one_to_one"] += 1
        elif operation == "merge_source":
            value = combine_metadata_values(field_name, source_rows[source_start:source_end])
            aligned.append(row_with_field(target_words[target_start], field_name, value))
            stats["source_tokens_merged"] += 1
        elif operation == "grouped_source":
            stats["grouped_source_metadata_skipped"] += 1
        elif operation == "skip_source":
            stats["unaligned_source_rows"] += source_end - source_start
        elif operation == "skip_target":
            stats["unfilled_target_words"] += target_end - target_start
    return aligned


def preprocess_candidates_for_myvault(
    rows: Iterable[CandidateRow],
    myvault_words: dict[str, MyVaultWord],
) -> tuple[list[CandidateRow], dict[str, int]]:
    stats = {
        "base_rows": len(myvault_words),
        "one_to_one": 0,
        "orthographic_remaps": 0,
        "source_tokens_merged": 0,
        "grouped_source_metadata_skipped": 0,
        "unaligned_source_rows": 0,
        "unconsumed_source_rows": 0,
        "unfilled_target_words": 0,
    }
    target_by_verse = myvault_words_by_verse(myvault_words)
    split_rows = split_metadata_rows(rows)
    rows_by_field_and_verse: dict[tuple[str, str], list[CandidateRow]] = {}
    for row in split_rows:
        field_names = [field_name for field_name in METADATA_FIELDS if getattr(row, field_name)]
        if not field_names:
            continue
        verse_key = ":".join(row.word_id.split(":")[:2])
        if verse_key not in target_by_verse:
            stats["unaligned_source_rows"] += 1
            continue
        for field_name in field_names:
            rows_by_field_and_verse.setdefault((field_name, verse_key), []).append(row)

    aligned_rows = canonical_base_rows(myvault_words)
    for (field_name, verse_key), field_rows in sorted(rows_by_field_and_verse.items()):
        aligned_rows.extend(
            align_field_rows_to_myvault(
                field_name=field_name,
                verse_key=verse_key,
                source_rows=field_rows,
                target_words=target_by_verse[verse_key],
                stats=stats,
            )
        )
    return aligned_rows, stats


def merge_candidates(rows: Iterable[CandidateRow]) -> tuple[dict[str, CandidateRow], list[dict[str, str]]]:
    grouped: dict[str, list[CandidateRow]] = {}
    for row in rows:
        grouped.setdefault(row.word_id, []).append(row)

    duplicate_ids: list[dict[str, str]] = []
    merged: dict[str, CandidateRow] = {}
    for word_id, items in sorted(grouped.items(), key=lambda item: natural_word_key(item[0])):
        base = CandidateRow(word_id=word_id)
        conflicts: set[str] = set()
        for item in items:
            conflicts.update(base.merge(item))
        if conflicts:
            duplicate_ids.append(
                {
                    "wordId": word_id,
                    "reason": "conflicting duplicate rows",
                    "fields": ", ".join(sorted(conflicts)),
                }
            )
        else:
            merged[word_id] = base
    return merged, duplicate_ids


def aligns(candidate: CandidateRow, word: MyVaultWord) -> bool:
    return (
        candidate.word_id == word.word_id
        and bool(candidate.arabic)
        and (
            candidate.arabic == word.arabic
            or bool(candidate.normalized_arabic)
            and candidate.normalized_arabic == word.normalized_arabic
        )
    )


def record_for_asset(row: CandidateRow) -> dict[str, str]:
    return {
        "wordId": row.word_id,
        "arabicText": row.arabic,
        "normalizedArabicText": row.normalized_arabic or normalize_arabic_word(row.arabic),
        "translation": row.translation,
        "transliteration": row.transliteration,
        "root": row.root,
        "lemma": row.lemma,
        "definition": row.definition,
        "source": row.source,
    }


def sample_status(sample_keys: list[str], accepted_ids: set[str], myvault_words: dict[str, MyVaultWord]) -> dict[str, int]:
    sample_word_ids = {
        word_id
        for word_id in myvault_words
        if ":".join(word_id.split(":")[:2]) in sample_keys
    }
    return {
        "totalWords": len(sample_word_ids),
        "acceptedRows": len(sample_word_ids & accepted_ids),
        "missingRows": len(sample_word_ids - accepted_ids),
    }


def build_report(
    myvault_words: dict[str, MyVaultWord],
    candidates: dict[str, CandidateRow],
    duplicate_ids: list[dict[str, str]],
    compatibility_stats: dict[str, int] | None = None,
) -> tuple[dict[str, Any], list[dict[str, str]]]:
    accepted: list[CandidateRow] = []
    rejected: list[dict[str, str]] = []

    for word_id, candidate in sorted(candidates.items(), key=lambda item: natural_word_key(item[0])):
        word = myvault_words.get(word_id)
        if word is None:
            rejected.append(
                {
                    "wordId": word_id,
                    "reason": "wordId not found in MyVault qpc_hafs.json",
                    "qulArabic": candidate.arabic,
                    "myVaultArabic": "",
                }
            )
            continue
        if aligns(candidate, word):
            accepted.append(candidate)
        else:
            rejected.append(
                {
                    "wordId": word_id,
                    "reason": "Arabic mismatch",
                    "qulArabic": candidate.arabic,
                    "myVaultArabic": word.arabic,
                }
            )

    accepted_ids = {row.word_id for row in accepted}
    missing = [
        {
            "wordId": word.word_id,
            "myVaultArabic": word.arabic,
        }
        for word in sorted(myvault_words.values(), key=lambda item: natural_word_key(item.word_id))
        if word.word_id not in accepted_ids
    ]

    report = {
        "totalMyVaultWords": len(myvault_words),
        "totalQulRowsFound": len(candidates) + len(duplicate_ids),
        "acceptedRows": len(accepted),
        "rejectedRows": len(rejected),
        "missingRows": len(missing),
        "duplicateIds": duplicate_ids,
        "first20RejectedExamples": rejected[:20],
        "first20MissingExamples": missing[:20],
        "compatibilityStats": compatibility_stats or {},
        "sampleVerification": {
            label: sample_status(keys, accepted_ids, myvault_words)
            for label, keys in SAMPLES.items()
        },
    }
    return report, [record_for_asset(row) for row in accepted]


def natural_word_key(word_id: str) -> tuple[int, int, int]:
    surah, ayah, word = word_id.split(":")
    return int(surah), int(ayah), int(word)


def write_outputs(output_dir: Path, report: dict[str, Any], records: list[dict[str, str]]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "verification_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (output_dir / "candidate_quran_word_metadata.json").write_text(
        json.dumps(
            {
                "version": 1,
                "alignment": "qpc_hafs_word_id_v1",
                "wordIdFormat": "surahNumber:ayahNumber:wordPosition",
                "records": records,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    (output_dir / "verification_report.md").write_text(format_markdown_report(report), encoding="utf-8")


def format_markdown_report(report: dict[str, Any]) -> str:
    lines = [
        "# QUL Quran Word Metadata Verification Report",
        "",
        f"- total MyVault words: {report['totalMyVaultWords']}",
        f"- total QUL rows found: {report['totalQulRowsFound']}",
        f"- accepted rows: {report['acceptedRows']}",
        f"- rejected rows: {report['rejectedRows']}",
        f"- missing rows: {report['missingRows']}",
        f"- duplicate IDs: {len(report['duplicateIds'])}",
        "",
        "## Sample Verification",
        "",
    ]
    for label, item in report["sampleVerification"].items():
        lines.append(
            f"- {label}: {item['acceptedRows']}/{item['totalWords']} accepted, "
            f"{item['missingRows']} missing"
        )

    if report.get("compatibilityStats"):
        lines.extend(["", "## Compatibility Preprocessing", ""])
        for key, value in report["compatibilityStats"].items():
            lines.append(f"- {key}: {value}")

    lines.extend(["", "## First 20 Rejected Examples", ""])
    if report["first20RejectedExamples"]:
        for item in report["first20RejectedExamples"]:
            lines.append(
                f"- {item['wordId']}: {item['reason']} | "
                f"MyVault={item.get('myVaultArabic', '')} | QUL={item.get('qulArabic', '')}"
            )
    else:
        lines.append("- None")

    lines.extend(["", "## First 20 Missing Examples", ""])
    if report["first20MissingExamples"]:
        for item in report["first20MissingExamples"]:
            lines.append(f"- {item['wordId']}: {item['myVaultArabic']}")
    else:
        lines.append("- None")
    return "\n".join(lines) + "\n"


def assert_clean_for_production(report: dict[str, Any]) -> None:
    if report["rejectedRows"] or report["missingRows"] or report["duplicateIds"]:
        raise SystemExit(
            "Production write blocked: verification is not clean. "
            "Resolve rejected, missing, and duplicate rows first."
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--qpc-path", type=Path, default=DEFAULT_QPC_PATH)
    parser.add_argument("--export-dir", type=Path, default=DEFAULT_EXPORT_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--production-path", type=Path, default=DEFAULT_PRODUCTION_PATH)
    parser.add_argument(
        "--fetch-public-samples",
        action="store_true",
        help="Fetch QUL public sample pages for the required verification samples.",
    )
    parser.add_argument(
        "--fetch-public-full",
        action="store_true",
        help="Fetch all QUL public per-ayah preview pages into a temporary export before verifying.",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=16,
        help="Concurrent workers for --fetch-public-full.",
    )
    parser.add_argument(
        "--write-production",
        action="store_true",
        help="Replace app/src/main/assets/quran_word_metadata.json only if verification is clean.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    sample_keys = sorted({key for keys in SAMPLES.values() for key in keys})
    fetch_only_samples = args.fetch_public_samples and not args.fetch_public_full and not any(args.export_dir.iterdir())
    myvault_words = load_myvault_words(
        args.qpc_path,
        verse_keys=set(sample_keys) if fetch_only_samples else None,
    )

    rows = load_export_candidates(args.export_dir)
    if args.fetch_public_full:
        verse_keys = sorted(
            {":".join(word_id.split(":")[:2]) for word_id in myvault_words},
            key=lambda key: tuple(map(int, key.split(":"))),
        )
        rows.extend(fetch_public_full_candidates(verse_keys, max(1, args.workers), args.export_dir))
    if args.fetch_public_samples:
        rows.extend(fetch_public_sample_candidates(sample_keys))

    rows, compatibility_stats = preprocess_candidates_for_myvault(rows, myvault_words)
    candidates, duplicate_ids = merge_candidates(rows)
    report, records = build_report(myvault_words, candidates, duplicate_ids, compatibility_stats)
    write_outputs(args.output_dir, report, records)

    if args.write_production:
        assert_clean_for_production(report)
        args.production_path.write_text(
            json.dumps(
                {
                    "version": 1,
                    "alignment": "qpc_hafs_word_id_v1",
                    "wordIdFormat": "surahNumber:ayahNumber:wordPosition",
                    "records": records,
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )

    print(f"Verification report: {args.output_dir / 'verification_report.md'}")
    print(f"Candidate asset: {args.output_dir / 'candidate_quran_word_metadata.json'}")
    print(
        "Summary: "
        f"accepted={report['acceptedRows']} "
        f"rejected={report['rejectedRows']} "
        f"missing={report['missingRows']} "
        f"duplicates={len(report['duplicateIds'])}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
