"""Repository foundation and CI configuration contracts."""

import tomllib
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


def test_backend_manifest_and_lockfile_are_valid():
    manifest_path = REPOSITORY_ROOT / "backend" / "pyproject.toml"
    lock_path = REPOSITORY_ROOT / "backend" / "poetry.lock"
    manifest = tomllib.loads(manifest_path.read_text(encoding="utf-8"))
    lockfile = tomllib.loads(lock_path.read_text(encoding="utf-8"))

    assert manifest["project"]["name"] == "wayvigil-ai-backend"
    assert manifest["project"]["requires-python"] == ">=3.11,<3.13"
    assert manifest["tool"]["pytest"]["ini_options"]["testpaths"] == ["tests"]
    assert manifest["tool"]["pytest"]["ini_options"]["asyncio_mode"] == "auto"
    assert lockfile["metadata"]["lock-version"]
    declared_dependencies = {
        dependency.split("[")[0].split("=")[0]
        for dependency in manifest["project"]["dependencies"]
    }
    assert {
        "fastapi",
        "sqlalchemy",
        "alembic",
        "asyncpg",
        "geoalchemy2",
        "firebase-admin",
    }.issubset(declared_dependencies)
    assert {"pytest", "pytest-asyncio", "pytest-xdist"}.issubset(
        manifest["tool"]["poetry"]["group"]["dev"]["dependencies"]
    )


def test_repository_foundation_files_exist():
    required_paths = (
        "README.md",
        "backend/pyproject.toml",
        "backend/poetry.lock",
        "backend/alembic.ini",
        "backend/alembic/env.py",
        ".github/workflows/backend-ci.yml",
        ".github/workflows/android-ci.yml",
        "android/gradlew",
    )

    assert all((REPOSITORY_ROOT / path).is_file() for path in required_paths)


def test_backend_ci_runs_database_tests_and_quality_checks():
    workflow = (
        REPOSITORY_ROOT / ".github" / "workflows" / "backend-ci.yml"
    ).read_text(encoding="utf-8")

    for required_step in (
        "push:",
        "pull_request:",
        "branches: [main, develop]",
        '"backend/**"',
        '".github/workflows/backend-ci.yml"',
        "workflow_dispatch:",
        "postgis/postgis:15-3.4",
        "--health-cmd pg_isready",
        "poetry install --no-interaction",
        "poetry run black --check app",
        "poetry run isort --check-only app",
        "poetry run flake8 app",
        "poetry run mypy app",
        "poetry run pytest -n auto",
        "codecov/codecov-action",
    ):
        assert required_step in workflow


def test_android_ci_runs_lint_unit_tests_and_build():
    workflow = (
        REPOSITORY_ROOT / ".github" / "workflows" / "android-ci.yml"
    ).read_text(encoding="utf-8")

    for required_step in (
        "push:",
        "pull_request:",
        "branches: [main, develop]",
        '"android/**"',
        '".github/workflows/android-ci.yml"',
        "workflow_dispatch:",
        'java-version: "17"',
        "ktlintCheck",
        "testDebugUnitTest",
        "compileReleaseKotlin",
        "assembleDebug",
        "actions/upload-artifact@v4",
    ):
        assert required_step in workflow


def test_local_secrets_are_ignored_but_template_can_be_tracked():
    gitignore_lines = set(
        (REPOSITORY_ROOT / ".gitignore").read_text(encoding="utf-8").splitlines()
    )

    assert ".env" in gitignore_lines
    assert ".env.*" in gitignore_lines
    assert "!.env.template" in gitignore_lines
