# Milestone 2: Navigation Foundation

This document records the implementation scope for Milestone 2 in the repository. It reconciles the broader requirements with the phase assignments in the approved roadmap and feature backlog so the work can ship in small, testable slices.

## In scope

- Google Maps SDK for map display, camera controls, and current-location display.
- Foreground location permission only. Continuous location updates run only during active navigation.
- GPS quality handling, filtered speed display, and location smoothing.
- Destination search and route planning through Google Places and Routes APIs.
- One primary route first, followed by alternate-route selection.
- Driving route first. Two-wheeler routing is a required Milestone 2 follow-up before the milestone is declared complete; validate provider support before promising it to users.
- Route drawing, distance and ETA, visual maneuver guidance, and device text-to-speech.
- Stable navigation heading selection and a camera behavior contract for overview, startup, and active following.
- Route deviation detection and basic rerouting.
- Portrait and landscape support, GPS/route/guidance tests, and battery-use measurement.

## Deferred

- Background location when navigation is not active.
- Waypoints and saved route history (later milestone).
- Offline routing and offline route storage (post-MVP; do not promise offline navigation).
- Lane guidance and speed-limit display (later milestone or dependent data source).
- Traffic overlay is not a Milestone 2 acceptance requirement.
- Hazard display, alerts, curve analysis, and other later milestone features.

Google Maps is the visualization layer. Routes API responses provide routes and maneuver steps; do not derive road geometry or safety decisions from rendered Google map tiles. Keep provider credentials out of committed source and define quota/error handling before API integration.

## Implementation units and manual acceptance cases

### Unit 0 — Scope contract

**Acceptance case:** Review this scope against the roadmap and backlog. Confirm the in-scope and deferred lists agree before route-provider implementation begins. The driving-first/two-wheeler-follow-up sequence is intentional; Milestone 2 cannot be marked complete until the two-wheeler requirement is delivered or the product requirement is formally revised.

### Unit 1 — Home navigation entry

**Acceptance case:** Sign in, tap **Navigate** on Home, and verify the navigation screen opens. Tap Back and verify Home is restored. Confirm the navigation screen is the only user-facing route from Home for navigation; the map-only route is not used by this action.

### Unit 2 — Foreground location permissions

**Acceptance cases:**

1. Fresh install: tap **Navigate**, grant precise location, and verify the map’s location indicator appears and the camera centers on the device when a fix is available.
2. Grant approximate location only: verify the map can display an approximate location, while the UI clearly indicates precise location is needed for accurate navigation.
3. Deny location: verify the screen remains open without a crash or location service running, explains that location is unavailable, and allows the user to retry from the location button.
4. Previously granted location: verify navigation opens without repeating the permission prompt and centers on the current/last known fix.
5. Verify permission grant alone does not start continuous tracking or an ongoing navigation notification; that starts only with active navigation.

### Unit 3 — One location-update source

The foreground service owns only the ongoing Android notification. `NavigationManager` owns the sole Fused Location Provider subscription and publishes accepted fixes, speed, heading, and route progress to the app. This avoids duplicate GPS requests and competing state.

**Acceptance cases:**

1. Start tracking with precise permission and verify each delivered fix updates the manager once; the service does not request location itself.
2. Stop tracking and verify the Fused Location callback is removed and the foreground notification disappears.
3. Start tracking twice and verify only one subscription and one notification remain active.

### Unit 4 — Navigation tracking lifecycle

Continuous tracking is coupled to an active route. Starting navigation requires precise foreground permission and starts tracking once. Stopping navigation or arriving stops tracking and removes the notification. Permission grant by itself does not start it.

**Acceptance cases:**

1. Attempt to start a route without precise permission; navigation and tracking remain stopped.
2. Start a route with permission; tracking and its notification start, and a duplicate start does not create another subscription.
3. Stop navigation; verify the state returns to idle, tracking stops, and the notification is dismissed. Repeat stop and confirm it is safe.
4. Simulate arrival; verify the Arrived state is retained while location tracking stops.

