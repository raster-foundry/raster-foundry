rf
==

A python client for Raster Foundry -- a web platform for combining, analyzing, and publishing raster data.

Usage
-----

.. code-block:: python

   from rf.api import API
   refresh_token = '<>'

   api = API(refresh_token=refresh_token)

   # List all projects
   my_projects = api.projects

   one_project = my_projects[0]

   # Get TMS URl without token
   one_project.tms()


Installation
------------
``python setup.py install``

Requirements
^^^^^^^^^^^^
See Python 2.7+ or Python 3.4+

License
-------

Apache License, Version 2
