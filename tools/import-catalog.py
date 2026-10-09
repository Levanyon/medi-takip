"""Rebuild the offline identity-only catalog from the pinned CC0 archive. Python stdlib only."""
import collections
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

COMMIT = "6211e27aeb1c48f77a7d2c3c0858ecec60a8505b"
BASE = f"https://raw.githubusercontent.com/Tip-Atlasi-Projesi/ilaclardb/{COMMIT}/"
ROOT = Path(__file__).resolve().parents[1]


def normalize(code):
    if len(code) not in (13, 14) or not code.isascii() or not code.isdigit() or set(code) == {"0"}:
        return None
    total = sum(int(c) * (3 if i % 2 == 0 else 1) for i, c in enumerate(code[-2::-1]))
    return code.zfill(14) if (10 - total % 10) % 10 == int(code[-1]) else None


def main():
    archive = urllib.request.urlopen(BASE + "data/json/ilac.json.zip").read()
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        source = json.loads(z.read("ilac.json"))
    records = next(x["data"] for x in source if x.get("type") == "table")
    groups = collections.defaultdict(set)
    invalid = 0
    for row in records:
        gtin = normalize(row.get("barcode", ""))
        name = row.get("Product_Name", "").strip()
        if not gtin or not name:
            invalid += 1
            continue
        # Name is sufficient for prefill. Exclude clinical text and uncertain optional metadata.
        groups[gtin].add(name)
    products = [{"gtin": g, "name": next(iter(names))} for g, names in sorted(groups.items()) if len(names) == 1]
    data = {"version": COMMIT, "products": products}
    target = ROOT / "app/src/main/assets/catalog"
    target.mkdir(parents=True, exist_ok=True)
    encoded = (json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
    (target / "medications.json").write_bytes(encoded)
    (target / "LICENSE.txt").write_bytes(urllib.request.urlopen(BASE + "LICENSE").read())
    manifest = {"source": "https://github.com/Tip-Atlasi-Projesi/ilaclardb", "commit": COMMIT,
                "sourcePath": "data/json/ilac.json.zip", "license": "CC0-1.0", "snapshotDate": "2026-10-09",
                "archiveSha256": hashlib.sha256(archive).hexdigest(), "snapshotSha256": hashlib.sha256(encoded).hexdigest(),
                "sourceRows": len(records), "products": len(products), "invalidRowsExcluded": invalid,
                "conflictingGtinsExcluded": sum(len(v) > 1 for v in groups.values())}
    (target / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
