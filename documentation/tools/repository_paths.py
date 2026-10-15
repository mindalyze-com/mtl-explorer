"""Locate the optional private maintainer checkout."""

import os
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[2]
PRIVATE_REPOSITORY_NAME = "mtl-explorer-private"
PRIVATE_REPOSITORY_ENV = "MTL_PRIVATE_REPO"


def private_repository(repository=REPOSITORY):
    configured = os.environ.get(PRIVATE_REPOSITORY_ENV)
    root = Path(configured).expanduser() if configured else repository.parent / PRIVATE_REPOSITORY_NAME
    root = root.resolve()
    if root.is_relative_to(repository.resolve()):
        raise ValueError("The private repository must be a separate checkout outside the application repository")
    return root
