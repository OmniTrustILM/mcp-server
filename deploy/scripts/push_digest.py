"""Read Helm push stdout + stderr and require a unique OCI sha256 digest."""
import re
import sys


def parse(output):
    digests = set(re.findall(r'^Digest:\s+(sha256:[a-f0-9]{64})\s*$', output, re.MULTILINE))
    if len(digests) != 1:
        raise ValueError('Helm push must report exactly one unique sha256 digest')
    return digests.pop()


if __name__ == '__main__':
    print(parse(sys.stdin.read()))
