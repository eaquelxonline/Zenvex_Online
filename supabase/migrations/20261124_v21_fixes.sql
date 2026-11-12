-- V21 düzeltmeleri.
--  1. Yönetici etkinlik sıralamasına giremez: puanı yazılmaz, mevcut puanı silinir, listede ve sıra hesabında yer almaz.

delete from public.event_scores where account_id = 0 or account_id in (select id from public.accounts where is_admin);

create or replace function public.zx_event_score(p_id bigint, p_key text, p_sid uuid, p_points int) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); s public.sessions; e public.events; last timestamptz; allow int; got int;
begin
  s := public._zx_session(a, p_sid);
  if a.id = 0 or a.is_admin then return jsonb_build_object('ok', false, 'admin', true); end if;
  select * into e from public.events where active and ends_at > now() order by id desc limit 1;
  if e.id is null or p_points <= 0 then return jsonb_build_object('ok', false); end if;
  select updated_at into last from public.event_scores where event_id = e.id and account_id = a.id;
  allow := (public._zx_event_cap(e.kind) * least(greatest(extract(epoch from now() - coalesce(last, now() - interval '1 minute')) / 60.0, 0.1), 10))::int;
  got := least(p_points, allow);
  if p_points > allow * 2 then
    perform public._zx_flag(a.id, 'event', e.kind || ': ' || p_points || ' puan istendi, sınır ' || allow);
  end if;
  insert into public.event_scores (event_id, account_id, score, updated_at) values (e.id, a.id, got, now())
  on conflict (event_id, account_id) do update set score = public.event_scores.score + excluded.score, updated_at = now();
  return jsonb_build_object('ok', true, 'added', got);
end $$;

create or replace function public.zx_event_board(p_id bigint, p_key text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare a public.accounts := public._zx_auth(p_id, p_key); e public.events; mine bigint; rk bigint;
begin
  perform public._zx_event_finalize(false);
  select * into e from public.events where active order by id desc limit 1;
  if e.id is null then return jsonb_build_object('active', false); end if;
  select score into mine from public.event_scores where event_id = e.id and account_id = a.id and a.id <> 0 and not a.is_admin;
  select count(*) + 1 into rk from public.event_scores s where s.event_id = e.id and s.score > coalesce(mine, 0)
    and s.account_id <> 0 and not exists (select 1 from public.accounts x where x.id = s.account_id and x.is_admin);
  return jsonb_build_object('active', true, 'kind', e.kind, 'title', e.title, 'ends', (extract(epoch from e.ends_at) * 1000)::bigint,
    'my_score', coalesce(mine, 0), 'my_rank', case when coalesce(mine, 0) > 0 then rk else null end,
    'top', coalesce((select jsonb_agg(jsonb_build_object('name', ac.name, 'id', ac.id, 'score', s.score) order by s.score desc)
      from (select * from public.event_scores where event_id = e.id and score > 0 and account_id <> 0 order by score desc limit 100) s
      join public.accounts ac on ac.id = s.account_id and not ac.is_admin), '[]'::jsonb));
end $$;
grant execute on function public.zx_event_score(bigint, text, uuid, int) to anon, authenticated;
grant execute on function public.zx_event_board(bigint, text) to anon, authenticated;
