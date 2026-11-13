//! 3B model önizleme ve simge araçları (geliştirme; oyuna girmez). Tek örnek, alt komutlu:
//! cargo run --release --example preview -- <anims|icons|render|sheet|vehicles> <argümanlar...>

/// Alt komut çıkarılmış argümanlar (her aracın kendi `main`i ilk argümanı program adı sayar)
fn args() -> std::vec::IntoIter<String> {
    let mut a: Vec<String> = std::env::args().collect();
    if a.len() > 1 {
        a.remove(1);
    }
    a.into_iter()
}

fn main() {
    let cmd = std::env::args().nth(1).unwrap_or_default();
    match cmd.as_str() {
        "anims" => anims::main(),
        "icons" => icons::main(),
        "render" => render::main(),
        "sheet" => sheet::main(),
        "vehicles" => vehicles::main(),
        _ => eprintln!("kullanım: preview <anims|icons|render|sheet|vehicles> ..."),
    }
}

mod anims {
    //! Animasyon önizleme sayfası:
    //! cargo run --release --example preview -- anims <m3d> <çıktı.png> <karakter> <silah|-> <anim,anim,...> [yaw] [kare]
    //! Her satır bir animasyon, sütunlar zaman içinde eşit aralıklı kareler.
    use std::path::Path;
    use zenvex::r3d::anim::{self, a, In, Style, Wk};
    use zenvex::r3d::model::{Model, Texture};
    use zenvex::r3d::raster::Light;
    use zenvex::r3d::rig::{self, Skeleton};
    use zenvex::r3d::{render, transform_model, Item, Settings};

    fn load(dir: &Path, name: &str) -> Model {
        let mut m = Model::parse(&std::fs::read(dir.join(format!("{name}.zxm"))).unwrap()).unwrap();
        let im = image::open(dir.join(format!("{name}.webp"))).unwrap().to_rgba8();
        m.tex = Some(Texture::from_rgba(im.width() as usize, im.height() as usize, im.as_raw()));
        m
    }

    fn anim_id(s: &str) -> (u8, f32) {
        // (kimlik, önizlenecek süre)
        match s.trim_end_matches("_e").trim_end_matches("_i") {
            "idle" => (a::IDLE, 3.0),
            "walk" => (a::WALK, 0.9),
            "run" => (a::RUN, 0.7),
            "punch" => (a::PUNCH, 0.58),
            "melee" | "m0" => (a::MELEE, 0.7),
            "melee2" | "m1" => (a::MELEE, 0.7),
            "m2" => (a::MELEE, 0.7),
            "fire" => (a::FIRE, 0.3),
            "reload" => (a::RELOAD, 1.6),
            "roll" => (a::ROLL, 0.32),
            "death" => (a::DEATH, 0.9),
            "knock" => (a::KNOCK, 0.6),
            "getup" => (a::GETUP, 0.5),
            "hurt" => (a::HURT, 0.18),
            "ride" => (a::RIDE, 1.0),
            "chop" => (a::CHOP, 0.9),
            "mine" => (a::MINE, 0.9),
            "pickup" => (a::PICKUP, 1.0),
            "drink" => (a::DRINK, 1.0),
            "cheer" => (a::CHEER, 1.0),
            "cast" => (a::CAST, 1.0),
            _ => (a::IDLE, 1.0),
        }
    }