### Unit 5 — Location quality and interruption handling

Reject invalid coordinates, fixes with accuracy worse than 50 meters, stale fixes older than five seconds, and timestamps more than two seconds in the future. Publish a GPS quality state to the UI, and resume from a fresh acceptable fix after interruptions. Ignore rejected fixes for map state, route matching, and speed.

**Acceptance cases:**

1. Feed invalid coordinates, poor accuracy, and stale fixes; verify none changes the current accepted location, route progress, or speed.
2. During navigation, pause location delivery for more than five seconds; verify the UI reports the signal as stale.
3. Resume with a fresh, accurate fix; verify the quality returns to good and updates resume.
4. Feed small stationary GPS drift and verify the existing location filter continues to suppress it.

### Unit 6 — Filtered speed in navigation UI

The speed indicator consumes the filtered speed from accepted navigation fixes and displays km/h. Bad, stale, and low-quality fixes must not affect it; the calculator drops readings older than its five-second window.

**Acceptance cases:**

1. Feed several valid speed samples and verify the UI reflects the filtered value in km/h rather than raw GPS noise.
2. Feed a low-quality or rejected location and verify the displayed speed does not jump.
3. Stop delivering fixes for more than five seconds and verify the speed returns to zero rather than remaining stale.

### Unit 7 — Routes and Places API security decision

Use the existing `GOOGLE_MAPS_API_KEY` backend setting for server-side Places/Routes calls through authenticated backend endpoints. Do not place a Routes/Places server key in Android resources, `BuildConfig`, the APK, URLs, or committed files. Keep the separate Maps SDK key in local build configuration and restrict it to the Android package and signing certificate. API calls must use HTTPS, enforce backend rate limits, honor provider quotas, and map authentication, quota, network, and provider failures to safe user-facing states. Cache provider responses only where the provider terms permit it. Units 8–10 implement and exercise these boundaries.

**Acceptance cases:**

1. Inspect Android resources, generated configuration, and packaged output; confirm no server-side Routes/Places credential is embedded.
2. Confirm server credentials are supplied from deployment environment/secrets and never included in request URLs or client-visible errors.
3. Verify navigation endpoints reject unauthenticated requests, apply rate limits, and return recoverable quota/network/provider errors without exposing credentials.

### Unit 8 — Backend Places proxy

Add authenticated, rate-limited backend endpoints for Places autocomplete and place details. Normalize provider data for the app, pass the API key only in the provider request header, and return safe errors without provider response bodies or credentials.

**Acceptance cases:**

1. Unauthenticated requests are rejected; short and oversized search inputs and invalid coordinates are rejected.
2. Mocked autocomplete responses normalize to a maximum of five predictions; place details return validated coordinates.
3. With no provider key, return a safe service-unavailable response. Provider timeout, quota, and malformed responses return safe errors without key or raw provider body.
4. Verify provider calls use HTTPS, field masks, and the API key header, with no key in request URLs.

### Unit 9 — Android destination search

Connect the search field to the authenticated backend. Debounce input, cancel obsolete searches, use one session token across an autocomplete session, resolve selected place details, show attribution, and display recoverable loading/error states.

**Acceptance cases:**

1. Inputs shorter than three characters do not call the API; a longer query shows provider predictions after the debounce.
2. Rapid query changes show only the latest results; selecting a prediction resolves its address and coordinates.
3. Search, authentication, or provider failures show a recoverable message and do not crash the screen.
4. Verify predictions include Google attribution and no Places server key is present in the Android app.

### Unit 10 — Route preview and navigation start

Add an authenticated Routes API proxy for validated origin/destination coordinates and driving travel mode. Display the route polyline, distance, and ETA on the map; allow the user to start and end navigation. The foreground tracking lifecycle from Unit 4 starts only after the user starts the previewed route.

**Acceptance cases:**

