"""Render the repository's standalone chart with public synthetic values."""
import os
from pathlib import Path
import subprocess
import tempfile

import yaml

ROOT = Path(__file__).resolve().parents[2]


def render(chart, values):
    return render_path(ROOT / 'deploy' / 'charts' / chart, values)


def render_path(chart_path, values):
    with tempfile.TemporaryDirectory(prefix='ilm-mcp-chart-test-') as temp:
        inputs = Path(temp) / 'values.yaml'
        inputs.write_text(yaml.safe_dump(values))
        result = subprocess.run([
            os.environ.get('HELM_BIN', 'helm'), 'template', 'ilm-test',
            str(chart_path), '--namespace', 'ilm-test',
            '-f', str(inputs),
        ], capture_output=True, text=True)
    if result.returncode:
        raise AssertionError(result.stderr)
    return [obj for obj in yaml.safe_load_all(result.stdout) if obj]
