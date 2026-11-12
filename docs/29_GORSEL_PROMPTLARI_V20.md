# Görsel Prompt'ları (V20) — Ana Menü Düğmeleri ve Yetenek Ağacı

Ortak kurallar (V19 belgesiyle aynı):

- Düz **#00FF00** yeşil zemin (arka plan hariç), yazı / harf / rakam yok, filigran yok.
- En az **1024×1024**; nesne kadrajın ~%80'i, ortada, tek nesne.
- Dosya adı = tablodaki ad. Hepsini tek zip yapıp gönder; oyun `assets/ui/<ad>.png` varsa **kod değişmeden** onu kullanır
  (yoksa eski kodla çizilen simge görünür).

**Stil satırı (her prompt'un sonuna `[STİL]` yerine):**
```
high quality detailed pixel-art style game icon, crisp edges, bold dark outline, rich hue-shifted shading, light from the
top-left, dark post-apocalyptic survival game, premium mobile game art, centered single object, no text, no letters,
no numbers, no watermark, no frame, no shadow on the background, flat solid #00FF00 green background
```

## 1. Ekran düğmeleri (kare, yuvarlak köşeli düğmenin içine girer)

| Dosya | Prompt |
|---|---|
| `btn_shop` | A pixel-art market stall icon: wooden counter with a striped red and cream awning, a small coin pouch and a glowing gem on the counter. [STİL] |
| `btn_skills` | A pixel-art skill tree icon: a small glowing golden tree whose branches end in four colored orbs (red, blue, green, yellow), a faint rune circle at its roots. [STİL] |
| `btn_chars` | A pixel-art character select icon: three hero silhouettes in a row (the middle one in front, taller), a purple glowing aura behind them, one wearing a hood, one with a bandana. [STİL] |
| `btn_crate` | A pixel-art diamond chest icon: a dark blue steel chest with silver trim, lid slightly open, brilliant cyan diamonds spilling and glowing from inside. [STİL] |

## 2. Yetenek ağacı düğümleri (21 dosya, `skill_<ad>`; ağaçta küçük daire içinde ~48 px görünür, sade ve okunaklı olsun)

Aile renkleri: hasar = kırmızı/turuncu, silah = mavi/çelik, can = yeşil/pembe, çeviklik = sarı/camgöbeği.

| Dosya | Aile | Prompt (en başa "A pixel-art skill icon:" ekle) |
|---|---|---|
| `skill_meleeflat` | mavi/çelik | düz yakın dövüş hasarı: bir kılıç ve üstünde parlayan yukarı ok, mavi/çelik renk vurgusu. [STİL] |
| `skill_fistpct` | kırmızı/turuncu | yumruk hasarı yüzdesi: kapalı bir yumruk, çevresinde şok halkası, kırmızı/turuncu renk vurgusu. [STİL] |
| `skill_dmgpct` | kırmızı/turuncu | toplam hasar yüzdesi: kırmızı alevli bir hasar yıldızı, kırmızı/turuncu renk vurgusu. [STİL] |
| `skill_crit` | kırmızı/turuncu | kritik vuruş şansı: hedef işaretli kafatası, ortası parlıyor, kırmızı/turuncu renk vurgusu. [STİL] |
| `skill_stun` | kırmızı/turuncu | sersemletme: başının çevresinde dönen yıldızlar olan bir kafa, kırmızı/turuncu renk vurgusu. [STİL] |
| `skill_lifesteal` | yeşil/pembe | can çalma: damlayan kan damlası ve içine akan kırmızı ışık, yeşil/pembe renk vurgusu. [STİL] |
| `skill_bladepct` | mavi/çelik | kesici silah hasarı: çapraz iki bıçak, mavi/çelik renk vurgusu. [STİL] |
| `skill_reach` | mavi/çelik | menzil: uzayan bir mızrak ucu ve menzil okları, mavi/çelik renk vurgusu. [STİL] |
| `skill_atkspeed` | mavi/çelik | saldırı hızı: bir kılıç ve arkasında hız çizgileri, mavi/çelik renk vurgusu. [STİL] |
| `skill_critdmg` | kırmızı/turuncu | kritik hasar: patlayan kırmızı yıldız ve içinde çatlak, kırmızı/turuncu renk vurgusu. [STİL] |
| `skill_hpflat` | yeşil/pembe | ekstra can: büyük bir kalp, üstünde artı işareti, yeşil/pembe renk vurgusu. [STİL] |
| `skill_dmgtaken` | yeşil/pembe | alınan hasarı azaltma: bir kalkan, üstünde aşağı ok, yeşil/pembe renk vurgusu. [STİL] |
| `skill_reflect` | yeşil/pembe | hasar yansıtma: yansıtan aynalı bir kalkan, geri sekmiş ışık, yeşil/pembe renk vurgusu. [STİL] |
| `skill_blastres` | yeşil/pembe | patlama direnci: patlamaya karşı duran kalkan ve duman, yeşil/pembe renk vurgusu. [STİL] |
| `skill_regen` | yeşil/pembe | can yenilenmesi: kalp ve çevresinde dönen yeşil oklar, yeşil/pembe renk vurgusu. [STİL] |
| `skill_lastbreath` | yeşil/pembe | son nefes (ölümden dönme): çatlak kalp ve içinden çıkan altın ışık, yeşil/pembe renk vurgusu. [STİL] |
| `skill_dodge` | sarı/camgöbeği | kaçınma: yarı saydam iki ardışık siluet, yana kayma, sarı/camgöbeği renk vurgusu. [STİL] |
| `skill_speed` | sarı/camgöbeği | hareket hızı: kanatlı bir bot, sarı/camgöbeği renk vurgusu. [STİL] |
| `skill_rollcd` | sarı/camgöbeği | yuvarlanma bekleme süresi: dönen bir ok halkası ve küçük saat, sarı/camgöbeği renk vurgusu. [STİL] |
| `skill_stamina` | sarı/camgöbeği | dayanıklılık: bir şimşek / enerji göstergesi, sarı/camgöbeği renk vurgusu. [STİL] |
| `skill_iframe` | sarı/camgöbeği | yuvarlanmada dokunulmazlık: hayalet gibi parlayan beyaz siluet, sarı/camgöbeği renk vurgusu. [STİL] |

## 3. İsteğe bağlı

| Dosya | Prompt |
|---|---|
| `skill_core` | A pixel-art tree root icon: a glowing white-gold crystal core with four colored rays (red, blue, green, yellow) radiating outward. [STİL] |
| `skill_tree_bg` | Seamless dark stone-and-mist texture, 2048x2048, very low contrast, faint arcane circles, no objects, no text (yeşil zemin YOK). |
