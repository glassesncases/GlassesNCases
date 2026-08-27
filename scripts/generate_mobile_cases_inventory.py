import csv
import json
import re
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from pypdf import PdfReader

PDF_PATH = Path(r"D:/Tempered Glass Models/Mobile Cases List 368 Entries.pdf")
OUTPUT_DIR = Path("scripts/output")
OUTPUT_JSON = OUTPUT_DIR / "mobile_cases_brandwise_sorted.json"
OUTPUT_CSV = OUTPUT_DIR / "mobile_cases_brandwise_sorted.csv"

TOKEN_RE = re.compile(r"\d+|[A-Za-z]+")


@dataclass
class ParsedEntry:
    piece: int
    model_name: str
    quantity: int


def normalize_model_name(value: str) -> str:
    value = value.replace("–", "-")
    value = re.sub(r"\s*/\s*", " / ", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def detect_brand(model_name: str) -> str:
    first_part = model_name.split("/", 1)[0].strip().lower()

    checks = [
        ("realme", "Realme"),
        ("narzo", "Narzo"),
        ("oppo", "Oppo"),
        ("reno", "Oppo"),
        ("vivo", "Vivo"),
        ("samsung", "Samsung"),
        ("redmi", "Redmi"),
        ("poco", "Poco"),
        ("xiaomi", "Xiaomi"),
        ("motorola", "Motorola"),
        ("moto", "Motorola"),
        ("edge", "Motorola"),
        ("oneplus", "OnePlus"),
        ("iqoo", "iQOO"),
        ("itel", "Itel"),
        ("nothing", "Nothing"),
    ]

    for prefix, brand in checks:
        if first_part.startswith(prefix + " ") or first_part == prefix:
            return brand

    return "Other"


def strip_brand_prefix(model_name: str, brand: str) -> str:
    work = model_name.strip()

    if brand == "Samsung":
        work = re.sub(r"^Samsung\s+Galaxy\s+", "", work, flags=re.IGNORECASE)
        work = re.sub(r"^Samsung\s+", "", work, flags=re.IGNORECASE)
    elif brand == "Oppo":
        work = re.sub(r"^Oppo\s+", "", work, flags=re.IGNORECASE)
    elif brand == "Realme":
        work = re.sub(r"^Realme\s+", "", work, flags=re.IGNORECASE)
    elif brand == "Vivo":
        work = re.sub(r"^Vivo\s+", "", work, flags=re.IGNORECASE)
    elif brand == "Redmi":
        work = re.sub(r"^Redmi\s+", "", work, flags=re.IGNORECASE)
    elif brand == "Motorola":
        work = re.sub(r"^Motorola\s+", "", work, flags=re.IGNORECASE)
        work = re.sub(r"^Moto\s+", "", work, flags=re.IGNORECASE)
    elif brand == "Poco":
        work = re.sub(r"^Poco\s+", "", work, flags=re.IGNORECASE)
    elif brand == "Xiaomi":
        work = re.sub(r"^Xiaomi\s+", "", work, flags=re.IGNORECASE)
    elif brand == "OnePlus":
        work = re.sub(r"^OnePlus\s+", "", work, flags=re.IGNORECASE)
    elif brand == "iQOO":
        work = re.sub(r"^iQOO\s+", "", work, flags=re.IGNORECASE)
    elif brand == "Itel":
        work = re.sub(r"^Itel\s+", "", work, flags=re.IGNORECASE)
    elif brand == "Nothing":
        work = re.sub(r"^Nothing\s+", "", work, flags=re.IGNORECASE)

    return work.strip()


def natural_key(text: str) -> tuple:
    key_parts = []
    for token in TOKEN_RE.findall(text.lower()):
        if token.isdigit():
            key_parts.append((0, int(token)))
        else:
            key_parts.append((1, token))
    return tuple(key_parts)


def extract_model_and_quantity(tail: str) -> tuple[str, int] | None:
    compact = tail.strip()
    if not compact:
        return None

    # Explicit missing row pattern.
    if compact.lower() == "missing-":
        return None

    # Primary parsing rule: quantity is the final single digit.
    m = re.match(r"^(.*?)(\d)$", compact)
    if m:
        model = m.group(1).strip()
        qty = int(m.group(2))
        if not model or model.lower() == "missing":
            return None
        return normalize_model_name(model), qty

    # Fallback: if quantity is not detectable, keep row with qty=1.
    fallback_model = normalize_model_name(compact)
    if fallback_model.lower().startswith("missing"):
        return None
    return fallback_model, 1


def parse_entries(raw_text: str) -> list[ParsedEntry]:
    lines = [ln.strip() for ln in raw_text.splitlines() if ln.strip()]
    parsed: list[ParsedEntry] = []

    i = 0
    while i < len(lines):
        line = lines[i]

        if not line.isdigit():
            i += 1
            continue

        piece = int(line)
        if not (1 <= piece <= 368):
            i += 1
            continue

        if i + 2 >= len(lines):
            break

        model_line = lines[i + 1]
        qty_line = lines[i + 2]

        if qty_line.isdigit():
            qty = int(qty_line)
            model = normalize_model_name(model_line)
            if model.lower() != "missing":
                parsed.append(ParsedEntry(piece=piece, model_name=model, quantity=qty))
            i += 3
            continue

        if qty_line == "-":
            i += 3
            continue

        extracted = extract_model_and_quantity(model_line + qty_line)
        if extracted is not None:
            model, qty = extracted
            parsed.append(ParsedEntry(piece=piece, model_name=model, quantity=qty))
            i += 3
            continue

        i += 1

    return parsed


def merge_exact_duplicates(entries: list[ParsedEntry]) -> list[dict]:
    merged: dict[str, dict] = {}

    for entry in entries:
        key = entry.model_name.casefold()
        if key not in merged:
            merged[key] = {
                "modelName": entry.model_name,
                "quantity": entry.quantity,
                "pieces": [entry.piece],
            }
        else:
            merged[key]["quantity"] += entry.quantity
            merged[key]["pieces"].append(entry.piece)

    normalized = []
    for item in merged.values():
        normalized.append(
            {
                "modelName": item["modelName"],
                "quantity": item["quantity"],
                "pieces": sorted(item["pieces"]),
            }
        )

    return normalized


def build_brandwise_rows(merged_models: list[dict]) -> list[dict]:
    grouped: dict[str, list[dict]] = defaultdict(list)

    for item in merged_models:
        brand = detect_brand(item["modelName"])
        grouped[brand].append(item)

    final_rows: list[dict] = []
    for brand in sorted(grouped.keys()):
        sorted_models = sorted(
            grouped[brand],
            key=lambda x: (
                natural_key(strip_brand_prefix(x["modelName"], brand)),
                x["modelName"].lower(),
            ),
        )

        for serial, model in enumerate(sorted_models, start=1):
            final_rows.append(
                {
                    "brand": brand,
                    "brandSerial": serial,
                    "modelName": model["modelName"],
                    "quantity": model["quantity"],
                    "sourcePieces": ",".join(str(x) for x in model["pieces"]),
                }
            )

    return final_rows


def main() -> None:
    if not PDF_PATH.exists():
        raise FileNotFoundError(f"PDF not found: {PDF_PATH}")

    source_text = "\n".join((page.extract_text() or "") for page in PdfReader(str(PDF_PATH)).pages)

    parsed_entries = parse_entries(source_text)
    merged_models = merge_exact_duplicates(parsed_entries)
    brand_rows = build_brandwise_rows(merged_models)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    OUTPUT_JSON.write_text(json.dumps(brand_rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    with OUTPUT_CSV.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.DictWriter(
            fp,
            fieldnames=["brand", "brandSerial", "modelName", "quantity", "sourcePieces"],
        )
        writer.writeheader()
        writer.writerows(brand_rows)

    print(f"Parsed rows           : {len(parsed_entries)}")
    print(f"Merged unique models  : {len(merged_models)}")
    print(f"Final brand-wise rows : {len(brand_rows)}")
    print(f"Wrote JSON: {OUTPUT_JSON}")
    print(f"Wrote CSV : {OUTPUT_CSV}")


if __name__ == "__main__":
    main()
