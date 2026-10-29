"""Validate the source chart version and resolve the version to publish."""
import argparse
from pathlib import Path
import re

import yaml


def resolve(source, ref_type, ref_name):
    if ref_type == 'tag':
        if not re.fullmatch(r'\d+\.\d+\.\d+', source) or ref_name not in (source, f'v{source}'):
            raise ValueError('release tags must match the stable Chart.yaml version (optional v prefix)')
        return source
    if ref_type != 'branch' or ref_name != 'main':
        raise ValueError('development publication is restricted to main')
    if not re.fullmatch(r'\d+\.\d+\.\d+-develop', source):
        raise ValueError('main must use a <major>.<minor>.<patch>-develop source chart version')
    return source


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ref-type', required=True, choices=['branch', 'tag'])
    parser.add_argument('--ref-name', required=True)
    args = parser.parse_args()
    chart_path = Path(__file__).resolve().parents[1] / 'charts/mcp-server/Chart.yaml'
    with chart_path.open(encoding='utf-8') as chart:
        source = yaml.safe_load(chart)['version']
    print(resolve(source, args.ref_type, args.ref_name))


if __name__ == '__main__':
    main()