    pub fn main() {
        let args: Vec<String> = crate::args().collect();
        let dir = Path::new(&args[1]);
        let ch = &args[3];
        let m = load(dir, ch);
        let sk = Skeleton::of(&m);
        let anims: Vec<&str> = args[5].split(',').collect();
        let yaw: f32 = args.get(6).and_then(|s| s.parse().ok()).unwrap_or(35.0);
        let nf: usize = args.get(7).and_then(|s| s.parse().ok()).unwrap_or(8);
        let mut cache: std::collections::HashMap<String, Model> = Default::default();
        let base_w = if args[4] == "-" { Wk::None } else { Wk::from_model(&args[4]) };
        let light = Light::default();
        let mut rows: Vec<Vec<zenvex::r3d::Image>> = Vec::new();
        let t0 = std::time::Instant::now();
        let mut n = 0;
        for an in &anims {
            let (id, dur) = anim_id(an);
            let mut row = Vec::new();
            for f in 0..nf {
                let off = match *an { "m1" => 0.7, "m2" => 1.4, "melee2" => 0.7, _ => 0.0 };
                let t = off + dur * f as f32 / nf as f32;
                let inp = In::new(id, t, if an.starts_with("melee") || an.starts_with('m') && an.len() == 2 { 0.7 } else if *an == "fire" { 0.3 } else { 1.6 }, base_w, Style::of(ch));
                let mut inp = inp;
                if an.ends_with("_e") { inp.flags |= anim::F_EMPTY; }
                if an.ends_with("_i") { inp.flags |= anim::F_IRON; }
                let shown_name = anim::key_of(&inp, 1.0).shown.model();
                if !shown_name.is_empty() && !cache.contains_key(shown_name) {
                    cache.insert(shown_name.to_string(), load(dir, shown_name));
                }
                if let Some(wm) = cache.get(shown_name) {
                    inp.wback = -wm.meta.back;
                    if let Some(pd) = wm.part("mag") {
                        inp.pivot = zenvex::r3d::math::V3::from(pd.pivot);
                    }
                }
                let out = anim::evaluate(&sk, &inp);
                let (mut p, mut nn) = (Vec::new(), Vec::new());
                rig::skin(&m, &out.world, &mut p, &mut nn);
                let (mut wp, mut wn) = (Vec::new(), Vec::new());
                let mut items = vec![Item { model: &m, pos: &p, nrm: &nn, range: None, id: 1, glow: 0.0 }];
                let wmodel = cache.get(shown_name);
                if let (Some(xf), Some(wm)) = (out.weapon, wmodel) {
                    transform_model(wm, &xf, &mut wp, &mut wn);
                    let rng = match (out.mag_hidden, wm.part("mag")) {
                        (true, Some(pd)) => Some((0, pd.start as usize)),
                        _ => None,
                    };
                    items.push(Item { model: wm, pos: &wp, nrm: &wn, range: rng, id: 2, glow: out.shown.glow() });
                }
                let (mut mp, mut mn) = (Vec::new(), Vec::new());
                if let (Some(xf), Some(wm)) = (out.mag, wmodel)
                    && let Some(pd) = wm.part("mag") {
                    let piv = zenvex::r3d::math::V3::from(pd.pivot);
                    let xf2 = zenvex::r3d::math::Xf::new(xf.r, xf.t - xf.r.apply(piv));
                    transform_model(wm, &xf2, &mut mp, &mut mn);
                    items.push(Item { model: wm, pos: &mp, nrm: &mn, range: Some((pd.start as usize, pd.count as usize)), id: 3, glow: 0.0 });
                }
                let s = Settings { yaw, pitch: 22.0, scale: 95.0, ssaa: 1, outline_px: 1 };
                row.push(render(&items, &s, &light));
                n += 1;
            }
            rows.push(row);
        }
        eprintln!("{} kare, ortalama {:?}", n, t0.elapsed() / n as u32);
        let cell_w = rows.iter().flatten().map(|i| i.w).max().unwrap() + 6;
        // çapanın altına taşan (zeminin altı) en büyük kısım: hücrede yer ayır, zemin çizgisi çiz
        let below = rows.iter().flatten().map(|i| i.h as i32 - i.ay as i32).max().unwrap().max(0) as usize;
        let above = rows.iter().flatten().map(|i| i.ay as usize).max().unwrap();
        let cell_h = above + below + 12;
        eprintln!("zemin altı en çok {below} px");
        let mut out = image::RgbaImage::from_pixel((cell_w * nf) as u32, (cell_h * rows.len()) as u32, image::Rgba([72, 92, 72, 255]));
        for (r, row) in rows.iter().enumerate() {
            for (c, im) in row.iter().enumerate() {
                // çapa (ayak) hücrede aynı yerde
                let ox = (c * cell_w + cell_w / 2) as i32 - im.ax as i32;
                let gy = (r * cell_h + above + 6) as u32;
                for x in (c * cell_w) as u32..((c + 1) * cell_w) as u32 {
                    out.put_pixel(x, gy, image::Rgba([40, 52, 40, 255]));
                }
                let oy = gy as i32 - im.ay as i32;
                for yy in 0..im.h {
                    for xx in 0..im.w {
                        let px = im.rgba[yy * im.w + xx];
                        let (x, y) = (ox + xx as i32, oy + yy as i32);
                        if px[3] > 0 && x >= 0 && y >= 0 && (x as u32) < out.width() && (y as u32) < out.height() {
                            out.put_pixel(x as u32, y as u32, image::Rgba(px));
                        }
                    }
                }
            }
        }
        out.save(&args[2]).unwrap();
    }
}

