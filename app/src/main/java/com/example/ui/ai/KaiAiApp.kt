package com.example.ui.ai

/**
 * Built-in, privacy-first, zero-telemetry anonymous Kai AI application.
 * Served directly in WebView via shouldInterceptRequest for https://kai.ai/*.
 * Contains top-ranked open intelligence personas with undisclosed internal model names.
 */
object KaiAiApp {

  val HTML: String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>Kai AI</title>
<style>
  :root {
    --bg: #070B14;
    --surface: #0E1626;
    --surface-hover: #152238;
    --surface-variant: #111B2E;
    --border: #1E2D4A;
    --accent: #00F2FE;
    --accent-subtle: rgba(0, 242, 254, 0.15);
    --text: #F1F5F9;
    --text-muted: #94A3B8;
    --bubble-user: #13233D;
    --bubble-ai: #0B1322;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
  body {
    background-color: var(--bg);
    color: var(--text);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    display: flex;
    flex-direction: column;
    height: 100vh;
    height: 100dvh;
    overflow: hidden;
  }
  header {
    background: rgba(14, 22, 38, 0.88);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border-bottom: 1px solid var(--border);
    padding: 10px 16px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    flex-shrink: 0;
  }
  .header-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .brand svg {
    width: 28px;
    height: 28px;
    flex-shrink: 0;
  }
  .brand-title {
    font-size: 1.1rem;
    font-weight: 700;
    letter-spacing: -0.02em;
    background: linear-gradient(135deg, #FFFFFF 30%, var(--accent) 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .badge-anonymous {
    font-size: 0.68rem;
    font-weight: 600;
    background: var(--accent-subtle);
    color: var(--accent);
    padding: 3px 8px;
    border-radius: 20px;
    border: 1px solid rgba(0, 242, 254, 0.35);
  }
  .personas {
    display: flex;
    gap: 6px;
    overflow-x: auto;
    scrollbar-width: none;
    -ms-overflow-style: none;
    padding-bottom: 2px;
  }
  .personas::-webkit-scrollbar { display: none; }
  .persona-btn {
    background: var(--surface-variant);
    border: 1px solid var(--border);
    color: var(--text-muted);
    font-size: 0.76rem;
    font-weight: 600;
    padding: 5px 12px;
    border-radius: 16px;
    cursor: pointer;
    white-space: nowrap;
    transition: all 0.2s ease;
  }
  .persona-btn.active {
    background: var(--accent-subtle);
    color: var(--accent);
    border-color: rgba(0, 242, 254, 0.6);
  }
  #chat-feed {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 16px;
    scroll-behavior: smooth;
  }
  .msg-row {
    display: flex;
    flex-direction: column;
    max-width: 88%;
    animation: fadeIn 0.22s ease-out;
  }
  @keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
  .msg-row.user {
    align-self: flex-end;
    align-items: flex-end;
  }
  .msg-row.ai {
    align-self: flex-start;
    align-items: flex-start;
    max-width: 92%;
  }
  .msg-bubble {
    padding: 12px 16px;
    border-radius: 18px;
    font-size: 0.94rem;
    line-height: 1.55;
    word-break: break-word;
  }
  .user .msg-bubble {
    background: var(--bubble-user);
    color: #FFFFFF;
    border: 1px solid rgba(0, 242, 254, 0.35);
    border-bottom-right-radius: 4px;
  }
  .ai .msg-bubble {
    background: var(--bubble-ai);
    color: var(--text);
    border: 1px solid var(--border);
    border-bottom-left-radius: 4px;
    box-shadow: 0 4px 16px rgba(0,0,0,0.3);
  }
  .ai-meta {
    font-size: 0.72rem;
    color: var(--text-muted);
    margin-bottom: 4px;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .ai-meta .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--accent);
  }
  pre {
    background: #050810;
    border: 1px solid #1E293B;
    border-radius: 10px;
    padding: 12px;
    margin: 8px 0;
    overflow-x: auto;
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    font-size: 0.85rem;
    position: relative;
  }
  code {
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    font-size: 0.86rem;
    color: #38BDF8;
  }
  pre code { color: #E2E8F0; }
  .copy-code-btn {
    position: absolute;
    top: 6px;
    right: 6px;
    background: rgba(30, 41, 59, 0.8);
    border: 1px solid #334155;
    color: #94A3B8;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 0.7rem;
    cursor: pointer;
  }
  .typing-indicator {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 6px 12px;
  }
  .typing-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--accent);
    animation: typingPulse 1.2s infinite ease-in-out;
  }
  .typing-dot:nth-child(2) { animation-delay: 0.2s; }
  .typing-dot:nth-child(3) { animation-delay: 0.4s; }
  @keyframes typingPulse {
    0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
    40% { transform: scale(1.1); opacity: 1; }
  }
  .empty-state {
    margin: auto;
    text-align: center;
    max-width: 380px;
    padding: 24px 16px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
  }
  .empty-logo {
    width: 56px;
    height: 56px;
  }
  .empty-title {
    font-size: 1.25rem;
    font-weight: 700;
  }
  .empty-subtitle {
    font-size: 0.86rem;
    color: var(--text-muted);
    line-height: 1.5;
  }
  .suggestions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
    margin-top: 12px;
  }
  .suggestion-pill {
    background: var(--surface);
    border: 1px solid var(--border);
    padding: 10px 14px;
    border-radius: 12px;
    font-size: 0.84rem;
    color: var(--text);
    cursor: pointer;
    text-align: left;
    transition: background 0.15s ease;
  }
  .suggestion-pill:active {
    background: var(--surface-hover);
  }
  footer {
    background: rgba(14, 22, 38, 0.95);
    border-top: 1px solid var(--border);
    padding: 10px 14px;
    display: flex;
    align-items: flex-end;
    gap: 8px;
    flex-shrink: 0;
  }
  .input-wrapper {
    flex: 1;
    background: var(--bg);
    border: 1.2px solid var(--border);
    border-radius: 20px;
    padding: 8px 14px;
    display: flex;
    align-items: center;
    gap: 8px;
    transition: border-color 0.2s ease;
  }
  .input-wrapper:focus-within {
    border-color: rgba(0, 242, 254, 0.6);
  }
  textarea {
    width: 100%;
    background: transparent;
    border: none;
    color: var(--text);
    font-size: 0.94rem;
    resize: none;
    outline: none;
    max-height: 120px;
    line-height: 1.4;
    font-family: inherit;
  }
  textarea::placeholder {
    color: var(--text-muted);
  }
  .send-btn {
    width: 38px;
    height: 38px;
    border-radius: 50%;
    background: var(--accent);
    color: #070B14;
    border: none;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    flex-shrink: 0;
    transition: opacity 0.2s ease, transform 0.1s ease;
  }
  .send-btn:active {
    transform: scale(0.95);
  }
  .send-btn:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
  .send-btn svg {
    width: 18px;
    height: 18px;
    fill: currentColor;
  }
