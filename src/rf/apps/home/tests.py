# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib.auth.models import User
from django.test import TestCase
from django.test.client import Client
from django.core.urlresolvers import reverse

from apps.core.models import Layer, LayerMeta, UserFavoriteLayer


class AbstractLayerTestCase(TestCase):
    def setUp(self):
        self.client = Client()

        self.logged_in_user = 'bob'
        self.other_user = 'steve'
        self.invalid_user = 'mike'
        self.usernames = [self.logged_in_user, self.other_user]
        self.save_users()
        self.setup_models()
        self.client.login(username=self.logged_in_user,
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

    def make_layer(self, layer_name, is_public=False):
        layer = {
            'name': layer_name,
            'is_public': is_public,
            'capture_start': '2015-08-15',
            'capture_end': '2015-08-15',
            'images': [
                'http://www.foo.com',
                'http://www.bar.com',
            ],
            'tags': [
                layer_name
            ],
            'area': 0,
        }
        return layer

    def save_layer(self, layer, user):
        url = reverse('create_layer', kwargs={'username': user.username})
        return self.client.post(url, layer)

    def setup_models(self):
        """
        Create a public and private layer for each user.
        """
        for username in self.usernames:
            self.client.login(username=username, password=username)

            user = self.user_models[username]
            layer_name = username + ' Public Layer'
            layer = self.make_layer(layer_name, is_public=True)
            self.save_layer(layer, user)

            layer_name = username + ' Private Layer'
            layer = self.make_layer(layer_name, is_public=False)
            self.save_layer(layer, user)


class LayerTestCase(AbstractLayerTestCase):
    # Create
    def test_create_layer(self):
        user = self.user_models[self.logged_in_user]
        layer = self.make_layer('Test Layer', self.logged_in_user)
        response = self.save_layer(layer, user)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data['images']), 2)
        self.assertEqual(len(response.data['tags']), 1)

        self.assertEqual(Layer.objects.filter(name='Test Layer').count(), 1)

    def test_create_layer_no_permission(self):
        user = self.user_models[self.other_user]
        layer = self.make_layer('Test Layer', self.other_user)
        response = self.save_layer(layer, user)
        self.assertEqual(response.status_code, 401)

    # List
    def test_list_all_layers(self):
        url = reverse('all_layers')
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        # 2 layers from logged_in_user, and 1 public layer from other_user
        self.assertEqual(len(response.data), 3)

    def test_list_layers_from_logged_in_user(self):
        url = reverse('user_layers', kwargs={'username': self.logged_in_user})
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 2)

    def test_list_layers_from_other_user(self):
        url = reverse('user_layers', kwargs={'username': self.other_user})
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)

    def test_list_layers_from_invalid_user(self):
        url = reverse('user_layers', kwargs={'username': self.invalid_user})
        response = self.client.get(url)
        self.assertEqual(response.status_code, 404)

    # Filtering and Sorting
    def test_list_layers_with_filter_by_name_from_logged_in_user(self):
        url = reverse('user_layers', kwargs={'username': self.logged_in_user})
        url += '?name=%s Public Layer' % (self.logged_in_user,)
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['name'],
                         '%s Public Layer' % (self.logged_in_user,))

    def test_list_layers_with_filter_by_tag_from_logged_in_user(self):
        url = reverse('user_layers', kwargs={'username': self.logged_in_user})
        url += '?tag=%s+Public+Layer' % (self.logged_in_user,)
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['name'],
                         '%s Public Layer' % (self.logged_in_user,))

        url = reverse('user_layers', kwargs={'username': self.logged_in_user})
        url += '?tag=invalid+tag'
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 0)

    def test_list_layers_with_sorting_from_logged_in_user(self):
        url = reverse('user_layers', kwargs={'username': self.logged_in_user})
        url += '?ordering=name'
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 2)
        self.assertEqual(response.data[0]['name'],
                         '%s Private Layer' % (self.logged_in_user,))
        self.assertEqual(response.data[1]['name'],
                         '%s Public Layer' % (self.logged_in_user,))

    # Retrieve
    def test_retrieve_layer_from_logged_in_user(self):
        user = self.user_models[self.logged_in_user]
        layer = Layer.objects.filter(user=user, is_public=True)[0]
        url = reverse('layer_detail', kwargs={
            'username': user.username,
            'layer_id': layer.id,
        })
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data['name'],
                         '%s Public Layer' % (self.logged_in_user,))

    def test_retrieve_invalid_layer_from_logged_in_user(self):
        url = reverse('layer_detail', kwargs={
            'username': self.logged_in_user,
            'layer_id': 100,
        })
        response = self.client.get(url)
        self.assertEqual(response.status_code, 404)

    def test_retrieve_public_layer_from_other_user(self):
        user = self.user_models[self.other_user]
        layer = Layer.objects.filter(user=user, is_public=True)[0]
        url = reverse('layer_detail', kwargs={
            'username': user.username,
            'layer_id': layer.id,
        })
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data['name'],
                         '%s Public Layer' % (self.other_user,))

    def test_retrieve_private_layer_from_other_user(self):
        user = self.user_models[self.other_user]
        layer = Layer.objects.filter(user=user, is_public=False)[0]
        url = reverse('layer_detail', kwargs={
            'username': user.username,
            'layer_id': layer.id,
        })
        response = self.client.get(url)
        self.assertEqual(response.status_code, 404)

    # Retrieve LayerMeta
    def test_retrieve_meta_from_logged_in_user(self):
        user = self.user_models[self.logged_in_user]
        layer = Layer.objects.filter(user=user, is_public=True)[0]
        url = reverse('layer_meta', kwargs={
            'username': user.username,
            'layer_id': layer.id,
        })
        response = self.client.get(url)

        # TODO: Test that meta yields "CREATED"

        LayerMeta.objects.create(layer=layer, state='IN PROGRESS')
        LayerMeta.objects.create(layer=layer, state='DONE')

        url = reverse('layer_meta', kwargs={
            'username': user.username,
            'layer_id': layer.id,
        })
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data['state'], 'DONE')

    # Destroy
    def test_destroy_layer_from_logged_in_user(self):
        user = self.user_models[self.logged_in_user]
        layer = Layer.objects.filter(user=user, is_public=True)[0]
        url = reverse('layer_detail', kwargs={
            'username': user.username,
            'layer_id': layer.id,
        })
        response = self.client.delete(url)
        self.assertEqual(response.status_code, 200)

    def test_destroy_invalid_layer_from_logged_in_user(self):
        url = reverse('layer_detail', kwargs={
            'username': self.logged_in_user,
            'layer_id': 100,
        })
        response = self.client.delete(url)
        self.assertEqual(response.status_code, 404)

    def test_destroy_layer_from_other_user(self):
        user = self.user_models[self.other_user]
        layer = Layer.objects.filter(user=user, is_public=True)[0]
        url = reverse('layer_detail', kwargs={
            'username': user.username,
            'layer_id': layer.id,
        })
        response = self.client.delete(url)
        self.assertEqual(response.status_code, 401)

        layer = Layer.objects.filter(user=user, is_public=False)[0]
        url = reverse('layer_detail', kwargs={
            'username': user.username,
            'layer_id': layer.id,
        })
        response = self.client.delete(url)
        self.assertEqual(response.status_code, 404)


