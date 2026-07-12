#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path

pdf_path = Path(sys.argv[1])
index_path = Path(sys.argv[2])
assert pdf_path.exists() and pdf_path.stat().st_size > 10_000_000, "Source PDF missing or unexpectedly small"
items = json.loads(index_path.read_text(encoding="utf-8"))
assert len(items) == 156, f"Expected 156 indexed PDF pages, got {len(items)}"

chapters = [
    ("Voorwoord", 9, ["voorwoord"]),
    ("Het Versteende Tijdperk", 11, ["versteende", "tijdperk"]),
    ("De Groene Stad als innovatieve hot spot", 21, ["innovatieve", "hot spot"]),
    ("Van norm naar waarde", 29, ["norm", "waarde"]),
    ("Aangenaam verpozen", 37, ["aangenaam", "verpozen"]),
    ("Groen vast goed", 45, ["groen", "vast", "goed"]),
    ("Eten wat de stad schaft", 55, ["eten", "stad", "schaft"]),
    ("De rijkdom van de rustplaats", 63, ["rijkdom", "rustplaats"]),
    ("Mensenrijk, dierenrijk en plantenrijk", 71, ["mensenrijk", "dierenrijk", "plantenrijk"]),
    ("Aan de straatstenen niet kwijt", 79, ["straatstenen", "kwijt"]),
    ("Werk aan de winkel", 89, ["werk", "winkel"]),
    ("Wat heet warm", 97, ["heet", "warm"]),
    ("Over groene longen en leven van de lucht", 105, ["groene", "longen", "lucht"]),
    ("Van kantoortuin tot groen kantoor tot groene werkplaats", 115, ["kantoortuin", "groene", "werkplaats"]),
    ("Kom je buiten spelen?", 123, ["buiten", "spelen"]),
    ("Van zorg voor meer groen tot minder zorg door meer groen", 133, ["zorg", "minder", "groen"]),
    ("Over nieuw eigenaarschap en nieuwe collectiviteit", 143, ["eigenaarschap", "collectiviteit"]),
    ("Epiloog: De balans opgemaakt", 149, ["epiloog", "balans", "opgemaakt"]),
]

for title, book_page, needles in chapters:
    pdf_index = book_page + 1
    text = re.sub(r"\s+", " ", items[pdf_index]["text"].lower())
    missing = [word for word in needles if word not in text]
    assert not missing, f"Chapter mapping failed for {title}: PDF index {pdf_index}, missing {missing}"

assert sum(bool(x.get("text")) for x in items) >= 150, "Too many empty search pages"
blob = " ".join(x["text"].lower() for x in items)
for term in ["wateroverlast", "biodiversiteit", "zorgkosten", "groene daken", "ozb"]:
    assert term in blob, f"Search term missing from index: {term}"
print("PASS: 156 pages, 18 chapter mappings and full-text search index validated")
