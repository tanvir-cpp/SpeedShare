using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

namespace SpeedShareSetup
{
    /// <summary>
    /// Direct P/Invoke wrappers for the shell's IShellLink and IPersistFile
    /// interfaces. Replaces the previous late-bound `WScript.Shell` approach
    /// (which had no compile-time type safety and is a flag for AV heuristics).
    /// </summary>
    internal static class ShellLinkInterop
    {
        private static readonly Guid CLSID_ShellLink = new("00021401-0000-0000-C000-000000000046");
        private static readonly Guid IID_IShellLinkW = new("000214F9-0000-0000-C000-000000000046");
        private static readonly Guid IID_IPersistFile = new("0000010B-0000-0000-C000-000000000046");

        [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
        private class ShellLinkCoClass { }

        [ComImport, Guid("000214F9-0000-0000-C000-000000000046"),
         InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IShellLinkW
        {
            void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszFile, int cch, IntPtr pfd, uint fFlags);
            void GetIDList(out IntPtr ppidl);
            void SetIDList(IntPtr pidl);
            void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszName, int cch);
            void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string pszName);
            void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszDir, int cch);
            void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string pszDir);
            void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszArgs, int cch);
            void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string pszArgs);
            void GetHotkey(out short pwHotkey);
            void SetHotkey(short wHotkey);
            void GetShowCmd(out int piShowCmd);
            void SetShowCmd(int iShowCmd);
            void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszIconPath, int cch, out int piIcon);
            void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string pszIconPath, int iIcon);
            void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string pszPathRel, uint dwReserved);
            void Resolve(IntPtr hwnd, uint fFlags);
            void SetPath([MarshalAs(UnmanagedType.LPWStr)] string pszFile);
        }

        [ComImport, Guid("0000010B-0000-0000-C000-000000000046"),
         InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IPersistFile
        {
            void GetClassID(out Guid pClassID);
            [PreserveSig] int IsDirty();
            void Load([MarshalAs(UnmanagedType.LPWStr)] string pszFileName, uint dwMode);
            void Save([MarshalAs(UnmanagedType.LPWStr)] string pszFileName, [MarshalAs(UnmanagedType.Bool)] bool fRemember);
            void SaveCompleted([MarshalAs(UnmanagedType.LPWStr)] string pszFileName);
            void GetCurFile(out IntPtr ppszFileName);
        }

        public static void CreateShortcut(string linkPath, string targetPath, string workingDirectory, string description, string? iconPath = null)
        {
            // Ensure parent directory exists
            var dir = Path.GetDirectoryName(linkPath);
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
            }

            // Create the COM object via the shell CLSID
            var link = (IShellLinkW)Activator.CreateInstance(Type.GetTypeFromCLSID(CLSID_ShellLink)!)!;
            try
            {
                link.SetPath(targetPath);
                link.SetWorkingDirectory(workingDirectory);
                if (!string.IsNullOrEmpty(description))
                {
                    link.SetDescription(description);
                }
                if (!string.IsNullOrEmpty(iconPath))
                {
                    link.SetIconLocation(iconPath, 0);
                }
                else
                {
                    // Default: pull the icon from the target exe
                    link.SetIconLocation(targetPath, 0);
                }

                var persist = (IPersistFile)link;
                persist.Save(linkPath, true);
            }
            finally
            {
                Marshal.FinalReleaseComObject(link);
            }
        }
    }
}