1. Invalid coordinates and unsupported travel modes are rejected; a provider response is normalized into route geometry and maneuver steps.
2. Selecting a destination and requesting a route draws its encoded polyline and displays distance and ETA.
3. Starting the route enters navigation, starts tracking, and shows progress; ending navigation stops tracking and removes the route preview.
4. No-route, quota, timeout, and provider errors show recoverable UI messages and do not start tracking.
5. Confirm no server credential is embedded in Android and the route provider receives the server key only in its request header.

### Unit 11 — Route progress and arrival

Match accepted GPS fixes to the route geometry using traveled distance along the polyline. Keep displayed progress monotonic so GPS noise cannot move the trip backward. Update remaining distance and ETA from route progress. Mark arrival only when progress is near the route end and the device is within 35 meters of the destination; stop tracking and show the destination name.

**Acceptance cases:**

1. Move along a route with unevenly spaced polyline points and verify progress follows traveled geometry rather than point count.
2. Feed a location that projects behind the furthest matched point and verify remaining distance and ETA do not increase.
3. Get near the route end but remain far from the destination; verify navigation does not mark arrival.
4. Reach within 35 meters of the destination after at least 90% route progress; verify arrival is shown with the destination name and tracking stops.
5. Tap **Done** after arrival; verify navigation returns to idle and the route preview is cleared.

### Unit 12 — Maneuver progression and on-screen guidance

Advance through route steps based on monotonic route progress, show the upcoming instruction and distance while navigating, and keep the map camera centered on the accepted current location. Hide destination search controls while the user is navigating.

**Acceptance cases:**

1. Start a route with multiple steps and verify the first instruction and distance to its maneuver are shown.
2. Progress past a step boundary and verify the next instruction appears without reverting when GPS progress jitters backward.
3. Verify the map follows accepted current-location updates during navigation and does not recenter for rejected or stale fixes.
4. Verify destination search controls return after navigation ends or arrival is acknowledged.

### Unit 13 — Spoken maneuver guidance

Use Android text-to-speech to announce the first instruction, an upcoming maneuver within 300 meters, and the maneuver when it is within 50 meters. Avoid duplicate prompts, provide a voice mute control, stop speech when navigation ends or is muted, and handle unavailable English speech data without crashing.

**Acceptance cases:**

1. Start a route and verify the first instruction is spoken once.
2. Approach a maneuver and verify one advance announcement and one immediate announcement; repeated GPS updates do not repeat either prompt.
3. Mute voice guidance and verify active speech stops and no more prompts are spoken until re-enabled.
4. End navigation and verify speech stops; confirm missing TTS language support leaves visual guidance usable.

### Unit 14 — Confirmed off-route detection

Show an off-route state only after three distinct, consecutive, reliable fixes indicate the user is more than 150 meters from the route. Ignore uncertain GPS fixes, reset confirmation after an on-route or mildly off-route fix and after a five-second location gap, and resume navigation if the user returns to the route. Keep rerouting as a separate follow-up unit.

**Acceptance cases:**

1. One or two far-away fixes do not show an off-route state; the third consecutive reliable fix does.
2. GPS uncertainty does not count toward confirmation or erase reliable deviation fixes; an on-route fix resets confirmation.
3. A location gap longer than five seconds starts a new confirmation sequence.
4. Return to the planned route and verify navigation guidance resumes with the preserved route progress.
5. Choose **Continue on planned route** and verify tracking remains active and deviation confirmation restarts cleanly.

### Unit 15 — Basic rerouting

When the user confirms **Recalculate route**, request a replacement driving route from the latest fresh GPS fix to the original destination through the authenticated backend. Preserve the destination identity, replace the displayed route and navigation progress only after a valid route response, and retain the off-route state with a recoverable error if the request fails. Prevent concurrent reroute requests and do not send stale or uncertain coordinates.

**Acceptance cases:**

