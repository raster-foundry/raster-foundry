# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib.auth.models import User
from django.test import TestCase

from rest_framework.test import APIClient

from apps.core.models import LayerMeta, Layer, UserFavoriteLayer
from apps.core.serializers import LayerSerializer


class AbstractLayerTestCase(TestCase):
    def setUp(self):
        self.c = APIClient()

        self.logged_in_user = 'bob'
        self.other_user = 'steve'
        self.invalid_user = 'mike'
        self.usernames = [self.logged_in_user, self.other_user]
        self.save_users()
        self.setup_models()
        self.c.login(username=self.logged_in_user,
                     password=self.logged_in_user)

    def save_user(self, username):
        email = '%s@%s.com' % (username, username)
        return User.objects.create_user(username=username,
                                        email=email,
                                        password=username)

    def save_users(self):
        self.user_models = {}
        for username in self.usernames:
            self.user_models[username] = self.save_user(username)

    def make_layer(self, layer_name, username, is_public=False):
        layer = {
            'name': layer_name,
            'is_public': is_public,
            'capture_start': '2015-08-15',
            'capture_end': '2015-08-15',
            'layer_images': [
                'http://www.foo.com',
                'http://www.bar.com',
            ],
            'layer_tags': [
                layer_name
            ],
        }
        return layer

    def save_layer(self, layer, user):
        class MockRequest:
            def __init__(self, user):
                self.user = user

        # The context is needed because the LayerSerializer uses
        # CurrentUserDefault which initializes the
        # the user from the request object.
        context = {'request': MockRequest(user)}
        layer_serializer = LayerSerializer(data=layer,
                                           context=context)
        layer_serializer.is_valid()
        return layer_serializer.save()

    def setup_models(self):
        self.layer_models = {}
        for username in self.usernames:
            user = self.user_models[username]
            layer_name = username + ' Public Layer'
            layer = self.make_layer(layer_name, username, is_public=True)
            public_layer = self.save_layer(layer, user)

            layer_name = username + ' Private Layer'
            layer = self.make_layer(layer_name, username, is_public=False)
            private_layer = self.save_layer(layer, user)

            self.layer_models[username] = {
                'public': public_layer,
                'private': private_layer,
            }


class LayerTestCase(AbstractLayerTestCase):
    # Create
    def test_create_layer(self):
        layer = self.make_layer('Test Layer', self.logged_in_user)
        response = self.c.post('/user/%s/layers/' % (self.logged_in_user,),
                               layer,
                               format='json')
        self.assertEqual(response.status_code, 201)
        self.assertEqual(len(response.data['layer_images']), 2)
        self.assertEqual(len(response.data['layer_tags']), 1)

        self.assertEqual(len(Layer.objects.filter(name='Test Layer')), 1)

    def test_create_layer_no_permission(self):
        layer = self.make_layer('Test Layer', self.other_user)
        response = self.c.post('/user/%s/layers/' % (self.other_user,),
                               layer,
                               format='json')
        self.assertEqual(response.status_code, 404)

    # List
    def test_list_all_layers(self):
        response = self.c.get('/layers/',
                              format='json')
        self.assertEqual(response.status_code, 200)
        # 2 layers from logged_in_user, and 1 public layer from other_user
        self.assertEqual(len(response.data), 3)

    def get_list_url(self, username):
        return '/user/%s/layers/' % (username,)

    def test_list_layers_from_logged_in_user(self):
        response = self.c.get(self.get_list_url(self.logged_in_user),
                              format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 2)

    def test_list_layers_from_other_user(self):
        response = self.c.get(self.get_list_url(self.other_user),
                              format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)

    def test_list_layers_from_invalid_user(self):
        response = self.c.get(self.get_list_url(self.invalid_user),
                              format='json')
        self.assertEqual(response.status_code, 404)

    # Filtering and Sorting
    def test_list_layers_with_filter_by_name_from_logged_in_user(self):
        response = self.c.get('/user/%s/layers/?name=%s+Public+Layer' %
                              (self.logged_in_user, self.logged_in_user),
                              format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['name'],
                         '%s Public Layer' % (self.logged_in_user,))

    def test_list_layers_with_filter_by_tag_from_logged_in_user(self):
        response = self.c.get('/user/%s/layers/?tag=%s+Public+Layer' %
                              (self.logged_in_user, self.logged_in_user),
                              format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['name'],
                         '%s Public Layer' % (self.logged_in_user,))

        response = self.c.get('/user/%s/layers/?tag=invalid+tag' %
                              (self.logged_in_user,),
                              format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 0)

    def test_list_layers_with_sorting_from_logged_in_user(self):
        response = self.c.get('/user/%s/layers/?ordering=name' %
                              (self.logged_in_user,),
                              format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 2)
        self.assertEqual(response.data[0]['name'],
                         '%s Private Layer' % (self.logged_in_user,))
        self.assertEqual(response.data[1]['name'],
                         '%s Public Layer' % (self.logged_in_user,))

    # Retrieve
    def test_retrieve_layer_from_logged_in_user(self):
        response = self.c.get('/user/%s/layers/%s-public-layer/' %
                              (self.logged_in_user, self.logged_in_user),
                              format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data['name'],
                         '%s Public Layer' % (self.logged_in_user,))

    def test_retrieve_invalid_layer_from_logged_in_user(self):
        response = self.c.get('/user/%s/layers/invalid-layer/' %
                              (self.logged_in_user,),
                              format='json')
        self.assertEqual(response.status_code, 404)

    def test_retrieve_public_layer_from_other_user(self):
        response = self.c.get('/user/%s/layers/%s-public-layer/' %
                              (self.other_user, self.other_user),
                              format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data['name'],
                         '%s Public Layer' % (self.other_user,))

    def test_retrieve_private_layer_from_other_user(self):
        response = self.c.get('/user/%s/layers/%s-private-layer/' %
                              (self.other_user, self.other_user),
                              format='json')
        self.assertEqual(response.status_code, 404)

    # Retrieve LayerMeta
    def test_retrieve_meta_from_logged_in_user(self):
        response = self.c.get('/user/%s/layers/%s-public-layer/meta/' %
                              (self.logged_in_user, self.logged_in_user),
                              format='json')
        self.assertEqual(response.status_code, 404)

        response = self.c.get('/user/%s/layers/%s-public-layer/' %
                              (self.logged_in_user, self.logged_in_user),
                              format='json')

        layer_id = response.data['id']
        layer = Layer.objects.get(pk=layer_id)
        LayerMeta.objects.create(layer=layer, state='IN PROGRESS')
        LayerMeta.objects.create(layer=layer, state='DONE')

        response = self.c.get('/user/%s/layers/%s-public-layer/meta/' %
                              (self.logged_in_user, self.logged_in_user),
                              format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data['state'], 'DONE')

    # Destroy
    def test_destroy_layer_from_logged_in_user(self):
        response = self.c.delete('/user/%s/layers/%s-public-layer/' %
                                 (self.logged_in_user, self.logged_in_user),
                                 format='json')
        self.assertEqual(response.status_code, 204)

    def test_destroy_invalid_layer_from_logged_in_user(self):
        response = self.c.delete('/user/%s/layers/invalid-layer/' %
                                 (self.logged_in_user,),
                                 format='json')
        self.assertEqual(response.status_code, 404)

    def test_destroy_layer_from_other_user(self):
        response = self.c.delete('/user/%s/layers/%s-public-layer/' %
                                 (self.other_user, self.other_user),
                                 format='json')
        self.assertEqual(response.status_code, 401)

        response = self.c.delete('/user/%s/layers/%s-private-layer/' %
                                 (self.other_user, self.other_user),
                                 format='json')
        self.assertEqual(response.status_code, 404)


