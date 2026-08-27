import json
import os
import re
import zipfile
import xml.etree.ElementTree as ET

XLSX_PATH = r"d:/Tempered Glass Models/Tempered_Glass_Models_Renumbered.xlsx"
OUT_PATH = r"d:/TempGlass/app/src/main/assets/initial_models.json"

NS_MAIN = {"main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
NS_RELS = {"pkg": "http://schemas.openxmlformats.org/package/2006/relationships"}
RID_KEY = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"


def get_column_index(cell_ref: str) -> int:
    letters = "".join(ch for ch in cell_ref if ch.isalpha())
    index = 0
    for ch in letters:
        index = index * 26 + (ord(ch.upper()) - 64)
    return index


def read_sheet_rows(zip_file: zipfile.ZipFile, sheet_path: str, shared_strings: list[str]) -> list[list[str]]:
    root = ET.fromstring(zip_file.read(sheet_path))
    rows: list[list[str]] = []

    for row in root.findall("main:sheetData/main:row", NS_MAIN):
        values_by_col: dict[int, str] = {}
        for cell in row.findall("main:c", NS_MAIN):
            ref = cell.attrib.get("r", "")
            if not ref:
                continue

            col_index = get_column_index(ref)
            cell_type = cell.attrib.get("t")

            value_node = cell.find("main:v", NS_MAIN)
            if value_node is None:
                inline = cell.find("main:is", NS_MAIN)
                if inline is not None:
                    value = "".join(t.text or "" for t in inline.findall(".//main:t", NS_MAIN))
                else:
                    value = ""
            else:
                raw = value_node.text or ""
                if cell_type == "s":
                    idx = int(raw)
                    value = shared_strings[idx] if 0 <= idx < len(shared_strings) else ""
                else:
                    value = raw

            values_by_col[col_index] = value

        if not values_by_col:
            continue

        max_col = max(values_by_col.keys())
        row_values = [values_by_col.get(i, "") for i in range(1, max_col + 1)]
        rows.append(row_values)

    return rows


def parse_box_number(raw: str) -> int | None:
    match = re.search(r"\d+", raw or "")
    return int(match.group()) if match else None


def split_compatibles(raw: str) -> list[str]:
    return [item.strip() for item in (raw or "").split(",") if item.strip()]


def main() -> None:
    with zipfile.ZipFile(XLSX_PATH) as zf:
        shared_strings: list[str] = []
        if "xl/sharedStrings.xml" in zf.namelist():
            shared_root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
            for si in shared_root.findall("main:si", NS_MAIN):
                shared_strings.append("".join(t.text or "" for t in si.findall(".//main:t", NS_MAIN)))

        workbook = ET.fromstring(zf.read("xl/workbook.xml"))
        rels = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
        rel_map = {
            rel.attrib["Id"]: rel.attrib["Target"]
            for rel in rels.findall("pkg:Relationship", NS_RELS)
        }

        sheet_paths: dict[str, str] = {}
        for sheet in workbook.findall("main:sheets/main:sheet", NS_MAIN):
            name = sheet.attrib.get("name", "")
            rid = sheet.attrib.get(RID_KEY, "")
            target = rel_map.get(rid, "")
            if target.startswith("/"):
                normalized = target.lstrip("/")
            else:
                normalized = f"xl/{target}"
            sheet_paths[name] = normalized

        tempered_rows = read_sheet_rows(zf, sheet_paths["Tempered Glass Models"], shared_strings)
        searchable_rows = read_sheet_rows(zf, sheet_paths["Searchable Models"], shared_strings)

    box_to_compatible: dict[int, list[str]] = {}
    for row in tempered_rows[1:]:
        if not row:
            continue
        box = parse_box_number(row[0] if len(row) > 0 else "")
        if box is None:
            continue
        additional = split_compatibles(row[2] if len(row) > 2 else "")
        box_to_compatible[box] = additional

    output: list[dict] = []
    for row in searchable_rows[1:]:
        model_name = (row[0] if len(row) > 0 else "").strip()
        box_raw = (row[1] if len(row) > 1 else "").strip()
        box = parse_box_number(box_raw)

        if not model_name or box is None:
            continue

        output.append(
            {
                "modelName": model_name,
                "boxNumber": box,
                "additionalCompatible": box_to_compatible.get(box, []),
            }
        )

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding="utf-8") as fp:
        json.dump(output, fp, ensure_ascii=False, indent=2)

    print(f"Generated {len(output)} records -> {OUT_PATH}")


if __name__ == "__main__":
    main()
