# Devir Raporu — Zenvex Online (yeni oturum için)

Hazırlanma: 12 Kasım 2026. Bu belge, projeyi hiç görmemiş yeni bir oturumun kaldığımız yerden sürmesi için yazıldı.
Kullanıcıyla Türkçe konuş. Depo: GitHub `eaquelxonline/Zenvex_Online` (13 Kasım 2026'da zip'ten temiz geçmişle aktarıldı;
eski geçmiş yalnız Drive'daki zip'te). Kullanıcı kuralı: sürüm bilgisi için her zaman internete/kayıtlara bak, eğitim verisine güvenme.

## 1. Proje
- Android (Kotlin, Compose + özel Canvas oyun motoru) + Rust çekirdeği (`rust/zenvex_core`, JNI: `zenvex_jni`, 3B: `zenvex_r3d`,
  sunucu: `zenvex_server`) + Supabase (proje `blvstsvmvmdydyrnecpo`: veritabanı, edge fonksiyonları).
- Hayatta kalma / zombi / ortak dünya oyunu. 4 karakter (Kael, Lyra, Vex, Morvath), 3B modeller, bölgeler, tezgâh üretimi,
  kişisel üs, klan, etkinlikler, sezon bileti, mağaza (Google Play), destek bileti, yönetici paneli.
- Ayrıntı: `docs/00_GENEL_BAKIS.md`, `docs/04_TEKNIK_MIMARI.md`, `docs/09_SUPABASE.md`, `CHANGELOG.md` (en üst: V21, V20…).

