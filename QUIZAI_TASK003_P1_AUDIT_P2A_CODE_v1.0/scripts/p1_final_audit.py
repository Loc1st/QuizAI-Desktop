#!/usr/bin/env python3
"""Read-only P1 final audit of a local QuizAI-Desktop repository.

No Gradle, network, DB, environment reads or file writes. It parses only named files.
Exit 0 on PASS, 1 on FAIL, 2 if required files unavailable.
"""
import argparse
import hashlib
import re
from pathlib import Path

ERD_SHA = '5ff37a672d9a76ce4092301f53064a850304fed0ace74d8f71fa9db766aa31ab'
try:
    import yaml  # optional, PyYAML
except ImportError:
    yaml = None


def dig(mapping, *path):
    out = mapping
    for segment in path:
        if not isinstance(out, dict):
            return None
        out = out.get(segment)
    return out


def report(root):
    errors, warnings, checks = [], [], []

    def read(path):
        p = root / path
        if not p.is_file():
            errors.append(f'MISSING: {path}')
            return None
        return p.read_text(encoding='utf-8-sig')

    erd = root / 'docs/QUIZAI_DB05_ERD_FINAL_FOR_APPROVAL_v1.0.md'
    if not erd.exists():
        errors.append('ERD approved file missing')
    elif hashlib.sha256(erd.read_bytes()).hexdigest() != ERD_SHA:
        errors.append('ERD SHA-256 mismatch: STOP before making any source change')
    else:
        checks.append('ERD source checksum verified')

    gradle = read('quizai-server/build.gradle')
    if gradle:
        needed = ['spring-boot-starter-jdbc', 'spring-boot-starter-flyway',
                  'org.postgresql:postgresql', 'flyway-database-postgresql']
        for dep in needed:
            if dep not in gradle:
                errors.append('Missing backend dependency: ' + dep)
        for forbidden in ('spring-boot-starter-data-jpa', 'hibernate-core'):
            if forbidden in gradle:
                errors.append('JPA present in P1: ' + forbidden)
        checks.append('P1 dependency names reviewed (not resolved)')

    settings = read('settings.gradle')
    if settings and 'quizai-server' not in settings:
        errors.append('settings.gradle does not include quizai-server')

    paths = {
        'base': 'quizai-server/src/main/resources/application.yml',
        'dev': 'quizai-server/src/main/resources/application-dev.yml',
        'test': 'quizai-server/src/test/resources/application-test.yml',
    }
    content = {key: read(path) for key, path in paths.items()}
    configs = {}
    if yaml is None:
        warnings.append('PyYAML not installed: YAML parse skipped; text safety checks still run')
    else:
        for name, value in content.items():
            if value is None:
                continue
            try:
                configs[name] = list(yaml.safe_load_all(value))
                if not all(x is None or isinstance(x, dict) for x in configs[name]):
                    errors.append(f'{name}: YAML document is not a mapping')
            except yaml.YAMLError as exc:
                errors.append(f'{name}: invalid YAML: {exc}')

    base = content['base'] or ''
    dev = content['dev'] or ''
    test = content['test'] or ''
    if re.search(r'(?m)^\s*active\s*:\s*(dev|test)\s*$', base):
        errors.append('Default application.yml activates DEV or TEST automatically')
    if 'DataSourceAutoConfiguration' not in base:
        errors.append('Default profile does not visibly disable DataSourceAutoConfiguration')
    if 'FlywayAutoConfiguration' not in base:
        errors.append('Default profile does not visibly disable FlywayAutoConfiguration')

    if configs:
        base_docs = [d for d in configs.get('base', []) if isinstance(d, dict)]
        if not any(dig(d, 'spring','flyway','enabled') is False for d in base_docs):
            errors.append('Missing spring.flyway.enabled=false in default config')
        if not any(dig(d,'spring','sql','init','mode') == 'never' for d in base_docs):
            errors.append('Missing spring.sql.init.mode=never in default config')
        if not any(dig(d,'spring','jpa','hibernate','ddl-auto') in ('none','validate') for d in base_docs):
            errors.append('Missing safe ddl-auto value in default config')
        for name in ('dev','test'):
            docs = [d for d in configs.get(name, []) if isinstance(d, dict)]
            if not docs:
                continue
            if not any(dig(d,'spring','flyway','enabled') is False for d in docs):
                errors.append(f'{name}: Flyway not explicitly disabled')
            if not any(dig(d,'spring','sql','init','mode') == 'never' for d in docs):
                errors.append(f'{name}: SQL init not explicitly disabled')
            if not any(dig(d,'spring','jpa','hibernate','ddl-auto') in ('none','validate') for d in docs):
                errors.append(f'{name}: ddl-auto is not none/validate')
            dss = [dig(d,'spring','datasource') for d in docs]
            datasource = next((d for d in dss if isinstance(d,dict)), {})
            for key in ('url','username','password'):
                value = datasource.get(key)
                prefix = 'QUIZAI_DEV_DB_' if name == 'dev' else 'QUIZAI_TEST_DB_'
                required = prefix + {'url':'URL','username':'USER','password':'PASSWORD'}[key]
                if not isinstance(value,str) or value != '${'+required+'}':
                    errors.append(f'{name}: datasource.{key} must be exactly ${{{required}}}')

    for name, blob, desired, other in [
        ('dev',dev,'QUIZAI_DEV_DB_','QUIZAI_TEST_DB_'),
        ('test',test,'QUIZAI_TEST_DB_','QUIZAI_DEV_DB_'),
    ]:
        if desired not in blob:
            errors.append(f'{name}: expected dedicated environment placeholders')
        if other in blob:
            errors.append(f'{name}: references the wrong DB environment variables')
        if re.search(r'(?im)^\s*password\s*:\s*(?!\$\{)[^#\s].*$', blob):
            errors.append(f'{name}: possible hardcoded DB password')
    checks.append('Default profile and DEV/TEST isolation statically reviewed')
    checks.append('No DB connection, Gradle, test or startup executed')
    return checks, warnings, errors


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--repo', type=Path, required=True, help='QuizAI-Desktop local repo root')
    args = ap.parse_args()
    if not args.repo.is_dir():
        ap.error('Repository path not found')
    checks, warnings, errors = report(args.repo)
    for line in checks:
        print('CHECK:', line)
    for line in warnings:
        print('WARN:', line)
    for line in errors:
        print('FAIL:', line)
    print('P1 FINAL STATIC AUDIT:', 'PASS' if not errors else 'FAIL',
          f'({len(errors)} errors, {len(warnings)} warnings)')
    raise SystemExit(1 if errors else 0)

if __name__ == '__main__':
    main()
