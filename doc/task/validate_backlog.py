#!/usr/bin/env python3
"""Validate the Scrum backlog schema and dependency graph."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
TODO = ROOT / "todo"
EXPECTED_STORY_COUNT = 40
ALLOWED = {
    "Type": {"fix", "enhance", "new", "idea", "exclusive"},
    "Status": {"todo"},
    "Priority": {"P0", "P1", "P2", "P3"},
    "Evidence": {"confirmed", "conditional", "decision", "idea"},
    "Estimate": {f"{points} SP" for points in (1, 2, 3, 5, 8, 13)},
    "Risk": {"Low", "Medium", "High", "Critical"},
}
REQUIRED_FIELDS = (*ALLOWED, "Epic", "Dependencies")
REQUIRED_TEST_MARKERS = (
    "## Required test matrix",
    "Unit tests",
    "Widget/UI tests",
    "Integration tests",
    "Tecno device",
)


def validate() -> list[str]:
    errors: list[str] = []
    stories: dict[str, Path] = {}
    dependencies: dict[str, list[str]] = {}
    files = sorted(TODO.glob("*.md"))

    if len(files) != EXPECTED_STORY_COUNT:
        errors.append(
            f"expected {EXPECTED_STORY_COUNT} stories, found {len(files)}"
        )

    for path in files:
        content = path.read_text(encoding="utf-8")
        heading = re.match(r"# ([A-Z0-9]+-\d+) — ", content)
        if heading is None:
            errors.append(f"{path.name}: invalid story heading")
            continue

        story_id = heading.group(1)
        if story_id in stories:
            errors.append(f"duplicate story ID: {story_id}")
        stories[story_id] = path

        metadata = dict(
            re.findall(r"^\| ([A-Za-z ]+) \| (.*?) \|$", content, re.MULTILINE)
        )
        for field in REQUIRED_FIELDS:
            if field not in metadata:
                errors.append(f"{story_id}: missing metadata field {field}")
        for field, allowed_values in ALLOWED.items():
            if metadata.get(field) not in allowed_values:
                errors.append(
                    f"{story_id}: invalid {field} value {metadata.get(field)!r}"
                )

        filename = re.match(
            r"(p[0-3])-([a-z0-9]+)-([a-z0-9]+-\d+)-", path.name
        )
        if (
            filename is None
            or filename.group(1).upper() != metadata.get("Priority")
            or filename.group(3).upper() != story_id
        ):
            errors.append(f"{story_id}: filename does not match ID/priority metadata")

        for marker in REQUIRED_TEST_MARKERS:
            if marker not in content:
                errors.append(f"{story_id}: missing test requirement {marker!r}")

        raw_dependencies = metadata.get("Dependencies", "None")
        dependencies[story_id] = (
            []
            if raw_dependencies == "None"
            else [value.strip() for value in raw_dependencies.split(",")]
        )

    for story_id, required_ids in dependencies.items():
        for required_id in required_ids:
            if required_id not in stories:
                errors.append(f"{story_id}: unresolved dependency {required_id}")

    state: dict[str, int] = {}
    stack: list[str] = []

    def visit(story_id: str) -> None:
        state[story_id] = 1
        stack.append(story_id)
        for dependency in dependencies.get(story_id, []):
            if dependency not in stories:
                continue
            if state.get(dependency) == 1:
                errors.append(
                    "dependency cycle: " + " -> ".join((*stack, dependency))
                )
            elif state.get(dependency, 0) == 0:
                visit(dependency)
        stack.pop()
        state[story_id] = 2

    for story_id in stories:
        if state.get(story_id, 0) == 0:
            visit(story_id)

    return errors


if __name__ == "__main__":
    validation_errors = validate()
    if validation_errors:
        print("Backlog validation failed:")
        for error in validation_errors:
            print(f"- {error}")
        sys.exit(1)
    print(f"Backlog validation passed: {EXPECTED_STORY_COUNT} stories, no cycles")
