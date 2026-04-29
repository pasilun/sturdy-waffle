#!/usr/bin/env python3
"""Generate a PDF invoice from a JSON spec.

Usage:
    python3 scripts/gen_invoice.py scripts/fixtures/rent.json
    python3 scripts/gen_invoice.py scripts/fixtures/  # regenerate all
"""

import json
import sys
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer, HRFlowable
)
from reportlab.lib.enums import TA_RIGHT, TA_LEFT

FIXTURES_OUT = Path(__file__).parent.parent / "api/src/eval/fixtures"

# ── Colours ──────────────────────────────────────────────────────────────────
DARK = colors.HexColor("#1a1a2e")
MID  = colors.HexColor("#4a4a6a")
LITE = colors.HexColor("#f4f4f8")
LINE = colors.HexColor("#d0d0e0")
ACC  = colors.HexColor("#2563eb")


def _dec(v) -> Decimal:
    return Decimal(str(v)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def _fmt(v) -> str:
    return f"{_dec(v):,.2f}".replace(",", " ")  # thin-space thousands sep


def load_spec(path: Path) -> dict:
    with open(path) as f:
        spec = json.load(f)

    vat_rate = Decimal(str(spec["vat_rate"]))
    net = Decimal("0")
    for line in spec["lines"]:
        line["line_net"] = _dec(Decimal(str(line["qty"])) * Decimal(str(line["unit_price"])))
        net += line["line_net"]

    vat   = (net * vat_rate).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    gross = net + vat

    spec["net"]   = net
    spec["vat"]   = vat
    spec["gross"] = gross
    spec["vat_pct"] = int(vat_rate * 100)
    return spec


def validate(spec: dict, path: Path):
    errors = []
    if "netTotal" in spec:
        if _dec(spec["net"]) != _dec(spec["netTotal"]):
            errors.append(f"net mismatch: computed {spec['net']} vs spec {spec['netTotal']}")
    if errors:
        raise ValueError(f"{path}: " + "; ".join(errors))


def build_pdf(spec: dict, out_path: Path):
    doc = SimpleDocTemplate(
        str(out_path),
        pagesize=A4,
        leftMargin=20*mm, rightMargin=20*mm,
        topMargin=20*mm, bottomMargin=20*mm,
    )

    styles = getSampleStyleSheet()
    h1 = ParagraphStyle("h1", fontSize=22, textColor=DARK, spaceAfter=2,
                        fontName="Helvetica-Bold")
    h2 = ParagraphStyle("h2", fontSize=10, textColor=MID, spaceAfter=1,
                        fontName="Helvetica")
    body = ParagraphStyle("body", fontSize=9, textColor=DARK, leading=13,
                          fontName="Helvetica")
    small = ParagraphStyle("small", fontSize=8, textColor=MID, leading=11,
                           fontName="Helvetica")
    right = ParagraphStyle("right", fontSize=9, textColor=DARK, leading=13,
                           alignment=TA_RIGHT, fontName="Helvetica")
    right_bold = ParagraphStyle("right_bold", fontSize=9, textColor=DARK,
                                leading=13, alignment=TA_RIGHT,
                                fontName="Helvetica-Bold")
    label = ParagraphStyle("label", fontSize=8, textColor=MID, leading=10,
                           fontName="Helvetica")

    sup = spec["supplier"]
    buy = spec["buyer"]
    story = []

    # ── Header ────────────────────────────────────────────────────────────────
    header_data = [
        [Paragraph("FAKTURA", h1), ""],
        [Paragraph(sup["name"], h2), ""],
    ]
    for line in sup["address"]:
        header_data.append([Paragraph(line, small), ""])
    header_data.append([Paragraph(f"Org.nr: {sup['org_nr']}", small), ""])

    # Right column: invoice meta
    meta = [
        ["Fakturanummer:", spec["invoice_number"]],
        ["Fakturadatum:",  spec["invoice_date"]],
        ["Förfallodatum:", spec["due_date"]],
        ["Valuta:",        spec["currency"]],
    ]
    meta_table = Table(meta, colWidths=[35*mm, 45*mm])
    meta_table.setStyle(TableStyle([
        ("FONTNAME",  (0, 0), (-1, -1), "Helvetica"),
        ("FONTSIZE",  (0, 0), (-1, -1), 8),
        ("TEXTCOLOR", (0, 0), (0, -1), MID),
        ("TEXTCOLOR", (1, 0), (1, -1), DARK),
        ("ALIGN",     (1, 0), (1, -1), "RIGHT"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
    ]))

    # Two-column top layout
    top_table = Table(
        [[
            [Paragraph("FAKTURA", h1),
             Paragraph(sup["name"], h2),
             *[Paragraph(l, small) for l in sup["address"]],
             Paragraph(f"Org.nr: {sup['org_nr']}", small)],
            meta_table,
        ]],
        colWidths=[100*mm, 80*mm],
    )
    top_table.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
    ]))
    story.append(top_table)
    story.append(Spacer(1, 6*mm))
    story.append(HRFlowable(width="100%", thickness=1, color=ACC))
    story.append(Spacer(1, 4*mm))

    # ── Buyer block ───────────────────────────────────────────────────────────
    story.append(Paragraph("Faktureras till", label))
    story.append(Paragraph(buy["name"], body))
    for line in buy["address"]:
        story.append(Paragraph(line, body))
    story.append(Spacer(1, 6*mm))

    # ── Line items table ──────────────────────────────────────────────────────
    col_w = [85*mm, 15*mm, 12*mm, 25*mm, 30*mm]
    thead = [
        Paragraph("Beskrivning", label),
        Paragraph("Antal", label),
        Paragraph("Enhet", label),
        Paragraph("À-pris", label),
        Paragraph("Belopp", label),
    ]
    rows = [thead]
    for line in spec["lines"]:
        rows.append([
            Paragraph(line["description"], body),
            Paragraph(str(line["qty"]), right),
            Paragraph(line["unit"], body),
            Paragraph(_fmt(line["unit_price"]), right),
            Paragraph(_fmt(line["line_net"]), right),
        ])

    line_table = Table(rows, colWidths=col_w, repeatRows=1)
    line_table.setStyle(TableStyle([
        # Header row
        ("BACKGROUND",    (0, 0), (-1, 0), LITE),
        ("FONTNAME",      (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE",      (0, 0), (-1, 0), 8),
        ("BOTTOMPADDING", (0, 0), (-1, 0), 5),
        ("TOPPADDING",    (0, 0), (-1, 0), 5),
        # Data rows
        ("FONTNAME",      (0, 1), (-1, -1), "Helvetica"),
        ("FONTSIZE",      (0, 1), (-1, -1), 9),
        ("BOTTOMPADDING", (0, 1), (-1, -1), 4),
        ("TOPPADDING",    (0, 1), (-1, -1), 4),
        ("ROWBACKGROUNDS",(0, 1), (-1, -1), [colors.white, LITE]),
        # Grid
        ("LINEBELOW",     (0, 0), (-1, 0), 0.5, LINE),
        ("LINEBELOW",     (0, -1), (-1, -1), 0.5, LINE),
        ("VALIGN",        (0, 0), (-1, -1), "MIDDLE"),
    ]))
    story.append(line_table)
    story.append(Spacer(1, 4*mm))

    # ── Totals block ──────────────────────────────────────────────────────────
    totals = [
        ["Netto (exkl. moms):", _fmt(spec["net"])],
        [f"Moms {spec['vat_pct']}%:", _fmt(spec["vat"])],
        ["", ""],
        ["Att betala (inkl. moms):", _fmt(spec["gross"])],
    ]
    tot_table = Table(totals, colWidths=[60*mm, 30*mm])
    tot_table.setStyle(TableStyle([
        ("FONTNAME",      (0, 0), (-1, -1), "Helvetica"),
        ("FONTSIZE",      (0, 0), (-1, -1), 9),
        ("TEXTCOLOR",     (0, 0), (0, -1), MID),
        ("TEXTCOLOR",     (1, 0), (1, -1), DARK),
        ("ALIGN",         (1, 0), (1, -1), "RIGHT"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        # Final row bold
        ("FONTNAME",      (0, 3), (-1, 3), "Helvetica-Bold"),
        ("FONTSIZE",      (0, 3), (-1, 3), 10),
        ("TEXTCOLOR",     (0, 3), (1, 3), DARK),
        ("LINEABOVE",     (0, 3), (-1, 3), 0.5, LINE),
        ("TOPPADDING",    (0, 3), (-1, 3), 5),
    ]))

    # Right-align the totals block
    wrapper = Table([[None, tot_table]], colWidths=[80*mm, 90*mm])
    wrapper.setStyle(TableStyle([
        ("VALIGN",       (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING",  (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
    ]))
    story.append(wrapper)
    story.append(Spacer(1, 8*mm))
    story.append(HRFlowable(width="100%", thickness=0.5, color=LINE))
    story.append(Spacer(1, 3*mm))
    story.append(Paragraph("Betalning: Bankgiro 123-4567  ·  Swish 1234567890  ·  30 dagars nettokassa",
                            small))

    doc.build(story)


def process(path: Path):
    spec = load_spec(path)
    validate(spec, path)
    out = FIXTURES_OUT / spec["output"]
    build_pdf(spec, out)
    print(f"  wrote {out}  (net={_fmt(spec['net'])} {spec['currency']},"
          f" {len(spec['lines'])} line(s))")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    target = Path(sys.argv[1])
    if target.is_dir():
        specs = sorted(target.glob("*.json"))
        if not specs:
            print(f"No *.json files found in {target}")
            sys.exit(1)
        for s in specs:
            process(s)
    else:
        process(target)


if __name__ == "__main__":
    main()
