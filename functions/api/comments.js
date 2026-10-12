// Mobile Ustaad — public comments API (Cloudflare Pages Function).
// GET  /api/comments?slug=<phone-slug>   -> approved comments for that phone
// POST /api/comments  {slug, name, message, website?} -> new comment, status='pending'
//
// D1 binding: env.DB (database "mobileustaad-data").
// Moderation: comments are NEVER public until approved via /api/admin.

const hits = new Map();
function rateLimited(ip, limit, windowMs) {
  const now = Date.now();
  const arr = (hits.get(ip) || []).filter(function (t) { return now - t < windowMs; });
  arr.push(now);
  hits.set(ip, arr);
  return arr.length > limit;
}
function cors() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type'
  };
}
function j(o, s) {
  return new Response(JSON.stringify(o), {
    status: s || 200,
    headers: Object.assign({ 'Content-Type': 'application/json' }, cors())
  });
}
function clean(s, max) {
  return String(s == null ? '' : s).replace(/\s+/g, ' ').trim().slice(0, max);
}
function validSlug(s) {
  return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(s) && s.length <= 80;
}

export async function onRequestOptions() {
  return new Response(null, { headers: cors() });
}

export async function onRequestGet(ctx) {
  try {
    const url = new URL(ctx.request.url);
    const slug = clean(url.searchParams.get('slug'), 80);
    if (!validSlug(slug)) return j({ error: 'Invalid phone.' }, 400);
    if (!ctx.env.DB) return j({ error: 'Comments are not available right now.' }, 503);
    const rows = await ctx.env.DB.prepare(
      "SELECT id, name, message, created_at FROM comments WHERE phone_slug = ?1 AND status = 'approved' ORDER BY created_at DESC LIMIT 100"
    ).bind(slug).all();
    return j({ comments: rows.results || [] });
  } catch (e) {
    return j({ error: 'Could not load comments.' }, 500);
  }
}

export async function onRequestPost(ctx) {
  try {
    const req = ctx.request;
    const ip = req.headers.get('CF-Connecting-IP') || 'anon';
    // Anti-spam: max 5 comments per 10 minutes per IP
    if (rateLimited(ip, 5, 10 * 60 * 1000)) {
      return j({ error: 'You are posting too fast — please wait a few minutes.' }, 429);
    }
    let body = null;
    try { body = await req.json(); } catch (e) { return j({ error: 'Invalid request.' }, 400); }

    // Honeypot: bots fill this hidden field; humans never see it
    if (body && String(body.website || '').trim() !== '') {
      return j({ ok: true }); // pretend success, drop silently
    }

    const slug = clean(body && body.slug, 80);
    const name = clean(body && body.name, 50);
    const message = clean(body && body.message, 1000);

    if (!validSlug(slug)) return j({ error: 'Invalid phone.' }, 400);
    if (name.length < 2) return j({ error: 'Please enter your name.' }, 400);
    if (message.length < 3) return j({ error: 'Please write a comment.' }, 400);
    if (!ctx.env.DB) return j({ error: 'Comments are not available right now.' }, 503);

    const crypto = globalThis.crypto;
    const ipHashBuf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(ip + '|' + slug));
    const ipHash = Array.from(new Uint8Array(ipHashBuf)).map(function (b) { return b.toString(16).padStart(2, '0'); }).join('').slice(0, 32);

    await ctx.env.DB.prepare(
      "INSERT INTO comments (phone_slug, name, message, status, ip_hash, created_at) VALUES (?1, ?2, ?3, 'pending', ?4, datetime('now'))"
    ).bind(slug, name, message, ipHash).run();

    return j({ ok: true, moderated: true });
  } catch (e) {
    return j({ error: 'Could not save your comment — please try again.' }, 500);
  }
}
