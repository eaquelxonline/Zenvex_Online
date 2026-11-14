-- V22 varlık temizliği (istemcinin V22 sürümüyle birlikte uygulanmalı):
--  1. Karakter Kartı eşyası kalktı: mağaza paketlerindeki kartlar elmasa döner (kart başına 10 elmas), Aylık Kart'ın
--     günlük kartı da günlük +10 elmas olur.
--  2. Kişisel üs depo sandıkları kalktı: Elmas Sandığı'ndaki "Askerî Sandık" ödülü 1.500 altın olur.
--  3. Sezon bileti: kart ve sandık ödülleri elmasa döndü; sunucunun elmas tablosu Rust data::PASS_REWARDS ile aynı
--     (ücretsiz yol 360, premium 1.610 elmas).

-- 1. mağaza paketleri
update public.store_products set gems = gems + 30,
  items = '[{"id": "potion_heal", "n": 3}, {"id": "potion_speed", "n": 3}, {"id": "medkit", "n": 5}]'
  where id = 'pack_start';
update public.store_products set gems = gems + 100,
  items = '[{"id": "v_wheel", "n": 2}, {"id": "v_engine", "n": 1}, {"id": "v_frame_moto", "n": 1}, {"id": "potion_power", "n": 3}]'
  where id = 'pack_growth';
update public.store_products set gems = gems + 250,
  items = '[{"id": "v_wheel", "n": 4}, {"id": "v_engine", "n": 1}, {"id": "v_frame_car", "n": 1}, {"id": "potion_power", "n": 5}]'
  where id = 'pack_war';
update public.store_products set meta = (meta - 'daily_items') || jsonb_build_object('daily_gems', coalesce((meta ->> 'daily_gems')::int, 0) + 10)
  where id = 'card_month' and meta ? 'daily_items';

-- 2. Elmas Sandığı ödülleri
create or replace function public.zx_crate_open(p_id bigint, p_key text, p_sid uuid, p_n int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; d jsonb;
        cost int := case when p_n = 10 then 1080 else 120 end; n int := case when p_n = 10 then 10 else 1 end;
        legend text[] := array['sk_night', 'sk_blood', 'sk_frost', 'sk_gold', 'vs_gold', 'vs_flame'];
        epic text[] := array['sk_toxic', 'sk_shadow', 'vs_camo', 'vs_neon'];
        pity int; r int; got text; out text[] := '{}'; gold bigint := 0; i int; pick text[]; legend_hit boolean;
begin
  perform public._zx_maint_check(a);
  s := public._zx_session(a, p_sid);
  select data::jsonb into d from public.characters where account_id = a.id and server = s.server;
  if coalesce((d ->> 'gems')::int, 0) < cost then raise exception 'Yetersiz elmas (% gerekli)', cost; end if;
  insert into public.crate_pity (account_id) values (a.id) on conflict do nothing;
  select since_legend into pity from public.crate_pity where account_id = a.id for update;
  for i in 1..n loop
    pity := pity + 1;
    r := floor(random() * 10000)::int;
    legend_hit := r < 150 or pity >= 50;
    if legend_hit then
      -- garanti/şans: önce sahip olunmayan efsanevi
      select array_agg(x) into pick from unnest(legend) x where not exists (select 1 from public.skins_owned o where o.account_id = a.id and o.skin = x);
      if pick is null then pick := legend; end if;
      got := pick[1 + floor(random() * array_length(pick, 1))::int];
      pity := 0;
    elsif r < 550 then got := epic[1 + floor(random() * 4)::int];
    elsif r < 650 then got := 'ring_titan:1';
    elsif r < 1150 then got := 'titan_shard:2';
    elsif r < 1500 then got := 'neck_dark:1';
    elsif r < 2400 then got := 'dark_crystal:2';
    elsif r < 3500 then got := 'potion_power:3';
    elsif r < 4600 then got := 'gold:1500';
    elsif r < 6100 then got := 'medkit:5';
    elsif r < 7800 then got := 'potion_heal:3';
    else got := 'gold:1000';
    end if;
    if got not like '%:%' then
      -- kaplama: zaten varsa altına döner
      if exists (select 1 from public.skins_owned o where o.account_id = a.id and o.skin = got) then
        got := case when got = any(legend) then 'gold:2000' else 'gold:800' end;
      else
        insert into public.skins_owned (account_id, skin) values (a.id, got);
      end if;
    end if;
    if got like 'gold:%' then gold := gold + split_part(got, ':', 2)::int; end if;
    out := out || got;
  end loop;
  update public.crate_pity set opens = opens + n, since_legend = pity where account_id = a.id;
  insert into public.grants (account_id, gems, gold, note) values (a.id, -cost, gold, 'elmas sandığı ×' || n);
  perform public._zx_log(a.id, 'shop', 'Elmas Sandığı açtı ×' || n || ': ' || array_to_string(out, ', '));
  return jsonb_build_object('cost', cost, 'rewards', to_jsonb(out), 'pity', 50 - pity);
end $$;

-- 3. sezon bileti elmas tablosu
create or replace function public._zx_pass_gems(p_tier integer, p_premium boolean)
returns integer language sql immutable set search_path = public as $$
  select case when p_premium then
    case p_tier when 1 then 40 when 2 then 30 when 3 then 40 when 5 then 30 when 6 then 30 when 7 then 60 when 9 then 40 when 10 then 30 when 11 then 80 when 13 then 40 when 14 then 90 when 15 then 60 when 17 then 100 when 18 then 40 when 20 then 90 when 21 then 120 when 22 then 40 when 24 then 130 when 26 then 40 when 27 then 140 when 29 then 140 when 30 then 200 else 0 end
  else
    case p_tier when 4 then 10 when 5 then 15 when 8 then 10 when 10 then 25 when 12 then 10 when 15 then 30 when 16 then 20 when 19 then 20 when 20 then 35 when 23 then 20 when 25 then 45 when 28 then 30 when 30 then 90 else 0 end
  end
$$;
