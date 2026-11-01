@echo off
setlocal

cd /d "%~dp0"

echo Checking current network configuration:
ipconfig
echo.
echo Checking API_BASE_URL in local.properties...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $p='local.properties'; if(-not(Test-Path -LiteralPath $p)){throw 'android/local.properties was not found. Open the Android project in Android Studio first.'}; $r=Get-NetRoute -DestinationPrefix '0.0.0.0/0' | Sort-Object RouteMetric | Select-Object -First 1; if(-not $r){throw 'No active IPv4 default route was found. Connect to Wi-Fi and try again.'}; $a=Get-NetIPAddress -InterfaceIndex $r.InterfaceIndex -AddressFamily IPv4 -AddressState Preferred | Where-Object {$_.IPAddress -ne '127.0.0.1' -and $_.IPAddress -notlike '169.254.*'} | Select-Object -First 1; if(-not $a){throw 'No usable IPv4 address was found on the active network interface.'}; $url='http://'+$a.IPAddress+':8000'; $bytes=[System.IO.File]::ReadAllBytes($p); $bom=$bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191; $text=[System.IO.File]::ReadAllText($p); $pattern='(?m)^[ \t]*API_BASE_URL[ \t]*=([^\r\n]*)'; $found=[regex]::Matches($text,$pattern); $same=$found.Count -eq 1 -and $found[0].Groups[1].Value.Trim() -eq $url; if($same){Write-Host ('API_BASE_URL already matches the active address: '+$url)}else{$text=[regex]::Replace($text,'(?m)^[ \t]*API_BASE_URL[ \t]*=[^\r\n]*(?:\r?\n|$)',''); $text=$text.TrimEnd([char[]](13,10)); $nl=[Environment]::NewLine; if($text.Length -gt 0){$text+=$nl}; $text+='API_BASE_URL='+$url+$nl; [System.IO.File]::WriteAllText($p,$text,[System.Text.UTF8Encoding]::new($bom)); Write-Host ('Updated API_BASE_URL to '+$url)}"
if errorlevel 1 (
    echo.
    echo Could not detect the active network address or update local.properties.
    exit /b 1
)

echo.
echo Building and installing the debug app...
call gradlew.bat installDebug
if errorlevel 1 (
    echo.
    echo BUILD FAILED. The app was not launched.
    exit /b 1
)

echo.
echo BUILD SUCCESSFUL. Launching WayVigil AI...
adb shell monkey -p com.saferoute 1
if errorlevel 1 (
    echo.
    echo The app was installed, but could not be launched. Check that a device is connected and authorized with adb.
    exit /b 1
)

exit /b 0