1. Request a reroute and verify the backend receives the latest accepted origin and original destination coordinates.
2. Receive a valid replacement route and verify the map, maneuver guidance, distance, and ETA switch to it while location tracking continues once.
3. Simulate a provider failure or no route and verify the old route remains available, an error is shown, and retry is possible.
4. Verify stale or uncertain location prevents the provider call and concurrent taps do not create duplicate requests.
5. Continue on the planned route or stop navigation during a pending request; verify the late response cannot replace the active state.

### Unit 16 — Alternate route selection

Request up to three provider alternatives for a driving route, normalize and display each route's distance and ETA, draw alternatives distinctly, and switch the selected preview only when the user chooses one. The provider can return only the primary route for some trips; the UI must continue normally in that case.

**Acceptance cases:**

1. Verify a driving request asks for alternatives and the backend returns all provider routes with labels, geometry, distance, ETA, and steps.
2. When alternatives are returned, compare them on the map and choose each option; verify the chosen geometry, distance, ETA, and maneuver steps become active.
3. When only one route is returned, verify route selection remains usable without an empty or misleading alternative control.
4. A route calculation failure leaves the destination selected and allows retry.
5. Verify route alternatives are not requested during rerouting and no server key is exposed to Android.

### Unit 17 — Two-wheeler routing

Allow the user to request a motorized two-wheeler route through the backend Routes API, preserve the selected mode across route start and rerouting, and display the required provider coverage and beta warning. If the provider cannot return a route in the requested region, show a recoverable error and never silently substitute a driving route.

**Acceptance cases:**

1. Select **Motorcycle** and verify the backend receives `TWO_WHEELER`; start navigation and verify the selected route retains that mode.
2. Verify a successful response displays the two-wheeler safety warning during preview and active navigation.
3. Simulate a provider error or no route and verify the app shows an error without substituting `DRIVE`.
4. Trigger rerouting and verify it keeps `TWO_WHEELER`, targets the original destination, and does not request alternatives.
5. Confirm a driving route still requests `DRIVE` and remains the default mode.

### Unit 18 — Rotation and app interruption handling

Keep the active navigation route, progress, selected travel mode, destination, search/preview state, camera position, and voice mute setting stable through device rotation and ordinary app background/foreground interruptions. Keep the foreground location service active only while navigation is active; refresh permission state on resume.

**Acceptance cases:**

1. Rotate the device during route preview and verify destination, selected alternative, route, and map camera are restored.
2. Rotate during active navigation and verify progress, maneuver, voice mute state, route line, and ongoing location notification remain consistent without duplicate tracking or replaying already announced guidance.
3. Background the app briefly and return; verify tracking continues, stale GPS is reported if appropriate, and fresh fixes resume progress.
4. Revoke location permission in system settings, return to the app, and verify the permission state is refreshed and navigation reports the limitation safely.
5. End navigation after returning and verify tracking and the notification stop.

### Unit 19 — Full navigation acceptance and battery review

Run the manual checklist in `docs/MILESTONE_2_ACCEPTANCE.md` on a physical Android device. Record the device, Android version, route mode, duration, and battery measurements. Review navigation behavior, errors, GPS recovery, rotation/background behavior, and provider boundaries before declaring the milestone complete.

**Acceptance cases:**

1. Complete all device scenarios in the acceptance checklist, covering permissions, search, route selection, driving/two-wheeler mode, guidance, arrival, deviation, rerouting, and recovery.
2. Run the documented battery comparison with the same device, charge level, screen brightness, network, and test duration; record baseline and active-navigation results.
3. Resolve crashes, duplicate tracking, unsafe stale-location behavior, missing warnings, or credential leaks found during the review.
4. Record any provider coverage limitation and confirm it is visible to the user instead of silently substituting a different travel mode.

### Unit 20 — Navigation camera and heading behavior contract