class FavoriteTestCase(AbstractLayerTestCase):
    def setup_models(self):
        super(FavoriteTestCase, self).setup_models()
        self.setup_favorites()

    def save_favorite(self, layer, user):
        return UserFavoriteLayer.objects.create(layer=layer, user=user)

    def setup_favorites(self):
        # logged_in_user favorites other_user's public layer
        self.save_favorite(self.layer_models[self.other_user]['public'],
                           self.user_models[self.logged_in_user])

    # Create
    def test_create_favorite_from_other_user_public_layer(self):
        response = self.c.post('/user/%s/layers/%s-public-layer/favorite/' %
                               (self.other_user, self.other_user),
                               format='json')
        self.assertEqual(response.status_code, 201)

        favorites = UserFavoriteLayer.objects.filter(
            user__username=self.logged_in_user)

        for favorite in favorites:
            self.assertEqual(favorite.user,
                             self.user_models[self.logged_in_user])
            self.assertEqual(favorite.layer,
                             self.layer_models[self.other_user]['public'])

    def test_create_favorite_from_other_user_private_layer(self):
        response = self.c.post('/user/%s/layers/%s-private-layer/favorite/' %
                               (self.other_user, self.other_user),
                               format='json')
        self.assertEqual(response.status_code, 404)

    # List
    def get_list_url(self, username):
        return '/user/%s/favorites/' % (username,)

    def test_list_favorites_from_logged_in_user(self):
        response = self.c.get(self.get_list_url(self.logged_in_user),
                              format='json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)

    def test_list_favorites_from_other_user(self):
        response = self.c.get(self.get_list_url(self.other_user),
                              format='json')
        self.assertEqual(response.status_code, 404)

    # Destroy
    def test_destroy_favorite_from_logged_in_user(self):
        response = self.c.delete('/user/%s/layers/%s-private-layer/favorite/' %
                                 (self.other_user, self.other_user),
                                 format='json')
        self.assertEqual(response.status_code, 404)

    def test_destroy_invalid_favorite_from_logged_in_user(self):
        response = self.c.delete('/user/%s/layers/invalid-layer/favorite/' %
                                 (self.logged_in_user,),
                                 format='json')
        self.assertEqual(response.status_code, 404)

    def test_destroy_favorite_from_other_user(self):
        response = self.c.delete('/user/%s/layers/%s-public-layer/favorite/' %
                                 (self.other_user, self.other_user),
                                 format='json')
        self.assertEqual(response.status_code, 204)
