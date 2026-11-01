# WayVigil AI - Intelligent Road Safety & Navigation Platform

![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)
![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)
![Python](https://img.shields.io/badge/Python-3.11%2B-yellow.svg)

WayVigil AI is a modern Android application that combines navigation, intelligent road safety monitoring, AI-powered hazard detection, and community-driven road hazard reporting. The platform goes beyond conventional navigation by understanding upcoming road conditions and proactively warning users.

## Features

### Core Navigation
- Interactive maps powered by Google Maps SDK
- GPS-based turn-by-turn navigation
- Real-time speed monitoring
- Route calculation and optimization

### Community Hazard Reporting
- Quick hazard reporting with 11 categories
- Photo/video attachment support
- GPS-accurate location capture
- Offline report queue

### Real-Time Hazard Notifications
- Local processing for instant alerts
- Direction-aware filtering
- Configurable warning distances
- Duplicate prevention

### Intelligent Road Safety
- Curve detection from OpenStreetMap data
- Risk classification (not speed recommendations)
- Proactive warnings before dangerous curves
- Elevation and gradient analysis

## Tech Stack

### Android App
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Hilt DI
- **Maps**: Google Maps SDK
- **Networking**: Ktor Client
- **Database**: Room (for local caching)

### Backend API
- **Language**: Python 3.11+
- **Framework**: FastAPI
- **Database**: PostgreSQL with PostGIS
- **Migrations**: Alembic
- **Auth**: JWT + Firebase Auth

### Infrastructure
- **Containerization**: Docker & Docker Compose
- **CI/CD**: GitHub Actions
- **Cloud**: AWS (EC2, S3, RDS)

## Project Structure

```
wayvigil/
├── android/                 # Android application
│   └── app/
│       └── src/main/java/com/saferoute/
│           ├── data/        # Data layer (models, remote, local)
│           ├── di/          # Dependency injection
│           ├── service/     # Background services
│           └── ui/          # UI layer (screens, viewmodels, theme)
├── backend/                 # FastAPI backend
│   └── app/
│       ├── api/v1/          # API routes
│       ├── core/            # Config, database, security
│       ├── models/          # SQLAlchemy models
│       ├── schemas/         # Pydantic schemas
│       └── services/        # Business logic
├── infrastructure/          # DevOps and deployment
│   ├── docker/              # Docker configurations
│   └── terraform/           # Infrastructure as code
└── docs/                    # Documentation
```

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Python 3.11+
- Docker & Docker Compose
- Google Maps API key

### Local Development

1. **Clone the repository**
   ```bash
   git clone https://github.com/rahmanlibi47/wayvigil.git
   cd wayvigil
   ```

2. **Start the backend**
   ```bash
   cd infrastructure/docker
   docker compose up -d
   ```

   The local Compose stack reuses the cached `docker-backend:latest` and the
   pinned local PostGIS image. It also reuses the existing `docker_postgres_data`
   volume and fails instead of pulling an image that is not already cached.

3. **Run database migrations**
   ```bash
   cd ../../backend
   poetry env use 3.11
   poetry install
   poetry run alembic upgrade head
   ```

4. **Start the backend server**
   ```bash
   poetry run uvicorn app.main:app --reload
   ```

   Run backend tests from the same `backend/` directory while the local PostGIS
   service is running. The test setup creates the `saferoute_test` database if it
   is missing and isolates each worker in its own schema:
   ```bash
   poetry lock
   poetry install
   poetry run pytest -n 4
   ```

5. **Open Android project**
   - Open `android/` folder in Android Studio
   - Add a Maps SDK for Android key to `android/local.properties` and restrict it to this Android app's package name and signing certificate:
     ```
     MAPS_API_KEY=your_api_key_here
     ```
   - For a physical device, set `API_BASE_URL` in `android/local.properties` to the development computer's LAN address, for example `http://192.168.1.20:8000`.
   - Run the app on an emulator or device

### Environment Variables

Create a `.env` file in the backend directory:

```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/saferoute
SECRET_KEY=your-secret-key-here
DEBUG=true
GOOGLE_MAPS_API_KEY=your_maps_api_key
FIREBASE_PROJECT_ID=your_firebase_project
```

For Docker-based local development, add `GOOGLE_MAPS_API_KEY` to the root `.env.local` file used by `start-local.bat`. This is the server key for Places API (New) and Routes API; restrict it to those APIs. Keep it separate from the Android Maps SDK key and never commit either key.

## API Documentation

When the backend is running, API documentation is available at:
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## Development Roadmap

### Milestone 1: Project Foundation (Weeks 1-6) ✅
- [x] Repository setup
- [x] CI/CD pipelines
- [x] Database schema
- [x] Authentication

### Milestone 2: Navigation Foundation (Weeks 7-12) 🚧
- [ ] Google Maps integration
- [ ] GPS tracking service
- [ ] Route calculation
- [ ] Turn-by-turn navigation

See [Milestone 2 scope and acceptance cases](docs/MILESTONE_2_SCOPE.md) for the implementation sequence, deferred features, and manual checks.

### Milestone 3: Hazard Reporting (Weeks 13-18)
- [ ] Reporting UI
- [ ] Media capture
- [ ] Offline queue
- [ ] Backend pipeline

### Milestone 4: AI Verification (Weeks 19-26)
- [ ] Model evaluation
- [ ] Duplicate detection
- [ ] Verification workflow

### Milestone 5: Notifications (Weeks 27-34)
- [ ] Local hazard caching
- [ ] Direction-aware filtering
- [ ] Alert system

### Milestone 6: Intelligent Curve Safety (Weeks 35-42)
- [ ] OSM data import
- [ ] Curve detection
- [ ] Risk classification

### Milestone 7: Advanced Features (Weeks 43-50)
- [ ] Weather integration
- [ ] Emergency features
- [ ] Community features

### Milestone 8: Production (Weeks 51-60)
- [ ] Performance optimization
- [ ] Security hardening
- [ ] Launch preparation

## Architecture

The project follows clean architecture principles:

- **Android**: MVVM pattern with clear separation of concerns
- **Backend**: Layered architecture with API, Service, and Data layers
- **Database**: Normalized relational model with geospatial support

See the `docs/` folder for detailed architecture documentation.

## Contributing

1. Create a branch from `main` (`git switch -c fix/short-description`).
2. Make and commit your changes on that branch.
3. Push the branch (`git push -u origin fix/short-description`).
4. Open a pull request targeting `main`.
5. Wait for Backend CI and Android CI to pass before merging.

Both CI workflows run for every pull request targeting `main` or `develop`.
Repository settings must also require pull requests and the `test` and `build`
status checks on `main`; see GitHub **Settings → Branches**. Without that branch
rule, GitHub will allow direct pushes or merges with failing checks.

## Safety Disclaimer

WayVigil AI is designed to supplement, not replace, driver judgment. Always:
- Follow traffic laws and regulations
- Maintain safe following distances
- Adjust speed based on road conditions
- Stay alert and avoid distracted driving

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Google Maps Platform for navigation services
- OpenStreetMap contributors for road geometry data
- FastAPI team for the excellent web framework
- Jetpack Compose team for modern Android UI

---

**Status**: 🚧 Under Development (Milestone 1 Complete)

For questions or support, please open an issue on GitHub.
