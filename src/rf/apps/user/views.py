# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division


from django.contrib.auth import authenticate, login, logout, get_user_model
from django.contrib.auth.models import User
from django.contrib.auth.forms import PasswordResetForm, SetPasswordForm
from django.contrib.auth.tokens import default_token_generator

from django.core.exceptions import ObjectDoesNotExist
from django.contrib.sites.shortcuts import get_current_site
from django.shortcuts import redirect, get_object_or_404
from django.utils.encoding import force_text
from django.utils.http import urlsafe_base64_decode

from registration.models import RegistrationProfile
from registration.forms import RegistrationFormUniqueEmail
from registration.backends.default.views import RegistrationView

from apps.core.exceptions import Forbidden
from apps.core.decorators import accepts, api_view, login_required
from apps.core.models import UserFavoriteLayer


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
    return get_user_data(request)


def get_user_data(request):
    favorites = list(UserFavoriteLayer.objects.filter(user_id=request.user.id)
                     .values_list('layer_id', flat=True))
    return {
        'id': request.user.id,
        'username': request.user.username,
        'favorites': favorites,
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
@accepts('POST')
def reset_password(request):
    # Adapted from contrib.auth.views.password_reset_confirm
    UserModel = get_user_model()
    errors = []
    uidb64 = None
    token = None

    if 'uidb64' in request.POST:
        uidb64 = request.POST['uidb64']
    else:
        errors.append('UID was not provided')

    if 'token' in request.POST:
        token = request.POST['token']
    else:
        errors.append('Token was not provided')

    if uidb64 and token:
        try:
            # urlsafe_base64_decode() decodes to bytestring on Python 3
            uid = force_text(urlsafe_base64_decode(uidb64))
            user = UserModel._default_manager.get(pk=uid)
        except (TypeError, ValueError, OverflowError, UserModel.DoesNotExist):
            user = None

        if user and default_token_generator.check_token(user, token):
            form = SetPasswordForm(user, request.POST)
            if form.is_valid():
                form.save()
            elif 'password_mismatch' in form.errors:
                errors.append('Passwords do not match')
            else:
                # At the moment, password_mismatch is the only possible error
                # but this is to catch any other errors that could be added
                # in the future.
                errors.append('Password is invalid')
        else:
            errors.append('Reset URL is invalid')

    if errors:
        raise Forbidden(errors={
            'all': errors
        })
    else:
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
