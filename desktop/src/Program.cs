using System;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace Kio.Desktop
{
    static class Program
    {
        [DllImport("user32.dll")]
        private static extern bool SetProcessDPIAware();

        [STAThread]
        static void Main()
        {
            try
            {
                SetProcessDPIAware();
            }
            catch { }

            // Configure Assembly and Native DLL resolution
            SetupDllResolution();

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            try
            {
                using (BrowserForm form = new BrowserForm())
                {
                    Application.Run(form);
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "Error starting Kio Browser:\n\n" + ex.Message + "\n\nPlease ensure the Microsoft Edge WebView2 Runtime is installed.",
                    "Kio Browser Initialization Error",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
            }
        }

        private static void SetupDllResolution()
        {
            string appDir = AppDomain.CurrentDomain.BaseDirectory;

            // Add runtime native DLL directory to PATH for WebView2Loader.dll
            bool is64 = IntPtr.Size == 8;
            string archDir = is64 ? "runtimes\\win-x64\\native" : "runtimes\\win-x86\\native";
            string nativePath = Path.Combine(appDir, archDir);

            if (Directory.Exists(nativePath))
            {
                string path = Environment.GetEnvironmentVariable("PATH") ?? "";
                Environment.SetEnvironmentVariable("PATH", nativePath + ";" + path);
            }

            // Assembly Resolve for WebView2 Core and WinForms
            AppDomain.CurrentDomain.AssemblyResolve += delegate(object sender, ResolveEventArgs args)
            {
                string assemblyName = new AssemblyName(args.Name).Name + ".dll";

                string[] candidatePaths = new string[]
                {
                    Path.Combine(appDir, assemblyName),
                    Path.Combine(appDir, "lib", "net462", assemblyName),
                    Path.Combine(appDir, "lib", assemblyName)
                };

                foreach (string candidate in candidatePaths)
                {
                    if (File.Exists(candidate))
                    {
                        try
                        {
                            return Assembly.LoadFrom(candidate);
                        }
                        catch { }
                    }
                }

                return null;
            };
        }
    }
}
