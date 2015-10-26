# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import random
from datetime import datetime, timedelta

from django.core.management.base import BaseCommand
from django.contrib.auth.models import User

from apps.core import enums
from apps.core.models import (Layer, LayerImage, LayerTag,
                              LayerMeta, UserFavoriteLayer)
from random_words import RandomNicknames, RandomWords, LoremIpsum


class Command(BaseCommand):
    def handle(self, *args, **options):
        rn = RandomNicknames()
        rw = RandomWords()
        li = LoremIpsum()

        layers = Layer.objects.all()
        layer_ids = layers.values_list('id', flat=True)

        self.stdout.write('Deleting records')

        LayerMeta.objects.filter(layer__id__in=layer_ids).delete()
        LayerImage.objects.filter(layer__id__in=layer_ids).delete()
        LayerTag.objects.filter(layer__id__in=layer_ids).delete()
        UserFavoriteLayer.objects.all().delete()

        User.objects.filter(is_superuser=False).delete()
        layers.delete()

        # These usernames all have one thing in common.
        usernames = [
            'dudgeon',
            'wigeon',
            'surgeon',
            'georgian',
            'gorgeous',
            'outrageous',
            'courageous',
            'george',
            'gudgeon',
            'geocaching',
            'geodesic',
            'geology',
            'widgeon',
            'geologic',
            'pigeon',
            'geometry',
            'geologist',
        ]

        self.stdout.write('Generating sample data...')

        for username in usernames:
            u = User()
            u.username = username

            gender = random.choice(['m', 'f', 'u'])

            u.first_name = rn.random_nick(gender=gender)
            u.last_name = rn.random_nick(gender=gender)
            u.is_active = True
            u.save()

        for user in User.objects.all():
            for i in range(random.randint(3, 10)):
                layer = Layer()
                layer.user = user

                layer.name = li.get_sentence()
                layer.description = li.get_sentences(2)
                layer.organization = ' '.join(rw.random_words(count=5))
                layer.status = 'created'
                layer.status_updated_at = datetime.now()

                layer.is_public = random.random() <= 0.80

                days = random.randint(0, 60) - 30
                capture_start = datetime.now() + timedelta(days=days)
                capture_end = capture_start + timedelta(days=7)

                layer.capture_start = capture_start
                layer.capture_end = capture_end

                layer.area = random.randint(100, 1000)
                layer.area_unit = random.choice(enums.AREA_UNIT_CHOICES)[0]

                layer.projection = enums.WGS84
                layer.srid = enums.WGS84
                layer.tile_srid = enums.WGS84

                tile_format = random.choice(enums.TILE_FORMAT_CHOICES)[0]
                tile_origin = random.choice(enums.TILE_ORIGIN_CHOICES)[0]
                resampling = random.choice(enums.TILE_RESAMPLING_CHOICES)[0]
                transparency = random.choice(['#FF0000', '#00FF00', '#0000FF'])

                layer.tile_format = tile_format
                layer.tile_origin = tile_origin
                layer.resampling = resampling
                layer.transparency = transparency

                layer.thumb_small = 'http://placehold.it/80x80/ffffff/000000' \
                    if random.random() <= 0.80 else None
                layer.thumb_large = 'http://placehold.it/400x150/ffffff/000000' \
                    if random.random() <= 0.80 else None

                layer.save()

        tags = rw.random_words(count=25)

        for layer in layers.all():
            meta = LayerMeta()
            meta.layer = layer
            meta.state = 'created'
            meta.save()

            for i in range(random.randint(0, 5)):
                tag = LayerTag()
                tag.layer = layer
                tag.name = random.choice(tags)
                tag.save()

            for i in range(random.randint(1, 5)):
                image = LayerImage()
                image.layer = layer
                image.status = 'created'
                image.source_uri = 'http://'
                image.file_name = '_'.join(rw.random_words(count=2)) + '.png'
                image.thumb_small = 'http://placehold.it/80x80/ffffff/000000' \
                    if random.random() <= 0.80 else None
                image.thumb_large = 'http://placehold.it/300x300/ffffff/000000' \
                    if random.random() <= 0.80 else None
                image.priority = i
                image.save()

        layer_ids = Layer.objects.values_list('id', flat=True)

        for user in User.objects.all():
            for i in range(random.randint(1, 5)):
                layer_id = random.choice(layer_ids)
                layer = Layer.objects.get(id=layer_id)
                fav = UserFavoriteLayer()
                fav.user = user
                fav.layer = layer
                fav.save()

        self.stdout.write('Done')
