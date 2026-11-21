using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace Kio.Desktop
{
    public class QuickMenuDialog : Form
    {
        private BrowserTab _activeTab;
        private BrowserForm _parentForm;
        private bool _isDarkTheme;

        public QuickMenuDialog(BrowserForm parent, BrowserTab tab, bool isDarkTheme)
        {
            _parentForm = parent;
            _activeTab = tab;
            _isDarkTheme = isDarkTheme;

            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.Manual;
            ShowInTaskbar = false;
            Width = 340;
            Height = tab != null && tab.IsPrivate ? 520 : 440;

            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer, true);
            UpdateStyles();

            InitializeMenu();
        }

        private void InitializeMenu()
        {
            bool isPrivate = _activeTab != null && _activeTab.IsPrivate;
            BackColor = _isDarkTheme ? (isPrivate ? Color.FromArgb(22, 12, 40) : Color.FromArgb(14, 18, 28)) : Color.FromArgb(248, 250, 252);
            ForeColor = _isDarkTheme ? KioTheme.DarkText : KioTheme.LightText;

            Panel root = new Panel
            {
                Dock = DockStyle.Fill,
                Padding = new Padding(14)
            };

            int top = 14;

            // 1. Private Mode Banner (if Private)
            if (isPrivate)
            {
                Panel privBanner = new Panel
                {
                    Location = new Point(14, top),
                    Size = new Size(312, 36),
                    BackColor = Color.FromArgb(42, 22, 75)
                };
                Label lblPriv = new Label
                {
                    Text = "🛡️ Private Browsing Active (Zero Trails)",
                    Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                    ForeColor = KioTheme.PrivateAccent,
                    Dock = DockStyle.Fill,
                    TextAlign = ContentAlignment.MiddleCenter
                };
                privBanner.Controls.Add(lblPriv);
                root.Controls.Add(privBanner);
                top += 44;
            }

            // 2. Mode Chips Header (Desktop / Mobile / iPhone)
            Label lblModes = new Label
            {
                Text = "Viewing Mode",
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                ForeColor = _isDarkTheme ? KioTheme.DarkTextMuted : KioTheme.LightTextMuted,
                Location = new Point(14, top),
                AutoSize = true
            };
            root.Controls.Add(lblModes);
            top += 20;

            FlowLayoutPanel pnlModes = new FlowLayoutPanel
            {
                Location = new Point(14, top),
                Size = new Size(312, 34),
                FlowDirection = FlowDirection.LeftToRight
            };

            Button btnDesktop = CreateChipButton("🖥️ Desktop", _activeTab != null && _activeTab.UaMode == UserAgentMode.Desktop, delegate
            {
                if (_activeTab != null) _activeTab.SetUserAgentMode(UserAgentMode.Desktop);
                Close();
            });
            Button btnMobile = CreateChipButton("📱 Mobile", _activeTab != null && _activeTab.UaMode == UserAgentMode.Mobile, delegate
            {
                if (_activeTab != null) _activeTab.SetUserAgentMode(UserAgentMode.Mobile);
                Close();
            });
            Button btnIPhone = CreateChipButton("🍎 iPhone", _activeTab != null && _activeTab.UaMode == UserAgentMode.iPhone, delegate
            {
                if (_activeTab != null) _activeTab.SetUserAgentMode(UserAgentMode.iPhone);
                Close();
            });

            pnlModes.Controls.Add(btnDesktop);
            pnlModes.Controls.Add(btnMobile);
            pnlModes.Controls.Add(btnIPhone);
            root.Controls.Add(pnlModes);
            top += 40;

            // 3. Grid Actions
            Label lblTools = new Label
            {
                Text = "Quick Actions & Tools",
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                ForeColor = _isDarkTheme ? KioTheme.DarkTextMuted : KioTheme.LightTextMuted,
                Location = new Point(14, top),
                AutoSize = true
            };
            root.Controls.Add(lblTools);
            top += 22;

            TableLayoutPanel grid = new TableLayoutPanel
            {
                Location = new Point(14, top),
                Size = new Size(312, isPrivate ? 320 : 250),
                ColumnCount = 3,
                RowCount = isPrivate ? 5 : 4
            };
            for (int c = 0; c < 3; c++) grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 33.33f));

            int mediaCount = _activeTab != null ? _activeTab.DetectedMediaList.Count : 0;
            string mediaLabel = mediaCount > 0 ? string.Format("Media ({0})", mediaCount) : "Media";

            grid.Controls.Add(CreateGridTile("🎬", mediaLabel, mediaCount > 0 ? KioTheme.AccentCyan : ForeColor, delegate { _parentForm.ShowMediaDownloader(); Close(); }));
            grid.Controls.Add(CreateGridTile("🖼️", "Images", ForeColor, delegate { _parentForm.ShowImageScraper(); Close(); }));
            grid.Controls.Add(CreateGridTile("📸", "Capture", ForeColor, delegate { _parentForm.CaptureFullPage(); Close(); }));

            grid.Controls.Add(CreateGridTile("👁️", "Filter", _parentForm.IsFilterActive ? KioTheme.AccentAmber : ForeColor, delegate { _parentForm.ShowScreenFilter(); Close(); }));
            grid.Controls.Add(CreateGridTile("🧹", "Clean", _activeTab != null && _activeTab.CleanModeEnabled ? KioTheme.AccentGreen : ForeColor, delegate { _parentForm.ToggleCleanMode(); Close(); }));
            grid.Controls.Add(CreateGridTile("🖼️🚫", "No Images", _activeTab != null && _activeTab.BlockImages ? KioTheme.AccentRed : ForeColor, delegate { _parentForm.ToggleBlockImages(); Close(); }));

            grid.Controls.Add(CreateGridTile("📖", "Narrate", ForeColor, delegate { _parentForm.ToggleReaderAnnouncer(); Close(); }));
            grid.Controls.Add(CreateGridTile("🖥️", "Full Screen", ForeColor, delegate { _parentForm.ToggleFullscreen(); Close(); }));
            grid.Controls.Add(CreateGridTile("🌙/☀️", "Theme", ForeColor, delegate { _parentForm.ToggleTheme(); Close(); }));

            if (isPrivate)
            {
                // Exclusive Private-Only Tools
                grid.Controls.Add(CreateGridTile("🔥", "Burn Session", KioTheme.AccentRed, delegate { _parentForm.BurnPrivateSession(); Close(); }));
                grid.Controls.Add(CreateGridTile("🎭", "New Identity", KioTheme.PrivateAccent, delegate { _parentForm.NewPrivateIdentity(); Close(); }));
                grid.Controls.Add(CreateGridTile("⚡", "Purge RAM", KioTheme.AccentSky, delegate { _parentForm.PurgeRam(); Close(); }));
            }

            root.Controls.Add(grid);
            Controls.Add(root);

            Deactivate += delegate { Close(); };
        }

        private Button CreateChipButton(string text, bool isSelected, EventHandler onClick)
        {
            Button btn = new Button
            {
                Text = text,
                AutoSize = true,
                Height = 28,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 8f, isSelected ? FontStyle.Bold : FontStyle.Regular),
                Cursor = Cursors.Hand,
                BackColor = isSelected ? (_isDarkTheme ? Color.FromArgb(30, 41, 65) : Color.FromArgb(220, 235, 252)) : (_isDarkTheme ? Color.FromArgb(18, 24, 38) : Color.FromArgb(238, 242, 248)),
                ForeColor = isSelected ? KioTheme.AccentSky : ForeColor,
                Margin = new Padding(2)
            };
            btn.FlatAppearance.BorderColor = isSelected ? KioTheme.AccentSky : Color.FromArgb(40, 50, 75);
            btn.Click += onClick;
            return btn;
        }

        private Control CreateGridTile(string icon, string label, Color iconTint, EventHandler onClick)
        {
            Panel card = new Panel
            {
                Dock = DockStyle.Fill,
                Margin = new Padding(3),
                BackColor = _isDarkTheme ? Color.FromArgb(20, 26, 42) : Color.FromArgb(240, 244, 250),
                Cursor = Cursors.Hand
            };

            Label lblIcon = new Label
            {
                Text = icon,
                Font = new Font("Segoe UI", 12f),
                ForeColor = iconTint,
                Dock = DockStyle.Top,
                Height = 28,
                TextAlign = ContentAlignment.MiddleCenter,
                Cursor = Cursors.Hand
            };

            Label lblText = new Label
            {
                Text = label,
                Font = new Font("Segoe UI", 8f, FontStyle.Regular),
                ForeColor = ForeColor,
                Dock = DockStyle.Fill,
                TextAlign = ContentAlignment.MiddleCenter,
                Cursor = Cursors.Hand
            };

            card.Controls.Add(lblText);
            card.Controls.Add(lblIcon);

            EventHandler clickAction = delegate(object sender, EventArgs e) { onClick(sender, e); };
            card.Click += clickAction;
            lblIcon.Click += clickAction;
            lblText.Click += clickAction;

            card.MouseEnter += delegate { card.BackColor = _isDarkTheme ? Color.FromArgb(32, 42, 68) : Color.FromArgb(225, 235, 248); };
            card.MouseLeave += delegate { card.BackColor = _isDarkTheme ? Color.FromArgb(20, 26, 42) : Color.FromArgb(240, 244, 250); };

            return card;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            // Draw clean subtle border
            using (Pen pen = new Pen(_isDarkTheme ? Color.FromArgb(45, 55, 80) : Color.FromArgb(215, 225, 238), 1.5f))
            {
                e.Graphics.DrawRectangle(pen, 0, 0, Width - 1, Height - 1);
            }
        }
    }
}
