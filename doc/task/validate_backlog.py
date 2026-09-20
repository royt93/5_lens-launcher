#!/usr/bin/env python3
"""Validate the Scrum backlog schema and dependency graph."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
STATUS_DIRECTORIES = {
    "todo": ROOT / "todo",
    "inprogress": ROOT / "inprogress",
    "done": ROOT / "done",
}
EXPECTED_STORY_COUNT = 72
ALLOWED = {
    "Type": {"fix", "enhance", "new", "idea", "exclusive", "feature"},
    "Status": set(STATUS_DIRECTORIES),
    "Priority": {"P0", "P1", "P2", "P3"},
    "Evidence": {"confirmed", "conditional", "decision", "idea"},
    "Estimate": {f"{points} SP" for points in (1, 2, 3, 5, 8, 13)},
    "Risk": {"Low", "Medium", "High", "Critical"},
}
REQUIRED_FIELDS = (*ALLOWED, "Epic", "Dependencies")
REQUIRED_TEST_MARKERS = (
    "unit",
    "widget/ui",
    "integration",
    "smoke",
)


def validate() -> list[str]:
    errors: list[str] = []
    stories: dict[str, Path] = {}
    dependencies: dict[str, list[str]] = {}
    files = sorted(
        path
        for directory in STATUS_DIRECTORIES.values()
        for path in directory.glob("*.md")
    )

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

        raw_metadata = re.findall(r"^\| ([A-Za-z ]+) \| (.*?) \|$", content, re.MULTILINE)
        metadata = {k.strip(): v.strip().strip("`") for k, v in raw_metadata}

        for field in REQUIRED_FIELDS:
            if field not in metadata:
                errors.append(f"{story_id}: missing metadata field {field}")

        # Normalize Type (support compound types like 'fix + feature')
        story_type = metadata.get("Type", "")
        if "+" in story_type:
            parts = [p.strip().strip("`") for p in story_type.split("+")]
            if not all(p in ALLOWED["Type"] for p in parts):
                errors.append(f"{story_id}: invalid compound Type {story_type!r}")
        elif story_type not in ALLOWED["Type"]:
            errors.append(f"{story_id}: invalid Type value {story_type!r}")

        # Normalize Estimate (support numbers e.g. '8' -> '8 SP' and split notes)
        est = metadata.get("Estimate", "")
        first_token = est.split()[0] if est else ""
        if first_token.isdigit():
            est = f"{first_token} SP"
            metadata["Estimate"] = est
        if est not in ALLOWED["Estimate"]:
            errors.append(f"{story_id}: invalid Estimate value {metadata.get('Estimate')!r}")

        for field in ("Priority", "Evidence", "Risk"):
            if metadata.get(field) not in ALLOWED[field]:
                errors.append(
                    f"{story_id}: invalid {field} value {metadata.get(field)!r}"
                )

        expected_status = path.parent.name
        status_val = metadata.get("Status", "")
        if not status_val.startswith(expected_status):
            errors.append(
                f"{story_id}: Status {status_val!r} does not match "
                f"folder {expected_status!r}"
            )

        filename = re.match(
            r"(p[0-3])-([a-z0-9]+)-([a-z0-9]+-\d+)-", path.name.lower()
        )
        if (
            filename is None
            or filename.group(1).upper() != metadata.get("Priority")
            or filename.group(3).upper() != story_id
        ):
            errors.append(f"{story_id}: filename does not match ID/priority metadata")

        test_section = re.split(
            r"## (?:Required test matrix|Verification and Definition of Done|Verification)",
            content,
        )
        if len(test_section) < 2:
            errors.append(f"{story_id}: missing required test matrix section")
        else:
            test_content = test_section[1].split("\n## ", 1)[0].lower()
            if not any(m in test_content for m in ("unit", "not applicable", "n/a", "assemble")):
                errors.append(f"{story_id}: missing test requirement 'unit'")
            if not any(m in test_content for m in ("widget", "ui", "view", "layout", "draw", "screen", "not applicable", "n/a")):
                errors.append(f"{story_id}: missing test requirement 'widget/ui'")
            if not any(m in test_content for m in ("integration", "instrumented", "connected", "subsystem", "e2e", "am instrument", "assemble", "not applicable", "n/a")):
                errors.append(f"{story_id}: missing test requirement 'integration'")
            if not any(m in test_content for m in ("smoke", "device", "tecno", "s24", "pixel", "not applicable", "n/a")):
                errors.append(f"{story_id}: missing test requirement 'smoke'")

        raw_dependencies = metadata.get("Dependencies", "None")
        if raw_dependencies.strip().startswith("None"):
            dependencies[story_id] = []
        else:
            dep_ids = re.findall(r"\b[A-Z]{2,6}-\d{3}\b", raw_dependencies)
            dependencies[story_id] = dep_ids

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
    counts = {
        status: len(list(directory.glob("*.md")))
        for status, directory in STATUS_DIRECTORIES.items()
    }
    print(
        f"Backlog validation passed: {EXPECTED_STORY_COUNT} stories, no cycles "
        f"({counts['todo']} todo, {counts['inprogress']} in progress, "
        f"{counts['done']} done)"
    )
