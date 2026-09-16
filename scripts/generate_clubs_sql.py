#!/usr/bin/env python3
"""Convert the MVHS clubs CSV export into seed, refresh, and local H2 SQL.

Three outputs, all from the same CSV:

  mvhs_clubs_seed.sql      fresh-install seed (explicit primary keys)
  mvhs_clubs_refresh.sql   reconcile a database that already has a club list
  db/h2/data.sql           H2-only local development fixture
"""

from __future__ import annotations

import csv
import pathlib
import re
import sys
import unicodedata
from typing import List, Sequence


ROOT = pathlib.Path(__file__).resolve().parents[1]
# The official list for the school year this repository currently deploys.
# Pass a different path as argv[1] to regenerate from another year's export.
DEFAULT_CSV_FILENAME = "Official MVHS Clubs List 2026-2027 - Official list.csv"
# The 2026-2027 export renamed the first column from "Club Name" to
# "Name of Club"; both are accepted so either year's export still parses.
CLUB_NAME_COLUMNS = ("Name of Club", "Club Name")
OUTPUT_FILENAME = "mvhs_clubs_seed.sql"
REFRESH_OUTPUT_FILENAME = "mvhs_clubs_refresh.sql"
LOCAL_OUTPUT_PATH = pathlib.Path("backend/src/main/resources/db/h2/data.sql")
# The local H2 seed carries a hand-written warning that the generated SQL must
# preserve; regenerating it used to drop these lines.
H2_HEADER = """-- H2-compatible local seed using the official MVHS clubs list.
-- H2-PROFILE-ONLY: wired up exclusively through application-h2.yaml's
-- spring.sql.init.data-locations. Never reachable from the production datasource --
-- there is no default-location data.sql for Spring Boot to pick up, on purpose, so a
-- misconfigured SPRING_SQL_INIT_MODE=always in production could at most re-run
-- schema.sql, never re-seed these hardcoded-primary-key rows over real data.
"""
# The school a refresh targets. The slug matches APP_SUMMARY_SLUG, and the
# MIN(id) fallback keeps the script usable when the slug differs locally.
SCHOOL_SLUG = "mvhs"
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
    # Names that first appear in the 2026-2027 official list. Most are renames
    # of an entry above (see CLUB_RENAMES); MVHS BMES and Creative Writing Club
    # are the two clubs that are genuinely new. The retained old names keep the
    # 2025-2026 export regenerable, and category_for() is what catches a name
    # this map is still missing.
    "Aerospace and Aviation Club": "STEM & Innovation",
    "AIAS Architecture Club": "STEM & Innovation",
    "Bring Change to Mind": "Wellness & Athletics",
    "Creative Writing Club": "Creative Arts & Media",
    "DERM Club": "STEM & Innovation",
    "Dream Volunteers Club": "Service & Leadership",
    "Fellowship of Christian Athletes (FCA)": "Service & Leadership",
    "Fentanyl and Substance Awareness Club": "Service & Leadership",
    "Jewish Student Union": "Service & Leadership",
    "Marine Biology and Ocean Conservation Club": "STEM & Innovation",
    "Mountain View High School Science Olympiad": "STEM & Innovation",
    "Muslim Student Association (MSA)": "Service & Leadership",
    "MVHS BMES": "STEM & Innovation",
    "MVHS Driver's Association": "Service & Leadership",
    "MVHS Green Team": "Service & Leadership",
    "Owed Soap": "Service & Leadership",
    "Rock climbing club": "Wellness & Athletics",
    "Steam Club": "STEM & Innovation",
    "Theater Club": "Service & Leadership",
}


