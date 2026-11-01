# WayVigil AI Milestone 2 Device Acceptance

Use a physical Android phone with USB debugging, precise location permission, working internet, and the local backend available. Do not perform route-following checks while driving; use a passenger or walk in a safe area. Provider routes and API calls may incur Google Maps Platform charges.

## Device and build record

| Field | Record |
| --- | --- |
| Device model | |
| Android version | |
| App commit | |
| Backend commit | |
| Network | |
| Test date | |

## Functional acceptance

| Scenario | Pass/Fail | Notes |
| --- | --- | --- |
| Fresh install requests foreground location; denial is recoverable; approximate location is explained | | |
| Navigate opens the navigation screen and Back returns Home | | |
| Search returns attributed results; selecting one resolves its location | | |
| Driving route preview displays route, distance, ETA, and available alternatives | | |
| Selecting a different alternative updates the route line, distance, ETA, and guidance | | |
| Motorcycle route uses two-wheeler mode and displays its safety warning | | |
| Provider rejection for a requested mode shows an error without silently switching modes | | |
| Starting navigation starts one location subscription and one ongoing notification | | |
| Maneuver text and distance update along a route; voice announces without repeating prompts | | |
| Voice mute stops speech and remains muted after rotating the phone | | |
| Invalid, inaccurate, or stale GPS fixes do not corrupt speed or route progress | | |
| Three consecutive reliable far-off-route fixes show deviation; recovery returns guidance | | |
| Rerouting uses a fresh origin, original destination, and selected travel mode | | |
| Arrival appears near the actual destination and stops tracking; Done clears the route | | |
| Rotate during preview and active navigation; route, progress, camera, and voice state remain coherent | | |
| Background and resume the app; tracking continues and GPS quality recovers safely | | |
| Revoke location permission in settings and resume; permission state refreshes safely | | |
| End navigation; tracking callback and ongoing notification stop | | |

## Battery comparison

Use Android Studio **Profiler → Energy** if available. Compare an idle baseline with an active navigation session on the same phone. Keep screen brightness, network, location settings, and test duration the same. Start both runs at a similar battery percentage and avoid charging during measurement.

1. Let the phone settle for five minutes, then record the battery percentage.
2. Run the app idle on the navigation map for 15 minutes without an active route; record the ending battery percentage and profiler observations.
3. Let the phone return to a similar starting charge and thermal state.
4. Start a representative route, keep the app in active navigation for 15 minutes, and record the ending battery percentage and profiler observations.
5. Repeat each run once if the phone's percentage display has not changed; compare location/GPS activity, CPU activity, and battery percentage change. Treat percentage readings as coarse evidence, not a precise power measurement.

| Run | Start % | End % | Duration | Profiler notes |
| --- | ---: | ---: | ---: | --- |
| Idle map baseline 1 | | | 15 min | |
| Active navigation 1 | | | 15 min | |
| Idle map baseline 2 | | | 15 min | |
| Active navigation 2 | | | 15 min | |

## Final review

- [ ] All applicable functional scenarios pass on the physical device.
- [ ] Battery results and test conditions are recorded above.
- [ ] No API keys or provider credentials appear in Android artifacts, logs, or user-visible errors.
- [ ] Any provider route coverage limitations are disclosed to the user.
- [ ] Known failures are fixed or listed as explicit release blockers before Milestone 2 is marked complete.
