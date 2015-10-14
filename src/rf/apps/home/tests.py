# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json

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
                {
                    'file_name': 'foo.png',
                    's3_uuid': 'a8098c1a-f86e-11da-bd1a-00112444be1e',
                    'file_extension': 'png'
                },
                {
                    'file_name': 'bar.png',
                    's3_uuid': 'a8098c1a-f86e-11da-bd1a-00112444be1e',
                    'file_extension': 'png'
                },
            ],
            'tags': [
                layer_name
            ],
            'area': 0,
        }
        return layer

    def save_layer(self, layer, user):
        url = reverse('create_layer', kwargs={'username': user.username})
        return self.client.post(url,
                                json.dumps(layer),
                                content_type='application/json')

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

    def make_many_layers(self):
        """
        Create 30 public layers (15 for each user).
        """
        for username in self.usernames:
            self.client.login(username=username, password=username)
            user = self.user_models[username]

            for i in range(0, 15):
                layer_name = username + ' Public Layer ' + str(i)
                layer = self.make_layer(layer_name, is_public=True)
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

    def test_create_layer_date_errors(self):
        # start is after end
        layer = {
            'name': 'n',
            'capture_start': '2015-08-15',
            'capture_end': '2015-08-14',
        }
        user = self.user_models[self.logged_in_user]
        response = self.save_layer(layer, user)
        self.assertEqual(response.status_code, 403)

    # Modify
    def test_modify_layer(self):
        orig_name = 'Test Modify Layer'
        user = self.user_models[self.logged_in_user]
        layer = self.make_layer(orig_name, self.logged_in_user)
        response = self.save_layer(layer, user)

        new_name = 'Test Modify Layer 2'
        layer = json.loads(response.content)
        layer['name'] = new_name
        url = reverse('layer_detail', kwargs={
            'username': user.username,
            'layer_id': layer['id']
        })

        response = self.client.put(url,
                                   json.dumps(layer),
                                   content_type='application/json')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(Layer.objects.filter(name=orig_name).count(), 0)
        self.assertEqual(Layer.objects.filter(name=new_name).count(), 1)

    # List
    def test_list_all_layers(self):
        url = reverse('catalog')
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        # 2 layers from logged_in_user, and 1 public layer from other_user
        self.assertEqual(response.data['pages'], 1)
        self.assertEqual(response.data['current_page'], 1)
        self.assertEqual(len(response.data['layers']), 3)

    def test_list_layers_from_logged_in_user(self):
        url = reverse('user_layers', kwargs={'username': self.logged_in_user})
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data['layers']), 2)

    def test_list_layers_from_other_user(self):
        url = reverse('user_layers', kwargs={'username': self.other_user})
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data['layers']), 1)

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
        layers = response.data['layers']
        self.assertEqual(len(layers), 1)
        self.assertEqual(layers[0]['name'],
                         '%s Public Layer' % (self.logged_in_user,))

    def test_list_layers_with_filter_by_tag_from_logged_in_user(self):
        url = reverse('user_layers', kwargs={'username': self.logged_in_user})
        url += '?tag=%s+Public+Layer' % (self.logged_in_user,)
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        layers = response.data['layers']
        self.assertEqual(len(layers), 1)
        self.assertEqual(layers[0]['name'],
                         '%s Public Layer' % (self.logged_in_user,))

        url = reverse('user_layers', kwargs={'username': self.logged_in_user})
        url += '?tag=invalid+tag'
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data['layers']), 0)

    def test_list_layers_with_sorting_from_logged_in_user(self):
        url = reverse('user_layers', kwargs={'username': self.logged_in_user})
        url += '?ordering=name'
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        layers = response.data['layers']
        self.assertEqual(len(layers), 2)
        self.assertEqual(layers[0]['name'],
                         '%s Private Layer' % (self.logged_in_user,))
        self.assertEqual(layers[1]['name'],
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
        url = reverse('favorites')
        response = self.client.get(url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data['layers']), 1)

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