mod icons {
    //! Silah simgeleri: her silah modeli çapraz (sap sol altta, uç sağ üstte) önden çizilir.
    //! cargo run --release --example preview -- icons <m3d_klasörü> <çıktı_klasörü> [ad:dönme ...]
    //! Dönme (derece): silahın kendi ekseni etrafında; yassı yüzü (bıçak ağzı, namlu yanı) kameraya çevirir.
    use std::path::Path;
    use zenvex::r3d::math::{v3, M3, Xf};
    use zenvex::r3d::model::{Model, Texture};
    use zenvex::r3d::raster::Light;
    use zenvex::r3d::{render, transform_model, Item, Settings};

    const WEAPONS: &[&str] = &[
        "knife", "hatchet", "handaxe", "club", "effigy", "katana", "void_claw", "blood_scythe", "ice_scythe", "magma_hammer", "storm_axe",
        "pickaxe", "chainsaw", "pistol", "uzi", "ak47", "sniper", "rpg",
    ];

    fn load(dir: &Path, name: &str) -> Model {
        let mut m = Model::parse(&std::fs::read(dir.join(format!("{name}.zxm"))).unwrap()).unwrap();
        let im = image::open(dir.join(format!("{name}.webp"))).unwrap().to_rgba8();
        m.tex = Some(Texture::from_rgba(im.width() as usize, im.height() as usize, im.as_raw()));
        m
    }

    fn glow(name: &str) -> f32 {
        match name {
            "magma_hammer" => 0.18,
            "ice_scythe" => 0.12,
            "void_claw" => 0.10,
            "chainsaw" | "uzi" => 0.08,
            "storm_axe" => 0.06,
            _ => 0.0,
        }
    }

    pub fn main() {
        let a: Vec<String> = crate::args().collect();
        let dir = Path::new(&a[1]);
        let out = Path::new(&a[2]);
        std::fs::create_dir_all(out).unwrap();
        let mut roll: std::collections::HashMap<String, f32> = Default::default();
        for x in &a[3..] {
            if let Some((n, r)) = x.split_once(':') {
                roll.insert(n.to_string(), r.parse().unwrap_or(0.0));
            }
        }
        let light = Light::default();
        for &name in WEAPONS {
            let m = load(dir, name);
            let r = roll.get(name).copied().unwrap_or(0.0);
            // ateşli silahlar yatay (namlu sağa), diğerleri çapraz (uç sağ üst)
            let gun = matches!(name, "pistol" | "uzi" | "ak47" | "sniper" | "rpg");
            let ang: f32 = if gun { 0.0 } else { 45.0 };
            let fwd = v3(ang.to_radians().cos(), 0.0, ang.to_radians().sin());
            let up0 = v3(-ang.to_radians().sin(), 0.0, ang.to_radians().cos());
            let up = M3::axis_angle(fwd, r.to_radians()).apply(up0);
            let b = M3::basis(fwd, up);
            let (mut p, mut n) = (Vec::new(), Vec::new());
            transform_model(&m, &Xf::new(b, v3(0.0, 0.0, 0.0)), &mut p, &mut n);
            // en uzun kenar ~300 px (ssaa sonrası); sonra paketleyici hücreye sığdırır
            let (mut x0, mut x1, mut z0, mut z1) = (f32::MAX, f32::MIN, f32::MAX, f32::MIN);
            for q in &p {
                x0 = x0.min(q.x);
                x1 = x1.max(q.x);
                z0 = z0.min(q.z);
                z1 = z1.max(q.z);
            }
            let ext = (x1 - x0).max(z1 - z0).max(0.05);
            let s = Settings { yaw: 0.0, pitch: 0.0, scale: 300.0 / ext, ssaa: 2, outline_px: 1 };
            let items = [Item { model: &m, pos: &p, nrm: &n, range: None, id: 1, glow: glow(name) }];
            let img = render(&items, &s, &light);
            let mut im = image::RgbaImage::new(img.w as u32, img.h as u32);
            for (i, px) in img.rgba.iter().enumerate() {
                im.put_pixel((i % img.w) as u32, (i / img.w) as u32, image::Rgba(*px));
            }
            im.save(out.join(format!("{name}.png"))).unwrap();
            println!("{name}: {}x{}", img.w, img.h);
        }
    }
}

