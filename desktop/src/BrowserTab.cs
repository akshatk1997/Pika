using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace Kio.Desktop
{
    public class BrowserTab : IDisposable
    {
        public string Id { get; private set; }
        public string Title { get; set; }
        public string Url { get; set; }
        public bool IsPrivate { get; private set; }
        public bool IsLoading { get; set; }
        public bool CanGoBack { get { return WebView != null && WebView.CanGoBack; } }
        public bool CanGoForward { get { return WebView != null && WebView.CanGoForward; } }
        public double ZoomFactor { get; set; }
        public UserAgentMode UaMode { get; set; }
        public bool BlockImages { get; set; }
        public bool CleanModeEnabled { get; set; }

        public List<DetectedMediaItem> DetectedMediaList { get; private set; }
        public WebView2 WebView { get; private set; }

        public event EventHandler StateChanged;
        public event EventHandler MediaListUpdated;

        private const string DesktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Kio/2.0";
        private const string MobileUserAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36 Kio/2.0";
        private const string IPhoneUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1 Kio/2.0";

        public BrowserTab(bool isPrivate)
        {
            Id = Guid.NewGuid().ToString("N");
            Title = isPrivate ? "Private Tab" : "New Tab";
            Url = "about:blank";
            IsPrivate = isPrivate;
            ZoomFactor = 1.0;
            UaMode = UserAgentMode.Desktop;
            DetectedMediaList = new List<DetectedMediaItem>();

            WebView = new WebView2();
            WebView.Dock = DockStyle.Fill;
        }

        public async Task InitializeAsync()
        {
            string userDataDir;
            if (IsPrivate)
            {
                userDataDir = Path.Combine(Path.GetTempPath(), "KioPrivate_" + Id);
            }
            else
            {
                userDataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Kio", "WebView2Data");
            }

            CoreWebView2EnvironmentOptions options = new CoreWebView2EnvironmentOptions();
            CoreWebView2Environment env = await CoreWebView2Environment.CreateAsync(null, userDataDir, options);
            await WebView.EnsureCoreWebView2Async(env);

            ConfigureSettings();
            RegisterEvents();
        }

        private void ConfigureSettings()
        {
            if (WebView == null || WebView.CoreWebView2 == null) return;

            var settings = WebView.CoreWebView2.Settings;
            settings.IsScriptEnabled = true;
            settings.AreDefaultContextMenusEnabled = true;
            settings.IsWebMessageEnabled = true;
            settings.AreDevToolsEnabled = false;
            settings.IsStatusBarEnabled = false;

            ApplyUserAgent();

            if (BlockImages)
            {
                WebView.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.Image);
            }
        }

        public void SetUserAgentMode(UserAgentMode mode)
        {
            UaMode = mode;
            ApplyUserAgent();
            if (WebView != null && WebView.CoreWebView2 != null)
            {
                WebView.Reload();
            }
        }

        private void ApplyUserAgent()
        {
            if (WebView == null || WebView.CoreWebView2 == null) return;
            string ua = DesktopUserAgent;
            if (UaMode == UserAgentMode.Mobile) ua = MobileUserAgent;
            else if (UaMode == UserAgentMode.iPhone) ua = IPhoneUserAgent;

            WebView.CoreWebView2.Settings.UserAgent = ua;
        }

        public void SetBlockImages(bool block)
        {
            BlockImages = block;
            if (WebView != null && WebView.CoreWebView2 != null)
            {
                if (block)
                {
                    WebView.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.Image);
                }
                else
                {
                    WebView.CoreWebView2.RemoveWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.Image);
                }
            }
        }

        private void RegisterEvents()
        {
            if (WebView == null || WebView.CoreWebView2 == null) return;

            WebView.CoreWebView2.NavigationStarting += delegate(object sender, CoreWebView2NavigationStartingEventArgs e)
            {
                IsLoading = true;
                DetectedMediaList.Clear();
                if (MediaListUpdated != null) MediaListUpdated(this, EventArgs.Empty);
                if (StateChanged != null) StateChanged(this, EventArgs.Empty);
            };

            WebView.CoreWebView2.SourceChanged += delegate(object sender, CoreWebView2SourceChangedEventArgs e)
            {
                Url = WebView.Source != null ? WebView.Source.ToString() : "";
                if (StateChanged != null) StateChanged(this, EventArgs.Empty);
            };

            WebView.CoreWebView2.DocumentTitleChanged += delegate(object sender, object e)
            {
                Title = WebView.CoreWebView2.DocumentTitle;
                if (string.IsNullOrEmpty(Title)) Title = IsPrivate ? "Private Tab" : "New Tab";
                if (StateChanged != null) StateChanged(this, EventArgs.Empty);
            };

            WebView.CoreWebView2.NavigationCompleted += async delegate(object sender, CoreWebView2NavigationCompletedEventArgs e)
            {
                IsLoading = false;
                if (StateChanged != null) StateChanged(this, EventArgs.Empty);

                if (CleanModeEnabled)
                {
                    await InjectCleanModeCssAsync();
                }

                await SniffMediaAsync();
            };

            WebView.CoreWebView2.WebResourceResponseReceived += delegate(object sender, CoreWebView2WebResourceResponseReceivedEventArgs e)
            {
                try
                {
                    string reqUrl = e.Request.Uri;
                    string mime = e.Response.Headers.Contains("Content-Type") ? e.Response.Headers.GetHeader("Content-Type") : "";
                    if (IsMediaMime(mime) || IsMediaExtension(reqUrl))
                    {
                        AddDetectedMedia(reqUrl, mime);
                    }
                }
                catch { }
            };
        }

        private bool IsMediaMime(string mime)
        {
            if (string.IsNullOrEmpty(mime)) return false;
            string l = mime.ToLowerInvariant();
            return l.Contains("video/") || l.Contains("audio/") || l.Contains("application/vnd.apple.mpegurl") || l.Contains("application/x-mpegurl");
        }

        private bool IsMediaExtension(string url)
        {
            if (string.IsNullOrEmpty(url)) return false;
            string l = url.ToLowerInvariant();
            return l.Contains(".mp4") || l.Contains(".m3u8") || l.Contains(".webm") || l.Contains(".m4v") || l.Contains(".mp3");
        }

        public void AddDetectedMedia(string url, string mime)
        {
            if (string.IsNullOrEmpty(url)) return;
            foreach (var m in DetectedMediaList)
            {
                if (string.Equals(m.Url, url, StringComparison.OrdinalIgnoreCase)) return;
            }

            string fmt = "VIDEO";
            string lower = url.ToLowerInvariant();
            if (lower.Contains(".m3u8")) fmt = "HLS/M3U8";
            else if (lower.Contains(".mp4")) fmt = "MP4";
            else if (lower.Contains(".webm")) fmt = "WEBM";
            else if (lower.Contains(".mp3")) fmt = "AUDIO";

            DetectedMediaList.Add(new DetectedMediaItem
            {
                Url = url,
                Title = Title,
                MimeType = mime,
                FormatLabel = fmt
            });

            if (MediaListUpdated != null) MediaListUpdated(this, EventArgs.Empty);
        }

        public async Task SniffMediaAsync()
        {
            if (WebView == null || WebView.CoreWebView2 == null) return;
            try
            {
                string json = await WebView.CoreWebView2.ExecuteScriptAsync(SoulBrowserFeatures.MediaSnifferScript);
                if (!string.IsNullOrEmpty(json) && json != "[]" && json != "\"[]\"")
                {
                    // Basic parse of media stream URLs
                    MatchCollection matches = Regex.Matches(json, @"url"":""([^""]+)""");
                    foreach (Match m in matches)
                    {
                        if (m.Groups.Count > 1)
                        {
                            string u = m.Groups[1].Value.Replace("\\/", "/");
                            AddDetectedMedia(u, "video/mp4");
                        }
                    }
                }
            }
            catch { }
        }

        public async Task InjectCleanModeCssAsync()
        {
            if (WebView == null || WebView.CoreWebView2 == null) return;
            try
            {
                string js = string.Format(
                    "(function(){{ var s = document.createElement('style'); s.id = 'kio-clean-mode'; s.innerHTML = `{0}`; document.head.appendChild(s); }})();",
                    SoulBrowserFeatures.CleanModeCss.Replace("`", "\\`")
                );
                await WebView.CoreWebView2.ExecuteScriptAsync(js);
            }
            catch { }
        }

        public async Task RemoveCleanModeCssAsync()
        {
            if (WebView == null || WebView.CoreWebView2 == null) return;
            try
            {
                await WebView.CoreWebView2.ExecuteScriptAsync(
                    "(function(){ var s = document.getElementById('kio-clean-mode'); if (s) s.remove(); })();"
                );
            }
            catch { }
        }

        public void Navigate(string target)
        {
            if (string.IsNullOrWhiteSpace(target)) return;

            string dest = target.Trim();
            if (!dest.StartsWith("http://", StringComparison.OrdinalIgnoreCase) &&
                !dest.StartsWith("https://", StringComparison.OrdinalIgnoreCase) &&
                !dest.StartsWith("file://", StringComparison.OrdinalIgnoreCase) &&
                !dest.StartsWith("about:", StringComparison.OrdinalIgnoreCase))
            {
                if (dest.Contains(".") && !dest.Contains(" "))
                {
                    dest = "https://" + dest;
                }
                else
                {
                    dest = "https://duckduckgo.com/?q=" + Uri.EscapeDataString(dest);
                }
            }

            if (WebView != null && WebView.CoreWebView2 != null)
            {
                WebView.CoreWebView2.Navigate(dest);
            }
        }

        public void GoBack()
        {
            if (WebView != null && WebView.CanGoBack) WebView.GoBack();
        }

        public void GoForward()
        {
            if (WebView != null && WebView.CanGoForward) WebView.GoForward();
        }

        public void Reload()
        {
            if (WebView != null && WebView.CoreWebView2 != null) WebView.Reload();
        }

        public void Stop()
        {
            if (WebView != null && WebView.CoreWebView2 != null) WebView.Stop();
        }

        public void Dispose()
        {
            if (WebView != null)
            {
                WebView.Dispose();
                WebView = null;
            }

            if (IsPrivate)
            {
                try
                {
                    string dir = Path.Combine(Path.GetTempPath(), "KioPrivate_" + Id);
                    if (Directory.Exists(dir))
                    {
                        Directory.Delete(dir, true);
                    }
                }
                catch { }
            }
        }
    }
}
