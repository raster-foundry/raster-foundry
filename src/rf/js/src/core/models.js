'use strict';

var _ = require('underscore'),
    $ = require('jquery'),
    Backbone = require('../../shim/backbone'),
    L = require('leaflet'),
    utils = require('./utils');

var MapModel = Backbone.Model.extend({
    defaults: {
        lat: 0,
        lng: 0,
        zoom: 0
    }
});

var TabModel = Backbone.Model.extend({
    defaults: {
        activeTab: 'imports'
    }
});

var Layer = Backbone.Model.extend({
    defaultStatuses: {
        status_created: false,
        status_validate_start: false,
        status_validate_end: false,
        status_thumbnail_start: false,
        status_thumbnail_end: false,
        status_create_cluster_start: false,
        status_create_cluster_end: false,
        status_chunk_start: false,
        status_chunk_end: false,
        status_mosaic_start: false,
        status_mosaic_end: false,
        status_failed: false,
        status_completed: false,
        status_validate_error: null,
        status_thumbnail_error: null,
        status_create_cluster_error: null,
        status_chunk_error: null,
        status_mosaic_error: null,
        status_failed_error: null
    },

    defaults: function() {
        return _.defaults({
            name: '',
            organization: '',
            description: '',
            area: 0,
            area_unit: 0,
            capture_end: null,
            capture_start: null,
            srid: null,
            images: [],
            tags: [],
            thumb_small: '',
            thumb_large: '',
            active_image: false,
            url: null,
            // These fields are only on the client-side model.
            client_status_upload_start: false,
            client_status_upload_end: false,
            client_status_upload_error: null
        }, this.defaultStatuses);
    },

    initialize: function() {
        this.getLeafletLayer = _.memoize(this.getLeafletLayer);
        this.numUploadsDone = 0;
    },

    startUploading: function() {
        this.set('client_status_upload_start', true);
    },

    getUploadImages: function() {
        return _.filter(this.get('images'), function(image) {
            return !image.source_s3_bucket_key;
        });
    },

    hasUploadImages: function() {
        return this.getUploadImages().length > 0;
    },

    incrementUploadsDone: function() {
        this.numUploadsDone++;
        if (this.numUploadsDone === this.getUploadImages().length) {
            this.set('client_status_upload_end', true);
        }
    },

    setUploadError: function(error) {
        this.set({
            client_status_upload_end: true,
            client_status_upload_error: error
        });
    },

    isUploading: function() {
        // Use client status so that this works correctly after
        // hitting refresh after an upload fails (or mid-upload).
        return this.get('client_status_upload_start') &&
               !this.get('client_status_upload_end');
    },

    isUploaded: function() {
        var uploadImages = this.getUploadImages(),
            allBackendUploaded = _.every(uploadImages, function(image) {
                return image.status_transfer_end &&
                       !image.status_transfer_error;
            });

        return this.get('client_status_upload_end') || allBackendUploaded;
    },

    getUploadError: function() {
        // Indirectly infer if there was error so this works correctly
        // when hitting refresh after an upload fails (or mid-upload).
        var isError = !this.isUploading() && !this.isUploaded(),
            defaultError = 'Upload failed.',
            clientUploadError = this.get('client_status_upload_error');

        if (isError) {
            return clientUploadError ? clientUploadError : defaultError;
        }
        return null;
    },

    resetStatuses: function() {
        var images = this.get('images');
        this.set(this.defaultStatuses);
        _.each(images, function(image) {
            image.status_thumbnail_error = null;
            image.status_transfer_error = null;
            image.status_validate_error = null;
        });
        this.set('images', images);
    },

    url: function() {
        return this.get('url');
    },

    getLeafletLayer: function() {
        return new L.TileLayer(this.get('tile_url'));
    },

    getBounds: function() {
        return this.get('bounds');
    },

    getActiveImage: function() {
        var id = this.get('active_image');
        return _.findWhere(this.get('images'), {id: id});
    },

    getCopyImages: function() {
        return _.filter(this.get('images'), function(image) {
            return !!image.source_s3_bucket_key;
        });
    },

    hasCopyImages: function() {
        return this.getCopyImages().length > 0;
    },

    isCopyErrors: function() {
        var copyImages = this.getCopyImages();
        return _.some(copyImages, function(image) {
            return image.status_transfer_error;
        });
    },

    isCopying: function() {
        var copyImages = this.getCopyImages(),
            someStarted = _.some(copyImages, function(image) {
                return image.status_transfer_start;
            });
        return !this.isCopyErrors() && someStarted;
    },

    isCopied: function() {
        var copyImages = this.getCopyImages();
        return _.every(copyImages, function(image) {
            return image.status_transfer_end &&
                   !image.status_transfer_error;
        });
    },

    isCompleted: function() {
        return this.get('status_completed');
    },

    isFailed: function() {
        return this.get('status_failed');
    },

    isProcessing: function() {
        return !(this.isCompleted() || this.isFailed());
    },

    isDoneWorking: function() {
        return this.isCompleted() || this.isFailed();
    },

    getStatusByName: function(status) {
        var start = 'status_' + status + '_start',
            end = 'status_' + status + '_end',
            error = 'status_' + status + '_error';

        if (!this.layerStatusIsKnown(status)) {
            return {
                'started': null,
                'finished': null,
                'failed': null
            };
        }

        return {
            'started': this.get(start),
            'finished': this.get(end),
            'failed': this.get(error) !== null
        };
    },

    getErrorByName: function(status) {
        var error = 'status_' + status + '_error';

        if (!this.layerStatusIsKnown(status)) {
            return null;
        }
        return this.get(error);
    },

    layerStatusIsKnown: function(status) {
        var allowedStatuses = [
            'chunk',
            'create_cluster',
            'completed',
            'created',
            'failed',
            'mosaic',
            'thumbnail',
            'transfer',
            'validate'
        ];
        if (!_.contains(allowedStatuses, status)) {
            console.error('Unknown layer status. "' + status + '" is not recognized.');
            return false;
        }
        return true;
    },

    retryPossible: function () {
        return this.isFailed();
    },

    dismiss: function() {
        $.ajax({
            url: this.get('dismiss_url'),
            method: 'POST',
            data: { layer_id: this.get('id') }
        });
    },

    retry: function() {
        if (this.retryPossible()) {
            $.ajax({
                url: this.get('retry_url'),
                method: 'POST',
                data: { layer_id: this.get('id') }
            });

            // Set the model here so the user can get instant feedback that
            // the retry is in progress. Also causes a fetch to occur at the next
            // polling step by making the status non-failed.
            this.resetStatuses();
            this.set('status_created', true);
            if (this.hasCopyImages()) {
                this.set({
                    status_transfer_start: true
                });
            }
        }
    }
});

