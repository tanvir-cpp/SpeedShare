using System.Windows;
using SpeedShareWindows.Services;

namespace SpeedShareWindows;

/// <summary>
/// Interaction logic for App.xaml
/// </summary>
public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Resolve the persisted theme (System default) BEFORE the window is
        // constructed so every DynamicResource resolves against the right palette.
        var (_, _, theme) = SettingsService.Load();
        ThemeService.Apply(theme);

        // No StartupUri in App.xaml — create the main window explicitly so the
        // theme is applied before it loads its resources.
        new MainWindow().Show();
    }
}