class PaginationTestCase(AbstractLayerTestCase):
    def setup_models(self):
        Layer.objects.all().delete()
        super(PaginationTestCase, self).make_many_layers()

    def test_pagination(self):
        def pagination_assertion(response, pages, current, layers):
            self.assertEqual(response.status_code, 200)
            self.assertEqual(len(response.data), 5)
            self.assertEqual(int(response.data['pages']), pages)
            self.assertEqual(int(response.data['current_page']), current)
            self.assertEqual(len(response.data['layers']), layers)

        url = reverse('catalog')
        response = self.client.get(url)
        # 30 layers from 3 users
        pagination_assertion(response, 3, 1, 10)

        # Page 2.
        response = self.client.get(url + '?page=2')
        pagination_assertion(response, 3, 2, 10)

        # Page 3.
        response = self.client.get(url + '?page=3')
        pagination_assertion(response, 3, 3, 10)

        # Numbers greater than the last page should return the last page.
        response = self.client.get(url + '?page=4')
        pagination_assertion(response, 3, 3, 10)

        # Non numbers should return the first page.
        response = self.client.get(url + '?page=foo')
        pagination_assertion(response, 3, 1, 10)

        # page_size=0 should return all layers on a single page
        response = self.client.get(url + '?page=foo&page_size=0')
        pagination_assertion(response, 1, 1, 30)

        # page_size=5 should return 5 layers per page
        response = self.client.get(url + '?page_size=5')
        pagination_assertion(response, 6, 1, 5)


class OrderingAndFilteringTestCase(AbstractLayerTestCase):
    layer_data = [
        {
            'name': 'Alpha',
            'tag': 'tag1',
            'area': 3,
            'capture_start': '2015-08-15',
            'capture_end': '2015-08-15',
            'srid': 'mercator'
        },
        {
            'name': 'Beta',
            'tag': 'tag2',
            'area': 4,
            'capture_start': '2015-08-19',
            'capture_end': '2015-08-19',
            'srid': 'mercator'
        },
        {
            'name': 'Gamma',
            'tag': 'tag3',
            'area': 5,
            'capture_start': '2015-08-08',
            'capture_end': '2015-08-08',
            'srid': '4326'
        },
        {
            'name': 'Delta',
            'tag': 'tag4',
            'area': 6,
            'capture_start': '2015-08-02',
            'capture_end': '2015-08-02',
            'srid': 'utm'
        },
        {
            'name': 'Epsilon',
            'tag': 'tag4',
            'area': 1,
            'capture_start': '2015-08-22',
            'capture_end': '2015-08-22',
            'srid': 'epsg'
        },
        {
            'name': 'Zeta',
            'tag': 'tag5',
            'area': 2,
            'capture_start': '2015-08-21',
            'capture_end': '2015-08-21',
            'srid': '4326'
        }
    ]

    def setup_models(self):
        Layer.objects.all().delete()

        username = self.usernames[0]
        self.client.login(username=username, password=username)
        user = self.user_models[username]

        for data in self.layer_data:
            layer_name = data['name']
            tag = data['tag']
            layer = self.make_layer(layer_name, is_public=True)
            layer['tags'] = [tag]
            layer['area'] = data['area']
            layer['capture_start'] = data['capture_end']
            layer['capture_end'] = data['capture_end']
            layer['srid'] = data['srid']
            # Organization name is the reverse of the name.
            layer['organization'] = data['name'][::-1]
            self.save_layer(layer, user)

    def confirm_order(self, layers, order):
        for i in range(0, len(layers)):
            layer_index = order[i] - 1
            self.assertEqual(layers[i]['name'],
                             self.layer_data[layer_index]['name'])

    def test_area_ordering(self):
        url = reverse('catalog')
        response = self.client.get(url + '?o=area')
        self.assertEqual(int(response.data['pages']), 1)
        self.assertEqual(int(response.data['current_page']), 1)
        layers = response.data['layers']
        self.assertEqual(len(layers), 6)
        self.confirm_order(layers, [5, 6, 1, 2, 3, 4])

    def test_start_ordering(self):
        url = reverse('catalog')
        response = self.client.get(url + '?o=capture_start')
        layers = response.data['layers']
        self.assertEqual(len(layers), 6)
        self.confirm_order(layers, [4, 3, 1, 2, 6, 5])

    def test_end_ordering(self):
        url = reverse('catalog')
        response = self.client.get(url + '?o=capture_end')
        layers = response.data['layers']
        self.assertEqual(len(layers), 6)
        self.confirm_order(layers, [4, 3, 1, 2, 6, 5])

    def test_srid_ordering(self):
        url = reverse('catalog')
        response = self.client.get(url + '?o=srid')
        layers = response.data['layers']
        self.assertEqual(len(layers), 6)
        order = ['4326', '4326', 'epsg', 'mercator', 'mercator', 'utm']
        for i in range(0, 6):
            self.assertEqual(layers[i]['srid'], order[i])

    def test_filter_tag1(self):
        url = reverse('catalog')
        response = self.client.get(url + '?name_search=ta')
        layers = response.data['layers']
        self.assertEqual(len(layers), 6)

    def test_filter_tag2(self):
        url = reverse('catalog')
        response = self.client.get(url + '?name_search=tag4')
        layers = response.data['layers']
        self.assertEqual(len(layers), 2)

    def test_filter_tag3(self):
        url = reverse('catalog')
        response = self.client.get(url + '?name_search=et')
        layers = response.data['layers']
        self.assertEqual(len(layers), 2)

    def test_filter_tag4(self):
        url = reverse('catalog')
        response = self.client.get(url + '?name_search=gamma')
        layers = response.data['layers']
        self.assertEqual(len(layers), 1)

    def test_filter_tag5(self):
        url = reverse('catalog')
        response = self.client.get(url + '?name_search=ammag')
        layers = response.data['layers']
        self.assertEqual(len(layers), 1)


