#!/usr/bin/env python3
"""Фільтрує повний UFOP XML-дамп з data.gov.ua у компактний CSV для імпорту в DMS Scout.

Використання (на ПК, як з ЄДР-пошуковиком):
    python3 prepare_data.py 17.1-EX_XML_EDR_UO_FULL.xml edrpou_list.txt contacts.csv

edrpou_list.txt — по одному ЄДРПОУ на рядок. Без списку (тільки 2 аргументи) — витягне ВСІ записи з контактами (великий файл).
"""
import csv
import sys

from lxml import etree

def text(el, tag):
    f = el.find(tag)
    return (f.text or "").strip() if f is not None and f.text else ""

def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    xml_path, out_path = sys.argv[1], sys.argv[-1]
    wanted = None
    if len(sys.argv) == 4:
        with open(sys.argv[2], encoding="utf-8") as f:
            wanted = {line.strip().lstrip("0") for line in f if line.strip()}
        print(f"Шукаю {len(wanted)} ЄДРПОУ…")

    n_total = n_hit = 0
    with open(out_path, "w", newline="", encoding="utf-8") as out:
        w = csv.writer(out)
        # Додано "boss"
        w.writerow(["edrpou", "name", "phone", "email", "address", "boss"])
        
        for _, el in etree.iterparse(xml_path, events=("end",), tag=("SUBJECT", "RECORD")):
            n_total += 1
            edrpou = text(el, "EDRPOU") or text(el, "CODE")
            if wanted is None or (edrpou and edrpou.lstrip("0") in wanted):
                name = text(el, "NAME") or text(el, "SHORT_NAME")
                contacts = el.find("CONTACTS")
                src = contacts if contacts is not None else el
                phones = "; ".join(t.text.strip() for t in src.findall("TEL") if t.text)
                email = text(src, "EMAIL")
                address = text(el, "ADDRESS")
                boss = text(el, "BOSS")  # Витягуємо керівника з ЄДР
                
                # Записуємо, якщо є будь-які корисні контакти або директор
                if wanted is not None or phones or email or boss:
                    w.writerow([edrpou, name, phones, email, address, boss])
                    n_hit += 1
            el.clear()
            while el.getprevious() is not None:
                del el.getparent()[0]
            if n_total % 200_000 == 0:
                print(f"  оброблено {n_total:,}, знайдено {n_hit}")
    print(f"Готово: {n_hit} записів → {out_path}")

if __name__ == "__main__":
    main()
