// Zenvex Online — JVM testleri: Paparazzi ekran görüntüleri (oyun, HUD, arayüz), 3B modeller, efekt dokuları, çeviriler.
package com.zenvex.online

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

// ====================================================================================================
// com/zenvex/online/game/FxTexTest.kt
// ====================================================================================================

/** Efekt dokularının kontrol sayfası (build/game-shots/fx_tex.png): toz, duman, alev, kıvılcım, parça, leke, halka. */
class FxTexTest {
    @get:Rule val paparazzi = Paparazzi()

    private fun paint(color: Int, add: Boolean = false) = Paint().apply {
        isFilterBitmap = false; isAntiAlias = false
        colorFilter = LightingColorFilter(color or 0xFF000000.toInt(), 0)
        if (add) xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    @Test fun sheet() {
        val W = 1400; val H = 640
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = AndroidCanvas(bmp)
        c.drawColor(0xFF3A5A2C.toInt())
        val r = RectF()
        fun draw(b: Bitmap, x: Float, y: Float, s: Float, p: Paint) {
            r.set(x - s / 2, y - s * b.height / b.width / 2, x + s / 2, y + s * b.height / b.width / 2)
            c.drawBitmap(b, null, r, p)
        }
        // toz (kum, toprak, kar, kül) ve duman
        val dust = intArrayOf(0xFFD8B878.toInt(), 0xFF8A6A44.toInt(), 0xFFEAF2FF.toInt(), 0xFF4A4644.toInt(), 0xFF9AA2AA.toInt(), 0xFFC8C0B0.toInt())
        for (i in 0 until 8) draw(FxTex.puffs[i], 70f + i * 130f, 70f, 120f, paint(dust[i % dust.size]))
        // alevler: turuncu, mor, mavi (toplamalı)
        c.drawRect(0f, 150f, W.toFloat(), 330f, Paint().apply { color = 0xFF0A0810.toInt() })
        val fl = intArrayOf(0xFFFF8A20.toInt(), 0xFFB070FF.toInt(), 0xFF40C8FF.toInt(), 0xFFFFC850.toInt(), 0xFFFF4A6A.toInt(), 0xFF70FF9A.toInt())
        for (i in 0 until 6) { draw(FxTex.flames[i], 80f + i * 105f, 240f, 100f, paint(fl[i], true)); draw(FxTex.flames[i], 80f + i * 105f, 250f, 70f, paint(0xFFFFFFFF.toInt(), true)) }
        for (i in 0 until 5) draw(FxTex.sparkle, 760f + i * 60f, 190f, 14f * (i + 1), paint(0xFFFFE07A.toInt(), true))
        // kırık parçalar
        for (i in 0 until 5) draw(FxTex.chunks[i], 780f + i * 55f, 280f, 40f, paint(intArrayOf(0xFF8A8A80.toInt(), 0xFF7A5A3A.toInt(), 0xFF7A1A14.toInt(), 0xFF9AA2AA.toInt(), 0xFFB09060.toInt())[i]))
        // lekeler ve halkalar
        for (i in 0 until 5) draw(FxTex.splats[i], 90f + i * 150f, 450f, 130f, paint(0xFF7A1018.toInt()))
        for (i in 0 until 3) draw(FxTex.splats[i], 90f + i * 150f, 570f, 80f, paint(0xFF2A2A22.toInt()))
        draw(FxTex.ring, 960f, 460f, 200f, paint(0xFFFFE0A0.toInt(), true))
        draw(FxTex.ring, 1200f, 460f, 150f, paint(0xFFB070FF.toInt(), true))
        val dir = java.io.File("build/game-shots").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "fx_tex.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

// ====================================================================================================
// com/zenvex/online/game/GameShotsTest.kt
// ====================================================================================================

/**
 * Oyun ekranının kendisi (GameView) emülatörsüz: Rust çekirdeği bilgisayar için derlenir ve gerçek update/render yolu
 * bir bitmap'e çizilir. Oynanış turu gibi: kampta doğuş, savaş, şehir, Cephe Hattı, gece.
 * Ön koşul: ./gradlew nativeBuildHost (Native/target/release/libzenvex.so). Kütüphane yoksa test atlanır.
 * Çalıştırma: ./gradlew :Android:recordPaparazziDebug -x :Android:cargoBuild --tests '*GameShotsTest*'
 */
class GameShotsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
    )

    private val ui = object : GameUi {
        override fun onDeath() {}
        override fun onOpen(panel: String) {}
        override fun onToast(msg: String) {}
        override fun onLevelUp(level: Int) {}
    }

    private fun native(): Boolean = runCatching { Native.hashCode(); true }.getOrDefault(false)

    private fun cheat(t: String) = synchronized(GameSession.lock) {
        Native.command(GameSession.handle, JSONObject().put("cmd", "cheat").put("text", t).toString())
    }

    private fun cmd(o: JSONObject) = synchronized(GameSession.lock) { Native.command(GameSession.handle, o.toString()) }

    private fun shot(name: String, setup: () -> Unit, steps: Int = 60, input: FloatArray? = null, hud: Boolean = true,
                     after: ((GameView) -> Unit)? = null, overlay: (@androidx.compose.runtime.Composable () -> Unit)? = null,
                     prep: ((GameView) -> Unit)? = null) {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        Model3D.initSync(paparazzi.context)
        setup()
        val view = GameView(paparazzi.context, ui)
        view.localName = "Oyuncu"
        prep?.invoke(view)
        val w = 2340; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.previewFrames(AndroidCanvas(bmp), w, h, steps, input = input)
        if (after != null) { after(view); view.previewFrames(AndroidCanvas(bmp), w, h, 2) }
        // tam çözünürlüklü kopya (incelemek için): app/build/game-shots/<ad>.png
        runCatching {
            val dir = java.io.File("build/game-shots").apply { mkdirs() }
            java.io.FileOutputStream(java.io.File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // oyun görüntüsü + Compose HUD (gerçek oyundaki gibi üst üste)
        val img = bmp.asImageBitmap()
        paparazzi.snapshot(name) {
            Box(Modifier.fillMaxSize()) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                if (hud) Hud {}
                overlay?.invoke()
            }
        }
    }

    /** Kalabalık kıyası (yazılım tuvali; cihazdan yavaş ama göreli): 1000 yaratık, kare başına ms → konsol */
    @Test fun p01_crowdPerf() = shot("crowd_perf", {
        cheat("level 30"); cheat("god"); cheat("tp deep"); cheat("killall")
        repeat(34) { cheat("spawn walker 30 20") }
    }, steps = 5, prep = { v ->
        val bmp = Bitmap.createBitmap(2340, 1080, Bitmap.Config.ARGB_8888)
        v.previewFrames(AndroidCanvas(bmp), 2340, 1080, 10)
        val t0 = System.nanoTime()
        v.previewFrames(AndroidCanvas(bmp), 2340, 1080, 30)
        println("PERF kalabalık: " + (System.nanoTime() - t0) / 30 / 1_000_000.0 + " ms/kare")
    })

    @Test fun g01_campSpawn() = shot("camp", { })

    @Test fun g02_forestFight() = shot("fight", {
        cheat("level 6"); cheat("god"); cheat("tp deep"); cheat("spawn walker 4 4"); cheat("spawn runner 2 4")
    }, steps = 150, input = floatArrayOf(0f, 0f, 1f, 0f, 1f))

    @Test fun g11_title() = shot("title", {
        Account.profile.value = JSONObject().put("title", JSONObject().put("name", "Canavar Avcısı #1").put("rank", 1).put("kind", "kills"))
        cheat("level 15"); cheat("tp city")
    }, steps = 170)

    @Test fun g03_city() = shot("city", { cheat("level 12"); cheat("tp city") })

    @Test fun g04_cityStreet() = shot("downtown", { cheat("level 25"); cheat("tp downtown"); cheat("spawn dark 3 3"); cheat("spawn tank 1 3") }, steps = 90)

    @Test fun g05_front() = shot("front_hq", { cheat("level 30"); cheat("tp front") })

    @Test fun g06_base() = shot("base", { cheat("level 40"); cheat("tp base 1") }, steps = 120)

    @Test fun g07_night() = shot("night", { cheat("level 8"); cheat("tp camp"); cheat("night") })

    @Test fun g08_dog() = shot("dog", { cheat("level 10"); cheat("dog 3") }, steps = 90, input = floatArrayOf(1f, 0f, 0f, 0f, 0f))

    @Test fun g09_harbor() = shot("harbor", { cheat("level 45"); cheat("tp harbor"); cheat("spawn dark 3 3") }, steps = 90)

    /** V15: kare bölgenin kenarı (Başlangıç Ormanı'nın doğusu): ötesi boşluk, yürünmez */
    /** Gizemli Ağaç (görev noktası) ve çevresi */
    @Test fun g31_mysteryTree() = shot("mystery_tree", { cheat("level 8"); cheat("tp 232 264") }, steps = 40)

    @Test fun g12_border() = shot("border", { cheat("level 12"); cheat("tp 268 300") }, steps = 90, input = floatArrayOf(1f, 0f, 0f, 0f, 0f))

    /** V12.1: tezgâh eşyası kampın dışına kurulur (kalan süre), tezgâh ekranında yalnız tezgâh tarifleri */
    private fun placeBench() {
        cheat("level 12"); cheat("tp 150 320"); cheat("item workbench 1"); cheat("item swamp_moss 3"); cheat("item dried_hide 3"); cheat("item bandage 4")
        val slots = GameSession.ui("inventory").optJSONArray("slots") ?: return
        val k = (0 until slots.length()).firstOrNull { slots.optJSONObject(it)?.optString("id") == "workbench" } ?: return
        cmd(JSONObject().put("cmd", "use").put("slot", k))
    }

    @Test fun g13_bench() = shot("bench", { placeBench() }, steps = 40)

    /** V15 inşa modu: tezgâh çantadan "Kur" ile seçilir; önizleme yeşil (uygun) ya da kırmızı, ✓ / ✕ */
    @Test fun g16_buildMode() = shot("build_mode", {
        cheat("level 12"); cheat("tp 150 320"); cheat("item workbench 1")
    }, steps = 30, hud = false, after = { v ->
        val slots = GameSession.ui("inventory").optJSONArray("slots")!!
        v.startBuild((0 until slots.length()).first { slots.optJSONObject(it)?.optString("id") == "workbench" })
    })

    /** V15: her bölgenin gerçek haritası (incelemek için app/build/game-shots/region_<id>.png; ekran görüntüsü değil) */
    @Test fun g30_allRegionMaps() {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        cheat("level 50")
        val w = GameSession.world()
        val areas = com.zenvex.online.mapAreas(w)
        val names = w.getJSONArray("names")
        val atlas = Atlas.get(paparazzi.context)
        val statics = Array(names.length()) { atlas["s/" + names.getString(it)] }
        val view = GameView(paparazzi.context, ui)
        val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val dir = java.io.File("build/game-shots").apply { mkdirs() }
        for ((ai, a) in areas.withIndex()) {
            cheat("tp ${(a.rects[0][0] + a.rects[0][2]) / 2 / 64} ${(a.rects[0][1] + a.rects[0][3]) / 2 / 64}")
            view.previewFrames(AndroidCanvas(bmp), 640, 360, 3)
            val s = RegionMap.buildNow(ai, a, statics) ?: continue
            java.io.FileOutputStream(java.io.File(dir, "region_${a.id}.png")).use { s.bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** V15 gerçek bölge haritası: bölge oyunun kendi dokuları ve görselleriyle küçültülmüş; büyük harita bunu gösterir */
    @Test fun g17_regionMap() {
        var snap: RegionMap.Snap? = null
        shot("region_map", { cheat("level 8"); cheat("tp camp") }, steps = 30, hud = false, after = {
            val w = GameSession.world()
            val areas = com.zenvex.online.mapAreas(w)
            val names = w.getJSONArray("names")
            val atlas = Atlas.get(paparazzi.context)
            val statics = Array(names.length()) { atlas["s/" + names.getString(it)] }
            val hud = GameSession.hud()
            val ai = com.zenvex.online.areaIndexAt(areas, hud[Native.PX], hud[Native.PY])
            snap = RegionMap.buildNow(ai, areas[ai], statics)
            snap?.let { s ->
                val dir = java.io.File("build/game-shots").apply { mkdirs() }
                java.io.FileOutputStream(java.io.File(dir, "region_map_raw.png")).use { s.bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }, overlay = {
            val hud = GameSession.hud()
            com.zenvex.online.WorldMapView(null, null, GameSession.world(), hud[Native.PX], hud[Native.PY], 8, emptyList(), {}, region = snap)
        })
    }

    /** Büyük harita gerçek oyundaki gibi: keşif bulutu ve bölge dışı bulut denizi */
    @Test fun g17b_regionMapFog() {
        var snap: RegionMap.Snap? = null
        shot("region_map_fog", { cheat("level 8"); cheat("tp camp") }, steps = 30, hud = false, after = {
            val w = GameSession.world()
            val areas = com.zenvex.online.mapAreas(w)
            val names = w.getJSONArray("names")
            val atlas = Atlas.get(paparazzi.context)
            val statics = Array(names.length()) { atlas["s/" + names.getString(it)] }
            val hud = GameSession.hud()
            snap = RegionMap.buildNow(com.zenvex.online.areaIndexAt(areas, hud[Native.PX], hud[Native.PY]), areas[com.zenvex.online.areaIndexAt(areas, hud[Native.PX], hud[Native.PY])], statics)
        }, overlay = {
            val hud = GameSession.hud()
            com.zenvex.online.WorldMapView(null, com.zenvex.online.fogMask(GameSession.ui("fog")), GameSession.world(), hud[Native.PX], hud[Native.PY], 8,
                emptyList(), {}, region = snap)
        })
    }

    @Test fun g14_benchCraft() = shot("bench_craft", { placeBench() }, steps = 20, hud = false,
        overlay = { com.zenvex.online.CraftScreen({}, bench = true) })

    @Test fun g15_handCraft() = shot("hand_craft", { cheat("item cloth 6"); cheat("item resin 4") }, steps = 20, hud = false,
        overlay = { com.zenvex.online.CraftScreen({}) })

    /** V12.2 gezgin tüccar: kamyonet kampa gelir, park eder, tüccar iner; dükkânda gelme olasılıkları */
    private fun goCaravan() {
        val m = GameSession.ui("areas").optJSONArray("merchant")?.optJSONObject(0) ?: return
        val p = m.optJSONArray("park") ?: return
        cheat("tp ${(p.optDouble(0) / 64 - 2).toInt()} ${(p.optDouble(1) / 64 + 3).toInt()}")
    }

    @Test fun g16_caravan() = shot("caravan", { cheat("level 6"); goCaravan() }, steps = 420)

    @Test fun g17_shop() = shot("shop", { cheat("level 12"); cheat("gold 2000"); goCaravan() }, steps = 420, hud = false,
        overlay = { com.zenvex.online.ShopScreen({}) })

    // V13 bölge yaratıkları (yürür, saldırır; isim ve seviye başlarında)
    private fun creatures(lv: Int, tp: String, vararg ids: String) {
        cheat("level $lv"); cheat("tp $tp"); cheat("killall")
        for (id in ids) cheat("spawn $id 2 $lv")
    }
    @Test fun g18_forestCreatures() = shot("cr_forest", { creatures(8, "camp", "lumberjack", "wolf", "shroom") }, steps = 70)
    @Test fun g19_cityCreatures() = shot("cr_city", { creatures(15, "downtown", "office", "riot", "crawler") }, steps = 70)
    @Test fun g20_burntCreatures() = shot("cr_burnt", { creatures(20, "burnt", "charred", "emberhound", "ashwraith") }, steps = 70)
    @Test fun g21_desertCreatures() = shot("cr_desert", { creatures(25, "desert", "mummy", "scorpion", "biker") }, steps = 70)
    @Test fun g22_swampCreatures() = shot("cr_swamp", { creatures(30, "swamp", "bloater", "boggiant", "witch") }, steps = 70)
    @Test fun g23_harborCreatures() = shot("cr_harbor", { creatures(45, "harbor", "sailor", "icecrab", "dockbrute") }, steps = 70)

    // V13 Magma Titan: yürür, yere vurur, ateş püskürtür
    @Test fun g24_titan() = shot("titan", { cheat("level 50"); cheat("tp desert"); cheat("god"); cheat("boss") }, steps = 150)
    @Test fun g25_titanLate() = shot("titan_late", { cheat("level 50"); cheat("tp desert"); cheat("god"); cheat("boss") }, steps = 420)

    // V13 Cephe botları: üsse girince kalkanlı ve alev makineli askerler
    @Test fun g26_baseBots() = shot("base_bots", { cheat("level 40"); cheat("god"); cheat("tp base 1") }, steps = 120)

    // etkinlik silahları elde (kabza/uç doğrusu katana karelerinden)
    private fun evWeapon(id: String) { cheat("level 20"); cheat("item ev_${id}_weapon 1"); cmd(JSONObject().put("cmd", "cycle_weapon")) }
    @Test fun g27_evBlood() = shot("ev_blood", { evWeapon("blood") }, steps = 30)
    @Test fun g28_evTitanWalk() = shot("ev_titan_walk", { evWeapon("titan") }, steps = 40, input = floatArrayOf(1f, 0f, 0f, 0f, 0f))
    @Test fun g29_evPackAttack() = shot("ev_pack_attack", { evWeapon("pack"); cheat("spawn walker 2 2") }, steps = 50, input = floatArrayOf(0f, 0f, 1f, 0f, 0f))

    /** V18 bölge sınırı: komşu bölgenin nesneleri görünmez, sınırın ötesi bulut denizi */
    @Test fun e01_borderEast() = shot("border_east", { cheat("level 20"); cheat("tp 355 470") }, steps = 30)
    @Test fun e02_borderCorner() = shot("border_corner", { cheat("level 20"); cheat("tp 193 383") }, steps = 30)
    /** V18 kontrol rehberi: ilk girişte numaralı işaretler ve açıklama paneli */
    @Test fun e03_controlsGuide() = shot("controls_guide", { cheat("heal") }, steps = 40)
    @Test fun g10_vehicle() = shot("vehicle", { cheat("level 20"); cheat("tp desert"); cheat("vehicle 2") }, steps = 60)
    // ------------------------------------------------------------------ V16 3B kahramanlar, silahlar, araçlar
    private fun equip(item: String) {
        cheat("item $item 1")
        val slots = GameSession.ui("inventory").optJSONArray("slots") ?: return
        val k = (0 until slots.length()).firstOrNull { slots.optJSONObject(it)?.optString("id") == item } ?: return
        cmd(JSONObject().put("cmd", "equip").put("slot", k))
    }

    private var heroLevel = 30
    private fun hero3d(ch: Int, item: String?, vararg zombies: String) {
        cheat("level $heroLevel"); cheat("god"); cheat("hero $ch"); cheat("tp deep"); cheat("killall")
        if (item != null) equip(item)
        for (z in zombies) cheat("spawn $z 3 6")
    }

    /** sağa doğru saldırı (atak çubuğu basılı) */
    private val attackRight = floatArrayOf(0f, 0f, 1f, 0.15f, 1f)

    @Test fun h01_kaelKatana() = shot("h3d_kael_katana", { hero3d(0, "w_katana", "walker") }, steps = 47, input = attackRight)
    @Test fun h02_lyraAk() = shot("h3d_lyra_ak47", { hero3d(1, "w_ak47", "walker") }, steps = 40, input = attackRight)
    @Test fun h03_vexMagma() = shot("h3d_vex_magma", { hero3d(2, "ev_titan_weapon", "walker") }, steps = 50, input = attackRight)
    @Test fun h04_morvathIce() = shot("h3d_morvath_ice", { hero3d(3, "ev_breath_weapon", "walker") }, steps = 44, input = attackRight)
    @Test fun h05_bloodScythe() = shot("h3d_blood", { hero3d(0, "ev_blood_weapon", "walker") }, steps = 44, input = attackRight)
    @Test fun h06_stormAxe() = shot("h3d_storm", { hero3d(2, "ev_base_weapon", "walker") }, steps = 50, input = attackRight)
    @Test fun h07_walk() = shot("h3d_walk", { hero3d(1, "w_uzi") }, steps = 40, input = floatArrayOf(-0.7f, 0.7f, 0f, 0f, 0f))
    /** araca yürü, bin, sür (sürüş kareleri ayrı tuvale; son kareler ekran görüntüsü) */
    private fun drive(v: GameView, mx: Float, my: Float) {
        val tmp = Bitmap.createBitmap(2340, 1080, Bitmap.Config.ARGB_8888)
        cmd(JSONObject().put("cmd", "vehicle"))
        v.previewFrames(AndroidCanvas(tmp), 2340, 1080, 45, input = floatArrayOf(mx, my, 0f, 0f, 0f))
    }

    @Test fun h08_moto() = shot("h3d_moto", { hero3d(0, null); cheat("vehicle moto") }, steps = 8,
        input = floatArrayOf(1f, 0f, 0f, 0f, 0f), after = { drive(it, 1f, 0.3f) })
    @Test fun h09_car() = shot("h3d_car", { hero3d(2, null); cheat("vehicle car") }, steps = 8,
        input = floatArrayOf(1f, 0f, 0f, 0f, 0f), after = { drive(it, 0.8f, -0.6f) })
    /** Saldırı kare dizisi (incelemek için build/game-shots/seq_<ad>.png): oyuncu çevresi, 2 karede bir */
    private fun sequence(name: String, ch: Int, item: String?, input: FloatArray, frames: Int = 12, warm: Int = 20, stepPer: Int = 2, zoom: Float = 1.7f, setup: (() -> Unit)? = null) {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        Model3D.initSync(paparazzi.context)
        if (setup != null) setup() else hero3d(ch, item, "walker", "walker")
        val view = GameView(paparazzi.context, ui)
        val w = 2340; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.previewFrames(AndroidCanvas(bmp), w, h, warm, input = input)
        val cw = 520; val chh = 520
        val sheet = Bitmap.createBitmap(cw * 6, chh * ((frames + 5) / 6), Bitmap.Config.ARGB_8888)
        val sc = AndroidCanvas(sheet)
        for (i in 0 until frames) {
            view.previewFrames(AndroidCanvas(bmp), w, h, stepPer, input = input)
            val hw = (cw / zoom / 2).toInt(); val hh = (chh / zoom / 2).toInt()
            val src = android.graphics.Rect(w / 2 - hw, h / 2 - hh - 40, w / 2 + hw, h / 2 + hh - 40)
            sc.drawBitmap(bmp, src, android.graphics.Rect((i % 6) * cw, (i / 6) * chh, (i % 6 + 1) * cw, (i / 6 + 1) * chh), null)
        }
        val dir = java.io.File("build/game-shots").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "seq_$name.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun s01_katana() = sequence("katana", 0, "w_katana", attackRight)
    @Test fun s02_magma() = sequence("magma", 2, "ev_titan_weapon", attackRight)
    @Test fun s03_ice() = sequence("ice", 3, "ev_breath_weapon", attackRight)
    @Test fun s04_storm() = sequence("storm", 2, "ev_base_weapon", attackRight)
    @Test fun s05_ak() = sequence("ak", 1, "w_ak47", attackRight, 18, warm = 75)
    @Test fun s06_chainsaw() = sequence("chainsaw", 3, "ev_scrap_weapon", attackRight)
    @Test fun s07_scythe() { heroLevel = 8; sequence("scythe", 0, "ev_blood_weapon", attackRight, 24, warm = 10, stepPer = 3) }
    @Test fun s08_rpg() { heroLevel = 8; sequence("rpg", 1, "w_rpg", attackRight, 24, warm = 40, stepPer = 7) }
    private fun node(kind: String, ch: Int, item: String?) {
        cheat("level $heroLevel"); cheat("god"); cheat("hero $ch"); cheat("tp deep"); cheat("killall")
        if (item != null) equip(item)
        cheat("tp $kind")
    }
    @Test fun s09_chopStone() { heroLevel = 8; sequence("chop", 0, null, attackRight, 12, warm = 6, stepPer = 3, setup = { cheat("item t_axe_stone 1"); node("tree", 0, null) }) }
    @Test fun s10_chopIronHeld() { heroLevel = 8; sequence("chop_iron", 2, null, attackRight, 12, warm = 6, stepPer = 3, setup = { node("tree", 2, "t_axe_iron") }) }
    @Test fun s11_mineIron() { heroLevel = 8; sequence("mine", 1, null, attackRight, 12, warm = 6, stepPer = 3, setup = { cheat("item t_pick_iron 1"); node("rock", 1, null) }) }
    // ------------------------------------------------------------------ V16 efekt motoru v2
    private fun fxShot(name: String, setup: () -> Unit, steps: Int, input: FloatArray? = null, frames: Int = 6, after: ((GameView) -> Unit)? = null) {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        Model3D.initSync(paparazzi.context)
        setup()
        val view = GameView(paparazzi.context, ui)
        val w = 2340; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.previewFrames(AndroidCanvas(bmp), w, h, steps, input = input)
        after?.invoke(view)
        val cw = 600; val ch = 420
        val sheet = Bitmap.createBitmap(cw * 3, ch * ((frames + 2) / 3), Bitmap.Config.ARGB_8888)
        val sc = AndroidCanvas(sheet)
        for (i in 0 until frames) {
            view.previewFrames(AndroidCanvas(bmp), w, h, 4, input = input)
            val src = android.graphics.Rect(w / 2 - cw / 2, h / 2 - ch / 2 - 40, w / 2 + cw / 2, h / 2 + ch / 2 - 40)
            sc.drawBitmap(bmp, src, android.graphics.Rect((i % 3) * cw, (i / 3) * ch, (i % 3 + 1) * cw, (i / 3 + 1) * ch), null)
        }
        val dir = java.io.File("build/game-shots").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "fx_$name.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun f01_aura50() = fxShot("aura50", { cheat("level 50"); cheat("god"); cheat("hero 0"); cheat("tp camp"); cheat("night") }, steps = 40)
    @Test fun f02_aura25() = fxShot("aura25", { cheat("level 25"); cheat("god"); cheat("hero 1"); cheat("tp camp") }, steps = 40)
    @Test fun f03_aura10() = fxShot("aura10", { cheat("level 8"); cheat("god"); cheat("hero 0"); cheat("charlevel 10"); cheat("tp camp"); cheat("night") }, steps = 40)
    @Test fun f04_runDust() = fxShot("rundust", { cheat("level 12"); cheat("god"); cheat("hero 2"); cheat("tp desert") }, steps = 30, input = floatArrayOf(1f, 0f, 0f, 0f, 0f))
    @Test fun f05_motoDesert() = fxShot("motodesert", { cheat("level 25"); cheat("god"); cheat("hero 0"); cheat("tp desert"); cheat("vehicle moto") }, steps = 8,
        input = floatArrayOf(1f, 0f, 0f, 0f, 0f), after = { drive(it, 1f, 0.2f) })
    @Test fun f06_carForest() = fxShot("carforest", { cheat("level 25"); cheat("god"); cheat("hero 2"); cheat("tp deep"); cheat("vehicle car") }, steps = 8,
        input = floatArrayOf(1f, 0f, 0f, 0f, 0f), after = { drive(it, 1f, 0.1f) })

    /** Çizim çağrılarını sayan tuval: ön ayarların gerçekte ne kadar iş azalttığını ölçer */
    private class CountingCanvas(b: Bitmap) : AndroidCanvas(b) {
        var calls = 0
        override fun drawBitmap(bitmap: Bitmap, src: android.graphics.Rect?, dst: RectF, paint: Paint?) { calls++; super.drawBitmap(bitmap, src, dst, paint) }
        override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) { calls++; super.drawBitmap(bitmap, left, top, paint) }
        override fun drawBitmap(bitmap: Bitmap, matrix: android.graphics.Matrix, paint: Paint?) { calls++; super.drawBitmap(bitmap, matrix, paint) }
        override fun drawPath(path: AndroidPath, paint: Paint) { calls++; super.drawPath(path, paint) }
        override fun drawOval(oval: RectF, paint: Paint) { calls++; super.drawOval(oval, paint) }
        override fun drawOval(l: Float, t: Float, r: Float, b: Float, paint: Paint) { calls++; super.drawOval(l, t, r, b, paint) }
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) { calls++; super.drawCircle(cx, cy, radius, paint) }
        override fun drawLine(sx: Float, sy: Float, ex: Float, ey: Float, paint: Paint) { calls++; super.drawLine(sx, sy, ex, ey, paint) }
        override fun drawRect(l: Float, t: Float, r: Float, b: Float, paint: Paint) { calls++; super.drawRect(l, t, r, b, paint) }
        override fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, paint: Paint) { calls++; super.drawRoundRect(l, t, r, b, rx, ry, paint) }
        override fun drawBitmapMesh(bitmap: Bitmap, mw: Int, mh: Int, v: FloatArray, vo: Int, col: IntArray?, co: Int, paint: Paint?) { calls++; super.drawBitmapMesh(bitmap, mw, mh, v, vo, col, co, paint) }
    }

    /** Grafik ön ayarları gerçekten maliyeti düşürür: aynı kalabalık sahne 4 ön ayarda çizilir, kare süresi sıralanır. */
    @Test fun p01_presetCost() {
        Assume.assumeTrue("libzenvex.so yok", native())
        com.zenvex.online.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        Model3D.initSync(paparazzi.context)
        cheat("level 50"); cheat("god"); cheat("hero 0"); cheat("tp downtown"); cheat("killall")
        equip("ev_blood_weapon")
        for (z in listOf("walker", "runner", "dark", "tank", "toxic")) cheat("spawn $z 3 3")
        val view = GameView(paparazzi.context, ui)
        val w = 2340; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = CountingCanvas(bmp)
        val cost = FloatArray(4); val calls = IntArray(4)
        val before = Settings.current
        try {
            for (preset in intArrayOf(3, 0, 1, 2, 3, 0)) {
                Settings.preview(Settings().withPreset(preset))
                view.previewFrames(c, w, h, 30, input = attackRight)
                c.calls = 0
                val t0 = System.nanoTime()
                repeat(5) { view.previewFrames(c, w, h, 8, input = attackRight) }
                cost[preset] = (System.nanoTime() - t0) / 1e6f / 40f
                calls[preset] = c.calls / (5 * 6)
            }
        } finally { Settings.preview(before) }
        println("PRESET_COST çağrı/kare düşük=${calls[0]} orta=${calls[1]} yüksek=${calls[2]} ultra=${calls[3]} · ms/kare ${cost.toList()}")
        org.junit.Assert.assertTrue("çizim çağrısı düşük < orta < yüksek olmalı: ${calls.toList()}", calls[0] < calls[1] && calls[1] < calls[2])
    }

    private fun fxCalm(name: String): () -> Unit = { cheat("level 8"); cheat("god"); cheat("hero 0"); cheat("tp deep"); cheat("killall") }
    @Test fun f07_explosion() = fxShot("explode", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("explode") })
    @Test fun f08_bomb() = fxShot("bomb", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("bomb", 200f, -40f) })
    @Test fun f09_levelup() = fxShot("levelup", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("levelup") })
    @Test fun f10_toxic() = fxShot("toxic", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("toxic", 90f, 20f) })
    @Test fun f11_teleport() = fxShot("teleport", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("teleport", 0f, 30f) })
    @Test fun f12_bolt() = fxShot("bolt", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("bolt", 90f, 30f) })
    @Test fun f13_fireRage() = fxShot("firerage", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("fire", 100f, 30f); it.previewEffect("rage", 0f, 0f) })
    @Test fun f14_hitFx() = fxShot("hitfx", fxCalm("x"), steps = 20, frames = 6, after = { it.previewEffect("crit", 60f, -40f); it.previewEffect("blood", 110f, -10f); it.previewEffect("frost", -70f, 10f); it.previewEffect("spark", 160f, -30f) })

    // ------------------------------------------------------------------ "gerçek oyuncu gibi oynanış" klipleri (reklam/tanıtım)
    /**
     * Kahraman durmaz: koşar, kaçar, takla atar, sürüye döner; kamera yaklaşıp uzaklaşır.
     * PROMO_HUD=1: gerçek oyun arayüzüyle (build/promo_hud/<ad>), yoksa arayüzsüz (build/promo/<ad>). PROMO_EVERY=n: yalnız her n. kareyi kaydet (ön izleme).
     */
    private fun playClip(name: String, setup: () -> Unit, warm: Int, frames: Int, move: (Int) -> FloatArray, meters: (Int) -> Float,
                         rolls: Set<Int> = emptySet(), onFrame: ((GameView, Int) -> Unit)? = null, who: String = "Kael") {
        Assume.assumeTrue("libzenvex.so yok", native())
        val hud = System.getenv("PROMO_HUD") == "1"
        val every = (System.getenv("PROMO_EVERY") ?: "1").toInt().coerceAtLeast(1)
        com.zenvex.online.Demo.on = true
        GameSession.initPreview(paparazzi.context)
        Model3D.initSync(paparazzi.context)
        val before = Settings.current
        Settings.preview(Settings().withPreset(3).copy(zoom = 0, screenShake = 0.6f, showFps = false, showPing = false))
        try {
            setup()
            cheat("guide off"); cheat("heal")
            val view = GameView(paparazzi.context, ui)
            view.localName = who
            view.clean = !hud
            val portrait = System.getenv("PROMO_PORTRAIT") == "1"
            val w = if (portrait) 1080 else 2340; val h = if (portrait) 1920 else 1080
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = AndroidCanvas(bmp)
            val mk = if (portrait) 1.55f else 1f   // dikeyde ekranın yüksekliği daha çok metre gösterir
            view.promoZoom = meters(0) * mk
            view.previewFrames(c, w, h, warm, input = floatArrayOf(0f, 0f, 0f, 0f, 0f))
            // oyunun çaldığı her ses (olayın anında) kaydedilir: video sesi gerçek oyun sesleriyle birebir eşlenir
            var frameNo = 0
            val sfxLog = org.json.JSONArray()
            Audio.tap = { n, v, p -> sfxLog.put(org.json.JSONObject().put("f", frameNo).put("n", n).put("v", v.toDouble()).put("p", p.toDouble())) }
            val dir = java.io.File(if (portrait) "build/promo_v/$name" else if (hud) "build/promo_hud/$name" else "build/promo/$name").apply { deleteRecursively(); mkdirs() }
            for (i in 0 until frames) {
                view.promoZoom = meters(i) * mk
                frameNo = i
                if (i in rolls) GameSession.cmd("ability")
                onFrame?.invoke(view, i)
                view.previewFrames(c, w, h, 1, input = move(i))
                if (i % every == 0) java.io.FileOutputStream(java.io.File(dir, "f%04d.png".format(i))).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            java.io.File(dir, "sfx.json").writeText(sfxLog.toString())
        } finally { Audio.tap = null; Settings.preview(before) }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun ease(t: Float) = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    /** Sürü: koşarak çekip toplar, sonra ortasında dönerek biçer, arada takla atar. */
    @Test fun y01_playHorde() = playClip("horde",
        { cheat("level 30"); cheat("god"); cheat("hero 0"); cheat("tp camp"); cheat("killall"); equip("ev_blood_weapon"); cheat("night"); cheat("spawn walker 9 20"); cheat("spawn runner 4 20"); cheat("spawn dark 3 20") },
        110, 240,
        move = { i ->
            if (i < 50) { val a = i * 0.11f; floatArrayOf(kotlin.math.cos(a), kotlin.math.sin(a), kotlin.math.cos(a), kotlin.math.sin(a), 0f) }
            else { val a = i * 0.16f; floatArrayOf(0.4f * kotlin.math.cos(a * 0.5f), 0.4f * kotlin.math.sin(a * 0.5f), kotlin.math.cos(a), kotlin.math.sin(a), 1f) }
        },
        meters = { i -> lerp(8.6f, 6.0f, ease((i - 30) / 50f)) + lerp(0f, 1.4f, ease((i - 170) / 60f)) },
        rolls = setOf(95, 160))

    /** Roketatar: geri çekilerek ateş eder, tank ve sürü patlar. */
    @Test fun y02_playRocket() = playClip("rocket",
        { cheat("level 30"); cheat("god"); cheat("hero 1"); cheat("tp downtown"); cheat("killall"); equip("w_rpg"); cheat("spawn tank 2 30"); cheat("spawn walker 10 30"); cheat("spawn runner 4 30") },
        110, 240, who = "Lyra",
        move = { i -> floatArrayOf(-0.55f, 0.1f * kotlin.math.sin(i / 14f), 1f, 0.05f * kotlin.math.sin(i / 20f), 1f) },
        meters = { i -> lerp(9.0f, 7.0f, ease(i / 120f)) + lerp(0f, 1.0f, ease((i - 170) / 60f)) },
        rolls = setOf(120))

    /** Motosiklet: çölde sürüyle yarışır, zikzak çizer. */
    @Test fun y03_playChase() = playClip("chase",
        { cheat("level 30"); cheat("god"); cheat("hero 0"); cheat("tp desert"); cheat("killall"); cheat("spawn runner 8 30"); cheat("spawn walker 8 30"); cheat("vehicle moto") },
        8, 270,
        move = { i -> floatArrayOf(1f, 0.55f * kotlin.math.sin(i / 16f), 0f, 0f, 0f) },
        meters = { i -> lerp(9.5f, 8.0f, ease(i / 90f)) },
        onFrame = { v, i -> if (i == 0) drive(v, 1f, 0f) })

    /** Titan: etrafında dönerek takla atar ve vurur. */
    @Test fun y04_playTitan() = playClip("titan",
        { cheat("level 50"); cheat("god"); cheat("hero 2"); cheat("tp desert"); cheat("killall"); equip("ev_titan_weapon"); cheat("boss") },
        140, 240, who = "Vex",
        move = { i ->
            val a = i * 0.05f
            if (i < 40) floatArrayOf(0.9f, -0.43f, 0.9f, -0.43f, 0f)
            else floatArrayOf(-0.8f * kotlin.math.sin(a), 0.8f * kotlin.math.cos(a), 0.9f, -0.43f, 1f)
        },
        meters = { i -> lerp(9.2f, 7.0f, ease((i - 20) / 60f)) },
        rolls = setOf(70, 120, 175))

    /** Donmuş liman: buz tırpanıyla sürüyü biçer. */
    @Test fun y05_playFrost() = playClip("frost",
        { cheat("level 45"); cheat("god"); cheat("hero 3"); cheat("tp harbor"); cheat("killall"); equip("ev_breath_weapon"); cheat("spawn walker 8 40"); cheat("spawn dark 5 40"); cheat("spawn runner 3 40") },
        110, 210, who = "Morvath",
        move = { i ->
            if (i < 40) floatArrayOf(-0.7f, 0.3f, -0.7f, 0.3f, 0f)
            else { val a = i * 0.17f; floatArrayOf(0.35f * kotlin.math.cos(a * 0.4f), 0.35f * kotlin.math.sin(a * 0.4f), kotlin.math.cos(a), kotlin.math.sin(a), 1f) }
        },
        meters = { i -> lerp(8.4f, 6.2f, ease((i - 25) / 50f)) },
        rolls = setOf(85, 150))
}

// ====================================================================================================
// com/zenvex/online/game/I18nTest.kt
// ====================================================================================================

class I18nTest {
    private val dir = File("Source/Main/Assets/i18n")
    private fun load(code: String) = I18n.parse(File(dir, "$code.json").readText())
    private val ph = Regex("\\{(\\d+)\\}")

    @Test fun everyLanguageCoversTheCatalogWithMatchingPlaceholders() {
        val catalog = org.json.JSONArray(File(dir, "catalog.json").readText()).let { a -> List(a.length()) { a.getString(it) } }
        for (code in I18n.LANGS.keys - "tr") {
            val m = load(code)
            val missing = catalog.filter { it !in m }
            assertTrue("$code eksik: ${missing.take(5)}", missing.isEmpty())
            for (k in catalog) {
                val a = ph.findAll(k).map { it.value }.sorted().toList()
                val b = ph.findAll(m.getValue(k)).map { it.value }.sorted().toList()
                assertEquals("$code yer tutucu: $k", a, b)
            }
        }
    }

    @Test fun translatesExactAndTemplatedStrings() {
        I18n.install("en", load("en"))
        assertEquals("Settings", I18n.t("Ayarlar"))
        assertEquals("Level 7 required", I18n.t("Seviye 7 gerekli"))
        assertEquals("You killed Bob!", I18n.t("Bob oyuncusunu öldürdün!"))
        assertEquals("  Settings\n", I18n.t("  Ayarlar\n"))
        assertEquals("bilinmeyen metin xyz", I18n.t("bilinmeyen metin xyz"))
        I18n.install("tr", emptyMap())
        assertEquals("Ayarlar", I18n.t("Ayarlar"))
    }

    @Test fun arabicIsRtlAndOthersAreNot() {
        I18n.install("ar", load("ar"))
        assertTrue(I18n.rtl)
        assertNotEquals("Ayarlar", I18n.t("Ayarlar"))
        I18n.install("de", load("de"))
        assertTrue(!I18n.rtl)
        I18n.install("tr", emptyMap())
    }
}

// ====================================================================================================
// com/zenvex/online/game/Model3DTest.kt
// ====================================================================================================

/** 3B modellerin hepsi yüklenir ve her karakter + araç gerçekten çizilir (libzenvex.so gerekir). */
class Model3DTest {
    @get:Rule val paparazzi = Paparazzi()

    @Test fun allModelsRender() {
        Assume.assumeTrue("libzenvex.so yok", runCatching { Native.hashCode(); true }.getOrDefault(false))
        Model3D.initSync(paparazzi.context)
        val all = Model3D.loaded()
        println("3B modeller: ${all.sorted()}")
        Model3D.lastError?.printStackTrace(System.out)
        for (ch in Model3D.CHARS) {
            val fr = Model3D.actor("t_$ch", ch, "katana", 0, 0f, 30f, 90f, 1)
            println("$ch → ${fr?.bmp?.width}x${fr?.bmp?.height}")
            assertTrue("$ch çizilmedi", fr != null)
        }
        for (v in listOf("hellhound", "nightstalker")) {
            val fr = Model3D.vehicle("t_$v", v, if (v == "nightstalker") "kael" else "", 30f, 0f, 0f, 0f, 0f, 70f, 1)
            println("$v → ${fr?.bmp?.width}x${fr?.bmp?.height}")
            assertTrue("$v çizilmedi", fr != null)
        }
        assertTrue("eksik model: $all", all.size >= 24)
    }
}

// ====================================================================================================
// com/zenvex/online/ui/HudShotsTest.kt
// ====================================================================================================

/**
 * Oyun içi Compose HUD'unun (kısayollar, mağaza/posta/menü, altın+elmas, etkinlik afişi, cepler, sohbet çubuğu, bildirim)
 * küçük telefon ekranında çakışma denetimi: 1600×720 piksel, 320 dpi → 800×360 dp (en dar yaygın yatay ekran).
 */
class HudShotsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1600, screenHeight = 720, xdpi = 320, ydpi = 320, density = Density.XHIGH,
            orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
    )

    @Test fun hudSmallPhone() {
        org.junit.Assume.assumeTrue("libzenvex.so yok", runCatching { com.zenvex.online.Native.hashCode(); true }.getOrDefault(false))
        Demo.on = true
        com.zenvex.online.GameSession.initPreview(paparazzi.context)
        com.zenvex.online.Account.previewLogin(1234567L)
        com.zenvex.online.GameSession.cmd("cheat", "text" to "level 12")
        com.zenvex.online.GameSession.cmd("cheat", "text" to "gold 1234567")
        com.zenvex.online.GameSession.cmd("cheat", "text" to "gems 98765")
        Toaster.show("Doktor yaralarını sardı (-20 altın)")
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(Color(0xFF2E3A26))) {
                Hud {}
                ToastHost()
            }
        }
    }

    /** Öğretici: çanta tuşunda dokun eli (yerleşimi kaydırmadan) ve en üst katmandaki konuşma balonu */
    @Test fun hudTutorialBag() = tutorialShot("g4", "Alttaki parlayan çanta tuşuna dokun, üstteki Üret düğmesine bas ve Şifa sekmesinden Bandaj'ı üret.")

    /** Öğretici: yetenek tuşu işaretliyken sol üst tuşlar arası boşluk değişmemeli */
    @Test fun hudTutorialSkills() = tutorialShot("g6e", "Sol üstteki parlayan mor Yetenekler tuşuna dokun: ağaç açılır, düğümleri elmasla açarsın.")

    private fun tutorialShot(step: String, msg: String) {
        org.junit.Assume.assumeTrue("libzenvex.so yok", runCatching { com.zenvex.online.Native.hashCode(); true }.getOrDefault(false))
        Demo.on = true
        com.zenvex.online.GameSession.initPreview(paparazzi.context)
        com.zenvex.online.Account.previewLogin(1234567L)
        com.zenvex.online.GameSession.cmd("cheat", "text" to "guide $step")
        com.zenvex.online.HudLayout.tutMsg = msg
        com.zenvex.online.HudLayout.tutAlpha = 1f
        com.zenvex.online.HudLayout.tutTop = 0.14f
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(Color(0xFF2E3A26))) { Hud {} }
        }
        com.zenvex.online.HudLayout.tutMsg = ""
    }

    /** Hesap penceresi (yeni hesap / giriş): küçük yatay telefonda düğmeler ekranda kalmalı */
    @Test fun accountNewSmallPhone() { paparazzi.snapshot { AccountDialog(false) {} } }

    /** Klan deposundan gönder penceresi: alanlar ve Gönder tuşu küçük ekranda görünmeli */
    @Test fun clanSendSmallPhone() {
        val c = org.json.JSONObject().put("members", org.json.JSONArray((1..9).map { org.json.JSONObject().put("id", it).put("name", "Üye $it") }))
        val item = org.json.JSONObject().put("id", "wood").put("name", "Odun").put("n", 120)
        paparazzi.snapshot { DepotSend(c, item, {}, {}) }
    }

    /** Ayarlar: her sekme küçük ekranda kaydırmadan tek alanda sığmalı */
    private fun settingsTab(i: Int) {
        com.zenvex.online.Legal.init(paparazzi.context)
        Demo.on = true
        com.zenvex.online.GameSession.initPreview(paparazzi.context)
        com.zenvex.online.Account.previewLogin(1234567L)
        val owner = object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {}
            }
        }
        paparazzi.snapshot {
            androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) { SettingsScreen({}, initialTab = i) }
        }
    }
    @Test fun settingsGraphics() = settingsTab(0)
    @Test fun settingsControls() = settingsTab(1)
    @Test fun settingsSound() = settingsTab(2)
    @Test fun settingsNotif() = settingsTab(3)
    @Test fun settingsAccount() = settingsTab(4)
    @Test fun settingsSupport() = settingsTab(5)
    @Test fun settingsLanguage() = settingsTab(6)
    @Test fun settingsLegal() = settingsTab(7)

    // ------------------------------------------------------------------ yasal pencereler (küçük yatay telefon)
    private fun legal() {
        com.zenvex.online.Legal.init(paparazzi.context)
        com.zenvex.online.Legal.noticeSeen.value = true
        com.zenvex.online.Legal.termsNeeded.value = false
        com.zenvex.online.Store.confirm.value = null
        com.zenvex.online.Legal.open.value = null
    }
    private fun bg(content: @androidx.compose.runtime.Composable () -> Unit) =
        paparazzi.snapshot { Box(Modifier.fillMaxSize().background(Color(0xFF2E3A26))) { content() } }

    /** İlk açılış KVKK aydınlatması */
    @Test fun legalNotice() { legal(); com.zenvex.online.Legal.noticeSeen.value = false; bg { LegalHost() } }

    /** Güncellenen Kullanım Şartları'nın yeniden onayı */
    @Test fun legalTermsUpdate() {
        legal(); com.zenvex.online.Account.previewLogin(1234567L); com.zenvex.online.Legal.termsNeeded.value = true
        bg { LegalHost() }
    }

    /** Satın alma öncesi bilgilendirme ve onay (aylık kart) */
    @Test fun legalPurchaseConfirm() {
        legal()
        com.zenvex.online.Store.state.value = org.json.JSONObject(javaClass.getResource("/ui/store.json")!!.readText())
        com.zenvex.online.Store.confirm.value = "card_month"
        bg { LegalHost() }
    }

    /** Tam ekran yasal metin okuyucu */
    @Test fun legalReader() { legal(); com.zenvex.online.Legal.open.value = "presale"; bg { LegalHost() } }

    /** Profil fotoğrafı yükleme bildirimi */
    @Test fun legalPhotoNotice() { legal(); bg { PhotoNoticeDialog({}) {} } }

    /** Profilden şikâyet (hukuka aykırı içerik / telif) */
    @Test fun legalReport() { legal(); bg { ReportDialog(1000002L, "Veli") {} } }
}

