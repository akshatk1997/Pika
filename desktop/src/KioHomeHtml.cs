using System;

namespace Kio.Desktop
{
    public static class KioHomeHtml
    {
        public static string GetHtml(bool isPrivate, bool isDarkTheme)
        {
            string bg = isDarkTheme ? (isPrivate ? "#100820" : "#000000") : (isPrivate ? "#F6F3FF" : "#F8FAFC");
            string text = isDarkTheme ? "#F8FAFC" : "#0F172A";
            string muted = isDarkTheme ? "#94A3B8" : "#64748B";
            string cardBg = isDarkTheme ? (isPrivate ? "#1B1033" : "#0E121D") : "#FFFFFF";
            string cardBorder = isDarkTheme ? (isPrivate ? "#3B2068" : "#1E2638") : "#E2E8F0";
            string searchBg = isDarkTheme ? (isPrivate ? "#160C2C" : "#0A0D15") : "#FFFFFF";
            string searchBorder = isDarkTheme ? (isPrivate ? "#C084FC" : "#38BDF8") : "#0284C7";
            string accent = isPrivate ? "#C084FC" : "#00F2FE";
            string accent2 = isPrivate ? "#E879F9" : "#38BDF8";
            string title = isPrivate ? "Private Browsing" : "Kio";
            string subtitle = isPrivate ? "Zero search queries, cookies, or history are stored" : "Lightning Fast · OS-Engine · Strictly Under 2 MB";

            return string.Format(@"<!DOCTYPE html>
<html>
<head>
<meta charset='utf-8'>
<meta name='viewport' content='width=device-width, initial-scale=1'>
<title>{0}</title>
<style>
  * {{ box-sizing: border-box; margin: 0; padding: 0; user-select: none; }}
  body {{
    background-color: {1};
    color: {2};
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 100vh;
    padding: 24px;
    overflow-x: hidden;
  }}
  .container {{
    width: 100%;
    max-width: 680px;
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
  }}
  
  /* Concentric Orbital Kio Logo SVG Animation */
  .logo-box {{
    width: 88px;
    height: 88px;
    margin-bottom: 18px;
    position: relative;
  }}
  .orbit-outer {{
    transform-origin: 50% 50%;
    animation: rotateOrbit 8s linear infinite;
  }}
  .orbit-inner {{
    transform-origin: 50% 50%;
    animation: rotateOrbitRev 11s linear infinite;
  }}
  .core-pulse {{
    transform-origin: 50% 50%;
    animation: pulseCore 2.6s ease-in-out infinite alternate;
  }}
  @keyframes rotateOrbit {{
    0% {{ transform: rotate(0deg); }}
    100% {{ transform: rotate(360deg); }}
  }}
  @keyframes rotateOrbitRev {{
    0% {{ transform: rotate(360deg); }}
    100% {{ transform: rotate(0deg); }}
  }}
  @keyframes pulseCore {{
    0% {{ transform: scale(0.92); opacity: 0.85; }}
    100% {{ transform: scale(1.08); opacity: 1; }}
  }}

  h1 {{
    font-size: 32px;
    font-weight: 700;
    letter-spacing: -0.5px;
    margin-bottom: 6px;
    color: {2};
  }}
  .subtitle {{
    font-size: 13.5px;
    color: {3};
    margin-bottom: 24px;
  }}
  .private-badge {{
    display: inline-flex;
    align-items: center;
    gap: 8px;
    background: {4};
    border: 1px solid {5};
    border-radius: 999px;
    padding: 6px 14px;
    font-size: 12px;
    color: {6};
    font-weight: 600;
    margin-bottom: 24px;
  }}
  .private-dot {{
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: {6};
  }}

  /* Omnibox Search Bar */
  .search-bar {{
    width: 100%;
    display: flex;
    align-items: center;
    background: {7};
    border: 1.5px solid {5};
    border-radius: 999px;
    padding: 8px 18px;
    margin-bottom: 32px;
    box-shadow: 0 4px 20px rgba(0,0,0,0.15);
    transition: all 0.2s ease;
  }}
  .search-bar:focus-within {{
    border-color: {8};
    box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.25);
  }}
  .search-icon {{
    font-size: 16px;
    color: {3};
    margin-right: 12px;
  }}
  .search-input {{
    flex: 1;
    background: transparent;
    border: none;
    outline: none;
    color: {2};
    font-size: 15px;
    font-family: inherit;
    user-select: text;
  }}
  .search-input::placeholder {{
    color: {3};
  }}
  .search-btn {{
    background: {6};
    border: none;
    border-radius: 999px;
    width: 32px;
    height: 32px;
    color: #000;
    font-size: 14px;
    font-weight: bold;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: transform 0.15s ease;
  }}
  .search-btn:hover {{
    transform: scale(1.06);
  }}

  /* Speed Dial Grid */
  .grid {{
    width: 100%;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 14px;
    margin-bottom: 32px;
  }}
  .card {{
    background: {4};
    border: 1px solid {5};
    border-radius: 16px;
    padding: 16px 10px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    text-decoration: none;
    transition: all 0.2s ease;
  }}
  .card:hover {{
    transform: translateY(-3px);
    border-color: {6};
    box-shadow: 0 8px 24px rgba(0,0,0,0.2);
  }}
  .card-icon {{
    font-size: 24px;
    margin-bottom: 8px;
  }}
  .card-label {{
    font-size: 12.5px;
    font-weight: 500;
    color: {2};
  }}

  /* Privacy Badges */
  .footer-shield {{
    display: flex;
    gap: 16px;
    justify-content: center;
    font-size: 11.5px;
    color: {3};
  }}
  .shield-item {{
    display: flex;
    align-items: center;
    gap: 5px;
  }}
</style>
</head>
<body>
  <div class='container'>
    <div class='logo-box'>
      <svg viewBox='0 0 100 100' width='88' height='88'>
        <defs>
          <linearGradient id='gradOuter' x1='0%' y1='0%' x2='100%' y2='100%'>
            <stop offset='0%' stop-color='{6}' />
            <stop offset='100%' stop-color='{9}' />
          </linearGradient>
          <linearGradient id='gradInner' x1='100%' y1='0%' x2='0%' y2='100%'>
            <stop offset='0%' stop-color='{9}' />
            <stop offset='100%' stop-color='{6}' />
          </linearGradient>
        </defs>
        <!-- Outer Orbit Ring -->
        <g class='orbit-outer'>
          <circle cx='50' cy='50' r='42' fill='none' stroke='url(#gradOuter)' stroke-width='4' stroke-dasharray='180 85' stroke-linecap='round' />
          <circle cx='50' cy='8' r='4' fill='#FFFFFF' />
        </g>
        <!-- Inner Orbit Ring -->
        <g class='orbit-inner'>
          <circle cx='50' cy='50' r='28' fill='none' stroke='url(#gradInner)' stroke-width='3.5' stroke-dasharray='110 65' stroke-linecap='round' />
          <circle cx='50' cy='22' r='3.2' fill='{6}' />
        </g>
        <!-- Luminous Center Core -->
        <g class='core-pulse'>
          <circle cx='50' cy='50' r='14' fill='url(#gradOuter)' />
          <circle cx='50' cy='50' r='8' fill='#FFFFFF' />
        </g>
      </svg>
    </div>

    <h1>{0}</h1>
    <p class='subtitle'>{10}</p>

    {11}

    <div class='search-bar'>
      <span class='search-icon'>🔍</span>
      <input id='searchInput' class='search-input' type='text' placeholder='Search with DuckDuckGo or enter URL...' autofocus />
      <button id='searchBtn' class='search-btn'>➔</button>
    </div>

    <div class='grid'>
      <div class='card' onclick=""navigate('https://duckduckgo.com')"">
        <span class='card-icon'>🦆</span>
        <span class='card-label'>DuckDuckGo</span>
      </div>
      <div class='card' onclick=""navigate('https://google.com')"">
        <span class='card-icon'>🌐</span>
        <span class='card-label'>Google</span>
      </div>
      <div class='card' onclick=""navigate('https://youtube.com')"">
        <span class='card-icon'>▶️</span>
        <span class='card-label'>YouTube</span>
      </div>
      <div class='card' onclick=""navigate('https://github.com')"">
        <span class='card-icon'>🐙</span>
        <span class='card-label'>GitHub</span>
      </div>
      <div class='card' onclick=""navigate('https://reddit.com')"">
        <span class='card-icon'>💬</span>
        <span class='card-label'>Reddit</span>
      </div>
      <div class='card' onclick=""navigate('https://wikipedia.org')"">
        <span class='card-icon'>📚</span>
        <span class='card-label'>Wikipedia</span>
      </div>
      <div class='card' onclick=""navigate('https://twitter.com')"">
        <span class='card-icon'>🐦</span>
        <span class='card-label'>X / Twitter</span>
      </div>
      <div class='card' onclick=""navigate('https://news.ycombinator.com')"">
        <span class='card-icon'>⚡</span>
        <span class='card-label'>Hacker News</span>
      </div>
    </div>

    <div class='footer-shield'>
      <div class='shield-item'>🛡️ Tracker Shield Active</div>
      <div class='shield-item'>⚡ 120Hz/144Hz Accelerated</div>
      <div class='shield-item'>🔒 Zero Telemetry</div>
    </div>
  </div>

  <script>
    function navigate(url) {{
      if (window.chrome && window.chrome.webview) {{
        window.chrome.webview.postMessage(JSON.stringify({{ action: 'navigate', url: url }}));
      }} else {{
        window.location.href = url;
      }}
    }}

    var input = document.getElementById('searchInput');
    var btn = document.getElementById('searchBtn');

    function submitSearch() {{
      var val = input.value.trim();
      if (!val) return;
      if (val.indexOf('http://') === 0 || val.indexOf('https://') === 0) {{
        navigate(val);
      }} else if (val.indexOf('.') !== -1 && val.indexOf(' ') === -1) {{
        navigate('https://' + val);
      }} else {{
        navigate('https://duckduckgo.com/?q=' + encodeURIComponent(val));
      }}
    }}

    btn.addEventListener('click', submitSearch);
    input.addEventListener('keydown', function(e) {{
      if (e.key === 'Enter') submitSearch();
    }});
  </script>
</body>
</html>",
                title,                                                        // {0}
                bg,                                                           // {1}
                text,                                                         // {2}
                muted,                                                        // {3}
                cardBg,                                                       // {4}
                cardBorder,                                                   // {5}
                accent,                                                       // {6}
                searchBg,                                                     // {7}
                searchBorder,                                                 // {8}
                accent2,                                                      // {9}
                subtitle,                                                     // {10}
                isPrivate ? "<div class='private-badge'><div class='private-dot'></div>Private Browsing Active</div>" : "" // {11}
            );
        }
    }
}
