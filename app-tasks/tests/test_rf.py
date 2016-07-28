
from click.testing import CliRunner

from rf.cli import cli


def test_main():
    runner = CliRunner()
    result = runner.invoke(cli, [])

    assert result.exit_code == 0
