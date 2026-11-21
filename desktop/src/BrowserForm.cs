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

        private Panel _topBarPanel;
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

        // Right Action Cluster
        private Button _btnMedia;
        private Button _btnCleanIndicator;
        private Button _btnTheme;
        private Button _btnThreeDotMenu;

        // Theme and Screen Filter State
        private bool _isDarkTheme = true;
        private ScreenFilterMode _filterMode = ScreenFilterMode.None;
        private float _filterOpacity = 0.35f;
        private FilterOverlayControl _filterOverlay;

        public bool IsFilterActive { get { return _filterMode != ScreenFilterMode.None; } }

        public BrowserForm()
        {
            _tabs = new List<BrowserTab>();

            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer, true);
            UpdateStyles();

            Text = "Kio Browser";
            Width = 1200;
            Height = 800;
            StartPosition = FormStartPosition.CenterScreen;
            MinimumSize = new Size(820, 520);

            InitializeComponents();
            ApplyTheme();

            AddNewTab(false, "about:blank");
        }

        private void InitializeComponents()
        {
            // 1. Top Bar Panel (Kio Logo + Tab Bar)
            _topBarPanel = new Panel
            {
                Dock = DockStyle.Top,
                Height = 42,
                Padding = new Padding(8, 4, 8, 0)
            };
            _topBarPanel.Paint += TopBarPanel_Paint;
            _topBarPanel.MouseClick += TopBarPanel_MouseClick;

            // 2. Navigation Bar Panel
            _navBarPanel = new Panel
            {
                Dock = DockStyle.Top,
                Height = 46,
                Padding = new Padding(10, 6, 10, 6)
            };

            // Rounded Nav Icon Buttons
            _btnBack = CreatePillButton("◄", "Back (Alt+Left)", delegate { if (_activeTab != null) _activeTab.GoBack(_isDarkTheme); });
            _btnForward = CreatePillButton("►", "Forward (Alt+Right)", delegate { if (_activeTab != null) _activeTab.GoForward(); });
            _btnReload = CreatePillButton("⟳", "Reload (Ctrl+R / F5)", delegate { if (_activeTab != null) _activeTab.Reload(); });
            _btnHome = CreatePillButton("🏠", "Home", delegate { if (_activeTab != null) _activeTab.NavigateHome(_isDarkTheme); });

            // Security Lock Indicator
            _lblSecurity = new Label
            {
                Text = "🔒",
                AutoSize = false,
                Width = 26,
                Height = 26,
                TextAlign = ContentAlignment.MiddleCenter,
                Font = new Font("Segoe UI", 9.5f),
                Cursor = Cursors.Hand
            };

            // Omnibox Address Field
            _txtOmnibox = new TextBox
            {
                Font = new Font("Segoe UI", 10.5f),
                BorderStyle = BorderStyle.None,
                Height = 26
            };
            _txtOmnibox.KeyDown += TxtOmnibox_KeyDown;

            _btnGo = CreatePillButton("➔", "Navigate", delegate { NavigateActiveTab(); });

            // Right Action Cluster
            _btnMedia = CreatePillButton("🎬 Media", "Media Downloader (Live Sniffer)", delegate { ShowMediaDownloader(); });
            _btnCleanIndicator = CreatePillButton("🧹 Clean", "Clean Mode Active", delegate { ToggleCleanMode(); });
            _btnTheme = CreatePillButton("🌙", "Toggle Theme", delegate { ToggleTheme(); });
            _btnThreeDotMenu = CreatePillButton("⋮", "Quick Actions & Power Tools", delegate { OpenThreeDotMenu(); });
            _btnThreeDotMenu.Font = new Font("Segoe UI", 13f, FontStyle.Bold);

            // Add Controls to Nav Bar
            _navBarPanel.Controls.Add(_btnBack);
            _navBarPanel.Controls.Add(_btnForward);
            _navBarPanel.Controls.Add(_btnReload);
            _navBarPanel.Controls.Add(_btnHome);
            _navBarPanel.Controls.Add(_lblSecurity);
            _navBarPanel.Controls.Add(_txtOmnibox);
            _navBarPanel.Controls.Add(_btnGo);
            _navBarPanel.Controls.Add(_btnMedia);
            _navBarPanel.Controls.Add(_btnCleanIndicator);
            _navBarPanel.Controls.Add(_btnTheme);
            _navBarPanel.Controls.Add(_btnThreeDotMenu);
            _navBarPanel.Resize += delegate { LayoutNavBar(); };

            // 3. Web Container
            _webContainer = new Panel
            {
                Dock = DockStyle.Fill
            };

            // Screen Filter Overlay
            _filterOverlay = new FilterOverlayControl
            {
                Dock = DockStyle.Fill,
                Visible = false
            };
            _webContainer.Controls.Add(_filterOverlay);
            _filterOverlay.BringToFront();

            // 4. Status Strip
            _statusStrip = new StatusStrip();
            _statusLabel = new ToolStripStatusLabel { Text = "Ready", Spring = true, TextAlign = ContentAlignment.MiddleLeft };
            _zoomLabel = new ToolStripStatusLabel { Text = "100%", AutoToolTip = true, ToolTipText = "Zoom Level" };
            _statusStrip.Items.Add(_statusLabel);
            _statusStrip.Items.Add(_zoomLabel);

            // Add all panels to Form
            Controls.Add(_webContainer);
            Controls.Add(_statusStrip);
            Controls.Add(_navBarPanel);
            Controls.Add(_topBarPanel);

            KeyPreview = true;
            KeyDown += BrowserForm_KeyDown;
        }

        private Button CreatePillButton(string text, string tooltip, EventHandler onClick)
        {
            Button btn = new Button
            {
                Text = text,
                AutoSize = true,
                Height = 30,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9.5f),
                Cursor = Cursors.Hand,
                Margin = new Padding(2, 0, 2, 0)
            };
            btn.FlatAppearance.BorderSize = 0;
            btn.Click += onClick;
            ToolTip tip = new ToolTip();
            tip.SetToolTip(btn, tooltip);
            return btn;
        }

        private void LayoutNavBar()
        {
            int left = 12;
            Button[] navLeft = { _btnBack, _btnForward, _btnReload, _btnHome };
            foreach (var b in navLeft)
            {
                b.Location = new Point(left, 8);
                left += b.Width + 4;
            }

            _lblSecurity.Location = new Point(left, 10);
            left += _lblSecurity.Width + 4;

            int right = _navBarPanel.Width - 12;
            Button[] navRight = { _btnThreeDotMenu, _btnTheme, _btnCleanIndicator, _btnMedia, _btnGo };
            foreach (var b in navRight)
            {
                right -= b.Width;
                b.Location = new Point(right, 8);
                right -= 6;
            }

            int omniWidth = Math.Max(140, right - left - 6);
            _txtOmnibox.Location = new Point(left, 10);
            _txtOmnibox.Width = omniWidth;
        }

        public void ApplyTheme()
        {
            Color bg = _isDarkTheme ? KioTheme.DarkBackground : KioTheme.LightBackground;
            Color surface = _isDarkTheme ? KioTheme.DarkSurface : KioTheme.LightSurface;
            Color text = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText;

            BackColor = bg;
            ForeColor = text;

            _topBarPanel.BackColor = bg;
            _navBarPanel.BackColor = surface;

            _txtOmnibox.BackColor = _isDarkTheme ? Color.FromArgb(20, 27, 45) : Color.White;
            _txtOmnibox.ForeColor = text;

            Button[] buttons = { _btnBack, _btnForward, _btnReload, _btnHome, _btnGo, _btnMedia, _btnCleanIndicator, _btnTheme, _btnThreeDotMenu };
            foreach (var btn in buttons)
            {
                btn.BackColor = _isDarkTheme ? Color.FromArgb(24, 32, 53) : Color.FromArgb(241, 245, 249);
                btn.ForeColor = text;
            }

            _statusStrip.BackColor = surface;
            _statusLabel.ForeColor = text;
            _zoomLabel.ForeColor = text;

            _btnTheme.Text = _isDarkTheme ? "🌙" : "☀️";

            _topBarPanel.Invalidate();
            LayoutNavBar();

            if (_activeTab != null && _activeTab.IsHomeView)
            {
                _activeTab.NavigateHome(_isDarkTheme);
            }
        }

        private void TopBarPanel_Paint(object sender, PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;

            // 1. Draw Authentic Kio Logo on Top-Left
            bool isPriv = _activeTab != null && _activeTab.IsPrivate;
            Rectangle logoRect = new Rectangle(10, 8, 26, 26);
            KioTheme.DrawKioEmblem(g, logoRect, isPriv);

            int tabX = 46;
            int tabY = 6;
            int tabH = 32;
            int tabW = 190;

            // 2. Draw Tab Strips
            for (int i = 0; i < _tabs.Count; i++)
            {
                BrowserTab tab = _tabs[i];
                bool isActive = tab == _activeTab;

                Rectangle tabRect = new Rectangle(tabX, tabY, tabW, tabH);

                Color tabBg = isActive
                    ? (tab.IsPrivate ? KioTheme.PrivateSurface : (_isDarkTheme ? KioTheme.DarkSurface : KioTheme.LightSurface))
                    : (_isDarkTheme ? Color.FromArgb(14, 20, 36) : Color.FromArgb(235, 240, 248));

                using (GraphicsPath path = RoundedRect(tabRect, 10, 10, 0, 0))
                using (SolidBrush brush = new SolidBrush(tabBg))
                {
                    g.FillPath(brush, path);
                }

                if (isActive)
                {
                    // Luminous accent active line
                    using (Pen pen = new Pen(tab.IsPrivate ? KioTheme.PrivateAccent : KioTheme.AccentCyan, 2.5f))
                    {
                        g.DrawLine(pen, tabX + 6, tabY + 1, tabX + tabW - 6, tabY + 1);
                    }
                }

                // Favicon / Mode Icon
                string iconText = tab.IsPrivate ? "🛡️" : (tab.IsLoading ? "⏳" : "🌐");
                using (Font iconFont = new Font("Segoe UI", 9f))
                using (SolidBrush iconBrush = new SolidBrush(tab.IsPrivate ? KioTheme.PrivateAccent : KioTheme.AccentSky))
                {
                    g.DrawString(iconText, iconFont, iconBrush, tabX + 8, tabY + 7);
                }

                // Title
                string dispTitle = tab.Title ?? "New Tab";
                if (dispTitle.Length > 17) dispTitle = dispTitle.Substring(0, 17) + "…";
                using (Font f = new Font("Segoe UI", 9f, isActive ? FontStyle.Bold : FontStyle.Regular))
                using (SolidBrush textBrush = new SolidBrush(_isDarkTheme ? KioTheme.DarkText : KioTheme.LightText))
                {
                    g.DrawString(dispTitle, f, textBrush, tabX + 30, tabY + 8);
                }

                // Close Button (×)
                using (Font closeFont = new Font("Segoe UI", 9.5f))
                using (SolidBrush closeBrush = new SolidBrush(KioTheme.DarkTextMuted))
                {
                    g.DrawString("×", closeFont, closeBrush, tabX + tabW - 20, tabY + 7);
                }

                tabX += tabW + 5;
            }

            // New Standard Tab Button (+)
            Rectangle plusRect = new Rectangle(tabX, tabY + 3, 26, 26);
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
            Rectangle privRect = new Rectangle(tabX, tabY + 3, 36, 26);
            using (SolidBrush privBg = new SolidBrush(KioTheme.PrivateSurface))
            {
                g.FillEllipse(privBg, privRect);
            }
            using (Font privFont = new Font("Segoe UI", 9f, FontStyle.Bold))
            using (SolidBrush privText = new SolidBrush(KioTheme.PrivateAccent))
            {
                g.DrawString("🛡️+", privFont, privText, tabX + 5, tabY + 6);
            }
        }

        private void TopBarPanel_MouseClick(object sender, MouseEventArgs e)
        {
            // Click Logo -> Go Home
            Rectangle logoRect = new Rectangle(10, 8, 26, 26);
            if (logoRect.Contains(e.Location))
            {
                if (_activeTab != null) _activeTab.NavigateHome(_isDarkTheme);
                return;
            }

            int tabX = 46;
            int tabW = 190;
            int tabH = 32;

            for (int i = 0; i < _tabs.Count; i++)
            {
                Rectangle tabRect = new Rectangle(tabX, 6, tabW, tabH);
                Rectangle closeRect = new Rectangle(tabX + tabW - 24, 6, 22, tabH);

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

                tabX += tabW + 5;
            }

            // New standard tab (+)
            Rectangle plusRect = new Rectangle(tabX, 9, 26, 26);
            if (plusRect.Contains(e.Location))
            {
                AddNewTab(false, "about:blank");
                return;
            }
            tabX += 32;

            // New private tab (🛡️+)
            Rectangle privRect = new Rectangle(tabX, 9, 36, 26);
            if (privRect.Contains(e.Location))
            {
                AddNewTab(true, "about:blank");
                return;
            }
        }

        public async void AddNewTab(bool isPrivate, string startUrl)
        {
            BrowserTab tab = new BrowserTab(isPrivate);
            _tabs.Add(tab);

            tab.StateChanged += delegate { UpdateNavBar(); _topBarPanel.Invalidate(); };
            tab.MediaListUpdated += delegate { UpdateMediaButton(); };

            await tab.InitializeAsync();
            if (string.IsNullOrEmpty(startUrl) || startUrl == "about:blank")
            {
                tab.NavigateHome(_isDarkTheme);
            }
            else
            {
                tab.Navigate(startUrl, _isDarkTheme);
            }

            SetActiveTab(tab);
        }

        public void CloseTab(BrowserTab tab)
        {
            if (_tabs.Count <= 1)
            {
                tab.NavigateHome(_isDarkTheme);
                return;
            }

            int index = _tabs.IndexOf(tab);
            _tabs.Remove(tab);

            if (_webContainer.Controls.Contains(tab.WebView))
            {
                _webContainer.Controls.Remove(tab.WebView);
            }
            tab.Dispose();

            int nextIndex = Math.Max(0, index - 1);
            SetActiveTab(_tabs[nextIndex]);
        }

        public void SetActiveTab(BrowserTab tab)
        {
            if (tab == null) return;
            _activeTab = tab;

            _webContainer.Controls.Clear();
            _webContainer.Controls.Add(tab.WebView);
            _webContainer.Controls.Add(_filterOverlay);
            _filterOverlay.BringToFront();

            UpdateNavBar();
            UpdateMediaButton();
            _topBarPanel.Invalidate();
        }

        private void UpdateNavBar()
        {
            if (_activeTab == null) return;

            _btnBack.Enabled = _activeTab.CanGoBack;
            _btnForward.Enabled = _activeTab.CanGoForward;
            _btnReload.Text = _activeTab.IsLoading ? "✕" : "⟳";

            if (!_txtOmnibox.Focused)
            {
                _txtOmnibox.Text = _activeTab.IsHomeView ? "" : _activeTab.Url;
            }

            // Security Lock Indicator
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

            _btnCleanIndicator.Visible = _activeTab.CleanModeEnabled;
        }

        private void UpdateMediaButton()
        {
            if (_activeTab == null) return;
            int count = _activeTab.DetectedMediaList.Count;
            if (count > 0)
            {
                _btnMedia.Visible = true;
                _btnMedia.Text = string.Format("🎬 Media ({0})", count);
                _btnMedia.ForeColor = KioTheme.AccentCyan;
            }
            else
            {
                _btnMedia.Visible = false;
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
                _activeTab.Navigate(_txtOmnibox.Text, _isDarkTheme);
            }
        }

        // --- Android 3-Dot Quick Menu Trigger ---

        private void OpenThreeDotMenu()
        {
            QuickMenuDialog menu = new QuickMenuDialog(this, _activeTab, _isDarkTheme);
            Point screenPos = _btnThreeDotMenu.PointToScreen(new Point(0, _btnThreeDotMenu.Height + 4));
            // Ensure fits on screen
            int menuX = Math.Min(screenPos.X, Screen.FromControl(this).WorkingArea.Right - menu.Width - 10);
            menu.Location = new Point(menuX, screenPos.Y);
            menu.Show(this);
        }

        // --- Soul Browser Features & Dialogs ---

        public void ShowMediaDownloader()
        {
            if (_activeTab == null) return;

            Form dlg = new Form
            {
                Text = "Kio Media Sniffer & Downloader",
                Width = 660,
                Height = 420,
                StartPosition = FormStartPosition.CenterParent,
                BackColor = _isDarkTheme ? Color.FromArgb(14, 18, 28) : Color.White,
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
                ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText,
                BorderStyle = BorderStyle.None
            };
            lv.Columns.Add("Format", 90);
            lv.Columns.Add("Title", 220);
            lv.Columns.Add("Stream URL", 320);

            foreach (var m in _activeTab.DetectedMediaList)
            {
                var item = new ListViewItem(new string[] { m.FormatLabel, m.Title, m.Url });
                item.Tag = m;
                lv.Items.Add(item);
            }

            Panel btnPanel = new Panel { Dock = DockStyle.Bottom, Height = 55, Padding = new Padding(10) };
            Button btnDownload = CreatePillButton("⬇ Download", "Download stream", delegate
            {
                if (lv.SelectedItems.Count > 0)
                {
                    var m = (DetectedMediaItem)lv.SelectedItems[0].Tag;
                    SoulBrowserFeatures.DownloadMediaDirect(m.Url, m.Title);
                    MessageBox.Show("Download enqueued to Downloads\\Kio\\", "Kio Download", MessageBoxButtons.OK, MessageBoxIcon.Information);
                }
            });
            btnDownload.Location = new Point(10, 10);

            Button btnPlay = CreatePillButton("▶ Play External", "Open in VLC / system player", delegate
            {
                if (lv.SelectedItems.Count > 0)
                {
                    var m = (DetectedMediaItem)lv.SelectedItems[0].Tag;
                    SoulBrowserFeatures.PlayMediaExternal(m.Url);
                }
            });
            btnPlay.Location = new Point(130, 10);

            Button btnCopy = CreatePillButton("📋 Copy Link", "Copy direct URL", delegate
            {
                if (lv.SelectedItems.Count > 0)
                {
                    var m = (DetectedMediaItem)lv.SelectedItems[0].Tag;
                    Clipboard.SetText(m.Url);
                    MessageBox.Show("Stream URL copied to clipboard!", "Kio", MessageBoxButtons.OK, MessageBoxIcon.Information);
                }
            });
            btnCopy.Location = new Point(270, 10);

            Button btnClose = CreatePillButton("Close", "Close dialog", delegate { dlg.Close(); });
            btnClose.Location = new Point(560, 10);

            btnPanel.Controls.Add(btnDownload);
            btnPanel.Controls.Add(btnPlay);
            btnPanel.Controls.Add(btnCopy);
            btnPanel.Controls.Add(btnClose);

            dlg.Controls.Add(lv);
            dlg.Controls.Add(btnPanel);
            dlg.ShowDialog(this);
        }

        public async void ShowImageScraper()
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
                BackColor = _isDarkTheme ? Color.FromArgb(14, 18, 28) : Color.White,
                ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText
            };

            CheckedListBox clb = new CheckedListBox
            {
                Dock = DockStyle.Top,
                Height = 330,
                CheckOnClick = true,
                BackColor = _isDarkTheme ? Color.FromArgb(20, 27, 45) : Color.FromArgb(248, 250, 252),
                ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText,
                BorderStyle = BorderStyle.None
            };

            for (int i = 0; i < images.Count; i++) clb.Items.Add(images[i].Url, true);

            Panel pnl = new Panel { Dock = DockStyle.Bottom, Height = 55, Padding = new Padding(10) };
            Button btnSelectAll = CreatePillButton("Toggle All", "", delegate
            {
                bool anyUnchecked = false;
                for (int i = 0; i < clb.Items.Count; i++) if (!clb.GetItemChecked(i)) { anyUnchecked = true; break; }
                for (int i = 0; i < clb.Items.Count; i++) clb.SetItemChecked(i, anyUnchecked);
            });
            btnSelectAll.Location = new Point(10, 10);

            Button btnSave = CreatePillButton("💾 Save Selected to Pictures", "", delegate
            {
                List<ExtractedImageItem> selected = new List<ExtractedImageItem>();
                for (int i = 0; i < clb.Items.Count; i++)
                {
                    if (clb.GetItemChecked(i)) selected.Add(images[i]);
                }
                SoulBrowserFeatures.BatchDownloadImages(selected);
                MessageBox.Show(string.Format("Saving {0} images to Pictures\\Kio\\", selected.Count), "Kio Image Box", MessageBoxButtons.OK, MessageBoxIcon.Information);
                dlg.Close();
            });
            btnSave.Location = new Point(120, 10);

            Button btnClose = CreatePillButton("Close", "", delegate { dlg.Close(); });
            btnClose.Location = new Point(590, 10);

            pnl.Controls.Add(btnSelectAll);
            pnl.Controls.Add(btnSave);
            pnl.Controls.Add(btnClose);

            dlg.Controls.Add(clb);
            dlg.Controls.Add(pnl);
            dlg.ShowDialog(this);
        }

        public async void CaptureFullPage()
        {
            if (_activeTab == null) return;
            string savedPath = await SoulBrowserFeatures.CaptureFullPageAsync(_activeTab.WebView);
            if (!string.IsNullOrEmpty(savedPath))
            {
                DialogResult res = MessageBox.Show(
                    "Full page scrolling screenshot captured!\n\nSaved to: " + savedPath + "\n\nWould you like to open the image?",
                    "Kio Scroll Capture",
                    MessageBoxButtons.YesNo,
                    MessageBoxIcon.Information
                );
                if (res == DialogResult.Yes)
                {
                    try { System.Diagnostics.Process.Start(savedPath); } catch { }
                }
            }
        }

        public void ShowScreenFilter()
        {
            Form dlg = new Form
            {
                Text = "Screen Filter (Eye Care & Dimmer)",
                Width = 380,
                Height = 250,
                StartPosition = FormStartPosition.CenterParent,
                FormBorderStyle = FormBorderStyle.FixedDialog,
                MaximizeBox = false,
                MinimizeBox = false,
                BackColor = _isDarkTheme ? Color.FromArgb(14, 18, 28) : Color.White,
                ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText
            };

            RadioButton rbNone = new RadioButton { Text = "Off (Normal)", Left = 20, Top = 20, Checked = _filterMode == ScreenFilterMode.None };
            RadioButton rbAmber = new RadioButton { Text = "Warm Amber (Blue Light Eye Care)", Left = 20, Top = 50, Checked = _filterMode == ScreenFilterMode.WarmAmber };
            RadioButton rbOled = new RadioButton { Text = "Night Dimmer (OLED Dark Mode)", Left = 20, Top = 80, Checked = _filterMode == ScreenFilterMode.NightDimmer };

            Label lblOpacity = new Label { Text = string.Format("Intensity: {0}%", (int)(_filterOpacity * 100)), Left = 20, Top = 120, AutoSize = true };
            TrackBar tb = new TrackBar { Left = 20, Top = 145, Width = 320, Minimum = 10, Maximum = 80, Value = (int)(_filterOpacity * 100) };

            tb.Scroll += delegate
            {
                _filterOpacity = tb.Value / 100f;
                lblOpacity.Text = string.Format("Intensity: {0}%", tb.Value);
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
            }
            else
            {
                Color filterColor = _filterMode == ScreenFilterMode.WarmAmber ? Color.FromArgb(255, 183, 77) : Color.Black;
                _filterOverlay.SetFilter(filterColor, _filterOpacity);
                _filterOverlay.Visible = true;
            }
        }

        public async void ToggleCleanMode()
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
            UpdateNavBar();
        }

        public void ToggleBlockImages()
        {
            if (_activeTab == null) return;
            _activeTab.SetBlockImages(!_activeTab.BlockImages);
            UpdateNavBar();
        }

        public async void ToggleReaderAnnouncer()
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
                MessageBox.Show("No readable article text found on this page.", "Kio Narrate", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            Form dlg = new Form
            {
                Text = "Kio Narrator & Reader Mode",
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
            Button btnSpeak = CreatePillButton("🔊 Speak (On-Device)", "", delegate { SoulBrowserFeatures.SpeakTextOnDevice(text); });
            btnSpeak.Location = new Point(10, 10);

            Button btnStop = CreatePillButton("⏹ Stop", "", delegate { SoulBrowserFeatures.StopSpeaking(); });
            btnStop.Location = new Point(180, 10);

            Button btnClose = CreatePillButton("Close", "", delegate { SoulBrowserFeatures.StopSpeaking(); dlg.Close(); });
            btnClose.Location = new Point(610, 10);

            pnl.Controls.Add(btnSpeak);
            pnl.Controls.Add(btnStop);
            pnl.Controls.Add(btnClose);

            dlg.Controls.Add(txt);
            dlg.Controls.Add(pnl);
            dlg.FormClosing += delegate { SoulBrowserFeatures.StopSpeaking(); };
            dlg.ShowDialog(this);
        }

        public void ToggleTheme()
        {
            _isDarkTheme = !_isDarkTheme;
            ApplyTheme();
        }

        // --- Exclusive Private Tools ---

        public void BurnPrivateSession()
        {
            List<BrowserTab> privTabs = new List<BrowserTab>();
            foreach (var t in _tabs) if (t.IsPrivate) privTabs.Add(t);
            foreach (var t in privTabs) CloseTab(t);
            PurgeRam();
            MessageBox.Show("Private session incinerated! All temporary cache and memory purged.", "Kio Private Mode", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        public void NewPrivateIdentity()
        {
            AddNewTab(true, "about:blank");
            MessageBox.Show("New isolated Private Identity generated.", "Kio New Identity", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        public void PurgeRam()
        {
            GC.Collect();
            GC.WaitForPendingFinalizers();
            GC.Collect();
        }

        public void ToggleFullscreen()
        {
            if (!_isFullscreen)
            {
                _prevWindowState = WindowState;
                _prevBorderStyle = FormBorderStyle;
                FormBorderStyle = FormBorderStyle.None;
                WindowState = FormWindowState.Maximized;
                _topBarPanel.Visible = false;
                _statusStrip.Visible = false;
                _isFullscreen = true;
            }
            else
            {
                FormBorderStyle = _prevBorderStyle;
                WindowState = _prevWindowState;
                _topBarPanel.Visible = true;
                _statusStrip.Visible = true;
                _isFullscreen = false;
            }
        }

        private bool _isFullscreen = false;
        private FormWindowState _prevWindowState;
        private FormBorderStyle _prevBorderStyle;

        private void BrowserForm_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Control && e.KeyCode == Keys.T)
            {
                AddNewTab(false, "about:blank");
                e.Handled = true;
            }
            else if (e.Control && e.Shift && e.KeyCode == Keys.P)
            {
                AddNewTab(true, "about:blank");
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
                if (_activeTab != null) _activeTab.GoBack(_isDarkTheme);
                e.Handled = true;
            }
            else if (e.Alt && e.KeyCode == Keys.Right)
            {
                if (_activeTab != null) _activeTab.GoForward();
                e.Handled = true;
            }
            else if (e.Alt && e.KeyCode == Keys.Home)
            {
                if (_activeTab != null) _activeTab.NavigateHome(_isDarkTheme);
                e.Handled = true;
            }
            else if (e.Control && (e.KeyCode == Keys.Oemplus || e.KeyCode == Keys.Add))
            {
                if (_activeTab != null) { _activeTab.ZoomFactor = Math.Min(3.0, _activeTab.ZoomFactor + 0.1); _activeTab.WebView.ZoomFactor = _activeTab.ZoomFactor; UpdateNavBar(); }
                e.Handled = true;
            }
            else if (e.Control && (e.KeyCode == Keys.OemMinus || e.KeyCode == Keys.Subtract))
            {
                if (_activeTab != null) { _activeTab.ZoomFactor = Math.Max(0.5, _activeTab.ZoomFactor - 0.1); _activeTab.WebView.ZoomFactor = _activeTab.ZoomFactor; UpdateNavBar(); }
                e.Handled = true;
            }
            else if (e.Control && e.KeyCode == Keys.D0)
            {
                if (_activeTab != null) { _activeTab.ZoomFactor = 1.0; _activeTab.WebView.ZoomFactor = 1.0; UpdateNavBar(); }
                e.Handled = true;
            }
            else if (e.KeyCode == Keys.F11)
            {
                ToggleFullscreen();
                e.Handled = true;
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
    /// Transparent overlay control that applies color and dimming over web content
    /// with WS_EX_TRANSPARENT so mouse clicks pass right through to the underlying WebView2.
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
                cp.ExStyle |= 0x20; // WS_EX_TRANSPARENT
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