class PaginationSortingAndFilteringTestCase(AbstractLayerTestCase):
    def setup_models(self):
        Layer.objects.all().delete()
        username = self.usernames[0]
        self.client.login(username=username, password=username)
        user = self.user_models[username]

        # Add one layer that can be filtered out.
        layer_name = 'TK'
        layer = self.make_layer(layer_name, is_public=True)
        layer['tags'] = ['TKTK']
        layer['area'] = 10
        layer['capture_start'] = '2015-01-01'
        layer['capture_end'] = '2015-01-01'
        layer['organization'] = 'TK'
        self.save_layer(layer, user)

        # Add 30 layers.
        super(PaginationSortingAndFilteringTestCase, self).make_many_layers()

    def test_next_prev_pages(self):
        url = reverse('catalog')
        response = self.client.get(url)
        self.assertEqual(response.data['prev_url'], None)
        self.assertEqual(response.data['next_url'], '/catalog.json?page=2')
        self.assertEqual(int(response.data['pages']), 4)

        # Filter down to 30 results.
        response = self.client.get(url + '?name_search=pub&o=area')
        self.assertEqual(response.data['prev_url'], None)
        self.assertEqual(response.data['next_url'],
                         '/catalog.json?name_search=pub&page=2&o=area')
        self.assertEqual(int(response.data['pages']), 3)

        # Go to page 2
        response = self.client.get(url + '?name_search=pub&page=2&o=area')
        self.assertEqual(response.data['prev_url'],
                         '/catalog.json?name_search=pub&page=1&o=area')
        self.assertEqual(response.data['next_url'],
                         '/catalog.json?name_search=pub&page=3&o=area')

        # Go to page 3
        response = self.client.get(url + '?name_search=pub&page=3&o=area')
        self.assertEqual(response.data['prev_url'],
                         '/catalog.json?name_search=pub&page=2&o=area')
        self.assertEqual(response.data['next_url'], None)