mod render {
    //! Masaüstü önizleme: cargo run --release --example preview -- render <m3d_klasörü> <çıktı.png> [karakter] [silah]
    use std::path::Path;
    use zenvex::r3d::math::{v3, M3, Xf};
    use zenvex::r3d::model::{Model, Texture};
    use zenvex::r3d::raster::Light;
    use zenvex::r3d::rig::{self, Pose, Skeleton};
    use zenvex::r3d::{render, transform_model, Item, Settings};

    fn load(dir: &Path, name: &str) -> Model {
        let mut m = Model::parse(&std::fs::read(dir.join(format!("{name}.zxm"))).unwrap()).unwrap();
        let im = image::open(dir.join(format!("{name}.webp"))).unwrap().to_rgba8();
        m.tex = Some(Texture::from_rgba(im.width() as usize, im.height() as usize, im.as_raw()));
        m
    }

    pub fn main() {
        let a: Vec<String> = crate::args().collect();
        let dir = Path::new(&a[1]);
        let ch = a.get(3).map(|s| s.as_str()).unwrap_or("kael");
        let wp = a.get(4).map(|s| s.as_str());
        let m = load(dir, ch);
        let w = wp.map(|n| load(dir, n));
        let sk = Skeleton::of(&m);
        let pose = Pose::default();
        let xf = sk.world(&pose);
        let (mut p, mut n) = (Vec::new(), Vec::new());
        rig::skin(&m, &xf, &mut p, &mut n);
        let (mut wpv, mut wn) = (Vec::new(), Vec::new());
        if let Some(wm) = &w {
            // silah sağ avuçta, ucu öne-aşağı
            let palm = xf[rig::HAND_R].apply(sk.palm_r);
            let b = M3::basis(v3(0.0, -0.8, -0.5), v3(0.0, -0.5, 0.8));
            transform_model(wm, &Xf::new(b, palm), &mut wpv, &mut wn);
        }
        let light = Light::default();
        let mut imgs = Vec::new();
        let t0 = std::time::Instant::now();
        for d in 0..8 {
            let s = Settings { yaw: d as f32 * 45.0, pitch: 22.0, scale: 120.0, ssaa: 1, outline_px: 1 };
            let mut items = vec![Item { model: &m, pos: &p, nrm: &n, range: None, id: 1, glow: 0.0 }];
            if let Some(wm) = &w {
                items.push(Item { model: wm, pos: &wpv, nrm: &wn, range: None, id: 2, glow: 0.0 });
            }
            imgs.push(render(&items, &s, &light));
        }
        println!("8 kare: {:?}", t0.elapsed());
        let t1 = std::time::Instant::now();
        for _ in 0..50 {
            let s = Settings { yaw: 30.0, pitch: 22.0, scale: 120.0, ssaa: 1, outline_px: 1 };
            let mut items = vec![Item { model: &m, pos: &p, nrm: &n, range: None, id: 1, glow: 0.0 }];
            if let Some(wm) = &w {
                items.push(Item { model: wm, pos: &wpv, nrm: &wn, range: None, id: 2, glow: 0.0 });
            }
            std::hint::black_box(render(&items, &s, &light));
        }
        let t2 = std::time::Instant::now();
        for _ in 0..50 {
            rig::skin(&m, &sk.world(&pose), &mut p, &mut n);
        }
        println!("çizim/kare: {:?}  deri/kare: {:?}", t1.elapsed() / 50, t2.elapsed() / 50);
        let cw = imgs.iter().map(|i| i.w).sum::<usize>() + 8 * 10;
        let chh = imgs.iter().map(|i| i.h).max().unwrap();
        let mut out = image::RgbaImage::from_pixel(cw as u32, chh as u32, image::Rgba([70, 90, 70, 255]));
        let mut x = 0;
        for im in &imgs {
            for yy in 0..im.h {
                for xx in 0..im.w {
                    let c = im.rgba[yy * im.w + xx];
                    if c[3] > 0 {
                        out.put_pixel((x + xx) as u32, (chh - im.h + yy) as u32, image::Rgba(c));
                    }
                }
            }
            x += im.w + 10;
        }
        out.save(&a[2]).unwrap();
    }
}

