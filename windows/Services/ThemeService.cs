using System;
using System.Windows;
using Microsoft.Win32;

namespace SpeedShareWindows.Services
{
    /// <summary>User-facing theme selection. System follows the OS setting.</summary>
    public enum AppTheme
    {
        System,
        Light,
        Dark
    }

    /// <summary>
    /// Applies the SpeedShare light/dark resource dictionary at runtime so the
    /// whole window re-themes live via DynamicResource references.
    /// </summary>
    public static class ThemeService
    {
        public static AppTheme Current { get; private set; } = AppTheme.System;

        /// <summary>Effective (resolved) theme — System becomes Light or Dark.</summary>
        public static bool IsDark => Resolve(Current);

        /// <summary>Applies a theme and swaps the active palette dictionary.</summary>
        public static void Apply(AppTheme mode)
        {
            Current = mode;
            bool dark = Resolve(mode);

            var app = Application.Current;
            if (app == null) return;

            // Remove any existing palette dictionary, then add the right one first
            // so styles resolved later (with DynamicResource) pick it up.
            for (int i = app.Resources.MergedDictionaries.Count - 1; i >= 0; i--)
            {
                var dict = app.Resources.MergedDictionaries[i];
                var source = dict.Source?.OriginalString ?? string.Empty;
                if (source.Contains("Themes/Light.xaml", StringComparison.OrdinalIgnoreCase) ||
                    source.Contains("Themes/Dark.xaml", StringComparison.OrdinalIgnoreCase))
                {
                    app.Resources.MergedDictionaries.RemoveAt(i);
                }
            }

            var palette = new ResourceDictionary
            {
                Source = new Uri(
                    dark
                        ? "pack://application:,,,/Themes/Dark.xaml"
                        : "pack://application:,,,/Themes/Light.xaml",
                    UriKind.Absolute)
            };
            app.Resources.MergedDictionaries.Insert(0, palette);
        }

        /// <summary>Reads the Windows light/dark preference from HKCU.</summary>
        public static bool SystemUsesDarkTheme()
        {
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(
                    @"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
                var value = key?.GetValue("AppsUseLightTheme");
                return value is int i && i == 0;
            }
            catch
            {
                return true; // default to dark on failure
            }
        }

        private static bool Resolve(AppTheme mode) => mode switch
        {
            AppTheme.Light => false,
            AppTheme.Dark => true,
            _ => SystemUsesDarkTheme()
        };
    }
}
