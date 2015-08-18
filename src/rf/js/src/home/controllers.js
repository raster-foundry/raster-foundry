'use strict';

var $ = require('jquery'),
    React = require('react'),
    router = require('../router').router,
    coreViews = require('../core/views'),
    Login = require('./components/login'),
    Library = require('./components/library');

var HomeController = {
    index: function() {
        var el = $('#container').get(0);
        React.render(<Library />, el);

        this.mapView = new coreViews.MapView({
            el: '#map'
        });

        bindEvents();
    }
};

var UserController = {
    login: function() {
        var props = {
            handleLogin: function(e) {
                e.preventDefault();
                router.go('/');
            }
        };

        var el = $('#container').get(0);
        React.render(<Login {...props} />, el);
    },

    logout: function() {
        router.go('/login');
    }
};

// Copied from prototype.
function bindEvents() {
    $('#dl-menu').dlmenu();

    // Tooltips
    $('[data-toggle="tooltip"]').tooltip({
        container: '.sidebar-utility-content',
        viewport: '.sidebar'
    });

    // Layer metadata
    var layerDetail = $('.layer-detail');
    $('.list-group-item .list-group-link').click(function(evt) {
        evt.preventDefault();
        layerDetail.addClass('active');
    });

    $('.layer-detail .close').click(function(evt) {
        evt.preventDefault();
        layerDetail.addClass('slideOutLeft');
        setTimeout(function() {
            layerDetail.removeClass('slideOutLeft active');
        }, 400);
    });

    // Image metadata
    var imageMetadata = $('.image-metadata');
    $('.view-metadata').click(function(evt) {
        evt.preventDefault();
        imageMetadata.addClass('active');
    });

    $('.image-metadata .close').click(function(evt) {
        evt.preventDefault();
        imageMetadata.addClass('slideOutLeft');
        setTimeout(function() {
            imageMetadata.removeClass('slideOutLeft active');
        }, 400);
    });

    // Layer tools
    $('.select-all').click(function() {
        $(this).parent('.utility-tools-secondary').toggleClass('active');
    });
}

module.exports = {
    HomeController: HomeController,
    UserController: UserController
};
