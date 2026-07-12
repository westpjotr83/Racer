#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from pypdf import PdfReader

source = Path(sys.argv[1])
target = Path(sys.argv[2])
reader = PdfReader(str(source))
items = []
for index, page in enumerate(reader.pages):
    text = page.extract_text() or ""
    text = text.replace("\u00ad", "")
    text = re.sub(r"-\s*\n\s*", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    items.append({"page": index, "text": text})
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(items, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"Indexed {len(items)} pages into {target} ({target.stat().st_size} bytes)")