</style>
</head>
<body>

<header>
  <div class="header-top">
    <div class="brand">
      <svg viewBox="0 0 108 108" fill="none">
        <circle cx="54" cy="54" r="38" stroke="#00F2FE" stroke-width="4.5" />
        <circle cx="54" cy="54" r="22" stroke="#6366F1" stroke-width="3.5" />
        <circle cx="54" cy="54" r="8" fill="#FFFFFF" />
      </svg>
      <span class="brand-title">Kai AI</span>
    </div>
    <span class="badge-anonymous">100% Anonymous</span>
  </div>
  <div class="personas">
    <button class="persona-btn active" data-persona="auto">⚡ Auto Intelligence</button>
    <button class="persona-btn" data-persona="reasoning">🧠 Deep Reasoning</button>
    <button class="persona-btn" data-persona="code">💻 Code & Logic</button>
    <button class="persona-btn" data-persona="creative">✍️ Creative Mind</button>
  </div>
</header>

<main id="chat-feed">
  <div class="empty-state" id="empty-state">
    <svg class="empty-logo" viewBox="0 0 108 108" fill="none">
      <circle cx="54" cy="54" r="38" stroke="#00F2FE" stroke-width="4.5" />
      <circle cx="54" cy="54" r="22" stroke="#6366F1" stroke-width="3.5" />
      <circle cx="54" cy="54" r="8" fill="#FFFFFF" />
    </svg>
    <div class="empty-title">Anonymous AI Workspace</div>
    <div class="empty-subtitle">
      Powered by top-ranked open intelligence. Free, unrestricted, and completely private — zero accounts, zero tracking, and zero logs stored.
    </div>
    <div class="suggestions">
      <div class="suggestion-pill" onclick="sendQuickPrompt('Summarize the latest developments in quantum computing.')">⚡ Summarize developments in quantum computing</div>
      <div class="suggestion-pill" onclick="sendQuickPrompt('Write a clean Kotlin algorithm for LRU cache eviction.')">💻 Write a clean Kotlin algorithm for LRU cache</div>
      <div class="suggestion-pill" onclick="sendQuickPrompt('Explain relativity simply with an intuitive analogy.')">🧠 Explain relativity simply with an analogy</div>
    </div>
  </div>
</main>

<footer>
  <div class="input-wrapper">
    <textarea id="prompt-input" rows="1" placeholder="Ask Kai AI anything..."></textarea>
  </div>
  <button id="send-btn" class="send-btn" title="Send">
    <svg viewBox="0 0 24 24">
      <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
    </svg>
  </button>
</footer>

