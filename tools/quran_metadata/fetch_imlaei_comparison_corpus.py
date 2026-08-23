#!/usr/bin/env python3
"""
Fetch and verify an Imlaei comparison corpus for MyVault Quran memorisation.

The source rows come from the public Quran.com / Quran Foundation Content API.
MyVault's existing QPC/Hafs word IDs remain canonical. This tool only writes a
production asset when every local word ID can be covered exactly once.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DEFAULT_LOCAL_METADATA_PATH = Path("app/src/main/assets/quran_word_metadata.json")
DEFAULT_OUTPUT_ASSET_PATH = Path("app/src/main/assets/quran_imlaei_comparison_corpus.json")
DEFAULT_REPORT_PATH = Path("build/quran-imlaei-comparison/imlaei_corpus_verification_report.md")

QURAN_COM_API_BASE = "https://api.quran.com/api/v4"
QURAN_COM_SOURCE = (
    "Quran.com / Quran Foundation Content API v4 "
    "verses/by_chapter?words=true&word_fields=text_imlaei,text_imlaei_simple,text_uthmani,location"
)
MYVAULT_ALIGNMENT = "myvault_qpc_hafs_word_id_v1"
WORD_ID_FORMAT = "surahNumber:ayahNumber:wordPosition"

ARABIC_MARKS = re.compile(r"[\u064B-\u065F\u0670\u06D6-\u06ED]")


@dataclass(frozen=True)
class LocalWord:
    word_id: str
    verse_key: str
    arabic_text: str
    normalized_arabic_text: str


@dataclass(frozen=True)
class RemoteWord:
    verse_key: str
    location: str
    position: int
    text_imlaei: str
    text_imlaei_simple: str
    text_uthmani: str


@dataclass(frozen=True)
class RemoteUnit:
    verse_key: str
    source_location: str
    text_imlaei: str
    text_imlaei_simple: str
    text_uthmani: str


@dataclass(frozen=True)
class CorpusRow:
    word_id: str
    imlaei_text: str
    imlaei_simple_text: str
    source_location: str
    segmentation_note: str = ""


def normalize_arabic(value: str) -> str:
    text = value.replace("\u00A0", " ").replace("\u0640", "")
    text = ARABIC_MARKS.sub("", text)
    text = re.sub(r"[^\w\s\u0621-\u064A\u0671-\u06D3]", " ", text)
    return (
        text.replace("ٱ", "ا")
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("آ", "ا")
        .replace("ؤ", "ء")
        .replace("ئ", "ء")
        .replace("ى", "ي")
        .replace(" ", "")
        .strip()
    )


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def fetch_json(url: str) -> Any:
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "User-Agent": "MyVault Quran Imlaei Corpus Verifier/1.0",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def load_local_words(path: Path) -> list[LocalWord]:
    payload = read_json(path)
    records = payload.get("records", []) if isinstance(payload, dict) else payload
    local_words: list[LocalWord] = []
    for item in records:
        word_id = str(item.get("wordId", "")).strip()
        if not re.fullmatch(r"\d+:\d+:\d+", word_id):
            continue
        local_words.append(
            LocalWord(
                word_id=word_id,
                verse_key=":".join(word_id.split(":")[:2]),
                arabic_text=str(item.get("arabicText", "")).strip(),
                normalized_arabic_text=str(item.get("normalizedArabicText", "")).strip(),
            )
        )
    return local_words


def fetch_remote_words() -> list[RemoteWord]:
    chapters_payload = fetch_json(f"{QURAN_COM_API_BASE}/chapters?language=en")
    chapter_ids = [int(item["id"]) for item in chapters_payload.get("chapters", [])]
    remote_words: list[RemoteWord] = []
    for chapter_id in chapter_ids:
        url = (
            f"{QURAN_COM_API_BASE}/verses/by_chapter/{chapter_id}"
            "?words=true"
            "&word_fields=text_imlaei,text_imlaei_simple,text_uthmani,location"
            "&per_page=300"
        )
        payload = fetch_json(url)
        for verse in payload.get("verses", []):
            verse_key = str(verse.get("verse_key", ""))
            for word in verse.get("words", []):
                if word.get("char_type_name") != "word":
                    continue
                remote_words.append(
                    RemoteWord(
                        verse_key=verse_key,
                        location=str(word.get("location", "")).strip(),
                        position=int(word.get("position", 0) or 0),
                        text_imlaei=str(word.get("text_imlaei", "")).strip(),
                        text_imlaei_simple=str(word.get("text_imlaei_simple", "")).strip(),
                        text_uthmani=str(word.get("text_uthmani", "")).strip(),
                    )
                )
    return remote_words


def group_by_verse(items: list[Any]) -> dict[str, list[Any]]:
    grouped: dict[str, list[Any]] = {}
    for item in items:
        grouped.setdefault(item.verse_key, []).append(item)
    return grouped


def split_remote_word(word: RemoteWord) -> list[RemoteUnit]:
    imlaei_parts = meaningful_parts(word.text_imlaei)
    uthmani_parts = meaningful_parts(word.text_uthmani)
    simple_parts = meaningful_parts(word.text_imlaei_simple)
    part_count = max(len(imlaei_parts), len(uthmani_parts), len(simple_parts), 1)
    if part_count == 1:
        return [
            RemoteUnit(
                verse_key=word.verse_key,
                source_location=word.location,
                text_imlaei=word.text_imlaei,
                text_imlaei_simple=word.text_imlaei_simple,
                text_uthmani=word.text_uthmani,
            )
        ]
    units: list[RemoteUnit] = []
    for index in range(part_count):
        imlaei = imlaei_parts[index] if index < len(imlaei_parts) else ""
        uthmani = uthmani_parts[index] if index < len(uthmani_parts) else ""
        simple = simple_parts[index] if index < len(simple_parts) else ""
        if not simple:
            simple = normalize_arabic(imlaei or uthmani)
        units.append(
            RemoteUnit(
                verse_key=word.verse_key,
                source_location=word.location,
                text_imlaei=imlaei or simple,
                text_imlaei_simple=simple,
                text_uthmani=uthmani,
            )
        )
    return units


def meaningful_parts(value: str) -> list[str]:
    return [part for part in value.split() if normalize_arabic(part)]


def convert_verse(local_words: list[LocalWord], remote_words: list[RemoteWord]) -> tuple[list[CorpusRow], list[str]]:
    should_split_remote_phrases = len(remote_words) < len(local_words)
    units = [
        unit
        for word in remote_words
        for unit in (
            split_remote_word(word)
            if should_split_remote_phrases
            else [
                RemoteUnit(
                    verse_key=word.verse_key,
                    source_location=word.location,
                    text_imlaei=word.text_imlaei,
                    text_imlaei_simple=word.text_imlaei_simple,
                    text_uthmani=word.text_uthmani,
                )
            ]
        )
    ]
    notes: list[str] = []
    if len(units) == len(local_words):
        rows = [
            CorpusRow(
                word_id=local.word_id,
                imlaei_text=unit.text_imlaei,
                imlaei_simple_text=unit.text_imlaei_simple,
                source_location=unit.source_location,
                segmentation_note="split remote phrase" if unit.source_location != local.word_id else "",
            )
            for local, unit in zip(local_words, units)
        ]
        if any(row.segmentation_note for row in rows):
            notes.append(f"{local_words[0].verse_key}: split spaced remote phrase into MyVault word IDs")
        return rows, notes

    rows: list[CorpusRow] = []
    unit_index = 0
    while unit_index < len(units) and len(rows) < len(local_words):
        remaining_units = len(units) - unit_index
        remaining_local = len(local_words) - len(rows)
        if remaining_units == remaining_local:
            for local, unit in zip(local_words[len(rows) :], units[unit_index:]):
                rows.append(
                    CorpusRow(
                        word_id=local.word_id,
                        imlaei_text=unit.text_imlaei,
                        imlaei_simple_text=unit.text_imlaei_simple,
                        source_location=unit.source_location,
                    )
                )
            unit_index = len(units)
            break

        local = local_words[len(rows)]
        target = normalize_arabic(local.normalized_arabic_text or local.arabic_text)
        consumed: list[RemoteUnit] = []
        combined = ""
        while unit_index < len(units):
            consumed.append(units[unit_index])
            combined += normalize_arabic(units[unit_index].text_imlaei_simple)
            unit_index += 1
            if combined == target:
                break
            if len(combined) > len(target) + 2:
                break
        if combined != target:
            return rows, notes + [f"{local.verse_key}: could not safely align around {local.word_id}"]
        rows.append(
            CorpusRow(
                word_id=local.word_id,
                imlaei_text="".join(unit.text_imlaei for unit in consumed),
                imlaei_simple_text="".join(unit.text_imlaei_simple for unit in consumed),
                source_location="+".join(unit.source_location for unit in consumed),
                segmentation_note="merged remote tokens" if len(consumed) > 1 else "",
            )
        )
        if len(consumed) > 1:
            notes.append(f"{local.verse_key}: merged remote tokens for {local.word_id}")
    if len(rows) != len(local_words) or unit_index != len(units):
        notes.append(f"{local_words[0].verse_key}: remaining local/remote units after conversion")
    return rows, notes


def convert_corpus(local_words: list[LocalWord], remote_words: list[RemoteWord]) -> tuple[list[CorpusRow], list[str], list[str]]:
    local_by_verse = group_by_verse(local_words)
    remote_by_verse = group_by_verse(remote_words)
    all_rows: list[CorpusRow] = []
    notes: list[str] = []
    failures: list[str] = []
    for verse_key in sorted(local_by_verse, key=lambda key: tuple(int(part) for part in key.split(":"))):
        local_verse_words = sorted(local_by_verse[verse_key], key=lambda item: int(item.word_id.split(":")[2]))
        remote_verse_words = sorted(remote_by_verse.get(verse_key, []), key=lambda item: item.position)
        if not remote_verse_words:
            failures.append(f"{verse_key}: no remote rows")
            continue
        rows, verse_notes = convert_verse(local_verse_words, remote_verse_words)
        notes.extend(verse_notes)
        if len(rows) != len(local_verse_words):
            failures.append(f"{verse_key}: expected {len(local_verse_words)} rows, converted {len(rows)} rows")
        all_rows.extend(rows)
    return all_rows, notes, failures


def build_report(
    local_words: list[LocalWord],
    remote_words: list[RemoteWord],
    rows: list[CorpusRow],
    notes: list[str],
    failures: list[str],
) -> tuple[str, dict[str, Any]]:
    local_ids = {word.word_id for word in local_words}
    row_ids = [row.word_id for row in rows]
    row_id_set = set(row_ids)
    duplicate_ids = sorted({word_id for word_id in row_ids if row_ids.count(word_id) > 1})
    missing_ids = sorted(local_ids - row_id_set, key=lambda key: tuple(int(part) for part in key.split(":")))
    extra_ids = sorted(row_id_set - local_ids, key=lambda key: tuple(int(part) for part in key.split(":")))
    remote_by_verse = group_by_verse(remote_words)
    local_by_verse = group_by_verse(local_words)
    segmentation_mismatches = [
        f"{verse_key}: local={len(local_by_verse[verse_key])}, rawRemote={len(remote_by_verse.get(verse_key, []))}"
        for verse_key in sorted(local_by_verse, key=lambda key: tuple(int(part) for part in key.split(":")))
        if len(local_by_verse[verse_key]) != len(remote_by_verse.get(verse_key, []))
    ]
    summary = {
        "localWordRows": len(local_words),
        "rawRemoteRows": len(remote_words),
        "convertedRows": len(rows),
        "matchedRows": len(local_ids & row_id_set),
        "missingRows": len(missing_ids),
        "extraRows": len(extra_ids),
        "duplicateRows": len(duplicate_ids),
        "segmentationMismatchVerses": len(segmentation_mismatches),
        "conversionFailures": len(failures),
    }
    report = [
        "# Imlaei Quran Comparison Corpus Verification",
        "",
        f"Generated: {datetime.now(timezone.utc).isoformat()}",
        "",
        "## Source",
        "",
        f"- {QURAN_COM_SOURCE}",
        "- Candidate reference: QUL/TarteelAI Imlaei Simple word-by-word resource, which uses the same location pattern.",
        "",
        "## Summary",
        "",
        f"- Total expected MyVault words: {summary['localWordRows']}",
        f"- Raw remote Imlaei word rows: {summary['rawRemoteRows']}",
        f"- Converted Imlaei rows: {summary['convertedRows']}",
        f"- Matched MyVault word IDs: {summary['matchedRows']}",
        f"- Missing rows after conversion: {summary['missingRows']}",
        f"- Extra rows after conversion: {summary['extraRows']}",
        f"- Duplicate rows after conversion: {summary['duplicateRows']}",
        f"- Raw segmentation mismatch verses: {summary['segmentationMismatchVerses']}",
        f"- Conversion failures: {summary['conversionFailures']}",
        "",
        "## Segmentation Adjustments",
        "",
    ]
    report.extend(f"- {note}" for note in notes[:80])
    if not notes:
        report.append("- None")
    report.extend(
        [
            "",
            "## First Raw Segmentation Mismatches",
            "",
        ]
    )
    report.extend(f"- {item}" for item in segmentation_mismatches[:40])
    if not segmentation_mismatches:
        report.append("- None")
    report.extend(
        [
            "",
            "## Failures",
            "",
        ]
    )
    report.extend(f"- {failure}" for failure in failures[:80])
    if not failures:
        report.append("- None")
    report.extend(
        [
            "",
            "## First Missing IDs",
            "",
        ]
    )
    report.extend(f"- {word_id}" for word_id in missing_ids[:40])
    if not missing_ids:
        report.append("- None")
    report.extend(
        [
            "",
            "## First Extra IDs",
            "",
        ]
    )
    report.extend(f"- {word_id}" for word_id in extra_ids[:40])
    if not extra_ids:
        report.append("- None")
    return "\n".join(report) + "\n", summary


def write_asset(path: Path, rows: list[CorpusRow]) -> None:
    payload = {
        "version": 1,
        "alignment": MYVAULT_ALIGNMENT,
        "wordIdFormat": WORD_ID_FORMAT,
        "source": QURAN_COM_SOURCE,
        "records": [
            {
                "wordId": row.word_id,
                "imlaeiText": row.imlaei_text,
                "imlaeiSimpleText": row.imlaei_simple_text,
            }
            for row in rows
        ],
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--local-metadata", type=Path, default=DEFAULT_LOCAL_METADATA_PATH)
    parser.add_argument("--output-asset", type=Path, default=DEFAULT_OUTPUT_ASSET_PATH)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT_PATH)
    parser.add_argument("--write-production", action="store_true")
    args = parser.parse_args()

    local_words = load_local_words(args.local_metadata)
    remote_words = fetch_remote_words()
    converted_rows, notes, failures = convert_corpus(local_words, remote_words)
    report_text, summary = build_report(local_words, remote_words, converted_rows, notes, failures)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(report_text, encoding="utf-8")

    clean = (
        summary["localWordRows"] == 77430
        and summary["convertedRows"] == 77430
        and summary["matchedRows"] == 77430
        and summary["missingRows"] == 0
        and summary["extraRows"] == 0
        and summary["duplicateRows"] == 0
        and summary["conversionFailures"] == 0
    )
    if args.write_production:
        if not clean:
            print(f"Refusing to write production asset; verification is not clean. See {args.report}", file=sys.stderr)
            return 1
        args.output_asset.parent.mkdir(parents=True, exist_ok=True)
        write_asset(args.output_asset, converted_rows)
    print(json.dumps(summary, indent=2))
    print(f"Report: {args.report}")
    if args.write_production:
        print(f"Asset: {args.output_asset}")
    return 0 if clean else 2


if __name__ == "__main__":
    raise SystemExit(main())
