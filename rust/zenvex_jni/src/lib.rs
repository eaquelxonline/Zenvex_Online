//! Kotlin `com.zenvex.online.game.Native` nesnesinin JNI uygulaması.
//! Oyun nesnesi `Box<Game>` olarak tutulur, Kotlin'e `Long` tutamaç olarak verilir.
#![allow(non_snake_case)]

use jni::errors::{Result as JResult, ThrowRuntimeExAndDefault};
use jni::objects::{JByteArray, JClass, JFloatArray, JIntArray, JString};
use jni::sys::{jfloat, jint, jlong};
use jni::{Env, EnvUnowned};
use std::cell::RefCell;
use zenvex_core::game::{Remote, STRIDE};
use zenvex_core::{Game, Input};

/// Uzak oyuncu kaydı: x, y, yön, anim, anim_t, karakter, ekipman0..2, araç
pub const REMOTE_STRIDE: usize = 10;
/// Yerel oyuncu kaydı (çevrimiçi yayın): aynı sıra
pub const LOCAL_STRIDE: usize = 10;

thread_local! {
    static DRAW_BUF: RefCell<Vec<f32>> = RefCell::new(vec![0.0; STRIDE * 6000]);
    static PIX_BUF: RefCell<Vec<u32>> = const { RefCell::new(Vec::new()) };
}

fn game<'a>(h: jlong) -> Option<&'a mut Game> {
    if h == 0 {
        None
    } else {
        // SAFETY: tutamaç `create` ile oluşturuldu ve `destroy` çağrılana kadar geçerli;
        // Kotlin tarafı tüm çağrıları tek bir kilit altında yapar.
        Some(unsafe { &mut *(h as *mut Game) })
    }
}

/// jni 0.22: yerel yöntem gövdesi `Env` ile çalışır; hata ya da panik Java'ya RuntimeException olarak
/// döner (JVM'i çökertmez) ve varsayılan değer (0 / null) geri verilir.
fn with_env<'l, T: Default + 'l>(env: &mut EnvUnowned<'l>, f: impl FnOnce(&mut Env<'l>) -> JResult<T>) -> T {
    env.with_env(f).resolve::<ThrowRuntimeExAndDefault>()
}

fn jstr<'l>(env: &mut Env<'l>, s: &str) -> JResult<JString<'l>> {
    env.new_string(s)
}

