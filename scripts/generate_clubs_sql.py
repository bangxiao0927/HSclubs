#!/usr/bin/env python3
"""Convert the MVHS clubs CSV export into production and local seed SQL."""

from __future__ import annotations

import csv
import pathlib
import re
import sys
import unicodedata
from typing import List, Sequence


ROOT = pathlib.Path(__file__).resolve().parents[1]
CSV_FILENAME = "Official MVHS Clubs List 2025-2026 - Official list.csv"
OUTPUT_FILENAME = "mvhs_clubs_seed.sql"
LOCAL_OUTPUT_PATH = pathlib.Path("backend/src/main/resources/data.sql")
EMAIL_PATTERN = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
INSTAGRAM_HANDLE_PATTERN = re.compile(r"^[A-Za-z0-9._]{1,64}$")
INSTAGRAM_PLACEHOLDERS = {"n/a", "none", "none yet", "not established yet", "tbd"}
ROOM_PREFIX_PATTERN = re.compile(r"^room\s+(.+)$", re.IGNORECASE)
ROOM_NUMBER_PATTERN = re.compile(r"^(?:[A-Za-z]\d+|\d+)(?:\b.*)?$")
# Keep local and redeployed seed data aligned with the production assignments.
CLUB_CATEGORY_BY_NAME = {
    "6th Man": "Wellness & Athletics",
    "Aerospace & Aviation": "STEM & Innovation",
    "Analytics Club": "STEM & Innovation",
    "AAH (Animal Assisted Happiness)": "Wellness & Athletics",
    "Animal Welfare Club": "Service & Leadership",
    "Artificial Intelligence Club": "STEM & Innovation",
    "Asian Pacific Union": "Culture & Identity",
    "Badminton Club": "Wellness & Athletics",
    "Barkada Club": "Culture & Identity",
    "Biology Club": "STEM & Innovation",
    "Black Student Union": "Culture & Identity",
    "Bring Change to Mind Club": "Wellness & Athletics",
    "Cancer Awareness and Research Club": "Service & Leadership",
    "CASC (California Association of Student Councils) Club": "Service & Leadership",
    "Chemistry Club": "STEM & Innovation",
    "Chess Club": "Competition & Strategy",
    "Children's Health Advocacy": "Service & Leadership",
    "Chinese Culture Club": "Culture & Identity",
    "Computer Science Club": "STEM & Innovation",
    "Connected Through Cultures": "Culture & Identity",
    "Crochet Club": "Creative Arts & Media",
    "Dance-a-Gram": "Creative Arts & Media",
    "DERM (Dermatology Educational Research Mission) Club": "STEM & Innovation",
    "Drama Llamas": "Creative Arts & Media",
    "Dream Volunteers": "Service & Leadership",
    "MVHS Drivers Association": "Service & Leadership",
    "Dungeons and Dragons": "Competition & Strategy",
    "Economics Club": "Service & Leadership",
    "Esports Club": "Competition & Strategy",
    "F=ma Club": "Competition & Strategy",
    "Fellowship of Christian Athletes": "Service & Leadership",
    "Fentanyl & Substance Awareness Club": "Service & Leadership",
    "Film Club": "Creative Arts & Media",
    "Foreign Linguistics Club": "Service & Leadership",
    "French Club": "Service & Leadership",
    "French Honors Society": "Service & Leadership",
    "Future Founders": "Service & Leadership",
    "Gender Sexuality Alliance": "Service & Leadership",
    "Girls Who Code Club": "Service & Leadership",
    "Guitar Culture Club": "Service & Leadership",
    "Ignition Club": "Service & Leadership",
    "INARA": "Service & Leadership",
    "Information Security Club": "STEM & Innovation",
    "Japanese Culture Club": "Service & Leadership",
    "Japanese National Honor Society": "Service & Leadership",
    "Jewish Student Union (JSU)": "Service & Leadership",
    "Juggling Club": "Service & Leadership",
    "K-Pop Dance Club": "Service & Leadership",
    "Korean Culture Club": "Culture & Identity",
    "Latino Student Union": "Service & Leadership",
    "Leo Club": "Service & Leadership",
    "M1820 Christian Club": "Service & Leadership",
    "Magic the Gathering Club": "Competition & Strategy",
    "Marine Biology and Ocean Club": "STEM & Innovation",
    "Math Club": "STEM & Innovation",
    "MEChA": "Culture & Identity",
    "MeToo": "Service & Leadership",
    "Mock Trial": "Service & Leadership",
    "Model UN": "Service & Leadership",
    "Mountain View Los Altos Speech & Debate": "Competition & Strategy",
    "Mountain View Science Olympiad": "STEM & Innovation",
    "Music Outreach Club": "Service & Leadership",
    "Muslim Student Association": "Service & Leadership",
    "MV Greenteam": "Service & Leadership",
    "MVHS Drivers Association": "Service & Leadership",
    "MVHS Jazz and Composition Club": "Service & Leadership",
    "MVHS Makers With A Mission": "STEM & Innovation",
    "MVHS Pokémon Club": "Service & Leadership",
    "MVHS Red Cross Club": "Service & Leadership",
    "MVHS UNICEF": "Service & Leadership",
    "MVHS Women in STEM": "Service & Leadership",
    "National Art Honors Society": "Service & Leadership",
    "Native American Student Union": "Service & Leadership",
    "Owed Soap Initiative": "Service & Leadership",
    "Persian Culture Club": "Service & Leadership",
    "Personal Finance Club": "Service & Leadership",
    "Physics and Astronomy Club": "Service & Leadership",
    "Pitch & Prototype": "Service & Leadership",
    "Poetry Club": "Creative Arts & Media",
    "Psychology Club": "Wellness & Athletics",
    "Puzzle Club": "Competition & Strategy",
    "Rock Climbing Club": "Wellness & Athletics",
    "RL Game Club": "Service & Leadership",
    "SLAM Magazine": "Service & Leadership",
    "Slavic Student Union": "Service & Leadership",
    "South Asian Student Union": "Culture & Identity",
    "Spartan Buddies": "Service & Leadership",
    "Spartan Equity Partners": "Competition & Strategy",
    "Stationery Club": "Service & Leadership",
    "STEAM Club": "STEM & Innovation",
    "Student Athlete Service Club": "Wellness & Athletics",
    "Taiwanese American Student Association": "Culture & Identity",
    "Technology Public Policy Club": "Service & Leadership",
    "TEDxMVHS": "Service & Leadership",
    "Theatre Club": "Service & Leadership",
    "The MVHS AIAS Architecture Club": "STEM & Innovation",
    "The Pediatrics Club": "STEM & Innovation",
    "The Star Wars Club": "Service & Leadership",
    "Ukrainian Culture Club": "Service & Leadership",
    "Unhoused Awareness": "Service & Leadership",
    "Vietnamese Student Association (VSA)": "Culture & Identity",
    "Women Athlete's Club": "Service & Leadership",
    "Women's Empowerment Club": "Service & Leadership",
    "Write for Blue": "Service & Leadership",
    "Youth Hunger Initiative": "Service & Leadership",
    "Yearbook Club": "Creative Arts & Media",
}