# Clubs the 2026-2027 list carried over under a new name. A refresh renames
# these in place rather than inserting a new row and archiving the old one, so
# the club keeps its id and, with it, its roster, posts and join requests.
# Keys are the 2025-2026 names; values are the 2026-2027 names.
CLUB_RENAMES = {
    "Aerospace & Aviation": "Aerospace and Aviation Club",
    "The MVHS AIAS Architecture Club": "AIAS Architecture Club",
    "Bring Change to Mind Club": "Bring Change to Mind",
    "DERM (Dermatology Educational Research Mission) Club": "DERM Club",
    "Dream Volunteers": "Dream Volunteers Club",
    "Fellowship of Christian Athletes": "Fellowship of Christian Athletes (FCA)",
    # Spelling only: the list writes the conjunction out and re-cases an acronym.
    "Fentanyl & Substance Awareness Club": "Fentanyl and Substance Awareness Club",
    "Jewish Student Union (JSU)": "Jewish Student Union",
    "Marine Biology and Ocean Club": "Marine Biology and Ocean Conservation Club",
    "Mountain View Science Olympiad": "Mountain View High School Science Olympiad",
    "Muslim Student Association": "Muslim Student Association (MSA)",
    "MVHS Drivers Association": "MVHS Driver's Association",
    "MV Greenteam": "MVHS Green Team",
    "Owed Soap Initiative": "Owed Soap",
    "Rock Climbing Club": "Rock climbing club",
    "STEAM Club": "Steam Club",
    "Theatre Club": "Theater Club",
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


def club_name(row: dict[str, str | None]) -> str | None:
    """Return the club name from whichever heading this export used."""

    for column in CLUB_NAME_COLUMNS:
        name = clean(row.get(column))
        if name:
            return name
    return None


def category_for(name: str) -> str:
    """Return the production category or fail before writing an incomplete seed."""

    category = CLUB_CATEGORY_BY_NAME.get(name)
    if category is None:
        raise RuntimeError(f"Club {name!r} is missing from CLUB_CATEGORY_BY_NAME")
    return category


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
    name = club_name(row)
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
        "category": category_for(name),
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


def records_to_sql(records: Sequence[dict[str, object]], csv_filename: str) -> str:
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
        f"-- Source CSV: {csv_filename}",
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


def quote(value: object) -> str:
    """Render a Python value as a MySQL literal."""

    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def records_to_refresh_sql(records: Sequence[dict[str, object]], csv_filename: str) -> str:
    """Reconcile a database that already holds a club list with a newer one.

    The fresh-install seed cannot be replayed onto an existing database: it
    inserts explicit primary keys, so it collides with the clubs already there.
    Deleting the clubs that dropped off the list is the other obvious answer and
    is worse, because every child table cascades -- rosters, posts and join
    requests would go with them. So this renames the clubs that were renamed,
    upserts the rest by name, and archives whatever is left over, which the
    API's archive endpoints can undo.
    """

    if not records:
        raise RuntimeError("No club records were parsed from the CSV.")

    list_names = ", ".join(quote(record["name"]) for record in records)
    lines = [
        "-- Auto-generated by scripts/generate_clubs_sql.py",
        f"-- Source CSV: {csv_filename}",
        "--",
        "-- Reconciles a database that already holds a club list with the one above.",
        "-- Safe to re-run, and non-destructive: a club that dropped off the list is",
        "-- archived, not deleted, so its roster, posts and join requests survive.",
        "",
        "START TRANSACTION;",
        "",
        "-- Which school the clubs belong to. The slug matches APP_SUMMARY_SLUG; the",
        "-- fallback is the only school in a single-school deployment.",
        "SET @school_id = COALESCE(",
        f"  (SELECT id FROM schools WHERE slug = {quote(SCHOOL_SLUG)} LIMIT 1),",
        "  (SELECT MIN(id) FROM schools)",
        ");",
        "",
        "-- 1. Renames, applied in place so a club keeps its id and its data.",
    ]
    for old_name, new_name in CLUB_RENAMES.items():
        lines.append(f"UPDATE clubs SET name = {quote(new_name)} WHERE name = {quote(old_name)};")

    lines.extend(["", "-- 2. Upsert every club in the list."])
    columns = [
        "name",
        "description",
        "category",
        "meeting_schedule",
        "location",
        "contact_email",
        "advisor",
        "member_count",
        "school_id",
        "status",
        "visibility",
    ]
    for record in records:
        name = quote(record["name"])
        fields = [
            ("description", record["description"]),
            ("category", record["category"]),
            ("meeting_schedule", record["meeting_schedule"]),
            ("location", record["location"]),
            ("contact_email", record["contact_email"]),
            ("advisor", record["advisor"]),
        ]
        assignments = ", ".join(f"{column} = {quote(value)}" for column, value in fields)
        # A club that dropped off an earlier list and is back on this one returns
        # to the directory. A pending club is deliberately left pending: putting
        # it in the list is the school's call, not this script's.
        assignments += ", status = IF(status = 'archived', 'active', status)"
        lines.append(f"UPDATE clubs SET {assignments} WHERE name = {name};")
        values = ", ".join([
            name,
            quote(record["description"]),
            quote(record["category"]),
            quote(record["meeting_schedule"]),
            quote(record["location"]),
            quote(record["contact_email"]),
            quote(record["advisor"]),
            "0",
            "@school_id",
            "'active'",
            "'public'",
        ])
        # UPDATE cannot create the row, and there is no unique key on name for an
        # upsert to hang off, so the insert is guarded by a NOT EXISTS probe.
        lines.append(
            f"INSERT INTO clubs ({', '.join(columns)})\n"
            f"  SELECT {values} FROM DUAL\n"
            f"  WHERE NOT EXISTS (SELECT 1 FROM clubs WHERE name = {name});"
        )

    lines.extend([
        "",
        "-- 3. Re-point the Instagram link for every club in the list. Rows for",
        "--    other social types, and for clubs the list does not mention, are",
        "--    left alone.",
        "DELETE FROM club_social_medias",
        "WHERE social_type = 'instagram'",
        f"  AND club_id IN (SELECT id FROM clubs WHERE name IN ({list_names}));",
    ])
    for record in records:
        if record.get("instagram_url"):
            lines.append(
                "INSERT INTO club_social_medias (club_id, social_type, link_name, link_url)\n"
                f"  SELECT id, 'instagram', 'Instagram', {quote(record['instagram_url'])}"
                f" FROM clubs WHERE name = {quote(record['name'])};"
            )

    lines.extend([
        "",
        "-- 4. Archive the clubs that dropped off the list.",
        "UPDATE clubs",
        "SET status = 'archived'",
        "WHERE status = 'active'",
        f"  AND name NOT IN ({list_names});",
        "",
        "-- 5. Collapse duplicate names, keeping the lowest id. Production carried",
        "--    two active rows named \"MVHS Drivers Association\", which the rename in",
        "--    step 1 would otherwise have left as two identical directory entries.",
        "UPDATE clubs",
        "SET status = 'archived'",
        "WHERE status = 'active'",
        "  AND id NOT IN (",
        "    SELECT keep_id FROM (",
        "      SELECT MIN(id) AS keep_id FROM clubs WHERE status = 'active' GROUP BY name",
        "    ) AS keepers",
        "  );",
        "",
        "COMMIT;",
    ])
    return "\n".join(lines) + "\n"


def main() -> int:
    csv_filename = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_CSV_FILENAME
    csv_path = ROOT / csv_filename
    output_path = ROOT / OUTPUT_FILENAME
    refresh_output_path = ROOT / REFRESH_OUTPUT_FILENAME

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
                name = club_name(row)
                if not name:
                    raise ValueError(f"Row {idx} is missing a club name")
                base_slug = slugify(name)
                slug_counts[base_slug] = slug_counts.get(base_slug, 0) + 1
                suffix = slug_counts[base_slug]
                slug = base_slug if suffix == 1 else f"{base_slug}-{suffix}"
                records.append(build_record(idx, row, slug))
            except ValueError as exc:
                print(f"Skipping row {idx}: {exc}", file=sys.stderr)

    sql = records_to_sql(records, csv_filename)
    output_path.write_text(sql, encoding="utf-8")
    refresh_output_path.write_text(
        records_to_refresh_sql(records, csv_filename), encoding="utf-8"
    )
    local_output_path = ROOT / LOCAL_OUTPUT_PATH
    local_output_path.parent.mkdir(parents=True, exist_ok=True)
    local_output_path.write_text(
        H2_HEADER + "\n" + LOCAL_USER_SEED + "\n" + sql,
        encoding="utf-8",
    )
    instagram_count = sum(bool(record.get("instagram_url")) for record in records)
    print(f"Wrote {len(records)} clubs and {instagram_count} Instagram profiles to {output_path}")
    print(f"Wrote refresh script to {refresh_output_path}")
    print(f"Wrote local seed to {local_output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