fn rstr(env: &Env, s: &JString) -> String {
    s.try_to_string(env).unwrap_or_default()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_create<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    seed: jlong,
    save: JString<'l>,
    day: jint,
) -> jlong {
    with_env(&mut unowned, |env| -> JResult<jlong> {
        let save = rstr(env, &save);
        let progress = if save.is_empty() {
            zenvex_core::Progress::default()
        } else {
            zenvex_core::progress_from_json(&save)
        };
        Ok(Box::into_raw(Box::new(Game::new(
            seed as u64,
            progress,
            day.max(0) as u32,
        ))) as jlong)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_destroy<'l>(
    _env: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
) {
    if h != 0 {
        // SAFETY: `create` tarafından Box::into_raw ile üretildi, yalnızca bir kez serbest bırakılır.
        drop(unsafe { Box::from_raw(h as *mut Game) });
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_worldJson<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
) -> JString<'l> {
    with_env(&mut unowned, |env| -> JResult<JString<'l>> {
        let s = game(h)
            .map(|g| zenvex_core::world_json(g))
            .unwrap_or_default();
        jstr(env, &s)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_uiJson<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    kind: JString<'l>,
) -> JString<'l> {
    with_env(&mut unowned, |env| -> JResult<JString<'l>> {
        let kind = rstr(env, &kind);
        let s = game(h)
            .map(|g| g.ui_json(&kind))
            .unwrap_or_else(|| "{}".into());
        jstr(env, &s)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_command<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    cmd: JString<'l>,
) -> JString<'l> {
    with_env(&mut unowned, |env| -> JResult<JString<'l>> {
        let cmd = rstr(env, &cmd);
        let s = game(h)
            .map(|g| g.command(&cmd))
            .unwrap_or_else(|| "{\"ok\":false,\"msg\":\"oyun yok\"}".into());
        jstr(env, &s)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_step<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    dt: jfloat,
    input: JFloatArray<'l>,
) {
    with_env(&mut unowned, |env| -> JResult<()> {
        let mut v = [0.0f32; 5];
        if input.get_region(env, 0, &mut v).is_err() {
            return Ok(());
        }
        if let Some(g) = game(h) {
            g.step(
                dt,
                Input {
                    move_x: v[0],
                    move_y: v[1],
                    atk_x: v[2],
                    atk_y: v[3],
                    attacking: v[4] > 0.5,
                },
            );
        }
        Ok(())
    })
}

/// view: [x, y, w, h, bitki_yoğunluğu]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_drawList<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    view: JFloatArray<'l>,
    out: JFloatArray<'l>,
) -> jint {
    with_env(&mut unowned, |env| -> JResult<jint> {
        let Some(g) = game(h) else { return Ok(0) };
        let mut v = [0.0f32; 5];
        if view.get_region(env, 0, &mut v).is_err() {
            return Ok(0);
        }
        let cap = out.len(env).unwrap_or(0);
        Ok(DRAW_BUF.with(|b| {
            let mut buf = b.borrow_mut();
            let len = (cap.min(buf.len()) / STRIDE) * STRIDE;
            let n = g.draw_list(v[0], v[1], v[2], v[3], &mut buf[..len], v[4]);
            let _ = out.set_region(env, 0, &buf[..n * STRIDE]);
            n as jint
        }))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_hud<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    out: JFloatArray<'l>,
) {
    with_env(&mut unowned, |env| -> JResult<()> {
        if let Some(g) = game(h) {
            let _ = out.set_region(env, 0, &g.hud());
        }
        Ok(())
    })
}

/// Radar kayıtları (x, y, tür) üçlüleri; dönen değer kayıt sayısı.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_radar<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    out: JFloatArray<'l>,
) -> jint {
    with_env(&mut unowned, |env| -> JResult<jint> {
        let Some(g) = game(h) else { return Ok(0) };
        let cap = out.len(env).unwrap_or(0);
        let mut buf = vec![0.0f32; cap];
        let n = g.radar(&mut buf);
        let _ = out.set_region(env, 0, &buf[..n * 3]);
        Ok(n as jint)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_events<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
) -> JString<'l> {
    with_env(&mut unowned, |env| -> JResult<JString<'l>> {
        let s = game(h)
            .map(|g| g.take_events_json())
            .unwrap_or_else(|| "[]".into());
        jstr(env, &s)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_saveJson<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
) -> JString<'l> {
    with_env(&mut unowned, |env| -> JResult<JString<'l>> {
        let s = game(h)
            .map(|g| {
                g.sync_pos();
                zenvex_core::progress_to_json(&g.progress)
            })
            .unwrap_or_default();
        jstr(env, &s)
    })
}

/// Zemin dokuları: `size`x`size` ARGB, world::ground sırasıyla art arda.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_setTextures<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    size: jint,
    pixels: JIntArray<'l>,
) {
    with_env(&mut unowned, |env| -> JResult<()> {
        let Some(g) = game(h) else { return Ok(()) };
        let size = size.max(1) as usize;
        let len = pixels.len(env).unwrap_or(0);
        let mut all = vec![0i32; len];
        if pixels.get_region(env, 0, &mut all).is_err() {
            return Ok(());
        }
        let per = size * size;
        g.textures.size = size;
        g.textures.tex = all
            .chunks(per)
            .filter(|c| c.len() == per)
            .map(|c| c.iter().map(|&p| p as u32).collect())
            .collect();
        Ok(())
    })
}

/// V13 bölge dokuları (ana, ikinci, yol, boşluk; `size`x`size` ARGB art arda) ve ait oldukları bölge.
/// Ortak dokularla aynı boyda olmalı; ışınlanınca yenisi gönderilir.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_setRegionTextures<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    area: jint,
    size: jint,
    pixels: JIntArray<'l>,
) {
    with_env(&mut unowned, |env| -> JResult<()> {
        let Some(g) = game(h) else { return Ok(()) };
        let size = size.max(1) as usize;
        if g.textures.size != size {
            return Ok(());
        }
        let len = pixels.len(env).unwrap_or(0);
        let mut all = vec![0i32; len];
        if pixels.get_region(env, 0, &mut all).is_err() {
            return Ok(());
        }
        let base = zenvex_core::world::ground::BASE_COUNT;
        g.textures.tex.truncate(base);
        let per = size * size;
        for c in all.chunks(per).take(4) {
            g.textures.tex.push(if c.len() == per { c.iter().map(|&p| p as u32).collect() } else { Vec::new() });
        }
        g.textures.area = if area >= 0 { Some(area as usize) } else { None };
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_renderChunk<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    x0: jfloat,
    y0: jfloat,
    size: jint,
    out: JIntArray<'l>,
) -> jint {
    with_env(&mut unowned, |env| -> JResult<jint> {
        let Some(g) = game(h) else { return Ok(0) };
        if !g.textures.ready() {
            return Ok(0);
        }
        let size = size.max(1) as usize;
        Ok(PIX_BUF.with(|b| {
            let mut buf = b.borrow_mut();
            buf.resize(size * size, 0);
            zenvex_core::ground::render_chunk(&g.world, &g.textures, x0, y0, size, &mut buf);
            // SAFETY: u32 ve i32 aynı boyut ve hizalamaya sahip.
            let ints: &[i32] =
                unsafe { std::slice::from_raw_parts(buf.as_ptr() as *const i32, buf.len()) };
            let _ = out.set_region(env, 0, ints);
            1
        }))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_minimap<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    n: jint,
    out: JIntArray<'l>,
) {
    with_env(&mut unowned, |env| -> JResult<()> {
        let Some(g) = game(h) else { return Ok(()) };
        let n = n.max(1) as usize;
        let mut buf = vec![0u32; n * n];
        zenvex_core::minimap(g, n, &mut buf);
        let ints: Vec<i32> = buf.iter().map(|&p| p as i32).collect();
        let _ = out.set_region(env, 0, &ints);
        Ok(())
    })
}

/// Uzak oyuncular: REMOTE_STRIDE'lık kayıtlar.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_setRemotes<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    data: JFloatArray<'l>,
    count: jint,
) {
    with_env(&mut unowned, |env| -> JResult<()> {
        let Some(g) = game(h) else { return Ok(()) };
        let n = count.max(0) as usize;
        let mut v = vec![0.0f32; n * REMOTE_STRIDE];
        if n > 0 && data.get_region(env, 0, &mut v).is_err() {
            return Ok(());
        }
        g.remotes = v
            .chunks(REMOTE_STRIDE)
            .map(|r| Remote {
                x: r[0],
                y: r[1],
                dir: r[2] as u8 & 7,
                anim: r[3] as u8,
                anim_t: r[4],
                character: r[5] as u8,
                equip: [r[6], r[7], r[8]],
                vehicle: r[9],
            })
            .collect();
        Ok(())
    })
}

/// Yerel oyuncu durumu (çevrimiçi yayın için), LOCAL_STRIDE uzunluğunda.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_localState<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    out: JFloatArray<'l>,
) {
    with_env(&mut unowned, |env| -> JResult<()> {
        let Some(g) = game(h) else { return Ok(()) };
        let p = &g.player;
        let eq = g.equip_codes();
        let veh = p.vehicle.map_or(-1.0, |i| g.vehicles[i].kind as f32);
        let (anim, clock) = g.anim_state();
        let rec: [f32; LOCAL_STRIDE] = [
            p.x,
            p.y,
            p.dir as f32,
            anim as f32,
            clock,
            g.progress.character as f32,
            eq[0],
            eq[1],
            eq[2],
            veh,
        ];
        let _ = out.set_region(env, 0, &rec);
        Ok(())
    })
}

/// Ev sahibi: zombi anlık görüntüsü (net::NET_STRIDE kayıtlar); kayıt sayısı döner.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_netSnapshot<'l>(mut unowned: EnvUnowned<'l>, _c: JClass<'l>, h: jlong, out: JFloatArray<'l>) -> jint {
    with_env(&mut unowned, |env| -> JResult<jint> {
        let Some(g) = game(h) else { return Ok(0) };
        let cap = out.len(env).unwrap_or(0);
        let mut buf = vec![0.0f32; cap];
        let n = g.net_snapshot(&mut buf);
        let _ = out.set_region(env, 0, &buf[..n * zenvex_core::net::NET_STRIDE]);
        Ok(n as jint)
    })
}

/// İstemci: ev sahibinin görüntüsünü uygula.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_netApply<'l>(mut unowned: EnvUnowned<'l>, _c: JClass<'l>, h: jlong, data: JFloatArray<'l>, count: jint) {
    with_env(&mut unowned, |env| -> JResult<()> {
        let Some(g) = game(h) else { return Ok(()) };
        let n = count.max(0) as usize * zenvex_core::net::NET_STRIDE;
        let mut v = vec![0.0f32; n];
        if n > 0 && data.get_region(env, 0, &mut v).is_err() {
            return Ok(());
        }
        g.net_apply(&v);
        Ok(())
    })
}

/// V15 gerçek harita: bölgenin zemini oyunun kendi dokularıyla küçük ölçekte (`w`x`hh`, piksel başına `step` dünya pikseli).
/// Bölge dokuları yüklü değilse 0 döner (çağıran sonra yeniden dener).
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_renderRegion<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    x0: jfloat,
    y0: jfloat,
    step: jfloat,
    w: jint,
    hh: jint,
    out: JIntArray<'l>,
) -> jint {
    with_env(&mut unowned, |env| -> JResult<jint> {
        let Some(g) = game(h) else { return Ok(0) };
        if !g.textures.ready() {
            return Ok(0);
        }
        let (w, hh) = (w.max(1) as usize, hh.max(1) as usize);
        let mut buf = vec![0u32; w * hh];
        zenvex_core::ground::render_scaled(&g.world, &g.textures, x0, y0, step.max(0.1), w, hh, &mut buf);
        let ints: Vec<i32> = buf.iter().map(|&p| p as i32).collect();
        let _ = out.set_region(env, 0, &ints);
        Ok(1)
    })
}