mod sheet {
    //! Servis üzerinden önizleme sayfası (NPC'ler, kanatlı kahramanlar):
    //! cargo run --release --example preview -- sheet <m3d> <çıktı.png> <satır;satır;...>
    //! satır: karakter[+kanat]:anim:yaw:t,t,t[:silah]   (anim kimliği sayı; ör. "npc_aldric:23:20:0,0.8,1.6")
    use std::path::Path;
    use zenvex::r3d::service::{ActorReq, Service};

    pub fn main() {
        let args: Vec<String> = crate::args().collect();
        let dir = Path::new(&args[1]);
        let mut s = Service::new();
        for e in std::fs::read_dir(dir).unwrap().flatten() {
            let p = e.path();
            if p.extension().is_some_and(|x| x == "zxm") {
                let name = p.file_stem().unwrap().to_string_lossy().to_string();
                let im = image::open(dir.join(format!("{name}.webp"))).unwrap().to_rgba8();
                s.load(&name, &std::fs::read(&p).unwrap(), im.width() as usize, im.height() as usize, im.as_raw()).unwrap();
                if std::env::var("NOBUMP").is_err()
                    && let Ok(nm) = image::open(dir.join(format!("{name}_n.webp"))) {
                    let nm = nm.to_rgba8();
                    s.load_normal(&name, nm.width() as usize, nm.height() as usize, nm.as_raw());
                }
            }
        }
        // ölçek ve kenar yumuşatma: SCALE (px/m, varsayılan 90), AA (1-3, varsayılan 2)
        let scale = std::env::var("SCALE").ok().and_then(|v| v.parse().ok()).unwrap_or(90.0);
        let aa = std::env::var("AA").ok().and_then(|v| v.parse().ok()).unwrap_or(2);
        let mut rows = Vec::new();
        for line in args[3].split(';') {
            let f: Vec<&str> = line.split(':').collect();
            let (ch, anim, yaw) = (f[0], f[1].parse::<u8>().unwrap(), f[2].parse::<f32>().unwrap());
            let mut row = Vec::new();
            for t in f[3].split(',') {
                let r = ActorReq { ch, weapon: f.get(4).copied().unwrap_or(""), anim, t: t.parse().unwrap(), period: 0.4, yaw, pitch: 22.0, scale, ssaa: aa, flags: 0 };
                row.push(s.actor(&r).unwrap_or_else(|| panic!("{ch}")).0);
            }
            rows.push(row);
        }
        let cols = rows.iter().map(|r| r.len()).max().unwrap();
        let cw = rows.iter().flatten().map(|i| i.w).max().unwrap() + 6;
        let above = rows.iter().flatten().map(|i| i.ay as usize).max().unwrap();
        let below = rows.iter().flatten().map(|i| i.h as i32 - i.ay as i32).max().unwrap().max(0) as usize;
        let chh = above + below + 12;
        let mut out = image::RgbaImage::from_pixel((cw * cols) as u32, (chh * rows.len()) as u32, image::Rgba([72, 92, 72, 255]));
        for (r, row) in rows.iter().enumerate() {
            for (c, im) in row.iter().enumerate() {
                let ox = (c * cw + cw / 2) as i32 - im.ax as i32;
                let oy = (r * chh + above + 6) as i32 - im.ay as i32;
                for yy in 0..im.h {
                    for xx in 0..im.w {
                        let px = im.rgba[yy * im.w + xx];
                        let (x, y) = (ox + xx as i32, oy + yy as i32);
                        if px[3] > 0 && x >= 0 && y >= 0 && (x as u32) < out.width() && (y as u32) < out.height() {
                            out.put_pixel(x as u32, y as u32, image::Rgba(px));
                        }
                    }
                }
            }
        }
        out.save(&args[2]).unwrap();
    }
}

