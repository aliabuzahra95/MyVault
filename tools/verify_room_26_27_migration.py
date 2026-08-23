#!/usr/bin/env python3
"""Exercise the production 26→27 table-removal migration against real SQLite."""

from __future__ import annotations

import json
import sqlite3
import tempfile
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCHEMA_DIR = PROJECT_ROOT / "app/schemas/com.myvault.app.data.local.VaultDatabase"
RETIRED_TABLES = {
    "ai_messages",
    "ai_conversations",
    "home_chat_history",
    "library_ai_file_cache",
    "library_pdf_text_cache",
}
MIGRATION_SQL = [f"DROP TABLE IF EXISTS {table}" for table in (
    "ai_messages",
    "ai_conversations",
    "home_chat_history",
    "library_ai_file_cache",
    "library_pdf_text_cache",
)]


def load_entities(version: int) -> dict[str, dict]:
    document = json.loads((SCHEMA_DIR / f"{version}.json").read_text())
    return {
        entity["tableName"]: entity
        for entity in document["database"]["entities"]
    }


def create_schema(connection: sqlite3.Connection, entities: dict[str, dict]) -> None:
    for table_name, entity in entities.items():
        sql = entity["createSql"].replace("${TABLE_NAME}", table_name)
        connection.execute(sql)
    for table_name, entity in entities.items():
        for index in entity.get("indices", []):
            sql = index["createSql"].replace("${TABLE_NAME}", table_name)
            connection.execute(sql)
    connection.commit()


def columns(connection: sqlite3.Connection, table_name: str) -> list[tuple]:
    return [tuple(row) for row in connection.execute(f"PRAGMA table_info(`{table_name}`)")]


def indexes(connection: sqlite3.Connection, table_name: str) -> list[tuple]:
    return sorted(
        (row[1], row[2], row[3], row[4])
        for row in connection.execute(f"PRAGMA index_list(`{table_name}`)")
        if not row[1].startswith("sqlite_autoindex")
    )


def main() -> None:
    schema_26 = load_entities(26)
    schema_27 = load_entities(27)
    assert set(schema_26) - set(schema_27) == RETIRED_TABLES
    assert not (set(schema_27) - set(schema_26))

    with tempfile.TemporaryDirectory(prefix="myvault-room-migration-") as directory:
        old_path = Path(directory) / "version26.db"
        expected_path = Path(directory) / "version27.db"
        migrated = sqlite3.connect(old_path)
        expected = sqlite3.connect(expected_path)
        try:
            create_schema(migrated, schema_26)
            create_schema(expected, schema_27)

            migrated.execute("INSERT INTO tags(name) VALUES (?)", ("migration-survival-marker",))
            for statement in MIGRATION_SQL:
                migrated.execute(statement)
            migrated.commit()

            actual_tables = {
                row[0]
                for row in migrated.execute("SELECT name FROM sqlite_master WHERE type='table'")
            }
            assert not (actual_tables & RETIRED_TABLES)
            assert set(schema_27).issubset(actual_tables)
            assert migrated.execute(
                "SELECT name FROM tags WHERE name = ?",
                ("migration-survival-marker",),
            ).fetchone() == ("migration-survival-marker",)

            for table_name in sorted(schema_27):
                assert columns(migrated, table_name) == columns(expected, table_name), table_name
                assert indexes(migrated, table_name) == indexes(expected, table_name), table_name
        finally:
            migrated.close()
            expected.close()

    print("Room 26→27 SQLite migration verified: 5 retired tables removed; all surviving schemas and marker data preserved.")


if __name__ == "__main__":
    main()