// ====================================================================================================
// com/zenvex/online/ui/UiArtShotsTest.kt
// ====================================================================================================

/** Arayüz görselleri: sahne arka planları, amblem, metal çerçeveli pencere, bakım ekranı, HUD tuşları. */
class UiArtShotsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
    )

    @Test
    fun maintenance() {
        paparazzi.snapshot { MaintenanceScreen("Yeni Bölgeler Ekleniyor. Yarım Saat İçinde Dönüyoruz!") }
    }

    @Test
    fun lobbyArtAndDialog() {
        paparazzi.snapshot {
            SceneBackground("bg_craft", 0f, dim = 0.3f, zoom = 1.75f, ax = 0.97f, ay = 0.55f, drift = 0.03f) {
                Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameLogo(62.dp)
                }
                Row(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    for (b in listOf("btn_attack", "btn_roll", "btn_chat", "btn_drive", "btn_drive2", "btn_swap", "btn_hand"))
                        ArtImage(b, Modifier.padding(4.dp).size(72.dp))
                }
            }
        }
    }

    @Test
    fun metalDialog() {
        paparazzi.snapshot { SceneBackground("bg_craft", null, dim = 0.3f) { Box(Modifier.fillMaxSize()) { NewsDialog {} } } }
    }

    @Test
    fun rulesDialog() {
        paparazzi.snapshot { SceneBackground("bg_craft", null, dim = 0.3f) { Box(Modifier.fillMaxSize()) { NewsDialog(4) {} } } }
    }

    @Test
    fun craftBackground() {
        paparazzi.snapshot { ScreenFrame("ÜRETİM", "craft", {}, bg = "bg_craft") { PT("Tarifler", 20.sp, Px.Text) } }
    }
}