mod vehicles {
    //! Araç önizlemesi: cargo run --release --example preview -- vehicles <m3d> <çıktı.png> <araç> [sürücü]
    //! Satırlar: 8 yön; son satır tekerlek dönüşü + eğim + sarsıntı.
    use std::path::Path;
    use zenvex::r3d::service::{Service, VehicleReq};

    pub fn main() {
        let a: Vec<String> = crate::args().collect();
        let dir = Path::new(&a[1]);
        let name = a[3].as_str();
        let rider = a.get(4).map(|s| s.as_str()).unwrap_or("");
        let mut sv = Service::new();
        for n in [name, rider] {
            if n.is_empty() {
                continue;
            }
            let zxm = std::fs::read(dir.join(format!("{n}.zxm"))).unwrap();
            let im = image::open(dir.join(format!("{n}.webp"))).unwrap().to_rgba8();
            sv.load(n, &zxm, im.width() as usize, im.height() as usize, im.as_raw()).unwrap();
            if let Ok(nm) = image::open(dir.join(format!("{n}_n.webp"))) {
                let nm = nm.to_rgba8();
                sv.load_normal(n, nm.width() as usize, nm.height() as usize, nm.as_raw());
            }
        }
        let mut imgs = Vec::new();
        let t0 = std::time::Instant::now();
        for d in 0..8 {
            let r = VehicleReq { name, yaw: d as f32 * 45.0, pitch: 22.0, scale: 70.0, wheel: 0.0, bounce: 0.0, tilt: 0.0, rider, rider_t: 0.0, ssaa: 2 };
            imgs.push(sv.vehicle(&r).unwrap().0);
        }
        for f in 0..8 {
            let r = VehicleReq {
                name,
                yaw: 60.0,
                pitch: 22.0,
                scale: 70.0,
                wheel: f as f32 * 30.0,
                bounce: 0.02 * (f as f32 * 0.8).sin(),
                tilt: -4.0 + f as f32,
                rider,
                rider_t: f as f32 * 0.087,
                ssaa: 2,
            };
            imgs.push(sv.vehicle(&r).unwrap().0);
        }
        eprintln!("{} kare, ort {:?}", imgs.len(), t0.elapsed() / imgs.len() as u32);
        let cw = imgs.iter().map(|i| i.w).max().unwrap() + 8;
        let below = imgs.iter().map(|i| i.h as i32 - i.ay as i32).max().unwrap().max(0) as usize;
        let above = imgs.iter().map(|i| i.ay as usize).max().unwrap();
        let ch = above + below + 12;
        let mut out = image::RgbaImage::from_pixel((cw * 8) as u32, (ch * 2) as u32, image::Rgba([72, 92, 72, 255]));
        for (k, im) in imgs.iter().enumerate() {
            let (c, r) = (k % 8, k / 8);
            let gx = (c * cw + cw / 2) as i32;
            let gy = (r * ch + above + 6) as i32;
            for x in (c * cw) as u32..((c + 1) * cw) as u32 {
                out.put_pixel(x, gy as u32, image::Rgba([40, 52, 40, 255]));
            }
            for yy in 0..im.h {
                for xx in 0..im.w {
                    let px = im.rgba[yy * im.w + xx];
                    let (x, y) = (gx - im.ax as i32 + xx as i32, gy - im.ay as i32 + yy as i32);
                    if px[3] > 0 && x >= 0 && y >= 0 && (x as u32) < out.width() && (y as u32) < out.height() {
                        out.put_pixel(x as u32, y as u32, image::Rgba(px));
                    }
                }
            }
        }
        out.save(&a[2]).unwrap();
    }
}
