using System;
using System.Collections.Generic;

namespace Kio.Desktop
{
    public enum UserAgentMode
    {
        Desktop,
        Mobile,
        iPhone
    }

    public enum ScreenFilterMode
    {
        None,
        WarmAmber,
        NightDimmer
    }

    public enum SecurityState
    {
        Home,
        SecureHttps,
        InsecureHttp,
        PrivateMode,
        Error
    }

    public class DetectedMediaItem
    {
        public string Url { get; set; }
        public string Title { get; set; }
        public string MimeType { get; set; }
        public string FormatLabel { get; set; }
        public DateTime DetectedAt { get; set; }

        public DetectedMediaItem()
        {
            DetectedAt = DateTime.UtcNow;
            FormatLabel = "MEDIA";
        }
    }

    public class ExtractedImageItem
    {
        public string Url { get; set; }
        public string Alt { get; set; }
        public int Width { get; set; }
        public int Height { get; set; }
        public bool IsSelected { get; set; }

        public ExtractedImageItem()
        {
            IsSelected = true;
        }
    }

    public class BrowserBookmark
    {
        public string Title { get; set; }
        public string Url { get; set; }
        public DateTime AddedAt { get; set; }

        public BrowserBookmark()
        {
            AddedAt = DateTime.UtcNow;
        }
    }

    public class HistoryEntry
    {
        public string Title { get; set; }
        public string Url { get; set; }
        public DateTime VisitedAt { get; set; }

        public HistoryEntry()
        {
            VisitedAt = DateTime.UtcNow;
        }
    }
}