Define the expected map behavior before implementing the navigation camera and directional marker. Route preview uses an overview; active navigation uses a forward-looking follow view; user gestures temporarily allow free exploration; recenter restores following. Startup, off-route, reroute, arrival, and resume transitions must have a single camera owner and must not compete with route fitting. Heading priority is reliable GNSS course while moving, then fresh device orientation, then an unchanged/north-up camera when neither source is trustworthy. The location marker must not imply a trustworthy direction when heading is unavailable.

**Acceptance cases:**

1. Review route preview, navigation startup, active follow, free exploration, recenter, off-route, arrival, and resume states against the behavior contract.
2. Confirm stale or low-quality location and heading data cannot drive a camera rotation.
3. Confirm startup can transition from route overview to following without opposing camera animations or an abrupt heading snap.

### Unit 21 — Reliable heading source and smoothing

Prefer GNSS course only when speed is at least 2 m/s and Android reports bearing accuracy within 45 degrees. Otherwise use a fresh device orientation reading, corrected from magnetic north to true north using the accepted location. Prefer the rotation-vector sensor; fall back to accelerometer and magnetometer when the rotation vector is unavailable. Smooth angular changes across the 0/360-degree boundary. Subscribe only during active tracking, remove listeners when tracking stops, and fall back to no directional heading when sensors are absent or stale. Heading is orientation guidance only; it must not be treated as a substitute for position.

**Acceptance cases:**

1. Unit tests verify source priority, speed and accuracy thresholds, invalid and stale readings, north-crossing smoothing, and reset behavior.
2. On a device, verify GNSS course takes precedence while moving with a reliable bearing, and device orientation is used while stationary or when course quality is poor.
3. Verify magnetic declination correction and orientation remain coherent in portrait and landscape.
4. Verify devices without a rotation vector use the accelerometer/magnetometer fallback; devices without usable orientation sensors do not crash and produce no false heading.
5. Verify sensor listeners start once with navigation tracking and stop on arrival or when navigation ends.
6. Briefly block GPS and then restore it; verify stale position does not steer the navigation camera and a fresh fix resumes heading updates.

### Unit 22 — Direction-aware location marker

Replace the default map pin for the current position with a navigation arrow when a fresh accepted location and heading are available. Rotate the arrow to the selected true-north heading and use a centered location dot when heading is unavailable. Do not keep presenting a stale one-shot route origin as the current position while navigation is active.

**Acceptance cases:**

1. During active navigation with a reliable heading, verify the marker is an arrow aligned with direction of travel.
2. While stopped or when heading quality is unavailable, verify the marker becomes a neutral dot rather than a misleading directional pin.
3. Reject a stale or poor-quality fix and verify the marker does not jump; if the accepted location is no longer current, do not show the old route origin as current position.
4. Pan the map during free exploration and verify the arrow remains geographically aligned with its heading.

### Unit 23 — Overview, follow, and free-explore camera modes

Use an overview camera for route preview, follow mode during active navigation, and free-explore mode after the user moves the map. Suspend automatic camera updates while exploring. Provide a recenter control that restores follow mode using the latest accepted location and heading. Return to overview when navigation is no longer active.

**Acceptance cases:**

1. Calculate a route and verify the map presents the route overview.
2. Start navigation and verify follow mode begins; route-progress and heading updates keep it active.
3. Drag, rotate, or zoom the map and verify automatic following pauses without resetting route progress.
4. Tap recenter and verify the camera returns to follow mode; repeat while already following and verify the camera recenters.
5. End navigation or arrive and verify automatic follow stops and the camera mode returns to overview.

### Unit 24 — Forward-looking navigation framing

Frame active navigation with a close zoom, a tilted map, and the current position below the center so the upcoming road remains visible between the maneuver and trip-summary overlays. Hide the standalone screen toolbar while navigation is active to preserve map space. Adjust the target offset by reliable heading; when heading is unavailable, keep a stable north-up orientation and avoid guessing a forward direction.

**Acceptance cases:**

