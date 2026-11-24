package com.example.data.catalog

data class OemRecoveryGuide(
    val id: String,
    val brand: String,
    val recoveryComboHe: String,
    val recoveryComboEn: String,
    val bootloaderComboHe: String,
    val bootloaderComboEn: String,
    val notesHe: String,
    val notesEn: String,
)

data class TroubleshootingItem(
    val id: String,
    val errorTitle: String,
    val causeHe: String,
    val causeEn: String,
    val fixHe: String,
    val fixEn: String,
)

object RecoveryGuidesCatalog {

    val oemGuides: List<OemRecoveryGuide> = listOf(
        OemRecoveryGuide(
            id = "samsung",
            brand = "Samsung Galaxy",
            recoveryComboHe = "חבר כבל USB-C למחשב/מכשיר מארח ← כבה לגמרי ← החזק Volume Up + Power עד להופעת לוגו Samsung.",
            recoveryComboEn = "Connect USB-C cable -> Power off -> Hold Volume Up + Power until Samsung logo appears.",
            bootloaderComboHe = "Download Mode (Odin): כבה לגמרי ← החזק Volume Up + Volume Down יחד תוך כדי חיבור כבל USB.",
            bootloaderComboEn = "Download Mode (Odin): Power off -> Hold Volume Up + Volume Down while plugging USB cable.",
            notesHe = "במכשירי Samsung מודרניים (One UI) נדרש חיבור USB פעיל כדי להיכנס ל-Recovery. אין Fastboot סטנדרטי אלא Download Mode.",
            notesEn = "Modern Samsung devices require a USB connection to enter Recovery and use Download Mode instead of standard Fastboot.",
        ),
        OemRecoveryGuide(
            id = "xiaomi",
            brand = "Xiaomi / Redmi / POCO",
            recoveryComboHe = "כבה מכשיר ← החזק Volume Up + Power עד להופעת תפריט Mi Recovery / TWRP.",
            recoveryComboEn = "Power off -> Hold Volume Up + Power until Mi Recovery or TWRP menu appears.",
            bootloaderComboHe = "Fastboot Mode: כבה מכשיר ← החזק Volume Down + Power עד להופעת לוגו FASTBOOT.",
            bootloaderComboEn = "Fastboot Mode: Power off -> Hold Volume Down + Power until FASTBOOT appears.",
            notesHe = "שחרר את כפתור ה-Power מיד כשהמסך נדלק והמשך להחזיק רק את כפתור הווליום עוד 2-3 שניות.",
            notesEn = "Release Power right after the screen turns on while continuing to hold Volume for 2-3 seconds.",
        ),
        OemRecoveryGuide(
            id = "pixel",
            brand = "Google Pixel / AOSP",
            recoveryComboHe = "היכנס ל-Bootloader (Volume Down + Power) ← בחר עם מקשי הווליום 'Recovery Mode' ← אשר עם Power. במסך 'No command' לחץ Power + הקשה קצרה על Volume Up.",
            recoveryComboEn = "Enter Bootloader (Vol Down + Power) -> Select 'Recovery Mode' -> Press Power. At 'No command', hold Power + tap Vol Up.",
            bootloaderComboHe = "כבה מכשיר ← החזק Volume Down + Power עד לכניסה למסך Fastboot.",
            bootloaderComboEn = "Power off -> Hold Volume Down + Power to enter Fastboot.",
            notesHe = "מכשירי Pixel משתמשים במחיצות A/B וב-fastbootd. לצריבת system/vendor נדרש fastbootd.",
            notesEn = "Pixel devices use A/B seamless partitions and userspace fastbootd for dynamic partitions.",
        ),
        OemRecoveryGuide(
            id = "oneplus",
            brand = "OnePlus / OPPO / realme",
            recoveryComboHe = "כבה מכשיר ← החזק Volume Down + Power עד לכניסה ל-Recovery.",
            recoveryComboEn = "Power off -> Hold Volume Down + Power to enter Recovery.",
            bootloaderComboHe = "כבה מכשיר ← החזק Volume Up + Volume Down + Power (או דרך adb reboot bootloader).",
            bootloaderComboEn = "Power off -> Hold Volume Up + Volume Down + Power (or adb reboot bootloader).",
            notesHe = "בדגמי ColorOS / OxygenOS חדשים יש להפעיל 'Disable Permission Monitoring' בהגדרות מפתחים לצורך פקודות ADB מתקדמות.",
            notesEn = "Newer OxygenOS/ColorOS builds may require enabling 'Disable Permission Monitoring' in Developer Options.",
        ),
        OemRecoveryGuide(
            id = "motorola",
            brand = "Motorola",
            recoveryComboHe = "החזק Volume Down + Power לכניסה ל-Bootloader ← נווט בווליום ל-Recovery Mode ולחץ Power.",
            recoveryComboEn = "Hold Volume Down + Power for Bootloader -> Navigate to Recovery Mode and press Power.",
            bootloaderComboHe = "כבה מכשיר ← החזק Volume Down + Power.",
            bootloaderComboEn = "Power off -> Hold Volume Down + Power.",
            notesHe = "פתיחת Bootloader במוטורולה מתבצעת עם fastboot oem get_unlock_data.",
            notesEn = "Motorola bootloader unlock uses fastboot oem get_unlock_data.",
        ),
        OemRecoveryGuide(
            id = "nothing",
            brand = "Nothing Phone",
            recoveryComboHe = "היכנס ל-Bootloader (Volume Down + Power) ← בחר Recovery Mode ← במסך No Command לחץ Power + Volume Up.",
            recoveryComboEn = "Enter Bootloader (Vol Down + Power) -> Select Recovery Mode -> At No Command press Power + Vol Up.",
            bootloaderComboHe = "כבה מכשיר ← החזק Volume Down + Power.",
            bootloaderComboEn = "Power off -> Hold Volume Down + Power.",
            notesHe = "תמיכה מלאה ב-Fastboot תקני ובמחיצות A/B.",
            notesEn = "Supports standard Fastboot and A/B partition slots.",
        ),
        OemRecoveryGuide(
            id = "mediatek",
            brand = "MediaTek (MTK Helio / Dimensity)",
            recoveryComboHe = "כבה מכשיר ← החזק Volume Up + Power (בחלק מהדגמים מופיע תפריט Select Boot Mode עם חצי ווליום לבחירת Recovery).",
            recoveryComboEn = "Power off -> Hold Volume Up + Power (some MTK bootloaders show a 3-line Boot Mode menu: Recovery / Fastboot / Normal).",
            bootloaderComboHe = "Fastboot: Volume Down + Power או בחירה ב-Fastboot בתפריט MTK. BROM/Preloader: חיבור USB עם החזקת Vol+ / Vol- בזמן כיבוי.",
            bootloaderComboEn = "Fastboot: Vol Down + Power or MTK Boot Mode menu. BROM: hold Vol+/Vol- while plugging USB when powered off.",
            notesHe = "במכשירי MediaTek ישנים קיימת כותרת 512-byte (0x58881688), ובדגמים חדשים ה-Recovery נמצא לעיתים בתוך boot.img (Recovery-as-Boot) או vendor_boot.img. הפאטצ'ר המובנה באפליקציה מזהה את שניהם אוטומטית.",
            notesEn = "Older MTK images include a 512-byte MTK header (0x58881688), while newer MTK devices pack recovery inside boot.img or vendor_boot.img. The built-in patcher handles both.",
        ),
    )