/// V15 gerçek harita: dikdörtgendeki sabit nesneler (kırılmamış) — kayıt başına [sprite, x, y, tür]; yazılan kayıt sayısı döner.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_regionStatics<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    h: jlong,
    x0: jfloat,
    y0: jfloat,
    x1: jfloat,
    y1: jfloat,
    out: JFloatArray<'l>,
) -> jint {
    with_env(&mut unowned, |env| -> JResult<jint> {
        let Some(g) = game(h) else { return Ok(0) };
        let cap = out.len(env).unwrap_or(0) / 4;
        let mut v: Vec<f32> = Vec::with_capacity(cap.min(g.world.statics.len()) * 4);
        for s in &g.world.statics {
            if v.len() / 4 >= cap {
                break;
            }
            if s.broken || s.x < x0 || s.x >= x1 || s.y < y0 || s.y >= y1 {
                continue;
            }
            v.extend_from_slice(&[s.sprite as f32, s.x, s.y, s.kind as u8 as f32]);
        }
        let _ = out.set_region(env, 0, &v);
        Ok((v.len() / 4) as jint)
    })
}

// ============================================================================ 3B çizici
// Kotlin `Model3D` yalnız kendi iş parçacığından çağırır; yine de kilitle korunur.
static R3D: std::sync::Mutex<Option<zenvex_r3d::service::Service>> = std::sync::Mutex::new(None);

