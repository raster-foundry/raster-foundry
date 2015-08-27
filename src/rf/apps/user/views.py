# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib.auth import (authenticate,
                                 logout as auth_logout,
                                 login as auth_login)
from django.shortcuts import redirect
from django.contrib.auth.forms import PasswordResetForm
from django.core.exceptions import ObjectDoesNotExist
from django.contrib.sites.shortcuts import get_current_site

from registration.models import RegistrationProfile
from registration.forms import RegistrationFormUniqueEmail
from registration.backends.default.views import RegistrationView

from rest_framework import decorators, status
from rest_framework.response import Response
from rest_framework.permissions import AllowAny


@decorators.api_view(['POST', 'GET'])
@decorators.permission_classes((AllowAny, ))
def login(request):
    response_data = {}
    status_code = status.HTTP_200_OK

    if request.method == 'POST':
        user = authenticate(username=request.REQUEST.get('username'),
                            password=request.REQUEST.get('password'))

        if user is not None:
            if user.is_active:
                auth_login(request, user)
                response_data = {
                    'result': 'success',
                    'username': user.username,
                    'logged_in': True,
                    'id': user.id
                }
            else:
                response_data = {
                    'errors': ['Please activate your account'],
                    'logged_in': False,
                    'id': 0
                }
                status_code = status.HTTP_400_BAD_REQUEST
        else:
            response_data = {
                'errors': ['Invalid username or password'],
                'logged_in': False,
                'id': 0
            }
            status_code = status.HTTP_400_BAD_REQUEST

    elif request.method == 'GET':
        user = request.user

        if user.is_authenticated() and user.is_active:
            response_data = {
                'result': 'success',
                'username': user.username,
                'logged_in': True,
                'id': user.id
            }
        else:
            response_data = {
                'result': 'success',
                'logged_in': False,
                'id': 0
            }

        status_code = status.HTTP_200_OK

    return Response(data=response_data, status=status_code)


@decorators.api_view(['GET'])
@decorators.permission_classes((AllowAny, ))
def logout(request):
    auth_logout(request)

    if request.is_ajax():
        response_data = {
            'result': 'success',
            'logged_in': False,
            'id': 0
        }
        return Response(data=response_data)
    else:
        return redirect('/')


@decorators.api_view(['POST'])
@decorators.permission_classes((AllowAny, ))
def sign_up(request):
    view = RegistrationView()
    form = RegistrationFormUniqueEmail(request.POST)

    if form.is_valid():
        user = view.register(request, form)
        response_data = {'result': 'success',
                         'username': user.username,
                         'logged_in': True}
        return Response(data=response_data,
                        status=status.HTTP_200_OK)
    else:
        errors = []
        if 'username' not in form.cleaned_data:
            errors.append("Username is invalid or already in use")
        if 'password1' not in form.cleaned_data:
            errors.append("Password must be specified")
        if 'password2' not in form.cleaned_data or \
           form.cleaned_data['password1'] != form.cleaned_data['password2']:
            errors.append("Passwords do not match")
        if 'email' not in form.cleaned_data:
            errors.append("Email is invalid or already in use")

        if len(errors) == 0:
            errors.append("Invalid data submitted")

        response_data = {"errors": errors}
        return Response(data=response_data,
                        status=status.HTTP_400_BAD_REQUEST)


@decorators.api_view(['POST'])
@decorators.permission_classes((AllowAny, ))
def resend(request):
    # Resend activation email if the key hasn't expired.
    form = PasswordResetForm(request.POST)
    if form.is_valid():
        email = form.cleaned_data['email']
        try:
            registration_profile = RegistrationProfile.objects.get(
                user__email=email)
            if registration_profile.activation_key_expired():
                response_data = {'errors': ["Activation key expired"]}
                status_code = status.HTTP_400_BAD_REQUEST
            else:
                registration_profile.send_activation_email(
                    get_current_site(request))
                response_data = {'result': 'success'}
                status_code = status.HTTP_200_OK
        except ObjectDoesNotExist:
            response_data = {'errors': ["Email cannot be found"]}
            status_code = status.HTTP_400_BAD_REQUEST
    else:
        response_data = {'errors': ["Email is invalid"]}
        status_code = status.HTTP_400_BAD_REQUEST

    return Response(data=response_data, status=status_code)


@decorators.api_view(['POST'])
@decorators.permission_classes((AllowAny, ))
def forgot(request):
    form = PasswordResetForm(request.POST)

    if form.is_valid():
        email = form.cleaned_data['email']
        try:
            # If there are active user(s) that match email
            next(form.get_users(email))
            form.save(request=request)
            response_data = {'result': 'success',
                             'logged_in': False}
            status_code = status.HTTP_200_OK
        except StopIteration:
            response_data = {'errors': ["Email cannot be found"]}
            status_code = status.HTTP_400_BAD_REQUEST
    else:
        response_data = {'errors': ["Email is invalid"]}
        status_code = status.HTTP_400_BAD_REQUEST

    return Response(data=response_data, status=status_code)