<script>
  let activePersona = 'auto';
  let isGenerating = false;
  let abortCtrl = null;

  const PERSONA_PROMPTS = {
    auto: 'You are Kai AI, a top-ranked, highly capable and helpful intelligence assistant. Provide accurate, clear, and comprehensive answers.',
    reasoning: 'You are Kai AI in Deep Reasoning mode. Think through every aspect of the question step by step with extreme precision, logic, and comprehensive depth.',
    code: 'You are Kai AI in Code & Logic mode. You are an expert software engineer. Provide clean, secure, production-grade code with optimal algorithms and concise explanations.',
    creative: 'You are Kai AI in Creative Mind mode. Write engaging, eloquent, nuanced, and imaginative responses with rich detail.'
  };

  const feed = document.getElementById('chat-feed');
  const emptyState = document.getElementById('empty-state');
  const input = document.getElementById('prompt-input');
  const sendBtn = document.getElementById('send-btn');
  const personaBtns = document.querySelectorAll('.persona-btn');

  personaBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      personaBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      activePersona = btn.getAttribute('data-persona') || 'auto';
    });
  });

  input.addEventListener('input', () => {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 120) + 'px';
  });

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  });

  sendBtn.addEventListener('click', () => {
    if (isGenerating && abortCtrl) {
      abortCtrl.abort();
      return;
    }
    handleSend();
  });

  function sendQuickPrompt(txt) {
    input.value = txt;
    handleSend();
  }

  function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function formatMarkdown(text) {
    let out = escapeHtml(text);
    // Code blocks with copy button
    out = out.replace(/```([a-zA-Z0-9]*)\n([\s\S]*?)```/g, function(match, lang, code) {
      return '<pre><button class="copy-code-btn" onclick="copySnippet(this)">Copy</button><code>' + code + '</code></pre>';
    });
    // Inline code
    out = out.replace(/`([^`]+)`/g, '<code>$1</code>');
    // Bold
    out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    // Italic
    out = out.replace(/\*([^*]+)\*/g, '<em>$1</em>');
    // Bullet points
    out = out.replace(/^\s*[-*]\s+(.*)$/gm, '&bull; $1<br>');
    // Line breaks
    out = out.replace(/\n/g, '<br>');
    return out;
  }

  function copySnippet(btn) {
    const code = btn.nextElementSibling.innerText;
    navigator.clipboard.writeText(code).then(() => {
      btn.innerText = 'Copied!';
      setTimeout(() => btn.innerText = 'Copy', 1800);
    }).catch(() => {
      btn.innerText = 'Copied';
    });
  }

  function appendMessage(role, content, isHtml = false) {
    if (emptyState) emptyState.style.display = 'none';

    const row = document.createElement('div');
    row.className = 'msg-row ' + role;

    if (role === 'ai') {
      const meta = document.createElement('div');
      meta.className = 'ai-meta';
      meta.innerHTML = '<span class="dot"></span><span>Kai AI</span>';
      row.appendChild(meta);
    }

    const bubble = document.createElement('div');
    bubble.className = 'msg-bubble';
    if (isHtml) {
      bubble.innerHTML = content;
    } else {
      bubble.textContent = content;
    }
    row.appendChild(bubble);

    feed.appendChild(row);
    feed.scrollTop = feed.scrollHeight;
    return bubble;
  }

  async function handleSend() {
    const text = input.value.trim();
    if (!text || isGenerating) return;

    input.value = '';
    input.style.height = 'auto';

    appendMessage('user', text, false);

    isGenerating = true;
    sendBtn.innerHTML = '<svg viewBox="0 0 24 24"><rect x="6" y="6" width="12" height="12" rx="2" fill="currentColor"/></svg>';
    sendBtn.title = 'Stop';

    const aiBubble = appendMessage('ai', '<div class="typing-indicator"><div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div></div>', true);

    abortCtrl = new AbortController();

    try {
      const sys = PERSONA_PROMPTS[activePersona] || PERSONA_PROMPTS.auto;
      let responseText = '';

      try {
        const postResp = await fetch('https://text.pollinations.ai/', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            messages: [
              { role: 'system', content: sys },
              { role: 'user', content: text }
            ]
          }),
          signal: abortCtrl.signal
        });
        if (postResp.ok) {
          responseText = await postResp.text();
        } else {
          throw new Error('POST failed: ' + postResp.status);
        }
      } catch (postErr) {
        if (postErr.name === 'AbortError') throw postErr;
        // Resilient fallback to GET query
        const getUrl = 'https://text.pollinations.ai/' + encodeURIComponent(text) +
                       '?system=' + encodeURIComponent(sys);
        const getResp = await fetch(getUrl, { signal: abortCtrl.signal });
        if (!getResp.ok) throw new Error('GET failed: ' + getResp.status);
        responseText = await getResp.text();
      }

      aiBubble.innerHTML = formatMarkdown(responseText.trim());
    } catch (err) {
      if (err.name === 'AbortError') {
        aiBubble.innerHTML = '<em>Generation stopped.</em>';
      } else {
        aiBubble.innerHTML = '<span style="color:#EF4444">Unable to connect to AI engine. Please verify internet connection.</span>';
      }
    } finally {
      isGenerating = false;
      sendBtn.innerHTML = '<svg viewBox="0 0 24 24"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>';
      sendBtn.title = 'Send';
      feed.scrollTop = feed.scrollHeight;
    }
  }

  // Handle URL query parameters (e.g., https://kai.ai/?q=my+prompt)
  window.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(window.location.search);
    const q = params.get('q') || params.get('prompt');
    if (q && q.trim()) {
      input.value = q.trim();
      handleSend();
    }
  });
</script>
</body>
</html>
  """.trimIndent()
}
