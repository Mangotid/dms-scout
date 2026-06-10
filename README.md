# DMS Scout — Android APK

Офлайн Android-додаток для пошуку контактів компаній і ведення ДМС-обдзвону.
Дані живуть локально на планшеті (localStorage WebView), інтернет потрібен лише для відкриття лінків на реєстри.

## Збірка APK (без Android Studio)

1. Створи приватний репозиторій на GitHub, запуш цей проєкт:
   ```bash
   git init && git add . && git commit -m "DMS Scout"
   git remote add origin git@github.com:USER/dms-scout.git
   git push -u origin main
   ```
2. GitHub → Actions → workflow "Build APK" відпрацює автоматично (~3 хв).
3. Завантаж артефакт `dms-scout-debug-apk`, скинь `app-debug.apk` на планшет, встанови
   (дозволь установку з невідомих джерел).

Локальна збірка (якщо є Android SDK): `gradle assembleDebug`.

## Дані з data.gov.ua

1. Завантаж повний XML-дамп ЄДР (UFOP) з data.gov.ua.
2. Зроби `edrpou_list.txt` — ЄДРПОУ цільових компаній построчно (експортуй з додатку або візьми з Excel).
3. На ПК: `python3 prepare_data.py дамп.xml edrpou_list.txt contacts.csv`
   (потрібен lxml: `pip install lxml --break-system-packages`).
   Якщо структура тегів у свіжому дампі інша — поправ функцію `main` під фактичні теги (відкрий перші ~200 рядків дампу і звір).
4. Скинь `contacts.csv` на планшет → в додатку «Імпорт CSV». Телефони/email/адреси
   підтягнуться до існуючих карток по ЄДРПОУ, ЄДРПОУ позначиться як перевірений.

## Що всередині

- 43 defense-tech компанії вшиті; «⚠ перевірити» = код відновлений зі скріншота низької якості
- Пошук, фільтри по статусах воронки, нотатки
- Картка: лінки на YouControl / OpenDataBot / Clarity / Ring / Google / LinkedIn / Facebook / robota.ua / DOU — відкриваються в браузері планшета
- 📞 і ✉ — системний дзвонилка/пошта одним тапом
- Експорт CSV у Download/ (відкривається в Excel, BOM для кирилиці)

## Мінімальні вимоги
Android 10+ (API 29). Дані переживають перезапуск; видалення додатку стирає базу — роби експорт CSV як бекап.
