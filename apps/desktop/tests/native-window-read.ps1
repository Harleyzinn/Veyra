param([string]$WindowHandle,[int]$X=0,[int]$Y=0)
$ErrorActionPreference='Stop'
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class VeyraWindowRead {
 [DllImport("user32.dll",EntryPoint="GetWindowLongPtrW")] public static extern IntPtr GetStyle(IntPtr h,int n);
 [DllImport("user32.dll")] public static extern int GetWindowRgn(IntPtr h,IntPtr r);
 [DllImport("gdi32.dll")] public static extern IntPtr CreateRectRgn(int a,int b,int c,int d);
 [DllImport("gdi32.dll")] public static extern bool PtInRegion(IntPtr r,int x,int y);
 [DllImport("gdi32.dll")] public static extern bool DeleteObject(IntPtr h);
 [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(IntPtr h,int attr,out int value,int size);
 [DllImport("user32.dll",CharSet=CharSet.Auto)] public static extern IntPtr SendMessage(IntPtr h,uint msg,IntPtr wp,IntPtr lp);
}
'@
$windowPtr=[IntPtr]::new([Convert]::ToInt64($WindowHandle,16))
$style=[VeyraWindowRead]::GetStyle($windowPtr,-16).ToInt64()
$extended=[VeyraWindowRead]::GetStyle($windowPtr,-20).ToInt64()
$region=[VeyraWindowRead]::CreateRectRgn(0,0,0,0)
try {
 $regionKind=[VeyraWindowRead]::GetWindowRgn($windowPtr,$region)
 $corner=[VeyraWindowRead]::PtInRegion($region,0,0)
 $backdrop=0;$dwmResult=[VeyraWindowRead]::DwmGetWindowAttribute($windowPtr,38,[ref]$backdrop,4)
 $coords=($Y -shl 16) -bor ($X -band 0xffff)
 $hit=[VeyraWindowRead]::SendMessage($windowPtr,0x84,[IntPtr]::Zero,[IntPtr]::new($coords)).ToInt64()
 @{caption=(($style -band 0xc00000) -ne 0);thickFrame=(($style -band 0x40000) -ne 0);layered=(($extended -band 0x80000) -ne 0);topMost=(($extended -band 0x8) -ne 0);clickThrough=(($extended -band 0x20) -ne 0);regionKind=$regionKind;cornerInside=$corner;backdrop=$backdrop;dwmResult=$dwmResult;hitTest=$hit}|ConvertTo-Json -Compress
} finally {[void][VeyraWindowRead]::DeleteObject($region)}
