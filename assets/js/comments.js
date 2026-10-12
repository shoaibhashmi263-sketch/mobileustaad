/* Mobile Ustaad — shared comments widget.
 * Used by the SPA phone-detail view and by static /phones/<slug>/ pages.
 * Usage: MUComments.mount('comments-root', 'phone-slug');
 * Comments are moderated: new posts go to "pending" and appear after approval.
 */
(function () {
  'use strict';

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  function fmtDate(iso) {
    try {
      var d = new Date((iso || '').replace(' ', 'T') + 'Z');
      if (isNaN(d)) return '';
      return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
    } catch (e) { return ''; }
  }

  function card(c) {
    return '<div class="mu-c"><div class="mu-c-head"><b>' + esc(c.name) + '</b>'
      + '<span>' + esc(fmtDate(c.created_at)) + '</span></div>'
      + '<p>' + esc(c.message) + '</p></div>';
  }

  function formHtml() {
    return '<form class="mu-cform" novalidate>'
      + '<h3>Post a comment</h3>'
      + '<p class="mu-csub">Ask a question, share your experience, or post a buy/sell inquiry. Comments are moderated and appear after approval.</p>'
      + '<label>Your name<input name="name" maxlength="50" required autocomplete="name" placeholder="e.g. Ahmed"></label>'
      + '<label>Comment<textarea name="message" maxlength="1000" required rows="4" placeholder="Write your comment or question about this phone…"></textarea></label>'
      + '<input type="text" name="website" class="mu-hp" tabindex="-1" autocomplete="off" aria-hidden="true">'
      + '<button type="submit">Post comment</button>'
      + '<p class="mu-cmsg" role="status"></p>'
      + '</form>';
  }

  async function loadList(root, slug) {
    var list = root.querySelector('.mu-clist');
    try {
      var r = await fetch('/api/comments?slug=' + encodeURIComponent(slug));
      var d = await r.json();
      var arr = (d && d.comments) || [];
      if (!arr.length) {
        list.innerHTML = '<p class="mu-cempty">No comments yet — be the first to share your thoughts.</p>';
        return;
      }
      list.innerHTML = arr.map(card).join('');
    } catch (e) {
      list.innerHTML = '<p class="mu-cempty">Comments could not be loaded right now.</p>';
    }
  }

  function wireForm(root, slug) {
    var form = root.querySelector('.mu-cform');
    var msg = form.querySelector('.mu-cmsg');
    form.addEventListener('submit', async function (ev) {
      ev.preventDefault();
      var name = form.name.value.trim();
      var message = form.message.value.trim();
      var website = form.website.value; // honeypot
      msg.className = 'mu-cmsg';
      if (name.length < 2) { msg.textContent = 'Please enter your name.'; msg.classList.add('err'); return; }
      if (message.length < 3) { msg.textContent = 'Please write a comment.'; msg.classList.add('err'); return; }
      msg.textContent = 'Posting…';
      try {
        var r = await fetch('/api/comments', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ slug: slug, name: name, message: message, website: website })
        });
        var d = await r.json();
        if (r.ok && d && d.ok) {
          form.reset();
          msg.textContent = 'Thanks! Your comment was received and will appear after moderation.';
          msg.classList.add('ok');
        } else {
          msg.textContent = (d && d.error) || 'Could not post your comment.';
          msg.classList.add('err');
        }
      } catch (e) {
        msg.textContent = 'Network error — please try again.';
        msg.classList.add('err');
      }
    });
  }

  function mount(rootId, slug) {
    var root = document.getElementById(rootId);
    if (!root || !slug) return;
    root.innerHTML = '<h2>\uD83D\uDCAC Comments &amp; Questions</h2><div class="mu-clist"><p class="mu-cempty">Loading comments…</p></div>' + formHtml();
    loadList(root, slug);
    wireForm(root, slug);
  }

  window.MUComments = { mount: mount };
})();