    val troubleshooting: List<TroubleshootingItem> = listOf(
        TroubleshootingItem(
            id = "unauth",
            errorTitle = "unauthorized / ממתין לאישור RSA",
            causeHe = "מכשיר היעד עדיין לא אישר את טביעת האצבע של מפתח ה-RSA של האפליקציה.",
            causeEn = "Target device has not yet accepted this app's RSA public key prompt.",
            fixHe = "הדלק את מסך מכשיר היעד, אשר את חלונית 'Allow USB debugging' (סמן Always allow) ונסה להתחבר שוב.",
            fixEn = "Wake the target screen, accept 'Allow USB debugging' (check Always allow), and reconnect.",
        ),
        TroubleshootingItem(
            id = "offline",
            errorTitle = "device offline / Timeout",
            causeHe = "חיבור ה-USB OTG רופף, כבל טעינה בלבד ללא קווי דאטה, או שפורט 5555 לא נפתח במכשיר היעד.",
            causeEn = "Charge-only cable, loose OTG adapter, or TCP port 5555 is not listening on target.",
            fixHe = "החלף לכבל OTG עם תמיכת דאטה, העבר מצב USB ל-PTP/File Transfer, או הפעל במכשיר היעד את ADB over Wi-Fi / tcpip 5555.",
            fixEn = "Use a data-capable OTG cable, switch USB mode to File Transfer/PTP, or enable TCP/IP 5555.",
        ),
        TroubleshootingItem(
            id = "usb_perm",
            errorTitle = "לא מזוהה התקן USB OTG",
            causeHe = "OTG לא מופעל במכשיר המארח (נפוץ ב-OPPO/realme/OnePlus) או שהרשאת ה-USB לא אושרה.",
            causeEn = "OTG toggle disabled in host Settings (common on OPPO/realme/OnePlus) or USB permission denied.",
            fixHe = "הפעל 'OTG Connection' בהגדרות המכשיר המארח ולחץ על 'סרוק התקני USB' ובקשת ההרשאה.",
            fixEn = "Enable 'OTG Connection' in host device Settings, then scan USB devices and grant permission.",
        ),
        TroubleshootingItem(
            id = "ab_bootloop",
            errorTitle = "Bootloop במכשירי A/B לאחר עדכון",
            causeHe = "חריץ האתחול הפעיל פגום בעוד החריץ המשני עדיין תקין.",
            causeEn = "Active A/B slot fails to boot while the inactive slot still contains a working OS.",
            fixHe = "היכנס ל-Fastboot, בדוק איזה חריץ פעיל (`getvar current-slot`) והחלף לחריץ השני (`set_active a` או `set_active b`).",
            fixEn = "In Fastboot, check `getvar current-slot` and switch to the alternate slot with `set_active`.",
        ),
    )
}