1. On a physical device in portrait, verify the current position is visible below center and the next route segment remains visible above it without being covered by guidance cards.
2. Repeat in landscape and verify route, marker, maneuver instruction, and trip summary remain visible.
3. Verify the follow camera uses the current zoom and does not zoom out below its navigation framing when recentering.
4. Verify poor or stale location/heading data does not move the camera target or rotate the map.
5. Verify map gestures can still enter free-explore mode and recenter restores the forward-looking frame.

### Unit 25 — Single-owner camera transitions

Use one camera owner for route overview, active follow, and free exploration so route fitting and follow updates cannot animate the map at the same time. Cancel route fitting before follow begins, animate the first follow frame once, and apply later location/heading updates without restarting camera animations. Do not recenter from cached locations while a route is active.

**Acceptance cases:**

1. Starting a route causes no visible left/right spin or snap.
2. Route fitting cannot run over the active follow camera.
3. Off-route, reroute, arrival, and resume transitions do not cause camera flicker.
4. Rapid GPS updates do not queue multiple camera animations.
5. Automated tests verify route overview, follow, and free exploration resolve to one camera owner.

### Unit 26 — Sensor lifecycle and fallback behavior

Subscribe once during active tracking and unregister on every stop path, including arrival, location-service failure, permission failure, and repeated stop calls. Use the rotation vector when registration succeeds; use accelerometer plus magnetometer only when both registrations succeed and both readings are fresh and reliable. Reject unreliable sensor accuracy, clear stale heading after GPS loss, and retain GNSS-only heading when device sensors are missing. Do not request camera or light permissions for navigation orientation.

**Acceptance cases:**

1. Verify sensor registration occurs once when tracking starts; repeated starts do not add listeners.
2. Stop, arrive, force a location subscription failure, and revoke permission; verify all registered listeners are removed on each path.
3. Verify missing rotation-vector support uses both accelerometer and magnetometer, while a missing or failed fallback sensor leaves GNSS-only behavior without a crash.
4. Feed unreliable and stale sensor readings; verify they do not drive heading or camera rotation, and recovery uses fresh sensor values.
5. Rotate, background, and resume the app; verify no duplicate listeners and no old heading survives a stopped session.
6. Measure battery during active navigation and compare with the idle-map baseline in Unit 28.

### Unit 27 — Automated camera and heading tests

Test the pure heading estimator and camera policy without requiring Android sensors, Google Maps initialization, or network access. Cover source selection, staleness, north-boundary smoothing, camera-owner exclusivity, follow interruption, and portrait/landscape framing calculations.

**Acceptance cases:**

1. Tests cover invalid and stale readings, source priority, exact speed/accuracy boundaries, out-of-order sensor readings, reset, and wraparound.
2. Tests prove route overview, follow, free exploration, and arrival each resolve to one camera owner.
3. Tests prove user camera movement pauses following, programmatic updates do not, and recenter can restore following.
4. Tests verify framing offset changes for portrait/landscape sizes and zoom while remaining within safe bounds.
5. Sensor and visual behaviors that require a device are recorded separately in Unit 28.

### Unit 28 — Device acceptance and tuning

Run the expanded `docs/MILESTONE_2_ACCEPTANCE.md` checklist on the target phone after automated Android checks pass. Verify camera ownership, directional marker, user gestures, recenter, screen orientation, sensor fallback, GPS recovery, and battery behavior. Perform route-following safely while stationary or as a passenger; never inspect or operate the phone while driving.

**Acceptance cases:**

1. Complete every camera/heading row in the device acceptance table in portrait and landscape.
2. Start while stationary, begin moving, turn, pause, make a U-turn, and recover from temporary GPS/sensor loss; record heading/camera lag or drift.
3. Pan, rotate, and zoom the map; verify follow pauses and recenter restores it without route-progress changes.
4. Repeat key scenarios for driving and two-wheeler routes and verify mode warnings remain visible.
5. Record battery baseline and active-navigation results plus device, Android version, test duration, and app commit.
6. Leave unresolved failures explicitly listed; do not mark Milestone 2 complete until the device acceptance record is reviewed.
