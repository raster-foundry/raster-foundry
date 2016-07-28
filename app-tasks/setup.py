#!/usr/bin/env python

from __future__ import absolute_import
from __future__ import print_function

import io
from glob import glob
from os.path import (
    abspath,
    basename,
    dirname,
    join,
    splitext,
)
from setuptools import find_packages
from setuptools import setup

here = abspath(dirname(__file__))

def read(*names, **kwargs):
    return io.open(
        join(dirname(__file__), *names),
        encoding=kwargs.get('encoding', 'utf8')
    ).read()

# Test Requirements
with open(join(here, 'requirements.test.txt')) as f:
    test_reqs = f.read().split('\n')


# Installation Requirements
with open(join(here, 'requirements.txt')) as f:
    all_reqs = f.read().split('\n')


install_requires = [x.strip() for x in all_reqs if 'git+' not in x]
tests_require = [x.strip() for x in test_reqs if 'git+' not in x]
dependency_links = [x.strip().replace('git+', '') for x in all_reqs if 'git+' not in x]


setup(
    name='rf',
    version='0.1.0',
    license='Apache',
    description='Asynchronous tasks/workflows for Raster Foundry',
    author='Azavea',
    author_email='systems+rf@azavea.com',
    url='https://github.com/azavea/raster-foundry',
    packages=find_packages('src'),
    package_dir={'': 'src'},
    py_modules=[splitext(basename(path))[0] for path in glob('src/*.py')],
    include_package_data=True,
    zip_safe=False,
    classifiers=[
        # complete classifier list: http://pypi.python.org/pypi?%3Aaction=list_classifiers
        'Development Status :: 5 - Production/Stable',
        'Intended Audience :: Developers',
        'License :: OSI Approved :: BSD License',
        'Operating System :: Unix',
        'Operating System :: POSIX',
        'Operating System :: Microsoft :: Windows',
        'Programming Language :: Python',
        'Programming Language :: Python :: 2.7',
        'Programming Language :: Python :: 3',
        'Programming Language :: Python :: 3.3',
        'Programming Language :: Python :: 3.4',
        'Programming Language :: Python :: 3.5',
        'Programming Language :: Python :: Implementation :: CPython',
        'Programming Language :: Python :: Implementation :: PyPy',
        # uncomment if you test on these interpreters:
        # 'Programming Language :: Python :: Implementation :: IronPython',
        # 'Programming Language :: Python :: Implementation :: Jython',
        # 'Programming Language :: Python :: Implementation :: Stackless',
        'Topic :: Utilities',
    ],
    keywords=[
        # eg: 'keyword1', 'keyword2', 'keyword3',
    ],
    install_requires=install_requires,
    dependency_links=['https://github.com/boto/botoflow/tarball/bdb87b59226e249a1f8bf4f8914e44815dcffcab#egg=botoflow-8.0'],
    tests_require=tests_require,
    setup_requires=['pytest-runner'],
    extras_require={
        # eg:
        #   'rst': ['docutils>=0.11'],
        #   ':python_version=="2.6"': ['argparse'],
    },
    entry_points={
        'console_scripts': [
            'rf = rf.cli:cli',
        ]
    },
)
