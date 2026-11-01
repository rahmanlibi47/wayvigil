@echo off
setlocal

echo WayVigil Google Maps API access check
echo The key will be visible while you enter it, but will not be saved to a file.
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$key = (Read-Host 'Paste API key').Trim();" ^
  "try {" ^
  "  if ($key -notmatch '^[A-Za-z0-9_-]+$') { throw 'The pasted key contains whitespace or unsupported characters. Copy only the key value and paste it again.' }" ^
  "  $failed = $false;" ^
  "  $place = Read-Host 'Place to search (for example, Bengaluru)';" ^
  "  if ([string]::IsNullOrWhiteSpace($place)) { throw 'A place name is required.' }" ^
  "  $headers = @{ 'X-Goog-Api-Key' = $key; 'X-Goog-FieldMask' = 'suggestions.placePrediction.placeId,suggestions.placePrediction.text.text' };" ^
  "  $body = @{ input = $place } | ConvertTo-Json -Compress;" ^
  "  Write-Host ''; Write-Host 'Places API (New) Autocomplete:';" ^
  "  try { $result = Invoke-RestMethod -Method Post -Uri 'https://places.googleapis.com/v1/places:autocomplete' -Headers $headers -ContentType 'application/json' -Body $body; $items = @($result.suggestions | ForEach-Object { $_.placePrediction.text.text }); if ($items.Count -gt 0) { Write-Host '  ACCESS OK'; $items | Select-Object -First 5 | ForEach-Object { Write-Host ('  - ' + $_) } } else { Write-Host '  ACCESS OK, but Google returned no suggestions.' } } catch { $failed = $true; $response = $_.Exception.Response; if ($response) { $status = [int]$response.StatusCode; $reader = New-Object System.IO.StreamReader($response.GetResponseStream()); $errorBody = $reader.ReadToEnd(); Write-Host ('  ACCESS FAILED (HTTP ' + $status + ')'); try { $detail = ($errorBody | ConvertFrom-Json).error.message; if ($detail) { Write-Host ('  Google says: ' + $detail) } else { Write-Host ('  Google response: ' + $errorBody) } } catch { Write-Host ('  Google response: ' + $errorBody) } } else { Write-Host ('  REQUEST FAILED: ' + $_.Exception.Message) } }" ^
  "  $routeHeaders = @{ 'X-Goog-Api-Key' = $key; 'X-Goog-FieldMask' = 'routes.duration,routes.distanceMeters' };" ^
  "  $routeBody = @{ origin = @{ location = @{ latLng = @{ latitude = 12.9716; longitude = 77.5946 } } }; destination = @{ location = @{ latLng = @{ latitude = 12.9352; longitude = 77.6245 } } }; travelMode = 'DRIVE' } | ConvertTo-Json -Depth 6 -Compress;" ^
  "  Write-Host ''; Write-Host 'Routes API Compute Routes:';" ^
  "  try { $route = Invoke-RestMethod -Method Post -Uri 'https://routes.googleapis.com/directions/v2:computeRoutes' -Headers $routeHeaders -ContentType 'application/json' -Body $routeBody; if ($route.routes.Count -gt 0) { Write-Host '  ACCESS OK'; Write-Host ('  Distance: ' + $route.routes[0].distanceMeters + ' meters'); Write-Host ('  Duration: ' + $route.routes[0].duration) } else { Write-Host '  ACCESS OK, but Google returned no route.' } } catch { $failed = $true; $response = $_.Exception.Response; if ($response) { $status = [int]$response.StatusCode; $reader = New-Object System.IO.StreamReader($response.GetResponseStream()); $errorBody = $reader.ReadToEnd(); Write-Host ('  ACCESS FAILED (HTTP ' + $status + ')'); try { $detail = ($errorBody | ConvertFrom-Json).error.message; if ($detail) { Write-Host ('  Google says: ' + $detail) } else { Write-Host ('  Google response: ' + $errorBody) } } catch { Write-Host ('  Google response: ' + $errorBody) } } else { Write-Host ('  REQUEST FAILED: ' + $_.Exception.Message) } }" ^
  "} finally { $key = $null }; if ($failed) { exit 1 }"

echo.
if errorlevel 1 (
    echo One or more API checks failed. Review the response details above.
) else (
    echo Both Places and Routes API checks succeeded.
)
pause
endlocal
