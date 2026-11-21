using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace Kio.Desktop
{
    public static class KioTheme
    {
        // Kio Signature Palette
        public static readonly Color DarkBackground = Color.FromArgb(7, 11, 20);       // #070B14
        public static readonly Color DarkSurface = Color.FromArgb(15, 23, 42);          // #0F172A
        public static readonly Color DarkSurfaceHover = Color.FromArgb(30, 41, 59);     // #1E293B
        public static readonly Color DarkBorder = Color.FromArgb(51, 65, 85);           // #334155
        public static readonly Color DarkText = Color.FromArgb(241, 245, 249);          // #F1F5F9
        public static readonly Color DarkTextMuted = Color.FromArgb(148, 163, 184);     // #94A3B8

        // Kio Light Palette
        public static readonly Color LightBackground = Color.FromArgb(248, 250, 252);   // #F8FAFC
        public static readonly Color LightSurface = Color.FromArgb(255, 255, 255);      // #FFFFFF
        public static readonly Color LightSurfaceHover = Color.FromArgb(241, 245, 249); // #F1F5F9
        public static readonly Color LightBorder = Color.FromArgb(226, 232, 240);       // #E2E8F0
        public static readonly Color LightText = Color.FromArgb(15, 23, 42);            // #0F172A
        public static readonly Color LightTextMuted = Color.FromArgb(100, 116, 139);    // #64748B

        // Kio Accents
        public static readonly Color AccentCyan = Color.FromArgb(0, 242, 254);         // #00F2FE
        public static readonly Color AccentSky = Color.FromArgb(56, 189, 248);          // #38BDF8
        public static readonly Color AccentIndigo = Color.FromArgb(99, 102, 241);       // #6366F1
        public static readonly Color AccentGreen = Color.FromArgb(16, 185, 129);        // #10B981
        public static readonly Color AccentAmber = Color.FromArgb(255, 183, 77);        // #FFB74D
        public static readonly Color AccentRed = Color.FromArgb(239, 68, 68);           // #EF4444

        // Private / Stealth Mode Palette
        public static readonly Color PrivateBackground = Color.FromArgb(19, 9, 36);     // #130924
        public static readonly Color PrivateSurface = Color.FromArgb(35, 18, 66);       // #231242
        public static readonly Color PrivateAccent = Color.FromArgb(192, 132, 252);     // #C084FC
        public static readonly Color PrivateBorder = Color.FromArgb(124, 58, 237);      // #7C3AED

        /// <summary>
        /// Draws the authentic Kio Concentric Orbital Logo onto a Graphics surface at the specified rectangle.
        /// </summary>
        public static void DrawKioEmblem(Graphics g, Rectangle bounds, bool isPrivate)
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;

            float cx = bounds.X + bounds.Width / 2f;
            float cy = bounds.Y + bounds.Height / 2f;
            float maxRadius = Math.Min(bounds.Width, bounds.Height) / 2f - 2f;
            if (maxRadius <= 4f) return;

            // Outer Ring
            Color outerColor = isPrivate ? PrivateAccent : AccentCyan;
            Color innerColor = isPrivate ? Color.FromArgb(232, 121, 249) : AccentSky;
            Color centerColor = isPrivate ? Color.FromArgb(255, 255, 255) : Color.White;

            float rOuter = maxRadius * 0.90f;
            float rInner = maxRadius * 0.55f;
            float rCenter = maxRadius * 0.22f;

            // Outer Orbit Arc / Circle
            using (Pen penOuter = new Pen(Color.FromArgb(180, outerColor), Math.Max(1.5f, maxRadius * 0.10f)))
            {
                g.DrawEllipse(penOuter, cx - rOuter, cy - rOuter, rOuter * 2f, rOuter * 2f);
            }

            // Inner Orbit Arc / Circle
            using (Pen penInner = new Pen(Color.FromArgb(220, innerColor), Math.Max(1.2f, maxRadius * 0.08f)))
            {
                g.DrawEllipse(penInner, cx - rInner, cy - rInner, rInner * 2f, rInner * 2f);
            }

            // Luminous Center Core
            using (SolidBrush brushCenter = new SolidBrush(centerColor))
            {
                g.FillEllipse(brushCenter, cx - rCenter, cy - rCenter, rCenter * 2f, rCenter * 2f);
            }

            // Subtle glowing orbital satellite dots
            float dotR = Math.Max(1.2f, maxRadius * 0.08f);
            float dotAngle1 = 45f * (float)Math.PI / 180f;
            float dotAngle2 = 210f * (float)Math.PI / 180f;

            float d1x = cx + (float)Math.Cos(dotAngle1) * rOuter;
            float d1y = cy + (float)Math.Sin(dotAngle1) * rOuter;
            using (SolidBrush dotBrush1 = new SolidBrush(Color.White))
            {
                g.FillEllipse(dotBrush1, d1x - dotR, d1y - dotR, dotR * 2f, dotR * 2f);
            }

            float d2x = cx + (float)Math.Cos(dotAngle2) * rInner;
            float d2y = cy + (float)Math.Sin(dotAngle2) * rInner;
            using (SolidBrush dotBrush2 = new SolidBrush(outerColor))
            {
                g.FillEllipse(dotBrush2, d2x - dotR, d2y - dotR, dotR * 2f, dotR * 2f);
            }
        }

        /// <summary>
        /// Generates a high quality System.Drawing.Icon of the Kio Logo.
        /// </summary>
        public static Icon CreateKioAppIcon(int size = 32, bool isPrivate = false)
        {
            using (Bitmap bmp = new Bitmap(size, size))
            {
                using (Graphics g = Graphics.FromImage(bmp))
                {
                    g.Clear(Color.Transparent);
                    DrawKioEmblem(g, new Rectangle(0, 0, size, size), isPrivate);
                }
                IntPtr hIcon = bmp.GetHicon();
                Icon icon = Icon.FromHandle(hIcon);
                return icon;
            }
        }
    }
}