## 2. Kullanıcının değişmez kuralları
- Yönetici kimliği 0; anahtarı yalnız bcrypt özeti olarak tutulur. Google client secret'ı asla kullanma.
- Görsel değişiklikleri doğrula (Paparazzi anlık görüntüleri / ekran testi). Alt ajan (subagent) kullanma (istemedikçe).
- Commit/koda model adı yazma. Commit sonuna şu iki satır: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` ve
  `Claude-Session: <oturum bağlantısı>`.
- Her commit'ten önce `git checkout android/app/save_n1.json`.
- Yasal göç (`20261122_legal_compliance.sql`) ve `zx-legal` fonksiyonu için üretime uygulamadan ÖNCE kullanıcıya sor.
- Üretim veritabanına göç/edge dağıtımı: araç izin vermeyebilir (otomatik mod reddetti); kullanıcıdan izin iste.

## 3. Derleme ve test komutları
- Araç zinciri (V22, 13 Kasım 2026'da kayıtlardan doğrulandı): JDK 25 (Temurin), Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21,
  SDK 37 + build-tools 37.0.0, targetSdk 37 (appCategory=game), NDK 30.0.16248370, Rust 1.100 (edition 2024), jni 0.22.4, cargo-ndk 4.1.2.
  Sürüm denetimi: Gradle `services.gradle.org/versions/current`, Google Maven / Maven Central `maven-metadata.xml`,
  `crates.io/api/v1/crates/<ad>`, SDK paketleri `dl.google.com/android/repository/repository2-3.xml`.
- GameShotsTest referans görüntüleri `.gitignore`'da (yalnız 9 tanesi depoda); `verifyPaparazziDebug` bu yüzden GameShotsTest'te
  hep düşer. Doğrulama için HudShotsTest/UiShotsTest/UiArtShotsTest'e bak, oyun karelerini `app/build/game-shots/` altında incele.
- Rust: `cd rust/zenvex_core && cargo test` (87 test geçiyor). JNI: `cargo build -p zenvex_jni --release`.
- Android sıkı derleme: `cd android && CI=true ./gradlew :app:compileDebugKotlin -x cargoBuild` (uyarı = hata). Testler:
  `:app:compileDebugUnitTestKotlin`; Paparazzi: `GameShotsTest`, `HudShotsTest`, `UiShotsTest`.
- i18n: `python3 tools/i18n/extract.py` (hedef: tüm dillerde 0 eksik; 9 dil: en es pt ru de fr it id ar). Eksikleri çevirip
  `android/app/src/main/assets/i18n/<dil>.json` içine ekle (yer tutucular `{0}` korunmalı). Şu an 0 eksik.
- Yerel Postgres (SQL denemeleri için) gerekirse `/tmp/pgzx` altında yeniden başlatılır.
- Görsel üretim istemleri: `docs/28_*` (V19) ve `docs/29_GORSEL_PROMPTLARI_V20.md`; gelen görseller `assets/ui/<ad>.png` olarak
  konunca kod değişmeden kullanılır (`btn_skills`, `btn_chars`, `btn_crate`, `btn_shop`, `skill_<istatistik>`).

## 4. V22 (13 Kasım 2026): araç zinciri yenileme — ayrıntı CHANGELOG'da. Dal: `ccr-1288dc35-tplllu`.

## 4b. Önceki oturum (V21; yerel commit'ler, eski geçmişte)
Commit sırası (eskiden yeniye): 11c94bc (M1+M2) → cabdde1 (M3 araçlar) → a273200 (M4 destek/yönetici) → e524c5f (M5 karakter kimlikleri,
görsel kancaları, V20 prompt belgesi) → 0b2ef50 (V21). Depo bu zip'te; `git log` ile görülür. İtme: yeni bir GitHub deposu açılırsa
`git remote set-url origin <yeni>` ve `git push -u origin <dal>` (dal: `ccr-f72bf0e8-k8itdv`).

V21 (kullanıcının 17 maddelik listesi) — hepsi kodlandı:
1. Üretim ekranı malzeme değişimini 0,7 sn'de bir yeniler (`Screens.kt` CraftScreen `qtick`) → çık-gir gerekmez.
2. El üretimi ve tezgâh aynı tarif listesini gösterir (`ui.rs` craft/craft_bench filtresi kalktı; tezgâh tarifi elde "tezgâh gerekli").
3. Tezgâha Uzi, AK-47, Keskin Nişancı, RPG-7 tarifleri eklendi (`data.rs` RECIPES). Etkinlik silahları hariç tüm silahlar yapılabilir
   (Polis Copu ve M4 oyundan kaldırılmış durumda, RETIRED_ITEMS).
4. Etkinlik ödül afiş sayfası (`PosterView`) ve 18 `art_ev_*.webp` silindi. (`tools/pixel/event_import.py` hâlâ posters() içerir; ölü kod.)
5. Harita bulutları zamanla kaymıyor, haritaya sabit (`WorldMap.kt` drawClouds).
6/15. Eski gizemli ağaç: rastgele orman ağaçlarından `oak_autumn` çıkarıldı (RNG sırası korunarak `oak1`). Yeni gizemli ağaç
   `LANDMARKS "tree"` (232,262 karo) konumunda Deco olarak durur. Kullanıcı "X184 Y243" gördüğünü söyledi; bu `oak_autumn` olabilir —
   yeni sürümde hâlâ görülürse o koordinatın statiğini incele (`world.rs` push → sprite adı).
7. Motor simgesi artık gerçek Nightstalker modelinin karartılmışı (`VehicleUi.kt` MotoIcon).
8. Rehber görev yazısı sol üstte (`Tutorial.kt` GuideBubble), süresiz kalır (`GameView.kt` tutAlpha).
9. Profil: fotoğraf varken karakter resmi seçmek artık fotoğrafın yerine geçer (Kaydet'te fotoğraf silinir); varsayılan resim oyundaki
   karakterin portresi (eskiden Marco çıkıyordu). NOT: oyun içi sol üst portre hâlâ seçilen karakteri gösterir; kullanıcı NPC
   resmi seçtiyse oyun içinde yine karakter portresi görünür (istenirse HUD'a ProfileAvatar bindirilebilir).
10. Yerdeki tüm sandıklar (`Kind::Crate`, `chest_*`) çimene dönüştü (`world.rs` push başı). Depo sandıkları yalnız kişisel üste.
11. Rehber "İlk Silah": Bıçak yerine Sopa (`w_bat`; `story.rs` g4f/g4g, `Bag.kt`, `GameView.kt` metinleri, `training.rs` testi).
12. Haritada NPC'nin 10 karo yakınındaki yer imi çizilmez (`WorldMap.kt` mapMarks).
13. Arka planda 30 sn sayaç (`Session.kt` BG_LIMIT_MS, `bgKick`); süre dolmadan dönülürse oyun kesintisiz sürer.
14. Kopma sonrası girişte kampa ışınlanma (`Session.takeLost` → `GameSession.cmd("to_camp")`, `ui.rs` "to_camp").
16. Zombi yakınken ağaç/kaya vuruşu engeli kaldırıldı (`combat.rs`).
17. Yönetici etkinlik sıralamasından çıkarıldı: `supabase/migrations/20261124_v21_fixes.sql` (ÜRETİME UYGULANMADI, aşağıya bak).
18. (Kullanıcı boş bıraktı.)

M5 (karakter kimlikleri): `data.rs` CHAR_BASE (Kael hız/kaçınma, Lyra kritik/hız az can, Vex tank, Morvath hasar/can çalma) +
`char_stats()`; `rpg.rs` bonus uygular; `progress.rs` max_hp hp_pct ile; `Characters.kt` değerleri gösterir.

## 5. Üretim (Supabase) durumu
- Uygulandı: göçler V20'ye kadar, `v20_fixes` (zx_photos, set_level/set_char_level, zx_save3 admin_level izni, zx_admin_support photos);
  edge `zx-support` v2 (id 0 düzeltmesi) dağıtıldı.
- BEKLEYEN (hiçbiri uygulanmadı):
  - `20261124_v21_fixes.sql` (yönetici etkinlik sıralaması) — kullanıcı onayıyla uygula.
  - `20261122_legal_compliance.sql` + `zx-legal` edge fonksiyonu — kullanıcıya sormadan uygulama (kural).
  - `20261120_trade_expect.sql`.
  - `zx-billing` edge düzeltmesi (`id == null`) dağıtılmadı.
- Yayın öncesi (kodla çözülmeyen): `docs/teknik/HUKUKI_UYUM.md` baştaki 7 madde (künye, KVKK m.9 bildirimi, VERBİS/ETBİS, Play Veri Güvenliği…).

## 6. Bilinen eksikler / sonraki adımlar
1. Doğrulanmadı: bu oturumun son değişiklikleri (profil seçimi, bağlantı sayacı, rehber balonu, motor simgesi) için Paparazzi
   anlık görüntüsü ya da cihaz denemesi yapılmadı; yalnız derleme + Rust testleri geçti. Yeni oturumda GameShotsTest/HudShotsTest/
   UiShotsTest çalıştırıp görsel kontrol et (özellikle motor simgesi ve sol üst rehber balonunun HUD ile çakışmaması).
2. Kullanıcı görselleri `docs/29` istemlerinden üretip getirirse `assets/ui/` altına koy (kod hazır).
3. Üretim göçlerini kullanıcı onayıyla uygula (yukarıdaki liste).
4. GitHub: depo bağlı (`eaquelxonline/Zenvex_Online`); açık PR yok.
5. Rehber tutorial'ı el üretiminde tezgâh tarifleri görünür olduğundan `Tut.craftWant()`/`wrongPlace` mantığını bir kez gözden geçir.

## 7. Dosya haritası (en çok dokunulanlar)
- Rust: `data.rs` (eşya, tarif, karakter, görev tanımları), `ui.rs` (arayüz JSON'ları + komutlar), `world.rs` (dünya üretimi),
  `story.rs` (rehber ve ana görevler), `combat.rs`, `vehicles.rs`, `progress.rs`, `rpg.rs`, `game.rs`.
- Kotlin: `ui/Bag.kt`, `ui/Screens.kt` (üretim, tüccar), `ui/WorldMap.kt`, `ui/WizardMap.kt`, `ui/Profile.kt`, `ui/Tutorial.kt`,
  `ui/VehicleUi.kt`, `ui/Characters.kt`, `ui/TreeScreen.kt`, `ui/EventScreen.kt`, `game/Session.kt`, `game/GameView.kt` (HUD, çizim),
  `game/RegionMap.kt`, `game/Model3D.kt`, `net/*`.
- Sunucu: `supabase/migrations/*.sql`, `supabase/functions/*` (zx-support, zx-billing, zx-legal…).
