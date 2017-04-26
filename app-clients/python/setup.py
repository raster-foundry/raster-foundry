import setuptools

setuptools.setup(
    name="rf",
    version="0.0.1",
    url="https://github.com/azavea/raster-foundry",

    author="Raster Foundry",
    author_email="info@rasterfoundry.com",

    description="A python client for Raster Foundry -- a web platform for combining, analyzing, and publishing raster data.",
    long_description=open('README.rst').read(),

    packages=setuptools.find_packages(),

    package_data={'': ['*.yml']},

    install_requires=[
        'cryptography>=1.3.2',
        'pyasn1>=0.2.3',
        'requests>=2.9.1',
        'bravado>=8.4.0'
    ],

    classifiers=[
        'Development Status :: 2 - Pre-Alpha',
        'Programming Language :: Python',
        'Programming Language :: Python :: 2',
        'Programming Language :: Python :: 2.7',
        'Programming Language :: Python :: 3',
        'Programming Language :: Python :: 3.4',
        'Programming Language :: Python :: 3.5',
    ],
)