fn r3d<T>(f: impl FnOnce(&mut zenvex_r3d::service::Service) -> T) -> T {
    let mut g = R3D.lock().unwrap_or_else(|e| e.into_inner());
    f(g.get_or_insert_with(zenvex_r3d::service::Service::new))
}

/// Model yükler: .zxm baytları + doku (ARGB tamsayı, Bitmap.getPixels düzeni)
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_r3dLoad<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    name: JString<'l>,
    zxm: JByteArray<'l>,
    tw: jint,
    th: jint,
    argb: JIntArray<'l>,
) -> jint {
    with_env(&mut unowned, |env| -> JResult<jint> {
        let name = rstr(env, &name);
        let bytes = env.convert_byte_array(&zxm).unwrap_or_default();
        let n = (tw.max(0) * th.max(0)) as usize;
        let mut px = vec![0i32; n];
        if n > 0 && argb.get_region(env, 0, &mut px).is_err() {
            return Ok(0);
        }
        let mut rgba = Vec::with_capacity(n * 4);
        for p in px {
            let p = p as u32;
            rgba.extend_from_slice(&[(p >> 16) as u8, (p >> 8) as u8, p as u8, (p >> 24) as u8]);
        }
        Ok(match r3d(|s| s.load(&name, &bytes, tw as usize, th as usize, &rgba)) {
            Ok(()) => 1,
            Err(_) => 0,
        })
    })
}

