# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.models import User
from django.contrib.auth.forms import PasswordResetForm
from django.core.exceptions import ObjectDoesNotExist
from django.contrib.sites.shortcuts import get_current_site
from django.shortcuts import redirect, get_object_or_404

from registration.models import RegistrationProfile
from registration.forms import RegistrationFormUniqueEmail
from registration.backends.default.views import RegistrationView

from apps.core.exceptions import Forbidden
from apps.core.decorators import accepts, api_view, login_required


@api_view
@accepts('GET', 'POST')
def login_view(request):
    if request.method == 'GET':
        return _user_info(request)
    elif request.method == 'POST':
        return _authenticate_user(request)


def _authenticate_user(request):
    user = authenticate(username=request.POST.get('username'),
                        password=request.POST.get('password'))
    if user:
        if user.is_active:
            login(request, user)
            return {
                'id': user.id,
                'username': user.username,
            }
        else:
            raise Forbidden(errors={
                'all': ['Please activate your account'],
            })
    raise Forbidden(errors={
        'all': ['Invalid username or password'],
    })


@login_required
def _user_info(request):
    return {
        'id': request.user.id,
        'username': request.user.username,
    }


@api_view
@accepts('GET')
def logout_view(request):
    logout(request)
    return 'OK'


@api_view
@accepts('POST')
def sign_up(request):
    view = RegistrationView()
    form = RegistrationFormUniqueEmail(request.POST)
    if form.is_valid():
        user = view.register(request, form)
        return {
            'user_id': user.id
        }
    else:
        errors = []

        if 'username' not in form.cleaned_data:
            errors.append('Username is invalid or already in use')
        if 'password1' not in form.cleaned_data:
            errors.append('Password must be specified')
        if 'password2' not in form.cleaned_data or \
           form.cleaned_data['password1'] != form.cleaned_data['password2']:
            errors.append('Passwords do not match')
        if 'email' not in form.cleaned_data:
            errors.append('Email is invalid or already in use')

        if len(errors) == 0:
            errors.append('Invalid data submitted')

        raise Forbidden(errors={
            'all': errors
        })


@api_view
@accepts('POST')
def resend(request):
    """
    Resend activation email if the key hasn't expired.
    """
    form = PasswordResetForm(request.POST)
    if form.is_valid():
        email = form.cleaned_data['email']
        try:
            profile = RegistrationProfile.objects.get(user__email=email)
            profile.send_activation_email(get_current_site(request))
        except ObjectDoesNotExist:
            pass
    return 'OK'


@api_view
@accepts('POST')
def forgot(request):
    form = PasswordResetForm(request.POST)
    if form.is_valid():
        form.save(request=request)
    return 'OK'


@api_view
@accepts('GET')
def activate(request, activation_key):
    profile = get_object_or_404(RegistrationProfile,
                                activation_key=activation_key)
    user = get_object_or_404(User, id=profile.user_id)

    if user.is_active:
        return redirect('login')

    user.is_active = True
    user.save()

    # Backend needs to be added, for login without a password.
    # http://cheng.logdown.com/posts/2015/06/03/django-login-users-without-password
    user.backend = 'django.contrib.auth.backends.ModelBackend'

    login(request, user)
    return redirect('home_page')