LOCAL_USER_SEED = """-- Local-only users for authentication and membership testing.
INSERT INTO oauth_users (uid, provider, provider_user_id, email, display_name, avatar_url, role) VALUES
  (1, 'google', 'google-123', 'maya.chen@example.com', 'Maya Chen', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Maya', 'student'),
  (2, 'google', 'google-456', 'leo.martinez@example.com', 'Leo Martinez', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Leo', 'student'),
  (3, 'google', 'google-789', 'priya.singh@example.com', 'Priya Singh', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Priya', 'advisor'),
  (4, 'google', 'google-321', 'apatel@mvhs.org', 'Dr. Patel', 'https://api.dicebear.com/7.x/thumbs/svg?seed=Patel', 'staff');
"""
CATEGORY_SEED = """INSERT INTO club_category (id, cate_name, logo, description) VALUES
  (1, 'STEM & Innovation', NULL, 'Engineering, robotics, science, and technology-focused clubs.'),
  (2, 'Service & Leadership', NULL, 'Clubs centered on volunteering, civic engagement, and leadership.'),
  (3, 'Creative Arts & Media', NULL, 'Visual arts, performing arts, and multimedia storytelling clubs.'),
  (4, 'Culture & Identity', NULL, 'Clubs organized around heritage, culture, language, and identity communities.'),
  (5, 'Wellness & Athletics', NULL, 'Athletics, physical wellness, recreation, and mental health clubs.'),
  (6, 'Competition & Strategy', NULL, 'Debate, games, olympiads, and strategy-focused clubs.');
"""


def clean(value: str | None) -> str | None:
    """Collapse internal whitespace and strip leading/trailing spaces."""

    if value is None:
        return None
    value = value.strip()
    if not value:
        return None
    return re.sub(r"\s+", " ", value)


def normalize_location(raw: str | None) -> str | None:
    """Prefix room-number locations while preserving named campus locations."""

    value = clean(raw)
    if not value:
        return None
    prefixed_room = ROOM_PREFIX_PATTERN.fullmatch(value)
    if prefixed_room:
        return f"Room {prefixed_room.group(1).strip()}"
    if ROOM_NUMBER_PATTERN.fullmatch(value):
        return f"Room {value}"
    return value


def extract_emails(raw: str | None) -> str | None:
    """Return a normalized, comma-separated list of email addresses."""

    raw = clean(raw)
    if not raw:
        return None
    matches = [match.lower() for match in EMAIL_PATTERN.findall(raw)]
    if matches:
        # Deduplicate while preserving order.
        seen: set[str] = set()
        ordered: List[str] = []
        for email in matches:
            if email in seen:
                continue
            seen.add(email)
            ordered.append(email)
        return ", ".join(ordered)
    return raw


def build_meeting_schedule(row: dict[str, str | None]) -> str | None:
    parts: List[str] = []
    for heading in ("Meeting Day", "Meeting Frequency", "Meeting Time"):
        value = clean(row.get(heading)) if row.get(heading) is not None else None
        if value:
            parts.append(value)
    return " · ".join(parts) if parts else None