var BaseLayers = Backbone.Collection.extend({
    model: Layer,
    currentPage: 1,
    pages: 1,
    prevUrl: null,
    nextUrl: null,

    hasPrev: function() {
        return this.prevUrl !== null;
    },

    hasNext: function() {
        return this.nextUrl !== null;
    },

    getPrevPage: function() {
        if (this.prevUrl) {
            var data = utils.parseQueryData(this.prevUrl);
            this.fetch({ data: data });
        }
    },

    getNextPage: function() {
        if (this.nextUrl) {
            var data = utils.parseQueryData(this.nextUrl);
            this.fetch({ data: data });
        }
    },

    parse: function(data) {
        this.currentPage = data.current_page;
        this.pages = data.pages;
        this.prevUrl = data.prev_url;
        this.nextUrl = data.next_url;
        return data.layers;
    },

    getActiveLayer: function() {
        return this.findWhere({ active: true });
    },

    setActiveLayer: function(model) {
        var activeLayer = this.getActiveLayer();
        if (activeLayer) {
            activeLayer.set({
                active: false,
                active_image: false
            });
        }
        if (model) {
            model.set({
                active: true,
                active_image: false
            });
        }
    },

    setActiveImage: function(imageId) {
        var activeLayer = this.getActiveLayer();
        if (activeLayer) {
            activeLayer.set('active_image', imageId);
        }
    }
});

var MyLayers = BaseLayers.extend({
    url: '/imports.json'
});

var FavoriteLayers = BaseLayers.extend({
    url: '/favorites.json'
});

var PublicLayers = BaseLayers.extend({
    url: '/catalog.json'
});

var PendingLayers = BaseLayers.extend({
    url: function() {
        return '/imports.json?page_size=0&pending=true&o=-created_at';
    },

    existsUploading: function() {
        var uploading = this.find(function(layer) {
            return layer.isUploading();
        });
        return !!uploading;
    },

    existsCopying: function() {
        var copying = this.find(function(layer) {
            return layer.isCopying();
        });
        return !!copying;
    },

    existsProcessing: function() {
        var processing = this.find(function(layer) {
            return layer.isProcessing();
        });
        return !!processing;
    }
});

module.exports = {
    FavoriteLayers: FavoriteLayers,
    Layer: Layer,
    MapModel: MapModel,
    MyLayers: MyLayers,
    PublicLayers: PublicLayers,
    PendingLayers: PendingLayers,
    TabModel: TabModel
};
