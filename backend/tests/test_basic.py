"""Basic tests that don't require external dependencies."""


def test_basic_math():
    """Test basic math works."""
    assert 1 + 1 == 2


def test_string_operations():
    """Test string operations."""
    assert "hello".upper() == "HELLO"


def test_list_operations():
    """Test list operations."""
    items = [1, 2, 3]
    assert len(items) == 3
    assert sum(items) == 6
