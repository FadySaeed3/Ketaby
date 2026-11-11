"""Stand-in PDF for the emulator smoke test: numbered blank pages with ruled lines, no lexicon text."""
import sys
import pymupdf
name, pages, w, h, out = sys.argv[1], int(sys.argv[2]), float(sys.argv[3]), float(sys.argv[4]), sys.argv[5]
doc = pymupdf.open()
for i in range(pages):
    p = doc.new_page(width=w, height=h)
    p.insert_text((40, 40), f"{name.upper()} stand-in, page {i + 1}", fontsize=14)
    y = 70
    while y < h - 40:
        p.draw_line((40, y), (w - 40, y), color=(0.6, 0.6, 0.6), width=0.5)
        y += 18
doc.save(out)
