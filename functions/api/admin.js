// Mobile Ustaad — comment/contact moderation API (Cloudflare Pages Function).
// Protected by the ADMIN_SECRET env var (Pages secret). Send it as the
// `x-admin-token` request header. NEVER expose this endpoint's token publicly.
//
// GET  /api/admin?view=pending   -> pending comments (newest first)
// GET  /api/admin?view=contacts  -> contact messages (unread first, newest first)
// POST /api/admin  {action, id}
//   action=approve | action=reject        (comments)
//   action=delete_comment                  (comments, permanent)
//   action=mark_read                       (contact_messages)
//
// D1 binding: env.DB. Secret: env.ADMIN_SECRET.

function cors() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, x-admin-token'
  };
}
function j(o, s) {
  return new Response(JSON.stringify(o), {
    status: s || 200,
    headers: Object.assign({ 'Content-Type': 'application/json' }, cors())
  });
}
function authed(req, env) {
  const t = req.headers.get('x-admin-token') || '';
  return !!env.ADMIN_SECRET && t.length > 0 && t === env.ADMIN_SECRET;
}

export async function onRequestOptions() {
  return new Response(null, { headers: cors() });
}

export async function onRequestGet(ctx) {
  try {
    if (!authed(ctx.request, ctx.env)) return j({ error: 'Unauthorized.' }, 401);
    if (!ctx.env.DB) return j({ error: 'Database unavailable.' }, 503);
    const view = new URL(ctx.request.url).searchParams.get('view');
    if (view === 'pending') {
      const rows = await ctx.env.DB.prepare(
        "SELECT id, phone_slug, name, message, created_at FROM comments WHERE status = 'pending' ORDER BY created_at DESC LIMIT 200"
      ).all();
      return j({ pending: rows.results || [] });
    }
    if (view === 'contacts') {
      const rows = await ctx.env.DB.prepare(
        "SELECT id, name, email, subject, message, is_read, created_at FROM contact_messages ORDER BY is_read ASC, created_at DESC LIMIT 200"
      ).all();
      return j({ contacts: rows.results || [] });
    }
    return j({ error: 'Unknown view.' }, 400);
  } catch (e) {
    return j({ error: 'Server error.' }, 500);
  }
}

export async function onRequestPost(ctx) {
  try {
    if (!authed(ctx.request, ctx.env)) return j({ error: 'Unauthorized.' }, 401);
    if (!ctx.env.DB) return j({ error: 'Database unavailable.' }, 503);
    let body = null;
    try { body = await ctx.request.json(); } catch (e) { return j({ error: 'Invalid request.' }, 400); }
    const id = parseInt(body && body.id, 10);
    if (!id || id < 1) return j({ error: 'Invalid id.' }, 400);
    const action = String((body && body.action) || '');
    const DB = ctx.env.DB;

    if (action === 'approve') {
      await DB.prepare("UPDATE comments SET status = 'approved' WHERE id = ?1").bind(id).run();
      return j({ ok: true });
    }
    if (action === 'reject') {
      await DB.prepare("UPDATE comments SET status = 'rejected' WHERE id = ?1").bind(id).run();
      return j({ ok: true });
    }
    if (action === 'delete_comment') {
      await DB.prepare("DELETE FROM comments WHERE id = ?1").bind(id).run();
      return j({ ok: true });
    }
    if (action === 'mark_read') {
      await DB.prepare("UPDATE contact_messages SET is_read = 1 WHERE id = ?1").bind(id).run();
      return j({ ok: true });
    }
    return j({ error: 'Unknown action.' }, 400);
  } catch (e) {
    return j({ error: 'Server error.' }, 500);
  }
}
