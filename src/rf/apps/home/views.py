# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib.auth.models import User
from django.shortcuts import render_to_response
from rest_framework import serializers, viewsets

def home_page(request):
    return render_to_response('home/home.html')
