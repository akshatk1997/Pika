using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.WinForms;

namespace Kio.Desktop
{
    public class BrowserForm : Form
    {
        private List<BrowserTab> _tabs;
        private BrowserTab _activeTab;

        private Panel _titlePanel;
        private Panel _tabBarPanel;
        private Panel _navBarPanel;
        private Panel _webContainer;
        private StatusStrip _statusStrip;
        private ToolStripStatusLabel _statusLabel;
        private ToolStripStatusLabel _zoomLabel;

        // Nav Bar Controls
        private Button _btnBack;
        private Button _btnForward;
        private Button _btnReload;
        private Button _btnHome;
        private Label _lblSecurity;
        private TextBox _txtOmnibox;
        private Button _btnGo;

        // Soul Browser Feature Buttons
        private Button _btnMedia;
        private Button _btnImages;
        private Button _btnCapture;
        private Button _btnScreenFilter;
        private Button _btnCleanMode;
        private Button _btnUserAgent;
        private Button _btnBlockImages;
        private Button _btnReader;
        private Button _btnTheme;

        // Theme and Screen Filter State
        private bool _isDarkTheme = true;
        private ScreenFilterMode _filterMode = ScreenFilterMode.None;
        private float _filterOpacity = 0.35f;
        private FilterOverlayControl _filterOverlay;

        public BrowserForm()
        {
            _tabs = new List<BrowserTab>();

            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer, true);
            UpdateStyles();

            Text = "Kio Browser";
            Width = 1200;
            Height = 800;
            StartPosition = FormStartPosition.CenterScreen;
            MinimumSize = new Size(800, 500);

            InitializeComponents();
            ApplyTheme();

            AddNewTab(false, "https://duckduckgo.com");
        }

        private void InitializeComponents()
        {
            // 1. Title Bar Panel (Kio Logo + Title)
            _titlePanel = new Panel
            {
                Dock = DockStyle.Top,
                Height = 36,
                Padding = new Padding(8, 0, 8, 0)
            };
            _titlePanel.Paint += TitlePanel_Paint;

            // 2. Tab Bar Panel
            _tabBarPanel = new Panel
            {
                Dock = DockStyle.Top,
                Height = 38,
                Padding = new Padding(6, 2, 6, 0)
            };
            _tabBarPanel.Paint += TabBarPanel_Paint;
            _tabBarPanel.MouseClick += TabBarPanel_MouseClick;

            // 3. Navigation Bar Panel
            _navBarPanel = new Panel
            {
                Dock = DockStyle.Top,
                Height = 44,
                Padding = new Padding(8, 5, 8, 5)
            };

            // Back, Forward, Reload, Home buttons
            _btnBack = CreateNavButton("◄", "Back (Alt+Left)", delegate { if (_activeTab != null) _activeTab.GoBack(); });
            _btnForward = CreateNavButton("►", "Forward (Alt+Right)", delegate { if (_activeTab != null) _activeTab.GoForward(); });
            _btnReload = CreateNavButton("⟳", "Reload (Ctrl+R)", delegate { if (_activeTab != null) _activeTab.Reload(); });
            _btnHome = CreateNavButton("🏠", "Home (Alt+Home)", delegate { if (_activeTab != null) _activeTab.Navigate("https://duckduckgo.com"); });

            // Security Indicator Badge
            _lblSecurity = new Label
            {
                Text = "🔒",
                AutoSize = false,
                Width = 28,
                Height = 28,
                TextAlign = ContentAlignment.MiddleCenter,
                Font = new Font("Segoe UI", 10f),
                Cursor = Cursors.Hand
            };

            // Omnibox Address Field
            _txtOmnibox = new TextBox
            {
                Font = new Font("Segoe UI", 10.5f),
                BorderStyle = BorderStyle.FixedSingle,
                Height = 28
            };
            _txtOmnibox.KeyDown += TxtOmnibox_KeyDown;

            _btnGo = CreateNavButton("➔", "Go", delegate { NavigateActiveTab(); });

            // Soul Browser Feature Action Buttons
            _btnMedia = CreateNavButton("🎬 Media", "Media Downloader / Sniffer", delegate { ShowMediaDownloaderDialog(); });
            _btnImages = CreateNavButton("🖼️ Images", "Save All Images (Image Box)", delegate { ShowImageScraperDialog(); });
            _btnCapture = CreateNavButton("📸 Capture", "Full Page Scrolling Screenshot", async delegate { await CaptureFullPageScreenshotAsync(); });
            _btnScreenFilter = CreateNavButton("👁️ Filter", "Screen Filter (Night Dimmer & Eye Care)", delegate { ShowScreenFilterDialog(); });
            _btnCleanMode = CreateNavButton("🧹 Clean", "Clean Mode (Distraction-Free Browsing)", delegate { ToggleCleanMode(); });
            _btnUserAgent = CreateNavButton("📱 UA", "Switch User-Agent (Desktop/Mobile/iPhone)", delegate { ShowUserAgentMenu(); });
            _btnBlockImages = CreateNavButton("🖼️🚫", "Toggle Block Images", delegate { ToggleBlockImages(); });
            _btnReader = CreateNavButton("📖 Read", "Reader Mode & Speech Announcer", async delegate { await ToggleReaderAnnouncerAsync(); });
            _btnTheme = CreateNavButton("🌙", "Toggle Dark/Light Theme", delegate { ToggleTheme(); });

            // Layout Nav Bar
            _navBarPanel.Controls.Add(_btnBack);
            _navBarPanel.Controls.Add(_btnForward);
            _navBarPanel.Controls.Add(_btnReload);
            _navBarPanel.Controls.Add(_btnHome);
            _navBarPanel.Controls.Add(_lblSecurity);
            _navBarPanel.Controls.Add(_txtOmnibox);
            _navBarPanel.Controls.Add(_btnGo);
            _navBarPanel.Controls.Add(_btnMedia);
            _navBarPanel.Controls.Add(_btnImages);
            _navBarPanel.Controls.Add(_btnCapture);
            _navBarPanel.Controls.Add(_btnScreenFilter);
            _navBarPanel.Controls.Add(_btnCleanMode);
            _navBarPanel.Controls.Add(_btnUserAgent);
            _navBarPanel.Controls.Add(_btnBlockImages);
            _navBarPanel.Controls.Add(_btnReader);
            _navBarPanel.Controls.Add(_btnTheme);
            _navBarPanel.Resize += delegate { LayoutNavBarControls(); };

            // 4. Web Content Container Panel
            _webContainer = new Panel
            {
                Dock = DockStyle.Fill
            };

            // 5. Screen Filter Overlay Control
            _filterOverlay = new FilterOverlayControl
            {
                Dock = DockStyle.Fill,
                Visible = false
            };
            _webContainer.Controls.Add(_filterOverlay);
            _filterOverlay.BringToFront();

            // 6. Status Strip
            _statusStrip = new StatusStrip();
            _statusLabel = new ToolStripStatusLabel { Text = "Ready", Spring = true, TextAlign = ContentAlignment.MiddleLeft };
            _zoomLabel = new ToolStripStatusLabel { Text = "100%", AutoToolTip = true, ToolTipText = "Zoom Level" };
            _statusStrip.Items.Add(_statusLabel);
            _statusStrip.Items.Add(_zoomLabel);

            // Add all panels to Form in reverse dock order
            Controls.Add(_webContainer);
            Controls.Add(_statusStrip);
            Controls.Add(_navBarPanel);
            Controls.Add(_tabBarPanel);
            Controls.Add(_titlePanel);

            KeyPreview = true;
            KeyDown += BrowserForm_KeyDown;
        }