class FavoriteTestCase(AbstractLayerTestCase):
    def setup_models(self):
        super(FavoriteTestCase, self).setup_models()
        self.setup_favorites()

    def save_favorite(self, layer, user):
        return UserFavoriteLayer.objects.create(layer=layer, user=user)

    def setup_favorites(self):
        # logged_in_user favorites other_user's public layer
        layer = Layer.objects.filter(user__username=self.other_user,
                                     is_public=True)[0]
        self.save_favorite(layer, self.user_models[self.logged_in_user])

    # Create
    def test_create_favorite_from_other_user_public_layer(self):
        user = self.user_models[self.other_user]
        layer = Layer.objects.filter(user=user, is_public=True)[0]
        url = reverse('create_or_destroy_favorite', kwargs={
            'layer_id': layer.id,
        })
        response = self.client.post(url)
        self.assertEqual(response.status_code, 200)

        favorites = UserFavoriteLayer.objects.filter(
            user__username=self.logged_in_user)

        self.assertEqual(len(favorites), 1)
        self.assertEqual(favorites[0].layer, layer)

    def test_create_favorite_from_other_user_private_layer(self):
        user = self.user_models[self.other_user]
        layer = Layer.objects.filter(user=user, is_public=False)[0]
        url = reverse('create_or_destroy_favorite', kwargs={
            'layer_id': layer.id,
        })
        response = self.client.post(url)
        self.assertEqual(response.status_code, 404)

    # List
    def test_list_favorites_from_logged_in_user(self):
        url = reverse('my_favorites')
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)

    # Destroy
    def test_destroy_favorite_from_logged_in_user(self):
        user = self.user_models[self.other_user]
        layer = Layer.objects.filter(user=user, is_public=False)[0]
        url = reverse('create_or_destroy_favorite', kwargs={
            'layer_id': layer.id,
        })
        response = self.client.delete(url)
        self.assertEqual(response.status_code, 404)

    def test_destroy_invalid_favorite_from_logged_in_user(self):
        url = reverse('create_or_destroy_favorite', kwargs={
            'layer_id': 100,
        })
        response = self.client.delete(url)
        self.assertEqual(response.status_code, 404)

    def test_destroy_favorite_from_other_user(self):
        user = self.user_models[self.other_user]
        layer = Layer.objects.filter(user=user, is_public=True)[0]
        url = reverse('create_or_destroy_favorite', kwargs={
            'layer_id': layer.id,
        })
        response = self.client.delete(url)
        self.assertEqual(response.status_code, 200)
