#!/usr/bin/env python3
"""Write fabric/ata-test-times.properties from CI's ATA unit test results, so the shards stay balanced.

    gh run download <run id> -R aurelia-works/Aurelia-Transit-Railway -p 'ata-unit-results-*' -D /tmp/results
    python3 tools/ci/ata_test_times.py /tmp/results

Reads every JUnit XML under the given folders and writes each outer test class's time in whole seconds (nested classes
count towards their outer class). fabric/build.gradle deals the classes out to the ata-unit shards longest first.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'fabric', 'ata-test-times.properties')


def main(folders):
    times = {}
    for folder in folders:
        for path in glob.glob(os.path.join(folder, '**', '*.xml'), recursive=True):
            suite = ET.parse(path).getroot()
            name = suite.get('name', '').split('$')[0].replace('.', '/')
            if name:
                times[name] = times.get(name, 0.0) + float(suite.get('time', 0))
    if not times:
        sys.exit('no JUnit XML found under ' + ', '.join(folders))
    with open(OUT, 'w') as out:
        out.write('# ATA unit test class -> seconds on CI, from tools/ci/ata_test_times.py. fabric/build.gradle balances the\n')
        out.write('# ata-unit shards with it; classes missing here go to a shard by name hash.\n')
        for name in sorted(times):
            out.write('%s=%d\n' % (name, max(1, round(times[name]))))
    print('wrote', len(times), 'classes,', round(sum(times.values())), 's in all')


if __name__ == '__main__':
    main(sys.argv[1:] or sys.exit(__doc__))