        private Button CreateNavButton(string text, string tooltip, EventHandler onClick)
        {
            Button btn = new Button
            {
                Text = text,
                AutoSize = true,
                Height = 28,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f),
                Cursor = Cursors.Hand,
                Margin = new Padding(2, 0, 2, 0)
            };
            btn.FlatAppearance.BorderSize = 0;
            btn.Click += onClick;
            ToolTip tip = new ToolTip();
            tip.SetToolTip(btn, tooltip);
            return btn;
        }

        private void LayoutNavBarControls()
        {
            int left = 8;
            Button[] navLeft = { _btnBack, _btnForward, _btnReload, _btnHome };
            foreach (var b in navLeft)
            {
                b.Location = new Point(left, 8);
                left += b.Width + 4;
            }

            _lblSecurity.Location = new Point(left, 8);
            left += _lblSecurity.Width + 2;

            int right = _navBarPanel.Width - 8;
            Button[] navRight = { _btnTheme, _btnReader, _btnBlockImages, _btnUserAgent, _btnCleanMode, _btnScreenFilter, _btnCapture, _btnImages, _btnMedia, _btnGo };
            foreach (var b in navRight)
            {
                right -= b.Width;
                b.Location = new Point(right, 8);
                right -= 4;
            }

            int omniWidth = Math.Max(120, right - left - 4);
            _txtOmnibox.Location = new Point(left, 8);
            _txtOmnibox.Width = omniWidth;
        }

        private void ApplyTheme()
        {
            Color bg = _isDarkTheme ? KioTheme.DarkBackground : KioTheme.LightBackground;
            Color surface = _isDarkTheme ? KioTheme.DarkSurface : KioTheme.LightSurface;
            Color text = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText;
            Color border = _isDarkTheme ? KioTheme.DarkBorder : KioTheme.LightBorder;

            BackColor = bg;
            ForeColor = text;

            _titlePanel.BackColor = bg;
            _tabBarPanel.BackColor = bg;
            _navBarPanel.BackColor = surface;

            _txtOmnibox.BackColor = _isDarkTheme ? Color.FromArgb(20, 27, 45) : Color.White;
            _txtOmnibox.ForeColor = text;

            Button[] buttons = { _btnBack, _btnForward, _btnReload, _btnHome, _btnGo, _btnMedia, _btnImages, _btnCapture, _btnScreenFilter, _btnCleanMode, _btnUserAgent, _btnBlockImages, _btnReader, _btnTheme };
            foreach (var btn in buttons)
            {
                btn.BackColor = _isDarkTheme ? Color.FromArgb(24, 32, 53) : Color.FromArgb(241, 245, 249);
                btn.ForeColor = text;
            }

            _statusStrip.BackColor = surface;
            _statusLabel.ForeColor = text;
            _zoomLabel.ForeColor = text;

            _btnTheme.Text = _isDarkTheme ? "🌙" : "☀️";

            _titlePanel.Invalidate();
            _tabBarPanel.Invalidate();
            LayoutNavBarControls();
        }

        private void TitlePanel_Paint(object sender, PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;

            // Draw Kio Concentric Orbital Emblem
            bool isPriv = _activeTab != null && _activeTab.IsPrivate;
            Rectangle emblemRect = new Rectangle(10, 6, 24, 24);
            KioTheme.DrawKioEmblem(g, emblemRect, isPriv);

            // Draw Browser Title
            string title = isPriv ? "Kio Stealth Browser (Private)" : "Kio Browser";
            using (Font titleFont = new Font("Segoe UI", 10.5f, FontStyle.Bold))
            using (SolidBrush brush = new SolidBrush(isPriv ? KioTheme.PrivateAccent : KioTheme.AccentCyan))
            {
                g.DrawString(title, titleFont, brush, 42, 7);
            }

            // Draw Version / Tag
            using (Font subFont = new Font("Segoe UI", 8f))
            using (SolidBrush subBrush = new SolidBrush(KioTheme.DarkTextMuted))
            {
                g.DrawString("v2.0 Desktop (< 2 MB)", subFont, subBrush, 220, 9);
            }
        }

        private void TabBarPanel_Paint(object sender, PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;

            int tabX = 8;
            int tabY = 4;
            int tabH = 30;
            int tabW = 180;

            for (int i = 0; i < _tabs.Count; i++)
            {
                BrowserTab tab = _tabs[i];
                bool isActive = tab == _activeTab;

                Rectangle tabRect = new Rectangle(tabX, tabY, tabW, tabH);

                Color tabBg = isActive
                    ? (tab.IsPrivate ? KioTheme.PrivateSurface : (_isDarkTheme ? KioTheme.DarkSurface : KioTheme.LightSurface))
                    : (_isDarkTheme ? Color.FromArgb(14, 20, 36) : Color.FromArgb(235, 240, 248));

                using (GraphicsPath path = RoundedRect(tabRect, 8, 8, 0, 0))
                using (SolidBrush brush = new SolidBrush(tabBg))
                {
                    g.FillPath(brush, path);
                }

                if (isActive)
                {
                    // Glowing active line
                    using (Pen pen = new Pen(tab.IsPrivate ? KioTheme.PrivateAccent : KioTheme.AccentCyan, 2f))
                    {
                        g.DrawLine(pen, tabX + 4, tabY, tabX + tabW - 4, tabY);
                    }
                }

                // Favicon / Mode Icon
                string iconText = tab.IsPrivate ? "🛡️" : (tab.IsLoading ? "⏳" : "🌐");
                using (Font iconFont = new Font("Segoe UI", 9f))
                using (SolidBrush iconBrush = new SolidBrush(tab.IsPrivate ? KioTheme.PrivateAccent : KioTheme.AccentSky))
                {
                    g.DrawString(iconText, iconFont, iconBrush, tabX + 8, tabY + 6);
                }

                // Title
                string dispTitle = tab.Title ?? "New Tab";
                if (dispTitle.Length > 16) dispTitle = dispTitle.Substring(0, 16) + "…";
                using (Font f = new Font("Segoe UI", 9f, isActive ? FontStyle.Bold : FontStyle.Regular))
                using (SolidBrush textBrush = new SolidBrush(_isDarkTheme ? KioTheme.DarkText : KioTheme.LightText))
                {
                    g.DrawString(dispTitle, f, textBrush, tabX + 30, tabY + 7);
                }

                // Close Button (×)
                using (Font closeFont = new Font("Segoe UI", 9f))
                using (SolidBrush closeBrush = new SolidBrush(KioTheme.DarkTextMuted))
                {
                    g.DrawString("×", closeFont, closeBrush, tabX + tabW - 18, tabY + 6);
                }

                tabX += tabW + 4;
            }

            // New Standard Tab Button (+)
            Rectangle plusRect = new Rectangle(tabX, tabY + 2, 26, 26);
            using (SolidBrush plusBg = new SolidBrush(_isDarkTheme ? Color.FromArgb(24, 32, 53) : Color.FromArgb(230, 235, 245)))
            {
                g.FillEllipse(plusBg, plusRect);
            }
            using (Font plusFont = new Font("Segoe UI", 11f, FontStyle.Bold))
            using (SolidBrush plusText = new SolidBrush(_isDarkTheme ? KioTheme.DarkText : KioTheme.LightText))
            {
                g.DrawString("+", plusFont, plusText, tabX + 7, tabY + 4);
            }
            tabX += 32;

            // New Private Tab Button (🛡️+)
            Rectangle privRect = new Rectangle(tabX, tabY + 2, 34, 26);
            using (SolidBrush privBg = new SolidBrush(KioTheme.PrivateSurface))
            {
                g.FillEllipse(privBg, privRect);
            }
            using (Font privFont = new Font("Segoe UI", 9f, FontStyle.Bold))
            using (SolidBrush privText = new SolidBrush(KioTheme.PrivateAccent))
            {
                g.DrawString("🛡️+", privFont, privText, tabX + 4, tabY + 5);
            }
        }

        private void TabBarPanel_MouseClick(object sender, MouseEventArgs e)
        {
            int tabX = 8;
            int tabW = 180;
            int tabH = 30;

            for (int i = 0; i < _tabs.Count; i++)
            {
                Rectangle tabRect = new Rectangle(tabX, 4, tabW, tabH);
                Rectangle closeRect = new Rectangle(tabX + tabW - 22, 4, 20, tabH);

                if (closeRect.Contains(e.Location))
                {
                    CloseTab(_tabs[i]);
                    return;
                }
                else if (tabRect.Contains(e.Location))
                {
                    SetActiveTab(_tabs[i]);
                    return;
                }

                tabX += tabW + 4;
            }

            // New standard tab (+)
            Rectangle plusRect = new Rectangle(tabX, 6, 26, 26);
            if (plusRect.Contains(e.Location))
            {
                AddNewTab(false, "https://duckduckgo.com");
                return;
            }
            tabX += 32;

            // New private tab (🛡️+)
            Rectangle privRect = new Rectangle(tabX, 6, 34, 26);
            if (privRect.Contains(e.Location))
            {
                AddNewTab(true, "https://duckduckgo.com");
                return;
            }
        }

        public async void AddNewTab(bool isPrivate, string startUrl)
        {
            BrowserTab tab = new BrowserTab(isPrivate);
            _tabs.Add(tab);

            tab.StateChanged += delegate { UpdateNavBarState(); _tabBarPanel.Invalidate(); };
            tab.MediaListUpdated += delegate { UpdateMediaButtonState(); };

            await tab.InitializeAsync();
            tab.Navigate(startUrl);

            SetActiveTab(tab);
        }

        public void CloseTab(BrowserTab tab)
        {
            if (_tabs.Count <= 1)
            {
                // Never close the last tab, reset to home
                tab.Navigate("https://duckduckgo.com");
                return;
            }

            int index = _tabs.IndexOf(tab);
            _tabs.Remove(tab);

            if (_webContainer.Controls.Contains(tab.WebView))
            {
                _webContainer.Controls.Remove(tab.WebView);
            }
            tab.Dispose();

            if (tab == _activeTab)
            {
                int nextIndex = Math.Max(0, index - 1);
                SetActiveTab(_tabs[nextIndex]);
            }
            else
            {
                _tabBarPanel.Invalidate();
            }
        }

        public void SetActiveTab(BrowserTab tab)
        {
            if (tab == null) return;
            _activeTab = tab;

            _webContainer.Controls.Clear();
            _webContainer.Controls.Add(tab.WebView);
            _webContainer.Controls.Add(_filterOverlay);
            _filterOverlay.BringToFront();

            UpdateNavBarState();
            UpdateMediaButtonState();
            _titlePanel.Invalidate();
            _tabBarPanel.Invalidate();
        }

        private void UpdateNavBarState()
        {
            if (_activeTab == null) return;

            _btnBack.Enabled = _activeTab.CanGoBack;
            _btnForward.Enabled = _activeTab.CanGoForward;
            _btnReload.Text = _activeTab.IsLoading ? "✕" : "⟳";

            if (!_txtOmnibox.Focused)
            {
                _txtOmnibox.Text = _activeTab.Url == "about:blank" ? "" : _activeTab.Url;
            }

            // Security Lock
            if (_activeTab.IsPrivate)
            {
                _lblSecurity.Text = "🛡️";
                _lblSecurity.ForeColor = KioTheme.PrivateAccent;
            }
            else if (_activeTab.Url.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                _lblSecurity.Text = "🔒";
                _lblSecurity.ForeColor = KioTheme.AccentGreen;
            }
            else if (_activeTab.Url.StartsWith("http://", StringComparison.OrdinalIgnoreCase))
            {
                _lblSecurity.Text = "⚠️";
                _lblSecurity.ForeColor = KioTheme.AccentAmber;
            }
            else
            {
                _lblSecurity.Text = "🌐";
                _lblSecurity.ForeColor = KioTheme.AccentSky;
            }

            _statusLabel.Text = _activeTab.IsLoading ? "Loading: " + _activeTab.Url : "Ready";
            _zoomLabel.Text = string.Format("{0}%", (int)(_activeTab.ZoomFactor * 100));

            _btnCleanMode.ForeColor = _activeTab.CleanModeEnabled ? KioTheme.AccentGreen : (_isDarkTheme ? KioTheme.DarkText : KioTheme.LightText);
            _btnBlockImages.ForeColor = _activeTab.BlockImages ? KioTheme.AccentRed : (_isDarkTheme ? KioTheme.DarkText : KioTheme.LightText);
        }

        private void UpdateMediaButtonState()
        {
            if (_activeTab == null) return;
            int count = _activeTab.DetectedMediaList.Count;
            if (count > 0)
            {
                _btnMedia.Text = string.Format("🎬 Media ({0})", count);
                _btnMedia.ForeColor = KioTheme.AccentCyan;
            }
            else
            {
                _btnMedia.Text = "🎬 Media";
                _btnMedia.ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText;
            }
        }

        private void TxtOmnibox_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.KeyCode == Keys.Enter)
            {
                NavigateActiveTab();
                e.Handled = true;
                e.SuppressKeyPress = true;
            }
        }

        private void NavigateActiveTab()
        {
            if (_activeTab != null && !string.IsNullOrWhiteSpace(_txtOmnibox.Text))
            {
                _activeTab.Navigate(_txtOmnibox.Text);
            }
        }

        // --- Soul Browser Power-User Features Dialogs & Actions ---

        private void ShowMediaDownloaderDialog()
        {
            if (_activeTab == null) return;

            Form dlg = new Form
            {
                Text = "Kio Media Sniffer & Downloader",
                Width = 650,
                Height = 420,
                StartPosition = FormStartPosition.CenterParent,
                BackColor = _isDarkTheme ? KioTheme.DarkSurface : Color.White,
                ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText,
                FormBorderStyle = FormBorderStyle.FixedDialog,
                MaximizeBox = false,
                MinimizeBox = false
            };

            ListView lv = new ListView
            {
                Dock = DockStyle.Top,
                Height = 310,
                View = View.Details,
                FullRowSelect = true,
                BackColor = _isDarkTheme ? Color.FromArgb(20, 27, 45) : Color.FromArgb(248, 250, 252),
                ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText
            };
            lv.Columns.Add("Format", 90);
            lv.Columns.Add("Title", 220);
            lv.Columns.Add("Stream URL", 300);

            foreach (var m in _activeTab.DetectedMediaList)
            {
                var item = new ListViewItem(new string[] { m.FormatLabel, m.Title, m.Url });
                item.Tag = m;
                lv.Items.Add(item);
            }

            Panel btnPanel = new Panel { Dock = DockStyle.Bottom, Height = 55, Padding = new Padding(10) };
            Button btnDownload = new Button { Text = "⬇ Download", AutoSize = true, Left = 10, Top = 12 };
            Button btnPlay = new Button { Text = "▶ Play in Player", AutoSize = true, Left = 120, Top = 12 };
            Button btnCopy = new Button { Text = "📋 Copy Link", AutoSize = true, Left = 245, Top = 12 };
            Button btnClose = new Button { Text = "Close", AutoSize = true, Left = 530, Top = 12 };

            btnDownload.Click += delegate
            {
                if (lv.SelectedItems.Count > 0)
                {
                    var m = (DetectedMediaItem)lv.SelectedItems[0].Tag;
                    SoulBrowserFeatures.DownloadMediaDirect(m.Url, m.Title);
                    MessageBox.Show("Download enqueued to Downloads\\Kio\\", "Kio Download", MessageBoxButtons.OK, MessageBoxIcon.Information);
                }
            };

            btnPlay.Click += delegate
            {
                if (lv.SelectedItems.Count > 0)
                {
                    var m = (DetectedMediaItem)lv.SelectedItems[0].Tag;
                    SoulBrowserFeatures.PlayMediaExternal(m.Url);
                }
            };

            btnCopy.Click += delegate
            {
                if (lv.SelectedItems.Count > 0)
                {
                    var m = (DetectedMediaItem)lv.SelectedItems[0].Tag;
                    Clipboard.SetText(m.Url);
                    MessageBox.Show("Stream URL copied to clipboard!", "Kio", MessageBoxButtons.OK, MessageBoxIcon.Information);
                }
            };

            btnClose.Click += delegate { dlg.Close(); };

            btnPanel.Controls.Add(btnDownload);
            btnPanel.Controls.Add(btnPlay);
            btnPanel.Controls.Add(btnCopy);
            btnPanel.Controls.Add(btnClose);

            dlg.Controls.Add(lv);
            dlg.Controls.Add(btnPanel);
            dlg.ShowDialog(this);
        }

        private async void ShowImageScraperDialog()
        {
            if (_activeTab == null || _activeTab.WebView == null) return;

            string json = await _activeTab.WebView.CoreWebView2.ExecuteScriptAsync(SoulBrowserFeatures.ImageScraperScript);
            List<ExtractedImageItem> images = new List<ExtractedImageItem>();

            MatchCollection matches = Regex.Matches(json ?? "", @"url"":""([^""]+)""");
            foreach (Match m in matches)
            {
                if (m.Groups.Count > 1)
                {
                    string u = m.Groups[1].Value.Replace("\\/", "/");
                    images.Add(new ExtractedImageItem { Url = u, Alt = "Image" });
                }
            }

            Form dlg = new Form
            {
                Text = string.Format("Kio Image Box — {0} Images Scraped", images.Count),
                Width = 700,
                Height = 450,
                StartPosition = FormStartPosition.CenterParent,
                BackColor = _isDarkTheme ? KioTheme.DarkSurface : Color.White,
                ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText
            };

            CheckedListBox clb = new CheckedListBox
            {
                Dock = DockStyle.Top,
                Height = 330,
                CheckOnClick = true,
                BackColor = _isDarkTheme ? Color.FromArgb(20, 27, 45) : Color.FromArgb(248, 250, 252),
                ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText
            };

            for (int i = 0; i < images.Count; i++)
            {
                clb.Items.Add(images[i].Url, true);
            }

            Panel pnl = new Panel { Dock = DockStyle.Bottom, Height = 55, Padding = new Padding(10) };
            Button btnSelectAll = new Button { Text = "Toggle All", Left = 10, Top = 12, AutoSize = true };
            Button btnSave = new Button { Text = "💾 Save Selected to Pictures", Left = 120, Top = 12, AutoSize = true };
            Button btnClose = new Button { Text = "Close", Left = 580, Top = 12, AutoSize = true };

            bool allChecked = true;
            btnSelectAll.Click += delegate
            {
                allChecked = !allChecked;
                for (int i = 0; i < clb.Items.Count; i++) clb.SetItemChecked(i, allChecked);
            };

            btnSave.Click += delegate
            {
                List<ExtractedImageItem> selected = new List<ExtractedImageItem>();
                for (int i = 0; i < clb.Items.Count; i++)
                {
                    if (clb.GetItemChecked(i)) selected.Add(images[i]);
                }
                SoulBrowserFeatures.BatchDownloadImages(selected);
                MessageBox.Show(string.Format("Saving {0} images directly to Pictures\\Kio\\", selected.Count), "Kio Image Box", MessageBoxButtons.OK, MessageBoxIcon.Information);
                dlg.Close();
            };

            btnClose.Click += delegate { dlg.Close(); };

            pnl.Controls.Add(btnSelectAll);
            pnl.Controls.Add(btnSave);
            pnl.Controls.Add(btnClose);

            dlg.Controls.Add(clb);
            dlg.Controls.Add(pnl);
            dlg.ShowDialog(this);
        }

        private async Task CaptureFullPageScreenshotAsync()
        {
            if (_activeTab == null) return;
            string savedPath = await SoulBrowserFeatures.CaptureFullPageAsync(_activeTab.WebView);
            if (!string.IsNullOrEmpty(savedPath))
            {
                DialogResult res = MessageBox.Show(
                    "Full page screenshot captured successfully!\n\nSaved to: " + savedPath + "\n\nWould you like to open the image?",
                    "Kio Full Page Capture",
                    MessageBoxButtons.YesNo,
                    MessageBoxIcon.Information
                );
                if (res == DialogResult.Yes)
                {
                    try { System.Diagnostics.Process.Start(savedPath); } catch { }
                }
            }
        }

        private void ShowScreenFilterDialog()
        {
            Form dlg = new Form
            {
                Text = "Screen Filter (Eye Care & Dimmer)",
                Width = 380,
                Height = 260,
                StartPosition = FormStartPosition.CenterParent,
                FormBorderStyle = FormBorderStyle.FixedDialog,
                MaximizeBox = false,
                MinimizeBox = false,
                BackColor = _isDarkTheme ? KioTheme.DarkSurface : Color.White,
                ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText
            };

            RadioButton rbNone = new RadioButton { Text = "Off (Normal)", Left = 20, Top = 20, Checked = _filterMode == ScreenFilterMode.None };
            RadioButton rbAmber = new RadioButton { Text = "Warm Amber (Blue Light Eye Care)", Left = 20, Top = 50, Checked = _filterMode == ScreenFilterMode.WarmAmber };
            RadioButton rbOled = new RadioButton { Text = "Night Dimmer (OLED Dark Mode)", Left = 20, Top = 80, Checked = _filterMode == ScreenFilterMode.NightDimmer };

            Label lblOpacity = new Label { Text = string.Format("Intensity / Opacity: {0}%", (int)(_filterOpacity * 100)), Left = 20, Top = 120, AutoSize = true };
            TrackBar tb = new TrackBar { Left = 20, Top = 145, Width = 320, Minimum = 10, Maximum = 80, Value = (int)(_filterOpacity * 100) };

            tb.Scroll += delegate
            {
                _filterOpacity = tb.Value / 100f;
                lblOpacity.Text = string.Format("Intensity / Opacity: {0}%", tb.Value);
                ApplyScreenFilter();
            };

            EventHandler modeChanged = delegate
            {
                if (rbAmber.Checked) _filterMode = ScreenFilterMode.WarmAmber;
                else if (rbOled.Checked) _filterMode = ScreenFilterMode.NightDimmer;
                else _filterMode = ScreenFilterMode.None;
                ApplyScreenFilter();
            };

            rbNone.CheckedChanged += modeChanged;
            rbAmber.CheckedChanged += modeChanged;
            rbOled.CheckedChanged += modeChanged;

            dlg.Controls.Add(rbNone);
            dlg.Controls.Add(rbAmber);
            dlg.Controls.Add(rbOled);
            dlg.Controls.Add(lblOpacity);
            dlg.Controls.Add(tb);

            dlg.ShowDialog(this);
        }

        private void ApplyScreenFilter()
        {
            if (_filterMode == ScreenFilterMode.None)
            {
                _filterOverlay.Visible = false;
                _btnScreenFilter.ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText;
            }
            else
            {
                Color filterColor = _filterMode == ScreenFilterMode.WarmAmber ? Color.FromArgb(255, 183, 77) : Color.Black;
                _filterOverlay.SetFilter(filterColor, _filterOpacity);
                _filterOverlay.Visible = true;
                _btnScreenFilter.ForeColor = KioTheme.AccentAmber;
            }
        }

        private async void ToggleCleanMode()
        {
            if (_activeTab == null) return;
            _activeTab.CleanModeEnabled = !_activeTab.CleanModeEnabled;
            if (_activeTab.CleanModeEnabled)
            {
                await _activeTab.InjectCleanModeCssAsync();
            }
            else
            {
                await _activeTab.RemoveCleanModeCssAsync();
            }
            UpdateNavBarState();
        }

        private void ToggleBlockImages()
        {
            if (_activeTab == null) return;
            _activeTab.SetBlockImages(!_activeTab.BlockImages);
            UpdateNavBarState();
        }

        private void ShowUserAgentMenu()
        {
            ContextMenuStrip menu = new ContextMenuStrip();
            menu.Items.Add("Desktop PC (Default)", null, delegate { if (_activeTab != null) _activeTab.SetUserAgentMode(UserAgentMode.Desktop); });
            menu.Items.Add("Mobile (Android Pixel)", null, delegate { if (_activeTab != null) _activeTab.SetUserAgentMode(UserAgentMode.Mobile); });
            menu.Items.Add("iPhone (Safari iOS)", null, delegate { if (_activeTab != null) _activeTab.SetUserAgentMode(UserAgentMode.iPhone); });
            menu.Show(_btnUserAgent, new Point(0, _btnUserAgent.Height));
        }

        private async Task ToggleReaderAnnouncerAsync()
        {
            if (_activeTab == null || _activeTab.WebView == null) return;

            string json = await _activeTab.WebView.CoreWebView2.ExecuteScriptAsync(SoulBrowserFeatures.ReaderExtractorScript);
            string text = "";
            Match match = Regex.Match(json ?? "", @"text"":""([^""]+)""");
            if (match.Success)
            {
                text = match.Groups[1].Value.Replace("\\n", "\n").Replace("\\\"", "\"");
            }

            if (string.IsNullOrWhiteSpace(text))
            {
                MessageBox.Show("No readable article text detected on this page.", "Kio Reader", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            Form dlg = new Form
            {
                Text = "Kio Reader Mode & On-Device Announcer",
                Width = 720,
                Height = 520,
                StartPosition = FormStartPosition.CenterParent,
                BackColor = _isDarkTheme ? Color.FromArgb(10, 15, 26) : Color.FromArgb(250, 250, 250),
                ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText
            };

            TextBox txt = new TextBox
            {
                Dock = DockStyle.Top,
                Height = 410,
                Multiline = true,
                ScrollBars = ScrollBars.Vertical,
                ReadOnly = true,
                Font = new Font("Georgia", 11.5f),
                Text = text,
                BackColor = dlg.BackColor,
                ForeColor = dlg.ForeColor,
                BorderStyle = BorderStyle.None
            };

            Panel pnl = new Panel { Dock = DockStyle.Bottom, Height = 55, Padding = new Padding(10) };
            Button btnSpeak = new Button { Text = "🔊 Speak On-Device", Left = 10, Top = 12, AutoSize = true };
            Button btnStop = new Button { Text = "⏹ Stop", Left = 150, Top = 12, AutoSize = true };
            Button btnClose = new Button { Text = "Close", Left = 600, Top = 12, AutoSize = true };

            btnSpeak.Click += delegate { SoulBrowserFeatures.SpeakTextOnDevice(text); };
            btnStop.Click += delegate { SoulBrowserFeatures.StopSpeaking(); };
            btnClose.Click += delegate { SoulBrowserFeatures.StopSpeaking(); dlg.Close(); };
            dlg.FormClosing += delegate { SoulBrowserFeatures.StopSpeaking(); };

            pnl.Controls.Add(btnSpeak);
            pnl.Controls.Add(btnStop);
            pnl.Controls.Add(btnClose);

            dlg.Controls.Add(txt);
            dlg.Controls.Add(pnl);
            dlg.ShowDialog(this);
        }

        private void ToggleTheme()
        {
            _isDarkTheme = !_isDarkTheme;
            ApplyTheme();
        }

        private void BrowserForm_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Control && e.KeyCode == Keys.T)
            {
                AddNewTab(false, "https://duckduckgo.com");
                e.Handled = true;
            }
            else if (e.Control && e.Shift && e.KeyCode == Keys.P)
            {
                AddNewTab(true, "https://duckduckgo.com");
                e.Handled = true;
            }
            else if (e.Control && e.KeyCode == Keys.W)
            {
                if (_activeTab != null) CloseTab(_activeTab);
                e.Handled = true;
            }
            else if (e.Control && e.KeyCode == Keys.L || e.Alt && e.KeyCode == Keys.D)
            {
                _txtOmnibox.Focus();
                _txtOmnibox.SelectAll();
                e.Handled = true;
            }
            else if (e.KeyCode == Keys.F5 || (e.Control && e.KeyCode == Keys.R))
            {
                if (_activeTab != null) _activeTab.Reload();
                e.Handled = true;
            }
            else if (e.Alt && e.KeyCode == Keys.Left)
            {
                if (_activeTab != null) _activeTab.GoBack();
                e.Handled = true;
            }
            else if (e.Alt && e.KeyCode == Keys.Right)
            {
                if (_activeTab != null) _activeTab.GoForward();
                e.Handled = true;
            }
            else if (e.Alt && e.KeyCode == Keys.Home)
            {
                if (_activeTab != null) _activeTab.Navigate("https://duckduckgo.com");
                e.Handled = true;
            }
            else if (e.Control && (e.KeyCode == Keys.Oemplus || e.KeyCode == Keys.Add))
            {
                if (_activeTab != null) { _activeTab.ZoomFactor = Math.Min(3.0, _activeTab.ZoomFactor + 0.1); _activeTab.WebView.ZoomFactor = _activeTab.ZoomFactor; UpdateNavBarState(); }
                e.Handled = true;
            }
            else if (e.Control && (e.KeyCode == Keys.OemMinus || e.KeyCode == Keys.Subtract))
            {
                if (_activeTab != null) { _activeTab.ZoomFactor = Math.Max(0.5, _activeTab.ZoomFactor - 0.1); _activeTab.WebView.ZoomFactor = _activeTab.ZoomFactor; UpdateNavBarState(); }
                e.Handled = true;
            }
            else if (e.Control && e.KeyCode == Keys.D0)
            {
                if (_activeTab != null) { _activeTab.ZoomFactor = 1.0; _activeTab.WebView.ZoomFactor = 1.0; UpdateNavBarState(); }
                e.Handled = true;
            }
            else if (e.KeyCode == Keys.F11)
            {
                ToggleFullscreen();
                e.Handled = true;
            }
        }

        private bool _isFullscreen = false;
        private FormWindowState _prevWindowState;
        private FormBorderStyle _prevBorderStyle;

        private void ToggleFullscreen()
        {
            if (!_isFullscreen)
            {
                _prevWindowState = WindowState;
                _prevBorderStyle = FormBorderStyle;
                FormBorderStyle = FormBorderStyle.None;
                WindowState = FormWindowState.Maximized;
                _titlePanel.Visible = false;
                _statusStrip.Visible = false;
                _isFullscreen = true;
            }
            else
            {
                FormBorderStyle = _prevBorderStyle;
                WindowState = _prevWindowState;
                _titlePanel.Visible = true;
                _statusStrip.Visible = true;
                _isFullscreen = false;
            }
        }

        private static GraphicsPath RoundedRect(Rectangle bounds, int radiusTopLeft, int radiusTopRight, int radiusBottomRight, int radiusBottomLeft)
        {
            GraphicsPath path = new GraphicsPath();
            int diameterTL = radiusTopLeft * 2;
            int diameterTR = radiusTopRight * 2;
            int diameterBR = radiusBottomRight * 2;
            int diameterBL = radiusBottomLeft * 2;

            if (radiusTopLeft > 0) path.AddArc(bounds.X, bounds.Y, diameterTL, diameterTL, 180, 90);
            else path.AddLine(bounds.X, bounds.Y, bounds.X, bounds.Y);

            if (radiusTopRight > 0) path.AddArc(bounds.Right - diameterTR, bounds.Y, diameterTR, diameterTR, 270, 90);
            else path.AddLine(bounds.Right, bounds.Y, bounds.Right, bounds.Y);

            if (radiusBottomRight > 0) path.AddArc(bounds.Right - diameterBR, bounds.Bottom - diameterBR, diameterBR, diameterBR, 0, 90);
            else path.AddLine(bounds.Right, bounds.Bottom, bounds.Right, bounds.Bottom);

            if (radiusBottomLeft > 0) path.AddArc(bounds.X, bounds.Bottom - diameterBL, diameterBL, diameterBL, 90, 90);
            else path.AddLine(bounds.X, bounds.Bottom, bounds.X, bounds.Bottom);

            path.CloseFigure();
            return path;
        }
    }

    /// <summary>
    /// Lightweight transparent overlay that applies color and dimming over web content
    /// with WS_EX_TRANSPARENT so mouse clicks pass right through to the webpage.
    /// </summary>
    public class FilterOverlayControl : Control
    {
        private Color _filterColor = Color.FromArgb(255, 183, 77);
        private float _opacity = 0.35f;

        public FilterOverlayControl()
        {
            SetStyle(ControlStyles.Opaque | ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint, true);
            UpdateStyles();
        }

        protected override CreateParams CreateParams
        {
            get
            {
                CreateParams cp = base.CreateParams;
                cp.ExStyle |= 0x20; // WS_EX_TRANSPARENT: passes all mouse/keyboard events through to underlying WebView
                return cp;
            }
        }

        public void SetFilter(Color color, float opacity)
        {
            _filterColor = color;
            _opacity = opacity;
            Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            int alpha = (int)(_opacity * 255);
            using (SolidBrush brush = new SolidBrush(Color.FromArgb(alpha, _filterColor)))
            {
                e.Graphics.FillRectangle(brush, ClientRectangle);
            }
        }
    }
}
