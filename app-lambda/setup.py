#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""The setup script."""

from setuptools import setup, find_packages

import rflambda


with open('README.rst') as readme_file:
    readme = readme_file.read()

requirements = []

setup_requirements = ['pytest-runner', ]

test_requirements = ['pytest', ]

setup(
    author="Raster Foundry",
    author_email='info@rasterfoundry.com',
    classifiers=[
        'Development Status :: 2 - Pre-Alpha',
        'Intended Audience :: Developers',
        'License :: OSI Approved :: Apache Software License',
        'Natural Language :: English',
        'Programming Language :: Python :: 3.6'
    ],
    description="Lambda functions supporting the Raster Foundry web application.",
    install_requires=[
        "pyproj>=1.9.5.1",
        "rasterfoundry>=1.16.0",
        "shapely>=1.6.4.post2",
        "mypy>=v0.650"
    ],
    license="Apache Software License 2.0",
    long_description=readme,
    include_package_data=True,
    keywords='raster earth-observation geospatial geospatial-processing',
    name='rflambda',
    packages=find_packages(include=['rflambda']),
    setup_requires=setup_requirements,
    test_suite='tests',
    tests_require=test_requirements,
    url='https://github.com/raster-foundry/raster-foundry',
    version=rflambda.__version__,
    zip_safe=False,
)
