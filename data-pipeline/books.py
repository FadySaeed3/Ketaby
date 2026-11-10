"""Book table: STEPBible code, OSIS code, Arabic name (Van Dyke usage), testament."""

BOOKS = [
    ('Gen', 'Gen', 'التكوين', 'OT'), ('Exo', 'Exod', 'الخروج', 'OT'), ('Lev', 'Lev', 'اللاويين', 'OT'),
    ('Num', 'Num', 'العدد', 'OT'), ('Deu', 'Deut', 'التثنية', 'OT'), ('Jos', 'Josh', 'يشوع', 'OT'),
    ('Jdg', 'Judg', 'القضاة', 'OT'), ('Rut', 'Ruth', 'راعوث', 'OT'), ('1Sa', '1Sam', 'صموئيل الأول', 'OT'),
    ('2Sa', '2Sam', 'صموئيل الثاني', 'OT'), ('1Ki', '1Kgs', 'الملوك الأول', 'OT'), ('2Ki', '2Kgs', 'الملوك الثاني', 'OT'),
    ('1Ch', '1Chr', 'أخبار الأيام الأول', 'OT'), ('2Ch', '2Chr', 'أخبار الأيام الثاني', 'OT'), ('Ezr', 'Ezra', 'عزرا', 'OT'),
    ('Neh', 'Neh', 'نحميا', 'OT'), ('Est', 'Esth', 'أستير', 'OT'), ('Job', 'Job', 'أيوب', 'OT'),
    ('Psa', 'Ps', 'المزامير', 'OT'), ('Pro', 'Prov', 'الأمثال', 'OT'), ('Ecc', 'Eccl', 'الجامعة', 'OT'),
    ('Sng', 'Song', 'نشيد الأنشاد', 'OT'), ('Isa', 'Isa', 'إشعياء', 'OT'), ('Jer', 'Jer', 'إرميا', 'OT'),
    ('Lam', 'Lam', 'مراثي إرميا', 'OT'), ('Ezk', 'Ezek', 'حزقيال', 'OT'), ('Dan', 'Dan', 'دانيال', 'OT'),
    ('Hos', 'Hos', 'هوشع', 'OT'), ('Jol', 'Joel', 'يوئيل', 'OT'), ('Amo', 'Amos', 'عاموس', 'OT'),
    ('Oba', 'Obad', 'عوبديا', 'OT'), ('Jon', 'Jonah', 'يونان', 'OT'), ('Mic', 'Mic', 'ميخا', 'OT'),
    ('Nam', 'Nah', 'ناحوم', 'OT'), ('Hab', 'Hab', 'حبقوق', 'OT'), ('Zep', 'Zeph', 'صفنيا', 'OT'),
    ('Hag', 'Hag', 'حجي', 'OT'), ('Zec', 'Zech', 'زكريا', 'OT'), ('Mal', 'Mal', 'ملاخي', 'OT'),
    ('Mat', 'Matt', 'متى', 'NT'), ('Mrk', 'Mark', 'مرقس', 'NT'), ('Luk', 'Luke', 'لوقا', 'NT'),
    ('Jhn', 'John', 'يوحنا', 'NT'), ('Act', 'Acts', 'أعمال الرسل', 'NT'), ('Rom', 'Rom', 'رومية', 'NT'),
    ('1Co', '1Cor', 'كورنثوس الأولى', 'NT'), ('2Co', '2Cor', 'كورنثوس الثانية', 'NT'), ('Gal', 'Gal', 'غلاطية', 'NT'),
    ('Eph', 'Eph', 'أفسس', 'NT'), ('Php', 'Phil', 'فيلبي', 'NT'), ('Col', 'Col', 'كولوسي', 'NT'),
    ('1Th', '1Thess', 'تسالونيكي الأولى', 'NT'), ('2Th', '2Thess', 'تسالونيكي الثانية', 'NT'),
    ('1Ti', '1Tim', 'تيموثاوس الأولى', 'NT'), ('2Ti', '2Tim', 'تيموثاوس الثانية', 'NT'), ('Tit', 'Titus', 'تيطس', 'NT'),
    ('Phm', 'Phlm', 'فليمون', 'NT'), ('Heb', 'Heb', 'العبرانيين', 'NT'), ('Jas', 'Jas', 'يعقوب', 'NT'),
    ('1Pe', '1Pet', 'بطرس الأولى', 'NT'), ('2Pe', '2Pet', 'بطرس الثانية', 'NT'), ('1Jn', '1John', 'يوحنا الأولى', 'NT'),
    ('2Jn', '2John', 'يوحنا الثانية', 'NT'), ('3Jn', '3John', 'يوحنا الثالثة', 'NT'), ('Jud', 'Jude', 'يهوذا', 'NT'),
    ('Rev', 'Rev', 'رؤيا يوحنا', 'NT'),
]
STEP2OSIS = {s: o for s, o, _, _ in BOOKS}
OSIS2ID = {o: i + 1 for i, (_, o, _, _) in enumerate(BOOKS)}
STEP2ID = {s: i + 1 for i, (s, _, _, _) in enumerate(BOOKS)}
