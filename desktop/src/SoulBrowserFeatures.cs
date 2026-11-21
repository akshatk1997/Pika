using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Net;
using System.Speech.Synthesis;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace Kio.Desktop
{
    public static class SoulBrowserFeatures
    {
        /// <summary>
        /// Highly targeted CSS rules that strip cookie banners, newsletter popups, floating promo bars, and sticky ads.
        /// </summary>
        public const string CleanModeCss = @"
            /* Kio Clean Mode - Distraction-Free Browsing */
            [id*='cookie-banner'], [id*='cookie-consent'], [id*='cookie-notice'], [id*='cookie-law'],
            [class*='cookie-banner'], [class*='cookie-consent'], [class*='cookie-notice'], [class*='cookie-law'],
            [id*='onetrust-consent-sdk'], [id*='CybotCookiebotDialog'], [id*='qc-cmp2-container'],
            [class*='cmp-container'], [id*='didomi-notice'], [id*='usercentrics-root'],
            [class*='newsletter-popup'], [class*='newsletter-modal'], [id*='newsletter-popup'],
            [class*='subscribe-popup'], [class*='subscribe-modal'], [id*='subscribe-overlay'],
            [class*='sticky-banner-ad'], [class*='floating-ad-banner'], [id*='floating-banner'],
            [aria-label*='cookie' i], [aria-label*='consent' i] {
                display: none !important;
                visibility: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
            }
            body.modal-open, html.modal-open {
                overflow: auto !important;
                position: static !important;
            }
        ";

        /// <summary>
        /// JavaScript injected into web pages to sniff HTML5 video, audio, and source stream elements.
        /// </summary>
        public const string MediaSnifferScript = @"
            (function() {
                try {
                    var detected = [];
                    var seen = {};

                    function addMedia(url, type) {
                        if (!url || typeof url !== 'string') return;
                        url = url.trim();
                        if (url.indexOf('blob:') === 0 || url.indexOf('data:') === 0 || url.indexOf('javascript:') === 0) return;
                        if (url.indexOf('//') === 0) url = window.location.protocol + url;
                        else if (url.indexOf('/') === 0) url = window.location.origin + url;
                        else if (url.indexOf('http') !== 0) {
                            try { url = new URL(url, window.location.href).href; } catch(e) { return; }
                        }
                        if (seen[url]) return;
                        seen[url] = true;

                        var fmt = 'VIDEO';
                        var lower = url.toLowerCase();
                        if (lower.indexOf('.m3u8') !== -1) fmt = 'HLS/M3U8';
                        else if (lower.indexOf('.mp4') !== -1) fmt = 'MP4';
                        else if (lower.indexOf('.webm') !== -1) fmt = 'WEBM';
                        else if (lower.indexOf('.mp3') !== -1 || lower.indexOf('.m4a') !== -1 || lower.indexOf('.ogg') !== -1) fmt = 'AUDIO';
                        else if (type && type.indexOf('audio') !== -1) fmt = 'AUDIO';

                        detected.push({
                            url: url,
                            title: document.title || 'Web Media Stream',
                            mimeType: type || 'video/mp4',
                            formatLabel: fmt
                        });
                    }

                    var videos = document.querySelectorAll('video, audio');
                    for (var i = 0; i < videos.length; i++) {
                        var el = videos[i];
                        if (el.currentSrc) addMedia(el.currentSrc, el.tagName.toLowerCase() === 'audio' ? 'audio/mp3' : 'video/mp4');
                        if (el.src) addMedia(el.src, el.tagName.toLowerCase() === 'audio' ? 'audio/mp3' : 'video/mp4');
                        var sources = el.querySelectorAll('source');
                        for (var j = 0; j < sources.length; j++) {
                            addMedia(sources[j].src, sources[j].type);
                        }
                    }

                    var links = document.querySelectorAll('a[href]');
                    for (var k = 0; k < links.length; k++) {
                        var href = links[k].href || '';
                        var lowHref = href.toLowerCase();
                        if (lowHref.indexOf('.mp4') !== -1 || lowHref.indexOf('.m3u8') !== -1 || lowHref.indexOf('.webm') !== -1) {
                            addMedia(href, 'video/mp4');
                        }
                    }

                    return JSON.stringify(detected);
                } catch(err) {
                    return '[]';
                }
            })();
        ";

        /// <summary>
        /// JavaScript injected into web pages to scrape all images (img, picture, srcset, CSS background-image).
        /// </summary>
        public const string ImageScraperScript = @"
            (function() {
                try {
                    var images = [];
                    var seen = {};

                    function addImg(url, alt, w, h) {
                        if (!url || typeof url !== 'string') return;
                        url = url.trim();
                        if (url.indexOf('data:') === 0 && url.length < 100) return;
                        if (url.indexOf('//') === 0) url = window.location.protocol + url;
                        else if (url.indexOf('/') === 0) url = window.location.origin + url;
                        else if (url.indexOf('http') !== 0 && url.indexOf('data:') !== 0) {
                            try { url = new URL(url, window.location.href).href; } catch(e) { return; }
                        }
                        if (seen[url]) return;
                        seen[url] = true;

                        images.push({
                            url: url,
                            alt: (alt || '').trim() || 'Image',
                            width: w || 0,
                            height: h || 0
                        });
                    }

                    var imgEls = document.querySelectorAll('img');
                    for (var i = 0; i < imgEls.length; i++) {
                        var img = imgEls[i];
                        var src = img.currentSrc || img.src || img.getAttribute('data-src') || '';
                        addImg(src, img.alt, img.naturalWidth || img.width, img.naturalHeight || img.height);
                    }

                    var sources = document.querySelectorAll('picture source');
                    for (var j = 0; j < sources.length; j++) {
                        var s = sources[j];
                        var srcset = s.getAttribute('srcset') || '';
                        if (srcset) {
                            var first = srcset.split(',')[0].trim().split(' ')[0];
                            addImg(first, 'Picture Source', 0, 0);
                        }
                    }

                    var allEls = document.querySelectorAll('*');
                    for (var k = 0; k < Math.min(allEls.length, 300); k++) {
                        var bg = window.getComputedStyle(allEls[k]).backgroundImage;
                        if (bg && bg.indexOf('url(') !== -1) {
                            var match = bg.match(/url\(['""]?(.*?)['""]?\)/);
                            if (match && match[1]) {
                                addImg(match[1], 'CSS Background', 0, 0);
                            }
                        }
                    }

                    return JSON.stringify(images);
                } catch(e) {
                    return '[]';
                }
            })();
        ";

        /// <summary>
        /// JavaScript injected to extract clean article content for Reader Mode and Speech Announcer.
        /// </summary>
        public const string ReaderExtractorScript = @"
            (function() {
                try {
                    var title = document.title || '';
                    var h1 = document.querySelector('h1');
                    if (h1 && h1.innerText) title = h1.innerText.trim();

                    var bodyClone = document.body.cloneNode(true);
                    var scripts = bodyClone.querySelectorAll('script, style, noscript, nav, header, footer, aside, .sidebar, .ad, .ads, [role=""banner""], [role=""navigation""]');
                    for (var i = 0; i < scripts.length; i++) scripts[i].remove();

                    var paras = bodyClone.querySelectorAll('p, h2, h3, article, section');
                    var fullText = [];
                    for (var j = 0; j < paras.length; j++) {
                        var t = (paras[j].innerText || '').trim();
                        if (t.length > 30) fullText.push(t);
                    }

                    return JSON.stringify({
                        title: title,
                        text: fullText.join('\n\n')
                    });
                } catch(e) {
                    return JSON.stringify({ title: document.title, text: '' });
                }
            })();
        ";

        /// <summary>
        /// Captures the full vertical document of the active tab into a lossless PNG in Pictures\Kio\.
        /// </summary>
        public static async Task<string> CaptureFullPageAsync(WebView2 webView)
        {
            if (webView == null || webView.CoreWebView2 == null) return null;

            string picturesDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyPictures), "Kio");
            if (!Directory.Exists(picturesDir)) Directory.CreateDirectory(picturesDir);

            string fileName = string.Format("Kio_Capture_{0:yyyyMMdd_HHmmss}.png", DateTime.Now);
            string fullPath = Path.Combine(picturesDir, fileName);

            using (MemoryStream ms = new MemoryStream())
            {
                await webView.CoreWebView2.CapturePreviewAsync(CoreWebView2CapturePreviewImageFormat.Png, ms);
                byte[] bytes = ms.ToArray();
                File.WriteAllBytes(fullPath, bytes);
            }

            return fullPath;
        }

        /// <summary>
        /// Enqueues a direct media download into Downloads\Kio\.
        /// </summary>
        public static void DownloadMediaDirect(string url, string title)
        {
            if (string.IsNullOrEmpty(url)) return;

            string downloadsDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "Kio");
            if (!Directory.Exists(downloadsDir)) Directory.CreateDirectory(downloadsDir);

            string ext = ".mp4";
            string lower = url.ToLowerInvariant();
            if (lower.Contains(".m3u8")) ext = ".m3u8";
            else if (lower.Contains(".webm")) ext = ".webm";
            else if (lower.Contains(".mp3")) ext = ".mp3";

            string cleanName = Regex.Replace(title ?? "Media", @"[^\w\s\.-]", "").Trim();
            if (string.IsNullOrEmpty(cleanName)) cleanName = "Media_" + DateTime.Now.Ticks;
            if (cleanName.Length > 40) cleanName = cleanName.Substring(0, 40);

            string filePath = Path.Combine(downloadsDir, cleanName + ext);

            Task.Factory.StartNew(delegate
            {
                try
                {
                    using (WebClient client = new WebClient())
                    {
                        client.Headers[HttpRequestHeader.UserAgent] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Kio/2.0";
                        client.DownloadFile(new Uri(url), filePath);
                    }
                }
                catch { }
            });
        }

        /// <summary>
        /// Plays media in the system's default media player or VLC.
        /// </summary>
        public static void PlayMediaExternal(string url)
        {
            if (string.IsNullOrEmpty(url)) return;
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = url,
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                MessageBox.Show("Could not launch external player: " + ex.Message, "Kio Media Player", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
        }

        /// <summary>
        /// Batch downloads multiple extracted images into Pictures\Kio\.
        /// </summary>
        public static void BatchDownloadImages(List<ExtractedImageItem> images)
        {
            if (images == null || images.Count == 0) return;

            string picturesDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyPictures), "Kio");
            if (!Directory.Exists(picturesDir)) Directory.CreateDirectory(picturesDir);

            Task.Factory.StartNew(delegate
            {
                using (WebClient client = new WebClient())
                {
                    client.Headers[HttpRequestHeader.UserAgent] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Kio/2.0";
                    int count = 0;
                    foreach (var img in images)
                    {
                        if (!img.IsSelected || string.IsNullOrEmpty(img.Url) || img.Url.StartsWith("data:")) continue;
                        try
                        {
                            count++;
                            string ext = ".jpg";
                            string l = img.Url.ToLowerInvariant();
                            if (l.Contains(".png")) ext = ".png";
                            else if (l.Contains(".webp")) ext = ".webp";
                            else if (l.Contains(".gif")) ext = ".gif";
                            else if (l.Contains(".svg")) ext = ".svg";

                            string savePath = Path.Combine(picturesDir, string.Format("Kio_Image_{0}_{1}{2}", DateTime.Now.ToString("yyyyMMdd_HHmmss"), count, ext));
                            client.DownloadFile(new Uri(img.Url), savePath);
                        }
                        catch { }
                    }
                }
            });
        }

        private static SpeechSynthesizer _synthesizer;

        /// <summary>
        /// Speaks the text using on-device Windows Text-to-Speech (zero cloud data transfer).
        /// </summary>
        public static void SpeakTextOnDevice(string text)
        {
            if (string.IsNullOrEmpty(text)) return;
            try
            {
                if (_synthesizer == null)
                {
                    _synthesizer = new SpeechSynthesizer();
                    _synthesizer.SetOutputToDefaultAudioDevice();
                    _synthesizer.Rate = 1;
                }
                _synthesizer.SpeakAsyncCancelAll();
                _synthesizer.SpeakAsync(text);
            }
            catch { }
        }

        public static void StopSpeaking()
        {
            try
            {
                if (_synthesizer != null)
                {
                    _synthesizer.SpeakAsyncCancelAll();
                }
            }
            catch { }
        }
    }
}