// ====================================================================================================
// com/zenvex/online/ui/UiShotsTest.kt
// ====================================================================================================

/**
 * Arayüz ekran görüntüleri (emülatörsüz). Örnek veriler Rust'tan üretilir:
 * ZX_DUMP_UI=../Android/Source/Test/Resources/ui cargo test dump_ui (Native/ içinde)
 * Çalıştırma: ./gradlew :Android:recordPaparazziDebug -x :Android:cargoBuild → Android/Source/Test/snapshots/images
 */
class UiShotsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
    )

    private fun sample(name: String) = JSONObject(javaClass.getResource("/ui/$name.json")!!.readText())

    /** 3B karakter vitrini (lobi, karakterler, çanta): yerel kütüphane varsa modeller anında çizilir */
    @org.junit.Before
    fun models() {
        if (runCatching { com.zenvex.online.Native.hashCode(); true }.getOrDefault(false))
            com.zenvex.online.Model3D.initSync(paparazzi.context)
    }

    @Test
    fun buttonsAndToasts() {
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF2A3020)).padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
            ) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    PxButton("OYNA", color = Px.Green, size = 22.sp, height = 56.dp) {}
                    PxButton("Satın Al", color = Px.Blue) {}
                    PxButton("Sil", color = Px.Red) {}
                    PxButton("Kilitli", enabled = false) {}
                    PxButton("Harita", icon = "map", color = Px.Dark) {}
                    PxIcon("mail", badge = 3) {}
                    PxIcon("bag", size = 54.dp) {}
                }
                ToastPlate("Doktor yaralarını sardı (-20 altın)", Px.Clan, "coin")
                ToastPlate("Yeni dostun seninle! Köpek seni takip edecek.", Px.Name, "check")
                ToastPlate("30 altın gerekli", androidx.compose.ui.graphics.Color(0xFFE0483C), "close")
            }
        }
    }

    @Test fun shopFeatured() { val d = sample("store"); paparazzi.snapshot { ShopView(d, true, d.optLong("now"), 0, {}) {} } }
    @Test fun shopGems() { val d = sample("store"); paparazzi.snapshot { ShopView(d, true, d.optLong("now"), 1, {}) {} } }
    @Test fun shopCards() { val d = sample("store"); paparazzi.snapshot { ShopView(d, true, d.optLong("now"), 3, {}) {} } }
    @Test fun merchantSell() { val d = sample("merchant"); paparazzi.snapshot { MerchantView(d, 1, {}, {}, {}, {}, {}) } }
    @Test fun merchantOdds() { val d = sample("merchant"); paparazzi.snapshot { MerchantView(d, 2, {}, {}, {}, {}, {}) } }
    @Test fun characters() { val d = sample("characters"); paparazzi.snapshot { CharactersView(d, {}, {}) {} } }

    /** Yönetici paneli (V19 düzeni): genel bakış, posta, dünya */
    @Test fun adminHome() { com.zenvex.online.Account.admin.value = true; paparazzi.snapshot { DevMenu({}, 0) } }
    @Test fun adminMail() { com.zenvex.online.Account.admin.value = true; paparazzi.snapshot { DevMenu({}, 5) } }
    @Test fun adminWorld() { com.zenvex.online.Account.admin.value = true; paparazzi.snapshot { DevMenu({}, 9) } }

    @Test
    fun skillTree() {
        val data = sample("tree")
        paparazzi.snapshot { TreeView(data) {} }
    }

    /** Çanta yarı saydam: arkada oyun görünsün diye çimen renginde zemin üstüne çizilir. */
    @Test
    fun bagScreen() {
        val data = sample("inventory")
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF3E5A2A))) {
                BagView(data, 0, true, {}, { _, _ -> com.zenvex.online.GameSession.Reply(true, "") }) {}
            }
        }
    }

    @Test
    fun worldMap() {
        val bmp: android.graphics.Bitmap = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/ui/map.png"))
        val img = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
        val w = sample("world")
        val me = sample("player")
        val fog = fogMask(sample("fog"))
        val marks = mapMarks(w, JSONObject()) + baseMarks(sample("bases")) +
            MapMark(me.getDouble("x").toFloat() + 900f, me.getDouble("y").toFloat() - 1400f, 3, "Gizemli Ağaç")
        paparazzi.snapshot {
            WorldMapView(img, fog, w, me.getDouble("x").toFloat(), me.getDouble("y").toFloat(), 3, marks, {})
        }
    }

    /** V12 büyücü haritası: bölgeye dokununca kart (yaratıklar, ganimet, seviye, ücret); kilitli bölge */
    @Test
    fun wizardMap() {
        val bmp: android.graphics.Bitmap = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/ui/map.png"))
        val img = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
        val items = itemNames(sample("world"))
        paparazzi.snapshot { WizardMapView(sample("areas"), items, img, {}, initial = 2) {} }
    }

    @Test
    fun wizardMapLocked() {
        val bmp: android.graphics.Bitmap = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/ui/map.png"))
        val img = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
        paparazzi.snapshot { WizardMapView(sample("areas"), itemNames(sample("world")), img, {}, initial = 6) {} }
    }

    /** V18: kamp karakterleri ve önemli yerler etiketli (nerede ne olduğu belli) */
    @Test
    fun worldMapLabels() {
        val bmp: android.graphics.Bitmap = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/ui/map.png"))
        val img = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
        val w = sample("world")
        val me = sample("player")
        val mx = me.getDouble("x").toFloat(); val my = me.getDouble("y").toFloat()
        val npcs = org.json.JSONArray()
            .put(org.json.JSONArray().put("Tüccar Marco").put("Tüccar").put(mx + 420.0).put(my - 260.0).put(1))
            .put(org.json.JSONArray().put("Yaşlı Silas").put("Görevci").put(mx - 380.0).put(my - 120.0).put(2))
            .put(org.json.JSONArray().put("Doktor Elena").put("Doktor").put(mx - 120.0).put(my + 360.0).put(3))
            .put(org.json.JSONArray().put("Tamirci Viktor").put("Tamirci").put(mx + 90.0).put(my - 520.0).put(4))
        w.put("npcs", npcs)
        val marks = mapMarks(w, JSONObject()) + MapMark(mx + 900f, my - 1400f, 3, "Gizemli Ağaç")
        paparazzi.snapshot { WorldMapView(img, null, w, mx, my, 3, marks, {}) }
    }

    /** V8: keşfedilen devlet üsleri (devlet / başka klan / kendi klanın) */
    @Test
    fun worldMapBases() {
        val bmp: android.graphics.Bitmap = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/ui/map.png"))
        val img = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap()
        val w = sample("world")
        val b = sample("bases").getJSONArray("bases").getJSONObject(1)
        val all = org.json.JSONObject().put("bases", org.json.JSONArray().apply {
            for (i in 0 until 50) put(org.json.JSONObject(sample("bases").getJSONArray("bases").getJSONObject(i).toString()).put("found", true))
        })
        paparazzi.snapshot {
            WorldMapView(img, null, w, b.getDouble("x").toFloat(), b.getDouble("y").toFloat(), 30, mapMarks(w, JSONObject()) + baseMarks(all), {}, baseMarks(all).firstOrNull { it.icon == "map_war" })
        }
    }

    @Test
    fun npcDialog() {
        val d = sample("talk")
        // son satır + seçenekler görünsün diye tek satırlık örnek
        d.put("lines", org.json.JSONArray().put(d.getJSONArray("lines").getString(0)))
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF3A5A2A))) {
                NpcDialogView(d, {}, {}, instant = true)
            }
        }
    }

    @Test
    fun questsScreen() {
        val q = sample("quests")
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Box {
                QuestsView(q) {}
                QuestTracker(q.getJSONArray("story"), androidx.compose.ui.Modifier.padding(start = 300.dp, top = 300.dp)) {}
            }
        }
    }

    @Test
    fun kitScreen() {
        paparazzi.snapshot {
            ScreenFrame("ÜRETİM", "craft", {}, right = { Chip("coin", "1.250", Pal.Gold) }) {
                Tabs(listOf("shop" to "satın al", "coin" to "sat", "gem" to "elmas"), 0) {}
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Panel(androidx.compose.ui.Modifier.width(360.dp)) {
                        SectionTitle("Malzemeler")
                        T("Kumaş ve bant ile bandaj yapılır.", 14.sp, maxLines = 2)
                        Bar(0.6f, Pal.Green, androidx.compose.ui.Modifier.fillMaxWidth(), 14.dp, "3 / 5")
                        androidx.compose.foundation.layout.Row { ItemSlot("bandage", 3, 0, 56.dp); ItemSlot(null, 0, 0, 56.dp, label = "Boş") }
                    }
                    androidx.compose.foundation.layout.Column {
                        GButton("ÜRET", tone = Tone.Green, icon = "craft") {}
                        GButton("×5", tone = Tone.Blue) {}
                        GButton("Kilitli", enabled = false) {}
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- sıralama + profil
    private fun fakePhoto(): String {
        val img = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        for (y in 0 until 96) for (x in 0 until 96) img.setPixel(x, y, (0xFF shl 24) or ((x * 2) shl 16) or ((y * 2) shl 8) or 120)
        val bo = java.io.ByteArrayOutputStream(); img.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo)
        return java.util.Base64.getEncoder().encodeToString(bo.toByteArray())
    }

    private fun title(k: String, r: Int) = JSONObject().put("kind", k).put("rank", r).put("name", (BOARD_CATS.first { it.kind == k }.title) + " #" + r)

    @Test
    fun leaderboard() {
        val names = listOf("Karanlık", "Bozkurt", "Gölge", "Yılmaz", "ZeroOne", "Kaan", "Ateş", "Demir", "Fırtına", "Ayaz")
        val rows = org.json.JSONArray()
        names.forEachIndexed { i, n ->
            rows.put(JSONObject().put("rank", i + 1).put("id", 100 + i).put("name", n).put("avatar", i % 6).put("frame", listOf("gold", "silver", "bronze", "", "vip")[i % 5])
                .put("photo", if (i == 1) fakePhoto() else "").put("v", 52000L - i * 4100).put("clan", if (i % 3 == 0) "KRT" else "")
                .put("title", if (i < 5) title("kills", i + 1) else JSONObject.NULL).put("banned", i == 6).put("vip", i == 0))
        }
        paparazzi.snapshot { SceneBackground("bg_craft", null, dim = 0.3f) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { BoardDialog({}, rows) } } }
    }

    /** Klan seviye armaları 1-9: kalkan her seviyede aynı boyda, rakam ortada */
    @Test
    fun clanEmblems() {
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF2B2B26)).padding(16.dp)) {
                for (s in listOf(42, 72)) androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    for (l in 1..9) { ClanEmblem(l, if (l <= 3) l else 0, s.dp); androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(6.dp)) }
                }
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    for (r in 1..8) { RankMedal(r, if (r <= 3) 44.dp else 36.dp); androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(10.dp)) }
                }
            }
        }
    }

    /** 4.-10. sıralar (unvan rozetleri satırdan taşmamalı) */
    @Test
    fun leaderboardLower() {
        val names = listOf("Yılmaz", "ZeroOne", "Kaan", "Ateş", "Demir", "Fırtına", "Ayaz")
        val rows = org.json.JSONArray()
        names.forEachIndexed { k, n ->
            val i = k + 3
            rows.put(JSONObject().put("rank", i + 1).put("id", 100 + i).put("name", n).put("avatar", i % 6).put("frame", "")
                .put("photo", "").put("v", 52000L - i * 4100).put("clan", if (i % 3 == 0) "KRT" else "")
                .put("title", title("kills", i + 1)).put("banned", false).put("vip", false))
        }
        paparazzi.snapshot { SceneBackground("bg_craft", null, dim = 0.3f) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { BoardDialog({}, rows) } } }
    }

    @Test
    fun playerProfile() {
        val p = JSONObject().put("id", 1234567).put("name", "Karanlık").put("avatar", 0).put("frame", "gold").put("photo", fakePhoto()).put("vip", true)
            .put("level", 37).put("kills", 52310).put("hours", 214).put("online", true).put("title", title("kills", 1))
            .put("titles", org.json.JSONArray().put(title("kills", 1)).put(title("pvp", 3)).put(title("time", 7)))
            .put("stats", JSONObject().put("titan", 41).put("soldiers", 1820).put("bases", 12).put("dog", 3400).put("crafted", 980))
            .put("clan", JSONObject().put("name", "KRT").put("rank_name", "Lider").put("title", "Efsanevi Klan"))
        paparazzi.snapshot { SceneBackground("bg_craft", null, dim = 0.3f) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { PlayerProfileView(p, "", false, {}, {}) {} } } }
    }

    @Test
    fun playerProfileBanned() {
        val p = JSONObject().put("id", 7654321).put("name", "Hileci").put("avatar", 3).put("frame", "").put("level", 12).put("kills", 900).put("hours", 8)
            .put("banned", true).put("stats", JSONObject())
        paparazzi.snapshot { SceneBackground("bg_craft", null, dim = 0.3f) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { PlayerProfileView(p, "", false, {}, {}) {} } } }
    }

    @Test
    fun profileEditorTitles() {
        val p = JSONObject().put("id", 1234567).put("name", "Karanlık").put("avatar", 0).put("frame", "gold").put("photo", fakePhoto()).put("level", 41).put("kills", 52310)
            .put("hours", 214).put("title", title("kills", 1)).put("titles", org.json.JSONArray().put(title("kills", 1)).put(title("pvp", 3)).put(title("time", 7)))
        paparazzi.snapshot { SceneBackground("bg_craft", null, dim = 0.3f) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) { ProfileEditorView(p, {}, {}) { _, _ -> } } } }
    }

    @Test
    fun passRewards() {
        val now = 1_793_000_000_000L
        paparazzi.snapshot { PassView(sample("pass"), "Sezon 1: Kan Ve Pas", now + 6_480_000_000L, now, {}, {}, {}, { _, _, _ -> }) }
    }

    @Test
    fun passWeekly() {
        val now = 1_793_000_000_000L
        paparazzi.snapshot { PassView(sample("pass"), "Sezon 1: Kan Ve Pas", now + 6_480_000_000L, now, {}, {}, {}, { _, _, _ -> }, startTab = 2) }
    }

    @Test
    fun crateMain() {
        paparazzi.snapshot { CratePreview(sample("skins")) }
    }

    @Test
    fun crateReveal() {
        paparazzi.snapshot { CratePreview(sample("skins"), reveal = listOf("sk_frost", "medkit:5", "gold:1000", "potion_power:3", "vs_camo", "dark_crystal:2", "potion_heal:3", "chest_military:1", "titan_shard:2", "gold:1000")) }
    }

    @Test
    fun crateCollection() {
        paparazzi.snapshot { CratePreview(sample("skins"), tab = 1) }
    }

    @Test
    fun eventScreen() {
        val now = 1_793_000_000_000L
        val top = org.json.JSONArray()
        listOf("Karanlık", "Bozkurt", "Gölge", "Yılmaz", "ZeroOne", "Kaan", "Ateş", "Demir", "Fırtına", "Ayaz", "Toprak", "Bora").forEachIndexed { i, n ->
            top.put(JSONObject().put("name", n).put("id", 100 + i).put("score", 18450L - i * 1230))
        }
        val b = JSONObject().put("active", true).put("kind", "blood_moon").put("title", "Kan Ayı").put("ends", now + 9 * 86_400_000L + 4 * 3_600_000L)
            .put("my_score", 7390).put("my_rank", 23).put("top", top)
        paparazzi.snapshot { EventView(b, "", now, {}) }
    }

    @Test
    fun framesAndAvatars() {
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF1E1D19)).padding(18.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
            ) {
                for (row in FRAMES.chunked(3)) androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(40.dp)) {
                    row.forEach { f -> ProfileAvatar(listOf(1, 2, 3, 4, 5, 16)[FRAMES.indexOf(f) % 6], f.id, 120.dp) }
                }
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    for (a in listOf(1, 2, 3, 4, 5, 16)) ProfileAvatar(a, "", 90.dp)
                }
            }
        }
    }

    @Test
    fun framesLarge() {
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF1E1D19)).padding(40.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(60.dp),
            ) {
                ProfileAvatar(16, "blood", 220.dp)
                ProfileAvatar(4, "lv50", 220.dp)
                ProfileAvatar(5, "season1", 220.dp)
            }
        }
    }

    @Test
    fun playerMenu() {
        paparazzi.snapshot {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF3A4A2A))) {
                PlayerMenu("a42", "Bozkurt", {}, {}, {})
            }
        }
    }

    // ---- V16 kodla çizilen canlı sahneler
    private fun scene(kind: SceneKind, t: Float) = paparazzi.snapshot {
        LiveScene(kind, still = t) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.CenterStart).padding(start = 54.dp),
            ) { CodeLogo(150.dp) }
        }
    }
    @Test fun liveSceneEmber() = scene(SceneKind.EMBER, 12.1f)
    @Test fun liveSceneIce() = scene(SceneKind.ICE, 20.5f)
    @Test fun liveSceneVault() = scene(SceneKind.VAULT, 5.2f)

    /** Gerçek lobi ekranı (canlı sahne + logo + oyuncu kartı) */
    @Test fun lobbyScreenLive() {
        org.junit.Assume.assumeTrue("libzenvex.so yok", runCatching { com.zenvex.online.Native.hashCode(); true }.getOrDefault(false))
        com.zenvex.online.Demo.on = true
        com.zenvex.online.GameSession.initPreview(paparazzi.context)
        com.zenvex.online.Account.previewLogin(1234567L)
        paparazzi.snapshot { LobbyScreen({}, 0) }
    }
}
