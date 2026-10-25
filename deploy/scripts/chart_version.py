"""Validate source chart versions and resolve immutable publication versions."""
import argparse
from pathlib import Path
import re

import yaml


def resolve(source, ref_type, ref_name, sha):
    if ref_type == 'tag':
        if not re.fullmatch(r'\d+\.\d+\.\d+', source) or ref_name not in (source, f'v{source}'):
            raise ValueError('release tags must match the stable Chart.yaml version (optional v prefix)')
        return source
    if ref_type != 'branch' or ref_name != 'main':
        raise ValueError('development publication is restricted to main')
    if not re.fullmatch(r'\d+\.\d+\.\d+-develop', source):
        raise ValueError('main must use a <major>.<minor>.<patch>-develop source chart version')
    if not re.fullmatch(r'[a-f0-9]{40}', sha):
        raise ValueError('an immutable development version requires the full commit SHA')
    return f'{source}.{sha}'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ref-type', required=True, choices=['branch', 'tag'])
    parser.add_argument('--ref-name', required=True)
    parser.add_argument('--sha', required=True)
    args = parser.parse_args()
    chart_path = Path(__file__).resolve().parents[1] / 'charts/mcp-server/Chart.yaml'
    with chart_path.open(encoding='utf-8') as chart:
        source = yaml.safe_load(chart)['version']
    print(resolve(source, args.ref_type, args.ref_name, args.sha))


if __name__ == '__main__':
    main()
