// Mobile Ustaad — contact form API (Cloudflare Pages Function).
// POST /api/contact  {name, email, subject, message, company?}
// PERMANENT FIX (2026-10-16): emails the owner directly via MailChannels on
// every submit (reply-to = submitter). D1 write below is a backup log only;
// the daily cron never polls D1 for contact messages.
// D1 binding: env.DB (database "mobileustaad-data").

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
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
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
function validEmail(e) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(e) && e.length <= 120;
}

export async function onRequestOptions() {
  return new Response(null, { headers: cors() });
}

export async function onRequestPost(ctx) {
  try {
    const req = ctx.request;
    const ip = req.headers.get('CF-Connecting-IP') || 'anon';
    // Anti-spam: max 5 messages per 30 minutes per IP
    if (rateLimited(ip, 5, 30 * 60 * 1000)) {
      return j({ error: 'You are sending too fast — please wait a while.' }, 429);
    }
    let body = null;
    try { body = await req.json(); } catch (e) { return j({ error: 'Invalid request.' }, 400); }

    // Honeypot
    if (body && String(body.company || '').trim() !== '') {
      return j({ ok: true }); // pretend success, drop silently
    }

    const name = clean(body && body.name, 80);
    const email = clean(body && body.email, 120);
    const subject = clean(body && body.subject, 120);
    const message = clean(body && body.message, 3000);

    if (name.length < 2) return j({ error: 'Please enter your name.' }, 400);
    if (!validEmail(email)) return j({ error: 'Please enter a valid email address.' }, 400);
    if (subject.length < 2) return j({ error: 'Please enter a subject.' }, 400);
    if (message.length < 5) return j({ error: 'Please write your message.' }, 400);
    if (!ctx.env.DB) return j({ error: 'The contact form is not available right now.' }, 503);

    await ctx.env.DB.prepare(
      "INSERT INTO contact_messages (name, email, subject, message, is_read, created_at) VALUES (?1, ?2, ?3, ?4, 0, datetime('now'))"
    ).bind(name, email, subject, message).run();

    // PERMANENT FIX (2026-10-16): send the email immediately via MailChannels
    // so the daily cron never needs the Cloudflare API / D1 polling.
    // D1 write above stays as a backup log.
    try {
      await fetch('https://api.mailchannels.net/tx/v1/send', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          personalizations: [{ to: [{ email: 'shoaibhashmi263@gmail.com', name: 'Mobile Ustaad' }] }],
          from: { email: 'contact@mobileustaad.com', name: 'Mobile Ustaad Contact' },
          reply_to: { email: email, name: name },
          subject: '[Mobile Ustaad Contact] ' + subject,
          content: [{
            type: 'text/plain',
            value: 'From: ' + name + ' <' + email + '>\nDate: ' + new Date().toISOString() + ' (UTC)\n\n' + message
          }]
        })
      });
    } catch (e) { /* email failure must not break the form; D1 has the backup */ }

    return j({ ok: true });
  } catch (e) {
    return j({ error: 'Could not send your message — please try again.' }, 500);
  }
}