/// Modelin normal haritası (ARGB). dönüş 1: yüklendi
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_r3dNormal<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    name: JString<'l>,
    tw: jint,
    th: jint,
    argb: JIntArray<'l>,
) -> jint {
    with_env(&mut unowned, |env| -> JResult<jint> {
        let name = rstr(env, &name);
        let n = (tw.max(0) * th.max(0)) as usize;
        let mut px = vec![0i32; n];
        if n == 0 || argb.get_region(env, 0, &mut px).is_err() {
            return Ok(0);
        }
        let mut rgba = Vec::with_capacity(n * 4);
        for p in px {
            let p = p as u32;
            rgba.extend_from_slice(&[(p >> 16) as u8, (p >> 8) as u8, p as u8, 255]);
        }
        Ok(r3d(|s| s.load_normal(&name, tw as usize, th as usize, &rgba)) as jint)
    })
}

fn put_image(env: &Env, img: &zenvex_r3d::Image, pts: &zenvex_r3d::service::Points, out: &JIntArray, info: &JFloatArray) -> jint {
    let cap = out.len(env).unwrap_or(0);
    let n = img.w * img.h;
    if n > cap {
        // çağıran daha büyük dizi ayırsın: gereken boyutu bildir
        let _ = info.set_region(env, 0, &[img.w as f32, img.h as f32]);
        return -(n as jint);
    }
    let px: Vec<i32> = img.rgba.iter().map(|c| ((c[3] as u32) << 24 | (c[0] as u32) << 16 | (c[1] as u32) << 8 | c[2] as u32) as i32).collect();
    let _ = out.set_region(env, 0, &px);
    let mut inf = [0f32; 4 + zenvex_r3d::service::Points::LEN];
    inf[0] = img.w as f32;
    inf[1] = img.h as f32;
    inf[2] = img.ax;
    inf[3] = img.ay;
    pts.write(&mut inf[4..]);
    let _ = info.set_region(env, 0, &inf);
    n as jint
}

/// Karakter karesi. p: [anim, t, period, yaw, pitch, scale, ssaa (kenar yumuşatma çarpanı 1-3), flags]; dönüş: piksel sayısı (eksi: dizi küçük)
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_r3dActor<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    ch: JString<'l>,
    weapon: JString<'l>,
    p: JFloatArray<'l>,
    out: JIntArray<'l>,
    info: JFloatArray<'l>,
) -> jint {
    with_env(&mut unowned, |env| -> JResult<jint> {
        let ch = rstr(env, &ch);
        let weapon = rstr(env, &weapon);
        let mut a = [0f32; 8];
        if p.get_region(env, 0, &mut a).is_err() {
            return Ok(0);
        }
        let req = zenvex_r3d::service::ActorReq {
            ch: &ch,
            weapon: &weapon,
            anim: a[0] as u8,
            t: a[1],
            period: a[2],
            yaw: a[3],
            pitch: a[4],
            scale: a[5],
            ssaa: (a[6].round() as i32).clamp(1, 3) as u8,
            flags: a[7] as u32,
        };
        Ok(match r3d(|s| s.actor(&req)) {
            Some((img, pts)) => put_image(env, &img, &pts, &out, &info),
            None => 0,
        })
    })
}

/// Araç karesi. p: [yaw, pitch, scale, wheel, bounce, tilt, rider_t, ssaa (1-3)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_zenvex_online_game_Native_r3dVehicle<'l>(
    mut unowned: EnvUnowned<'l>,
    _c: JClass<'l>,
    name: JString<'l>,
    rider: JString<'l>,
    p: JFloatArray<'l>,
    out: JIntArray<'l>,
    info: JFloatArray<'l>,
) -> jint {
    with_env(&mut unowned, |env| -> JResult<jint> {
        let name = rstr(env, &name);
        let rider = rstr(env, &rider);
        let mut a = [0f32; 8];
        if p.get_region(env, 0, &mut a).is_err() {
            return Ok(0);
        }
        let req = zenvex_r3d::service::VehicleReq {
            name: &name,
            yaw: a[0],
            pitch: a[1],
            scale: a[2],
            wheel: a[3],
            bounce: a[4],
            tilt: a[5],
            rider: &rider,
            rider_t: a[6],
            ssaa: (a[7].round() as i32).clamp(1, 3) as u8,
        };
        Ok(match r3d(|s| s.vehicle(&req)) {
            Some((img, pts)) => put_image(env, &img, &pts, &out, &info),
            None => 0,
        })
    })
}