def slugify(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", "-", normalized.lower()).strip("-") or "club"


def instagram_url(raw: str | None) -> str | None:
    value = clean(raw)
    if not value or value.casefold() in INSTAGRAM_PLACEHOLDERS:
        return None
    handle = value.removeprefix("@").strip().rstrip("/")
    if "instagram.com/" in handle.lower():
        handle = handle.split("instagram.com/", 1)[1].split("/", 1)[0].split("?", 1)[0]
    if not INSTAGRAM_HANDLE_PATTERN.fullmatch(handle):
        return None
    return f"https://www.instagram.com/{handle}/"


def build_record(idx: int, row: dict[str, str | None], slug: str) -> dict[str, object]:
    name = clean(row.get("Club Name"))
    if not name:
        raise ValueError(f"Row {idx} is missing a club name")

    description = clean(row.get("Mission Statement"))
    advisor = clean(row.get("Club Advisor"))
    advisor_email = extract_emails(row.get("Club Advisor Email"))
    president_email = extract_emails(row.get("Club President Email"))
    contact_email = president_email or advisor_email
    location = normalize_location(row.get("Meeting Room Number"))
    meeting_schedule = build_meeting_schedule(row)

    return {
        "id": idx,
        "name": name,
        "slug": slug,
        "alias_name": None,
        "description": description,
        "category": CLUB_CATEGORY_BY_NAME[name],
        "meeting_schedule": meeting_schedule,
        "location": location,
        "contact_email": contact_email,
        "advisor": advisor,
        "image_url": None,
        "member_count": 0,
        "achievements": "[]",
        "instagram_url": instagram_url(row.get("Instagram")),
    }


def sql_literal(value: object, raw_columns: set[str], column: str) -> str:
    if column in raw_columns:
        return value if isinstance(value, str) else str(value)
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    text = str(value).replace("'", "''")
    return f"'{text}'"


def records_to_sql(records: Sequence[dict[str, object]]) -> str:
    if not records:
        raise RuntimeError("No club records were parsed from the CSV.")

    columns = [
        "id",
        "name",
        "slug",
        "alias_name",
        "description",
        "category",
        "meeting_schedule",
        "location",
        "contact_email",
        "advisor",
        "image_url",
        "member_count",
        "achievements",
    ]
    raw_columns: set[str] = set()

    values_sql = []
    for record in records:
        row_parts = [sql_literal(record.get(column), raw_columns, column) for column in columns]
        values_sql.append("  (" + ", ".join(row_parts) + ")")

    lines = [
        "-- Auto-generated by scripts/generate_clubs_sql.py",
        f"-- Source CSV: {CSV_FILENAME}",
        CATEGORY_SEED.rstrip(),
        "",
        "INSERT INTO clubs (" + ", ".join(columns) + ") VALUES",
        ",\n".join(values_sql) + ";",
    ]
    social_rows = [
        f"  ({record['id']}, 'instagram', 'Instagram', {sql_literal(record['instagram_url'], set(), 'instagram_url')})"
        for record in records
        if record.get("instagram_url")
    ]
    if social_rows:
        lines.extend([
            "",
            "INSERT INTO club_social_medias (club_id, social_type, link_name, link_url) VALUES",
            ",\n".join(social_rows) + ";",
        ])
    return "\n".join(lines) + "\n"


def main() -> int:
    csv_path = ROOT / CSV_FILENAME
    output_path = ROOT / OUTPUT_FILENAME

    if not csv_path.exists():
        print(f"CSV file not found: {csv_path}", file=sys.stderr)
        return 1

    records: List[dict[str, object]] = []
    slug_counts: dict[str, int] = {}
    with csv_path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        idx = 0
        for row in reader:
            # Some mission statements span multiple lines; DictReader handles them already.
            if not any(row.values()):
                continue
            idx += 1
            try:
                name = clean(row.get("Club Name"))
                if not name:
                    raise ValueError(f"Row {idx} is missing a club name")
                base_slug = slugify(name)
                slug_counts[base_slug] = slug_counts.get(base_slug, 0) + 1
                suffix = slug_counts[base_slug]
                slug = base_slug if suffix == 1 else f"{base_slug}-{suffix}"
                records.append(build_record(idx, row, slug))
            except ValueError as exc:
                print(f"Skipping row {idx}: {exc}", file=sys.stderr)

    sql = records_to_sql(records)
    output_path.write_text(sql, encoding="utf-8")
    local_output_path = ROOT / LOCAL_OUTPUT_PATH
    local_output_path.write_text(
        "-- H2-compatible local seed using the official MVHS clubs list.\n\n" + LOCAL_USER_SEED + "\n" + sql,
        encoding="utf-8",
    )
    instagram_count = sum(bool(record.get("instagram_url")) for record in records)
    print(f"Wrote {len(records)} clubs and {instagram_count} Instagram profiles to {output_path}")
    print(f"Wrote local seed to {local_output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
